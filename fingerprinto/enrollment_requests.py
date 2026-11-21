"""Verifier-owned, one-use enrollment challenges. Never import this DB from a bundle."""

from datetime import datetime
from contextlib import contextmanager
import secrets
import sqlite3
from hashlib import sha256

from .receipt_v3 import json_object


def instant(value: datetime) -> float:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("An aware verifier-owned evaluation time is required")
    return value.timestamp()


class EnrollmentRequests:
    def __init__(self, path):
        self.path = str(path)
        if not self.path or self.path == ":memory:":
            raise ValueError("A persistent verifier-owned SQLite path is required")
        with self._connection() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS enrollment_requests (
                challenge BLOB PRIMARY KEY, policy BLOB NOT NULL,
                issued REAL NOT NULL, expires REAL NOT NULL,
                consumed REAL, installation_id BLOB)""")
            db.execute("""CREATE TABLE IF NOT EXISTS enrollment_acceptances (
                challenge BLOB PRIMARY KEY, policy BLOB NOT NULL,
                installation_id BLOB NOT NULL, decision BLOB NOT NULL,
                digest BLOB NOT NULL)""")

    @contextmanager
    def _connection(self):
        db = sqlite3.connect(self.path, timeout=5)
        try:
            db.execute("PRAGMA synchronous=FULL")
            with db:
                yield db
        finally:
            db.close()

    def issue(self, policy_digest: bytes, *, now: datetime, lifetime_seconds: int = 300) -> bytes:
        if type(policy_digest) is not bytes or len(policy_digest) != 32:
            raise ValueError("Expected policy SHA-256")
        if type(lifetime_seconds) is not int or not 1 <= lifetime_seconds <= 3600:
            raise ValueError("Invalid challenge lifetime")
        issued = instant(now)
        challenge = secrets.token_bytes(32)
        with self._connection() as db:
            db.execute("INSERT INTO enrollment_requests VALUES (?, ?, ?, ?, NULL, NULL)",
                       (challenge, policy_digest, issued, issued + lifetime_seconds))
        return challenge

    def consume(self, challenge: bytes, policy_digest: bytes, installation_id: bytes, *, now: datetime,
                accepted_result: bytes | None = None) -> bool:
        """Atomically consume and, when supplied, retain the verified decision."""
        at = instant(now)
        for value in (challenge, policy_digest, installation_id):
            if type(value) is not bytes or len(value) != 32:
                raise ValueError("Invalid challenge/policy/installation digest")
        if accepted_result is not None:
            self._decision(accepted_result, policy_digest, installation_id, at)
        with self._connection() as db:
            updated = db.execute("""UPDATE enrollment_requests SET consumed=?, installation_id=?
                WHERE challenge=? AND policy=? AND consumed IS NULL AND issued<=? AND expires>?""",
                (at, installation_id, challenge, policy_digest, at, at))
            if updated.rowcount != 1:
                return False
            if accepted_result is not None:
                db.execute("INSERT INTO enrollment_acceptances VALUES (?,?,?,?,?)",
                           (challenge, policy_digest, installation_id, accepted_result,
                            sha256(accepted_result).digest()))
            return True

    @staticmethod
    def _decision(raw, policy_digest, installation_id, at):
        if type(raw) is not bytes or not 1 <= len(raw) <= 256 * 1024:
            raise ValueError("Invalid retained enrollment decision")
        obj = json_object(raw)
        if (type(obj.get("enrollment")) is not dict or obj["enrollment"].get("status") != "accepted"
                or obj.get("policy_sha256") != policy_digest.hex()
                or type(obj.get("details")) is not dict
                or obj["details"].get("installation_id") != installation_id.hex()
                or type(obj.get("evaluated_at")) is not str
                or instant(datetime.fromisoformat(obj["evaluated_at"])) != at):
            raise ValueError("Retained decision scope/time mismatch")

    def recover(self, challenge: bytes, policy_digest: bytes, installation_id: bytes) -> bytes | None:
        """Read only locally retained acceptance; never consume/re-enroll on retry.

        This trusted database is not a portable signed authority record. Its
        access controls/freshness remain the provisioner's responsibility.
        """
        for value in (challenge, policy_digest, installation_id):
            if type(value) is not bytes or len(value) != 32:
                raise ValueError("Invalid recovery scope")
        with self._connection() as db:
            row = db.execute("""SELECT a.decision,a.digest,r.consumed FROM enrollment_acceptances a
                JOIN enrollment_requests r ON a.challenge=r.challenge
                WHERE a.challenge=? AND a.policy=? AND a.installation_id=?
                AND r.policy=a.policy AND r.installation_id=a.installation_id
                AND r.consumed IS NOT NULL""", (challenge, policy_digest, installation_id)).fetchone()
        if row is None:
            return None
        raw, checksum, at = row
        if type(raw) is not bytes or sha256(raw).digest() != checksum:
            raise ValueError("Corrupt retained enrollment decision")
        self._decision(raw, policy_digest, installation_id, at)
        return raw
