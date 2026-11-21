"""SQLite transactions and leases survive worker/process restarts."""
from contextlib import contextmanager
import json
import os
from pathlib import Path
import sqlite3
import tempfile
import time
import uuid

from upp_protocol import build_payload


def canonical(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def publish_bytes(path: Path, data: bytes):
    """Atomic no-overwrite publication, including duplicate writers."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=".upload-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        try:
            os.link(name, path)
            return True
        except FileExistsError:
            if path.is_symlink() or path.read_bytes() != data:
                raise ValueError("Stored file conflicts with its content address.") from None
            return False
    finally:
        os.unlink(name)


class Store:
    def __init__(self, config):
        self.config = config
        self.root = config.data_dir
        self.media = self.root / "media"
        self.proofs = self.root / "proofs"
        self.payloads = self.root / "payloads"
        for path in (self.root, self.media, self.proofs, self.payloads):
            path.mkdir(parents=True, exist_ok=True)
        self.db = self.root / "social.sqlite3"
        with self.connection() as db:
            db.executescript('''
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS posts (
                    id TEXT PRIMARY KEY, event TEXT NOT NULL,
                    publication_status TEXT NOT NULL DEFAULT 'QUEUED',
                    settlement TEXT, created_at REAL NOT NULL
                );
                CREATE TABLE IF NOT EXISTS jobs (
                    event_id TEXT NOT NULL REFERENCES posts(id), kind TEXT NOT NULL,
                    due REAL NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
                    owner TEXT, lease_until REAL, done INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(event_id, kind)
                );
                CREATE INDEX IF NOT EXISTS jobs_due ON jobs(done, due);
            ''')

    @contextmanager
    def connection(self):
        db = sqlite3.connect(self.db, timeout=15)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        try:
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()

    def health(self):
        with self.connection() as db:
            db.execute("SELECT count(*) FROM posts").fetchone()

    def get(self, event_id):
        with self.connection() as db:
            row = db.execute("SELECT * FROM posts WHERE id=?", (event_id,)).fetchone()
            return dict(row) if row else None

    def accept(self, event):
        serialized = canonical(event)
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            existing = db.execute("SELECT event FROM posts WHERE id=?", (event["id"],)).fetchone()
            if existing:
                if existing["event"] != serialized:
                    raise ValueError("event_conflict")
                return False
            db.execute("INSERT INTO posts(id,event,created_at) VALUES(?,?,?)", (event["id"], serialized, time.time()))
            db.executemany("INSERT INTO jobs(event_id,kind,due) VALUES(?,?,?)",
                           [(event["id"], kind, time.time()) for kind in ("relay", "ots")])
        return True

    def payload_path(self, event):
        path = self.payloads / (event["id"] + ".bin")
        publish_bytes(path, build_payload(event))
        return path

    def proof_path(self, event_id):
        return self.proofs / (event_id + ".bin.ots")

    def settlement(self, event_id, result, *, job=None):
        with self.connection() as db:
            if job is None:
                db.execute("UPDATE posts SET settlement=? WHERE id=?", (canonical(result.to_dict()), event_id))
            else:
                # A recovered lease owner fences stale worker metadata writes.
                db.execute("""UPDATE posts SET settlement=? WHERE id=? AND EXISTS
                           (SELECT 1 FROM jobs WHERE event_id=? AND kind=? AND owner=?)""",
                           (canonical(result.to_dict()), event_id, event_id, job["kind"], job["owner"]))

    def snapshot(self, event_id):
        row = self.get(event_id)
        if row is None:
            return None
        receipt = json.loads(row["settlement"]) if row["settlement"] else None
        if receipt is not None:
            internal = receipt.pop("proof_file", None)
            receipt["proof_url"] = (self.config.public_url + "/v1/posts/" + event_id + "/proof"
                                    if internal and self.proof_path(event_id).is_file() else None)
        return {"event": json.loads(row["event"]), "publication_status": row["publication_status"], "settlement": receipt}

    def claim(self, now=None):
        now = time.time() if now is None else now
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute('''SELECT j.* FROM jobs j
                WHERE j.done=0 AND j.due<=? AND (j.lease_until IS NULL OR j.lease_until<=?)
                AND NOT EXISTS (SELECT 1 FROM jobs active WHERE active.event_id=j.event_id
                                AND active.lease_until>?)
                ORDER BY j.due, j.kind DESC LIMIT 1''', (now, now, now)).fetchone()
            if not row:
                return None
            owner = uuid.uuid4().hex
            db.execute("UPDATE jobs SET owner=?,lease_until=?,attempts=attempts+1 WHERE event_id=? AND kind=?",
                       (owner, now + self.config.lease_seconds, row["event_id"], row["kind"]))
            return {**dict(row), "owner": owner}

    def renew(self, job):
        with self.connection() as db:
            return db.execute("UPDATE jobs SET lease_until=? WHERE event_id=? AND kind=? AND owner=?",
                              (time.time() + self.config.lease_seconds, job["event_id"], job["kind"], job["owner"])).rowcount == 1

    def finish(self, job, *, done=False, published=None):
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            updated = db.execute('''UPDATE jobs SET done=?,due=?,owner=NULL,lease_until=NULL
                                 WHERE event_id=? AND kind=? AND owner=?''',
                                 (int(done), time.time() + self.config.retry_seconds,
                                  job["event_id"], job["kind"], job["owner"])).rowcount
            if updated and published is not None:
                db.execute("UPDATE posts SET publication_status=? WHERE id=?",
                           ("PUBLISHED" if published else "ERROR", job["event_id"]))
