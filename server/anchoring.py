"""Legacy image-byte adapter. All proof operations use the settlement service."""
from __future__ import annotations
import asyncio
from contextlib import nullcontext
import hashlib
import os
from pathlib import Path
import re
import tempfile
import threading
import time
from settlement import Diagnostic, OTSConfig, SettlementResult, SettlementService, Status
from settlement.ots_backend import OTSBackend
from .storage import SocialStore

_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_REGTEST_LOCK = threading.Lock()


class AnchorManager:
    def __init__(self, store: SocialStore, *, network="mainnet", bitcoin_conf=None,
                 calendars=None, auto_confirm_regtest=None, mining_timeout=120):
        self.store = store
        self.bitcoin_conf = bitcoin_conf or os.environ.get("IXH26_BITCOIN_CONF")
        if calendars is None:
            calendars = tuple(c.strip() for c in os.environ.get("IXH26_CALENDARS", "").split(",") if c.strip())
        self.config = OTSConfig(network=network, bitcoin_conf=self.bitcoin_conf, calendars=calendars)
        self.network, self.calendars = network, self.config.calendars
        self.auto_confirm_regtest = (os.environ.get("IXH26_AUTO_CONFIRM_REGTEST", "0") == "1"
                                    if auto_confirm_regtest is None else auto_confirm_regtest)
        if type(self.auto_confirm_regtest) is not bool or (self.auto_confirm_regtest and network != "regtest"):
            raise ValueError("Automatic mining requires an explicitly selected regtest network.")
        if not 0 < mining_timeout <= 120:
            raise ValueError("Mining timeout must be positive and at most 120 seconds.")
        self.mining_timeout = mining_timeout
        self.has_settlement_pkg = True

    def _service(self):
        return SettlementService(self.config)

    def proof_path(self, sha256):
        if not _SHA256.fullmatch(sha256):
            raise ValueError("Invalid SHA-256.")
        current = self.store.get_anchor(sha256)
        name = current.get("proof_file") if current else None
        if name and Path(name).name == name:
            return self.store.proofs_dir / name
        legacy = self.store.proofs_dir / (sha256 + ".ots")
        if legacy.exists() or legacy.is_symlink():
            return legacy
        photo = self.store.get_photo(sha256)
        suffix = "." + photo["ext"] if photo else ""
        return self.store.proofs_dir / (sha256 + suffix + ".ots")

    def _record(self, sha256, result):
        proof_name = None
        if result.proof_file:
            proof = Path(result.proof_file)
            if proof.parent.resolve() == self.store.proofs_dir.resolve() and proof.is_file() and not proof.is_symlink():
                proof_name = proof.name
        return self.store.upsert_anchor(sha256=sha256, status=result.status.value, network=result.network,
            proof_file=proof_name, block_height=result.bitcoin.block_height, block_time=result.bitcoin.block_time,
            diagnostics=[{"code": d.code, "message": d.message} for d in result.diagnostics])

    def _node(self):
        return OTSBackend(self.config)._bitcoin_proxy(wallet="ots")

    @staticmethod
    def _require_regtest(node):
        info = node._call("getblockchaininfo")
        if (info.get("chain") != "regtest" or info.get("initialblockdownload", True)
                or info.get("blocks") != info.get("headers")):
            raise ValueError("A synchronized regtest node is required for mining.")

    def _mempool(self):
        node = self._node()
        try:
            self._require_regtest(node)
            return set(node._call("getrawmempool"))
        finally:
            node.close()

    def _confirm(self, service, payload, proof, baseline, result):
        """Bounded development action. The verify endpoint never calls this."""
        node = None
        try:
            deadline = time.monotonic() + self.mining_timeout
            node = self._node()
            self._require_regtest(node)
            while True:
                pending = set(node._call("getrawmempool"))
                if pending and (baseline is None or pending - baseline):
                    break
                if time.monotonic() >= deadline:
                    raise TimeoutError()
                time.sleep(min(0.25, max(0, deadline - time.monotonic())))
            self._require_regtest(node)
            if time.monotonic() >= deadline:
                raise TimeoutError()
            address = node._call("getnewaddress", "", "bech32")
            if time.monotonic() >= deadline:
                raise TimeoutError()
            node._call("generatetoaddress", 2, address)
            while time.monotonic() < deadline:
                upgraded = service.upgrade_proof(proof)
                verified = service.verify_file(payload, proof)
                verified.diagnostics.extend(upgraded.diagnostics)
                if verified.status != Status.PENDING:
                    return verified
                result = verified
                time.sleep(min(0.25, max(0, deadline - time.monotonic())))
            raise TimeoutError()
        except Exception:
            result.diagnostics.append(Diagnostic("auto_mining_unavailable",
                "Regtest confirmation did not complete; the existing proof is preserved. Retry explicitly."))
            return result
        finally:
            if node is not None:
                node.close()

    def _operate(self, sha256, payload, upgrade):
        with _REGTEST_LOCK if self.auto_confirm_regtest else nullcontext():
            service = self._service()
            proof = self.proof_path(sha256)
            baseline = None
            if self.auto_confirm_regtest and not proof.exists():
                try:
                    baseline = self._mempool()
                except Exception:
                    result = service.stamp_file(payload, self.store.proofs_dir)
                    result.diagnostics.append(Diagnostic("auto_mining_unavailable",
                        "A synchronized regtest node is required; no blocks were mined."))
                    return result
            if proof.exists() or proof.is_symlink():
                result = service.verify_file(payload, proof)
                if result.status in (Status.INVALID, Status.ERROR):
                    return result
                if upgrade:
                    upgraded = service.upgrade_proof(proof)
                    if upgraded.status == Status.ERROR:
                        return upgraded
                if upgrade:
                    result = service.verify_file(payload, proof)
                    result.diagnostics.extend(upgraded.diagnostics)
            else:
                result = service.stamp_file(payload, self.store.proofs_dir)
                if result.proof_file:
                    proof = Path(result.proof_file)
            if self.auto_confirm_regtest and result.status == Status.PENDING:
                result = self._confirm(service, payload, proof, baseline, result)
            return result

    async def stamp(self, sha256, payload_path):
        result = await asyncio.to_thread(self._operate, sha256, payload_path, False)
        return self._record(sha256, result)

    async def upgrade_and_verify(self, sha256, payload_path):
        result = await asyncio.to_thread(self._operate, sha256, payload_path, True)
        return self._record(sha256, result)

    async def verify_bytes(self, payload_bytes, *, expected_sha256=None, proof_bytes=None):
        """Local-only standard OTS verification; never upgrades, submits or mines."""
        actual = hashlib.sha256(payload_bytes).hexdigest()
        target = expected_sha256 or actual
        if not _SHA256.fullmatch(target):
            raise ValueError("Invalid SHA-256.")
        path = self.proof_path(target)
        if expected_sha256 is not None and actual != target:
            result = SettlementResult(Status.INVALID, network=self.network, payload_sha256=actual,
                diagnostics=[Diagnostic("hash_mismatch", "The file differs from the expected SHA-256.")])
        elif proof_bytes is None and (not path.is_file() or path.is_symlink()):
            result = SettlementResult(Status.UNVERIFIABLE, network=self.network, payload_sha256=actual,
                diagnostics=[Diagnostic("proof_not_found", "No local standard OTS proof is available.")])
        else:
            raw = proof_bytes if proof_bytes is not None else path.read_bytes()
            def verify():
                with tempfile.TemporaryDirectory(prefix="ixh26-verify-") as directory:
                    payload, proof = Path(directory) / "payload.bin", Path(directory) / "proof.ots"
                    payload.write_bytes(payload_bytes)
                    proof.write_bytes(raw)
                    return self._service().verify_file(payload, proof)
            result = await asyncio.to_thread(verify)
        report = result.to_dict()
        report.pop("proof_file", None)
        report.update(sha256=actual, expected_sha256=target,
            proof_url=f"/api/proofs/{target}.ots" if path.is_file() and not path.is_symlink() else None,
            block_height=result.bitcoin.block_height, block_time=result.bitcoin.block_time)
        return report
