"""Only this module knows OpenTimestamps or Bitcoin implementation details."""
from __future__ import annotations

from dataclasses import dataclass
from http.client import HTTPException
import os
from pathlib import Path
import subprocess
import sys
import threading
from typing import Any, BinaryIO
from urllib.parse import quote, urlparse

import bitcoin.rpc
from bitcoin.core.serialize import SerializationError
from opentimestamps.calendar import (
    CommitmentNotFoundError, DEFAULT_CALENDAR_WHITELIST,
)
from opentimestamps.core.notary import (
    BitcoinBlockHeaderAttestation, PendingAttestation, VerificationError,
)
from opentimestamps.core.op import OpSHA256
from opentimestamps.core.serialize import (
    BytesDeserializationContext, BytesSerializationContext, DeserializationError,
)
from opentimestamps.core.timestamp import DetachedTimestampFile
from otsclient.cmds import get_timestamp_within, remote_calendar

from .exceptions import InvalidProof, SettlementFailure
from .models import BitcoinEvidence, Diagnostic, Status

# Standard client submission endpoints. The upstream whitelist also recognizes
# the canonical calendar URIs embedded in their receipts.
DEFAULT_CALENDARS = (
    "https://a.pool.opentimestamps.org",
    "https://b.pool.opentimestamps.org",
    "https://a.pool.eternitywall.com",
    "https://ots.btc.catallaxy.com",
)
_CALENDAR_LOCK = threading.Lock()  # upstream helper has a process-global map


@dataclass(frozen=True)
class OTSConfig:
    timeout: int = 10
    calendars: tuple[str, ...] | None = None
    bitcoin_conf: str | None = None
    network: str = "mainnet"

    def __post_init__(self) -> None:
        if type(self.timeout) is not int or not 1 <= self.timeout <= 300:
            raise ValueError("timeout must be between 1 and 300 seconds")
        if self.network not in ("mainnet", "regtest"):
            raise ValueError("network must be mainnet or regtest")
        if self.calendars is None:
            object.__setattr__(self, "calendars", DEFAULT_CALENDARS if self.network == "mainnet" else ())
        for url in self.calendars:
            parsed = urlparse(url)
            schemes = ("http", "https") if self.network == "regtest" else ("https",)
            if (parsed.scheme not in schemes or not parsed.hostname or parsed.username
                    or parsed.password or parsed.query or parsed.fragment):
                raise ValueError("Calendars require an authorized URL without credentials, query, or fragment; HTTP requires regtest.")
            parsed.port  # Reject malformed ports before a network request.


