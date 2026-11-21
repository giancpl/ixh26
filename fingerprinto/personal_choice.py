"""Local optional preference against C; never imported by a verifier/reducer."""

from contextlib import closing
from dataclasses import dataclass
from datetime import datetime
import sqlite3

from .enrollment_requests import instant
from .evidence_v3 import Decision


@dataclass(frozen=True)
class Choice:
    value: str
    recorded_at: str


class PersonalChoices:
    def __init__(self, path):
        self.path = str(path)
        if not self.path or self.path == ":memory:":
            raise ValueError("Persistent app-private choice database required")
        with closing(sqlite3.connect(self.path)) as db, db:
            db.execute("CREATE TABLE IF NOT EXISTS personal_choices (commitment BLOB PRIMARY KEY, choice TEXT NOT NULL, recorded_at TEXT NOT NULL)")

    @staticmethod
    def _key(commitment):
        if type(commitment) is not bytes or len(commitment) != 32:
            raise ValueError("Expected raw signed commitment C")

    def remember(self, commitment, choice, *, decision: Decision, now: datetime):
        self._key(commitment)
        instant(now)
        if type(decision) is not Decision or not decision.can_offer_choice or decision.state != "complete":
            raise ValueError("Choice unavailable until required imported-receipt checks complete")
        if choice not in ("trust", "distrust"):
            raise ValueError("Unknown personal choice")
        with closing(sqlite3.connect(self.path)) as db, db:
            db.execute("INSERT OR REPLACE INTO personal_choices VALUES (?,?,?)", (commitment,choice,now.isoformat()))

    def get(self, commitment):
        self._key(commitment)
        with closing(sqlite3.connect(self.path)) as db:
            row=db.execute("SELECT choice,recorded_at FROM personal_choices WHERE commitment=?",(commitment,)).fetchone()
        return None if row is None else Choice(*row)

    def forget(self, commitment):
        self._key(commitment)
        with closing(sqlite3.connect(self.path)) as db, db:
            db.execute("DELETE FROM personal_choices WHERE commitment=?",(commitment,))
