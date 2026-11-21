"""Local transaction journal: sign once before any external submission."""

import hashlib
from pathlib import Path
import re
import sqlite3


class ReceiptJournal:
    def __init__(self, path: str | Path):
        self.path = str(path)
        if not self.path or self.path == ":memory:":
            raise ValueError("Persistent app-owned journal required")
        db = sqlite3.connect(self.path, timeout=10)
        try:
            with db:
                db.execute("""CREATE TABLE IF NOT EXISTS signed_receipts (
                    operation TEXT PRIMARY KEY, request BLOB NOT NULL,
                    receipt BLOB NOT NULL, digest BLOB NOT NULL)""")
        finally:
            db.close()

    def sign_once(self, operation: str, request_digest: bytes, producer) -> bytes:
        """Serialize producers across processes and persist their exact returned bytes.

        request_digest binds immutable input bytes/metadata/suite and key identity.
        The producer must validate origin/enrollment and construct/sign locally;
        it must never submit to a ledger/calendar or perform other external writes.
        Returned stored bytes must still be independently verified by the caller.
        """
        if type(operation) is not str or not re.fullmatch(r"[0-9a-f]{32}", operation):
            raise ValueError("Expected persistent 128-bit hex operation ID")
        if type(request_digest) is not bytes or len(request_digest) != 32:
            raise ValueError("Expected immutable input-context digest")
        db = sqlite3.connect(self.path, timeout=10)
        try:
            db.execute("PRAGMA synchronous=FULL")
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT request, receipt, digest FROM signed_receipts WHERE operation=?", (operation,)).fetchone()
            if row is not None:
                request, receipt, checksum = row
                if request != request_digest:
                    raise ValueError("Operation ID reused with different inputs")
                if not isinstance(receipt, bytes) or not 1 <= len(receipt) <= 256 * 1024 or hashlib.sha256(receipt).digest() != checksum:
                    raise ValueError("Stored receipt is corrupt")
                db.commit()
                return receipt
            receipt = producer()
            if type(receipt) is not bytes or not 1 <= len(receipt) <= 256 * 1024:
                raise ValueError("Invalid signed receipt bytes")
            db.execute("INSERT INTO signed_receipts VALUES (?, ?, ?, ?)",
                       (operation, request_digest, receipt, hashlib.sha256(receipt).digest()))
            db.commit()
            return receipt
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()