class OTSBackend:
    def __init__(self, config: OTSConfig | None = None) -> None:
        self.config = config or OTSConfig()

    def parse(self, data: bytes) -> Any:
        try:
            proof = DetachedTimestampFile.deserialize(BytesDeserializationContext(data))
            if type(proof.file_hash_op) is not OpSHA256:
                raise InvalidProof()
            if not list(proof.timestamp.all_attestations()):
                raise InvalidProof("Proof contains no attestations.")
            return proof
        except (DeserializationError, ValueError, TypeError, RecursionError) as exc:
            raise InvalidProof() from exc

    def digest(self, data: bytes) -> bytes:
        return self.parse(data).file_digest

    def _require_calendars(self) -> None:
        if not self.config.calendars:
            raise SettlementFailure("calendar_required", "Configure an explicit calendar for stamp and upgrade.")

    def _bitcoin_proxy(self, wallet: str | None = None) -> Any:
        """Resolve RPC without mutating python-bitcoinlib's global chain params.

        Mainnet retains upstream config discovery. Regtest resolves its cookie
        explicitly because upstream otherwise uses the process-global network.
        This method is also used by the isolated development bootstrap/demo.
        """
        if self.config.network == "mainnet":
            return bitcoin.rpc.Proxy(btc_conf_file=self.config.bitcoin_conf,
                                     service_port=8332, timeout=self.config.timeout)
        if sys.platform == "darwin":
            default_dir = Path.home() / "Library/Application Support/Bitcoin"
        elif sys.platform == "win32":
            default_dir = Path(os.environ["APPDATA"]) / "Bitcoin"
        else:
            default_dir = Path.home() / ".bitcoin"
        conf = Path(self.config.bitcoin_conf).expanduser() if self.config.bitcoin_conf else default_dir / "bitcoin.conf"
        common: dict[str, str] = {}
        regtest: dict[str, str] = {}
        section = ""
        if conf.exists():
            for raw in conf.read_text().splitlines():
                line = raw.split("#", 1)[0].strip()
                if line.startswith("[") and line.endswith("]"):
                    section = line[1:-1]
                elif "=" in line and section in ("", "regtest"):
                    key, value = (part.strip() for part in line.split("=", 1))
                    (regtest if section else common)[key] = value
        settings = common | regtest
        if "includeconf" in settings:
            raise ValueError("Use a standalone RPC configuration, without includeconf")
        datadir = Path(settings.get("datadir", str(conf.parent))).expanduser()
        cookie = Path(settings.get("rpccookiefile", ".cookie")).expanduser()
        if not cookie.is_absolute():
            cookie = datadir / "regtest" / cookie
        try:
            username, password = cookie.read_text().strip().split(":", 1)
        except OSError:
            username, password = settings.get("rpcuser", ""), settings["rpcpassword"]
        host = settings.get("rpcconnect", "127.0.0.1")
        if ":" in host and not host.startswith("["):
            host = "[" + host + "]"
        port = int(settings.get("rpcport", "18443"))
        endpoint = f"http://{username}:{password}@{host}:{port}"
        parsed = urlparse(endpoint)
        # Upstream does not URL-decode credentials: reject ambiguous URLs rather
        # than silently using the wrong credentials or destination.
        if parsed.username != username or parsed.password != password or parsed.port != port or parsed.path:
            raise ValueError("RPC credentials cannot be represented safely; use cookie authentication")
        if wallet is not None:
            endpoint += "/wallet/" + quote(wallet, safe="")
        return bitcoin.rpc.Proxy(service_url=endpoint, timeout=self.config.timeout)

    def stamp(self, payload: BinaryIO) -> bytes:
        self._require_calendars()
        args = [sys.executable, "-c", "from otsclient.ots import main; main()",
                "--no-cache", "stamp", "--timeout", str(self.config.timeout),
                "-m", str(min(2, len(self.config.calendars)))]
        for calendar in self.config.calendars:
            args.extend(["-c", calendar])
        try:
            completed = subprocess.run(
                args, stdin=payload, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                timeout=self.config.timeout + 10, check=False,
            )
        except subprocess.TimeoutExpired as exc:
            raise SettlementFailure("calendar_timeout", "Calendar submission timed out.",
                                    Status.UNVERIFIABLE) from exc
        if completed.returncode:
            # Exit status is operational evidence only, never proof of validity.
            # Do not expose upstream stderr (URLs or local details may be present).
            raise SettlementFailure("submission_failed", "OTS client could not create a receipt.")
        self.parse(completed.stdout)
        return completed.stdout

    def upgrade(self, data: bytes) -> tuple[bytes, list[Diagnostic]]:
        self._require_calendars()
        proof = self.parse(data)
        if any(isinstance(a, BitcoinBlockHeaderAttestation)
               for _, a in proof.timestamp.all_attestations()):
            return data, []
        diagnostics: list[Diagnostic] = []
        requests: list[tuple[Any, str]] = []

        def walk(stamp: Any) -> None:
            for attestation in stamp.attestations:
                if isinstance(attestation, PendingAttestation):
                    uri = attestation.uri
                    if (uri in self.config.calendars or
                            (self.config.network == "mainnet" and uri in DEFAULT_CALENDAR_WHITELIST)):
                        requests.append((stamp, uri))
                    else:
                        diagnostics.append(Diagnostic("calendar_not_allowed", "Skipped an unapproved calendar."))
            for child in stamp.ops.values():
                walk(child)

        walk(proof.timestamp)
        failed = False
        # A failed request discards ALL tentative merges; caller keeps original.
        for stamp, uri in requests:
            try:
                with _CALENDAR_LOCK:
                    upgraded = get_timestamp_within(remote_calendar(uri), stamp.msg, self.config.timeout)
                stamp.merge(upgraded)
            except CommitmentNotFoundError:
                diagnostics.append(Diagnostic("calendar_pending", "Calendar has no completed commitment yet."))
            except Exception:
                # Upstream uses bare Exception for oversized/invalid HTTP
                # responses as well as typed network/deserialization errors.
                failed = True
                diagnostics.append(Diagnostic("calendar_unavailable", "Calendar upgrade failed; original proof preserved."))
        if failed:
            return data, diagnostics
        ctx = BytesSerializationContext()
        proof.serialize(ctx)
        result = ctx.getbytes()
        if self.digest(result) != self.digest(data):
            raise InvalidProof("Upgrade changed the payload commitment.")
        return result, diagnostics

    def verify(self, data: bytes) -> tuple[Status, BitcoinEvidence, list[Diagnostic]]:
        proof = self.parse(data)
        attestations = list(proof.timestamp.all_attestations())
        bitcoin_atts = sorted(
            ((msg, a) for msg, a in attestations if isinstance(a, BitcoinBlockHeaderAttestation)),
            key=lambda item: item[1].height,
        )
        if not bitcoin_atts:
            if any(isinstance(a, PendingAttestation) for _, a in attestations):
                return Status.PENDING, BitcoinEvidence(), [Diagnostic("pending", "Run upgrade later to retrieve Bitcoin attestations.")]
            return Status.UNVERIFIABLE, BitcoinEvidence(), [Diagnostic("unsupported_attestation", "No supported Bitcoin or pending attestation.")]
        proxy: Any = None
        try:
            proxy = self._bitcoin_proxy()
            info = proxy._call("getblockchaininfo")
            expected = "main" if self.config.network == "mainnet" else "regtest"
            if info["chain"] != expected:
                return Status.UNVERIFIABLE, BitcoinEvidence(), [Diagnostic("network_mismatch", "Bitcoin Core is on a different network from the explicitly selected network.")]
            if (info["initialblockdownload"]
                    or info["blocks"] < info["headers"]):
                return Status.UNVERIFIABLE, BitcoinEvidence(), [Diagnostic("node_not_ready", "A synchronized Bitcoin Core node on the selected network is required.")]
            invalid = False
            unavailable = False
            for msg, attestation in bitcoin_atts:
                if attestation.height > info["blocks"]:
                    unavailable = True
                    continue
                block_hash = proxy.getblockhash(attestation.height)
                header = proxy.getblockheader(block_hash)
                try:
                    block_time = attestation.verify_against_blockheader(msg, header)
                except VerificationError:
                    invalid = True
                    continue
                # Detect a reorg while fetching/verifying the header.
                if proxy.getblockhash(attestation.height) != block_hash:
                    unavailable = True
                    continue
                diagnostics = ([Diagnostic("regtest_only", "Confirmed on the local regtest chain; this is not a mainnet settlement.")]
                               if self.config.network == "regtest" else [])
                return Status.CONFIRMED, BitcoinEvidence(attestation.height, block_time), diagnostics
            if unavailable:
                return Status.UNVERIFIABLE, BitcoinEvidence(), [Diagnostic("block_unavailable", "Required active-chain block could not be verified.")]
            if invalid:
                return Status.INVALID, BitcoinEvidence(), [Diagnostic("bitcoin_mismatch", "Bitcoin attestation does not match the active-chain header.")]
        except (OSError, HTTPException, SerializationError, ValueError, TypeError,
                bitcoin.rpc.JSONRPCError, IndexError, KeyError):
            return Status.UNVERIFIABLE, BitcoinEvidence(), [Diagnostic("bitcoin_unavailable", "Bitcoin Core verification could not be completed.")]
        finally:
            if proxy is not None:
                proxy.close()
        return Status.UNVERIFIABLE, BitcoinEvidence(), [Diagnostic("bitcoin_unavailable", "Bitcoin verification could not be completed.")]
