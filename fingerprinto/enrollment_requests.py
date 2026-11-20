"""Verifier-owned, one-use enrollment challenges. Never import this DB from a bundle."""

from datetime import datetime
from contextlib import contextmanager
import secrets
import sqlite3


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

    @contextmanager
    def _connection(self):
        db = sqlite3.connect(self.path, timeout=5)
        try:
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

    def consume(self, challenge: bytes, policy_digest: bytes, installation_id: bytes, *, now: datetime) -> bool:
        """Atomic compare-and-consume; call only after all independent checks pass."""
        at = instant(now)
        with self._connection() as db:
            updated = db.execute("""UPDATE enrollment_requests SET consumed=?, installation_id=?
                WHERE challenge=? AND policy=? AND consumed IS NULL AND issued<=? AND expires>?""",
                (at, installation_id, challenge, policy_digest, at, at))
            return updated.rowcount == 1
