"""OpenTimestamps Bitcoin anchoring integration wrapping the repository's settlement package."""
from __future__ import annotations

import asyncio
import hashlib
from pathlib import Path
from typing import Any

import aiohttp

from .storage import SocialStore

# Standard OTS DetachedTimestampFile header + OpSHA256 tag (0x08).
OTS_HEADER = b"\x00OpenTimestamps\x00\x00Proof\x00\xbf\x89\xe2\xe8\x84\xe8\x92\x94\x01\x08"
DEFAULT_MAINNET_CALENDARS = (
    "https://a.pool.opentimestamps.org",
    "https://b.pool.opentimestamps.org",
    "https://a.pool.eternitywall.com",
    "https://ots.btc.catallaxy.com",
)


def _settlement_available() -> bool:
    try:
        import settlement.service  # noqa: F401
        return True
    except Exception:
        return False


class AnchorManager:
    def __init__(
        self,
        store: SocialStore,
        *,
        network: str = "mainnet",
        bitcoin_conf: str | None = None,
        calendars: tuple[str, ...] | None = None,
    ) -> None:
        self.store = store
        self.network = network if network in ("mainnet", "regtest") else "mainnet"
        self.bitcoin_conf = bitcoin_conf
        self.calendars = calendars or (DEFAULT_MAINNET_CALENDARS if self.network == "mainnet" else ())
        self.has_settlement_pkg = _settlement_available()

    def _proof_path(self, sha256: str) -> Path:
        return self.store.proofs_dir / f"{sha256}.ots"

    def _service(self) -> Any:
        from settlement.ots_backend import OTSConfig
        from settlement.service import SettlementService

        cfg = OTSConfig(
            timeout=10,
            calendars=self.calendars,
            bitcoin_conf=self.bitcoin_conf,
            network=self.network,
        )
        return SettlementService(cfg)

    async def stamp(self, sha256: str, payload_path: Path) -> dict[str, Any]:
        proof_path = self._proof_path(sha256)
        if proof_path.is_file() and proof_path.stat().st_size > 0:
            existing = self.store.get_anchor(sha256)
            if existing and existing["status"] in ("PENDING", "CONFIRMED"):
                return existing

        if self.has_settlement_pkg:
            return await asyncio.to_thread(self._stamp_with_settlement, sha256, payload_path)

        return await self._stamp_via_http_calendars(sha256, payload_path)

    def _stamp_with_settlement(self, sha256: str, payload_path: Path) -> dict[str, Any]:
        svc = self._service()
        # SettlementService stamps <payload.name>.ots into output_dir; copy/name by sha256.
        res = svc.stamp_file(payload_path, self.store.proofs_dir)
        produced = Path(res.proof_file) if res.proof_file else None
        canonical = self._proof_path(sha256)
        if produced and produced.is_file() and produced != canonical:
            canonical.write_bytes(produced.read_bytes())
        return self.store.upsert_anchor(
            sha256=sha256,
            status=res.status.value,
            network=res.network,
            proof_file=canonical.name if canonical.is_file() else None,
            block_height=res.bitcoin.block_height,
            block_time=res.bitcoin.block_time,
            diagnostics=[{"code": d.code, "message": d.message} for d in res.diagnostics],
        )

    async def _stamp_via_http_calendars(self, sha256: str, payload_path: Path) -> dict[str, Any]:
        digest = hashlib.sha256(payload_path.read_bytes()).digest()
        if digest.hex() != sha256:
            return self.store.upsert_anchor(
                sha256=sha256,
                status="ERROR",
                network=self.network,
                proof_file=None,
                block_height=None,
                block_time=None,
                diagnostics=[{"code": "digest_mismatch", "message": "Stored file SHA-256 mismatch."}],
            )
        if not self.calendars:
            return self.store.upsert_anchor(
                sha256=sha256,
                status="UNVERIFIABLE",
                network=self.network,
                proof_file=None,
                block_height=None,
                block_time=None,
                diagnostics=[{"code": "calendar_required", "message": "No OTS calendars configured for this network."}],
            )

        responses: list[bytes] = []
        timeout = aiohttp.ClientTimeout(total=8)
        headers = {
            "Accept": "application/vnd.opentimestamps.v1",
            "Content-Type": "application/x-www-form-urlencoded",
        }
        async with aiohttp.ClientSession(timeout=timeout) as session:
            for base in self.calendars:
                try:
                    async with session.post(f"{base.rstrip('/')}/digest", data=digest, headers=headers) as resp:
                        if resp.status == 200:
                            body = await resp.read()
                            if 10 <= len(body) <= 65536:
                                responses.append(body)
                                if len(responses) >= 2:
                                    break
                except Exception:
                    continue

        if not responses:
            return self.store.upsert_anchor(
                sha256=sha256,
                status="UNANCHORED",
                network=self.network,
                proof_file=None,
                block_height=None,
                block_time=None,
                diagnostics=[{
                    "code": "calendar_unavailable",
                    "message": "OpenTimestamps calendars unreachable right now; photo saved locally. Retry anchoring when online.",
                }],
            )

        # Build standard DetachedTimestampFile proof bytes (multi-branch fork byte 0xff between calendar branches).
        tree = b"".join(b"\xff" + part for part in responses[:-1]) + responses[-1]
        proof_bytes = OTS_HEADER + digest + tree
        canonical = self._proof_path(sha256)
        canonical.write_bytes(proof_bytes)

        return self.store.upsert_anchor(
            sha256=sha256,
            status="PENDING",
            network=self.network,
            proof_file=canonical.name,
            block_height=None,
            block_time=None,
            diagnostics=[{
                "code": "pending_bitcoin",
                "message": f"Submitted to {len(responses)} OpenTimestamps calendar(s). Awaiting Bitcoin block confirmation (~1–2 hours on mainnet).",
            }],
        )

    async def upgrade_and_verify(self, sha256: str, payload_path: Path) -> dict[str, Any]:
        proof_path = self._proof_path(sha256)
        if not proof_path.is_file():
            return await self.stamp(sha256, payload_path)

        if self.has_settlement_pkg:
            return await asyncio.to_thread(self._upgrade_verify_settlement, sha256, payload_path, proof_path)

        # Without opentimestamps-client + local Bitcoin Core RPC, preserve the .ots proof
        # and report honestly that local Bitcoin Core verification is unavailable here.
        current = self.store.get_anchor(sha256)
        return self.store.upsert_anchor(
            sha256=sha256,
            status=current["status"] if current else "PENDING",
            network=self.network,
            proof_file=proof_path.name,
            block_height=current["block_height"] if current else None,
            block_time=current["block_time"] if current else None,
            diagnostics=[{
                "code": "bitcoin_node_required",
                "message": "Proof .ots preserved. Run with settlement (Python 3.11+ & Bitcoin Core RPC) or ots verify locally to verify block confirmation.",
            }],
        )

    def _upgrade_verify_settlement(
        self, sha256: str, payload_path: Path, proof_path: Path
    ) -> dict[str, Any]:
        svc = self._service()
        svc.upgrade_proof(proof_path)
        res = svc.verify_file(payload_path, proof_path)
        return self.store.upsert_anchor(
            sha256=sha256,
            status=res.status.value,
            network=res.network,
            proof_file=proof_path.name,
            block_height=res.bitcoin.block_height,
            block_time=res.bitcoin.block_time,
            diagnostics=[{"code": d.code, "message": d.message} for d in res.diagnostics],
        )
