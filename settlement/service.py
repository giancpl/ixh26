"""Public file operations with no knowledge of the OTS wire format."""
from __future__ import annotations

from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import stat
import tempfile
from typing import BinaryIO, Iterator

from .exceptions import InvalidProof, SettlementFailure
from .models import Diagnostic, SettlementResult, Status
from .ots_backend import OTSBackend, OTSConfig


def _hash(fd: BinaryIO) -> str:
    digest = hashlib.sha256()
    while chunk := fd.read(1024 * 1024):
        digest.update(chunk)
    return digest.hexdigest()


def _regular(path: Path) -> None:
    if not path.is_file() or path.is_symlink():
        raise SettlementFailure("invalid_path", "Input must be an existing regular file, not a symlink.")


@contextmanager
def _lock(proof: Path) -> Iterator[None]:
    # Persistent lock inode: unlinking it would allow two different lock owners.
    fd = os.open(str(proof) + ".lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise SettlementFailure("proof_busy", "Another operation is updating this proof.") from exc
        yield
    finally:
        os.close(fd)


def _persist(path: Path, data: bytes, *, replace: bool = False) -> None:
    fd, name = tempfile.mkstemp(prefix=".upp-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as out:
            out.write(data)
            out.flush()
            os.fsync(out.fileno())
        if replace:
            os.replace(name, path)
        else:
            # Atomic publication that never overwrites a pre-existing path.
            os.link(name, path)
    finally:
        os.unlink(name) if os.path.exists(name) else None


class SettlementService:
    def __init__(self, config: OTSConfig | None = None) -> None:
        self._backend = OTSBackend(config)

    @staticmethod
    def _failure(result: SettlementResult, exc: Exception) -> SettlementResult:
        if isinstance(exc, SettlementFailure):
            result.status = exc.status
            result.diagnostics.append(Diagnostic(exc.code, str(exc)))
        else:
            result.status = Status.ERROR
            result.diagnostics.append(Diagnostic("operation_failed", "Local file or backend operation failed."))
        return result

    @staticmethod
    def _proof(result: SettlementResult, path: Path, data: bytes) -> None:
        result.proof_file = str(path.absolute())
        result.proof_sha256 = hashlib.sha256(data).hexdigest()

    def stamp_file(self, path: str | Path, output_dir: str | Path) -> SettlementResult:
        result = SettlementResult(Status.ERROR, network=self._backend.config.network)
        try:
            payload = Path(path)
            _regular(payload)
            directory = Path(output_dir)
            directory.mkdir(parents=True, exist_ok=True)
            proof_path = directory / (payload.name + ".ots")
            receipt_path = Path(str(proof_path) + ".json")
            with _lock(proof_path), payload.open("rb") as fd:
                before = os.fstat(fd.fileno())
                if not stat.S_ISREG(before.st_mode):
                    raise SettlementFailure("invalid_path", "Payload must be a regular file.")
                result.payload_sha256 = _hash(fd)
                if proof_path.exists() or proof_path.is_symlink():
                    _regular(proof_path)
                    data = proof_path.read_bytes()
                    try:
                        digest = self._backend.digest(data).hex()
                    except InvalidProof as exc:
                        raise SettlementFailure("output_conflict", "Existing proof is incompatible; no files changed.") from exc
                    if digest != result.payload_sha256:
                        raise SettlementFailure("output_conflict", "Existing proof belongs to different bytes.")
                else:
                    if receipt_path.exists() or receipt_path.is_symlink():
                        raise SettlementFailure("output_conflict", "Receipt path already exists without a proof.")
                    fd.seek(0)
                    data = self._backend.stamp(fd)
                    fd.seek(0)
                    after_hash = _hash(fd)
                    after = os.fstat(fd.fileno())
                    current = payload.stat()
                    if (after_hash != result.payload_sha256
                            or (before.st_size, before.st_mtime_ns, before.st_ctime_ns)
                            != (after.st_size, after.st_mtime_ns, after.st_ctime_ns)
                            or (current.st_dev, current.st_ino) != (before.st_dev, before.st_ino)):
                        raise SettlementFailure("payload_changed", "Payload changed during submission; no proof published.")
                    if self._backend.digest(data).hex() != result.payload_sha256:
                        raise InvalidProof("Produced proof does not match the payload digest.")
                    _persist(proof_path, data)
                self._proof(result, proof_path, data)
                result.binding_checked = True
                result.status = Status.PENDING
                result.diagnostics.append(Diagnostic("not_verified", "Receipt stored; run verify for Bitcoin confirmation."))
                if not receipt_path.exists():
                    _persist(receipt_path, (json.dumps(result.to_dict(), indent=2) + "\n").encode())
            return result
        except (SettlementFailure, OSError, ValueError, TypeError, RuntimeError) as exc:
            return self._failure(result, exc)

    def upgrade_proof(self, proof_path: str | Path) -> SettlementResult:
        result = SettlementResult(Status.ERROR, network=self._backend.config.network)
        try:
            path = Path(proof_path)
            _regular(path)
            with _lock(path):
                original = path.read_bytes()
                self._proof(result, path, original)
                result.payload_sha256 = self._backend.digest(original).hex()
                upgraded, diagnostics = self._backend.upgrade(original)
                if self._backend.digest(upgraded).hex() != result.payload_sha256:
                    raise InvalidProof("Upgrade changed the payload commitment.")
                if upgraded != original:
                    if path.read_bytes() != original:
                        raise SettlementFailure("proof_changed", "Proof changed concurrently; upgrade not persisted.")
                    _persist(path, upgraded, replace=True)
                self._proof(result, path, upgraded)
                result.status = Status.PENDING
                result.diagnostics = diagnostics + [Diagnostic("not_verified", "Upgrade does not verify the original payload or Bitcoin; run verify.")]
            return result
        except (SettlementFailure, OSError, ValueError, TypeError, RuntimeError) as exc:
            return self._failure(result, exc)

    def verify_file(self, payload_path: str | Path, proof_path: str | Path) -> SettlementResult:
        result = SettlementResult(Status.ERROR, network=self._backend.config.network)
        try:
            payload, proof = Path(payload_path), Path(proof_path)
            _regular(payload)
            _regular(proof)
            with payload.open("rb") as fd:
                result.payload_sha256 = _hash(fd)
            data = proof.read_bytes()
            self._proof(result, proof, data)
            if self._backend.digest(data).hex() != result.payload_sha256:
                raise InvalidProof("Payload SHA-256 does not match the proof.")
            result.binding_checked = True
            result.status, result.bitcoin, result.diagnostics = self._backend.verify(data)
            return result
        except (SettlementFailure, OSError, ValueError, TypeError, RuntimeError) as exc:
            return self._failure(result, exc)


def stamp_file(path: str | Path, output_dir: str | Path) -> SettlementResult:
    return SettlementService().stamp_file(path, output_dir)


def upgrade_proof(proof_path: str | Path) -> SettlementResult:
    return SettlementService().upgrade_proof(proof_path)


def verify_file(payload_path: str | Path, proof_path: str | Path) -> SettlementResult:
    return SettlementService().verify_file(payload_path, proof_path)
