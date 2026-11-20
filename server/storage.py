"""SQLite persistence for Nostr events, uploaded photos, and Bitcoin OTS anchors."""
from __future__ import annotations

import json
from pathlib import Path
import sqlite3
import time
from typing import Any


def _d_tag(tags: list[list[str]]) -> str:
    for tag in tags:
        if len(tag) >= 2 and tag[0] == "d":
            return str(tag[1])
    return ""


class SocialStore:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.media_dir = root / "media"
        self.proofs_dir = root / "proofs"
        self.root.mkdir(parents=True, exist_ok=True)
        self.media_dir.mkdir(parents=True, exist_ok=True)
        self.proofs_dir.mkdir(parents=True, exist_ok=True)
        self.db_path = root / "social.db"
        self.conn = sqlite3.connect(self.db_path, check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.execute("PRAGMA foreign_keys=ON")
        self._init_schema()

    def _init_schema(self) -> None:
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS nostr_events (
                id TEXT PRIMARY KEY,
                pubkey TEXT NOT NULL,
                kind INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                d_tag TEXT NOT NULL DEFAULT '',
                tags_json TEXT NOT NULL,
                content TEXT NOT NULL,
                sig TEXT NOT NULL,
                raw_json TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_nostr_kind_time ON nostr_events(kind, created_at DESC);
            CREATE INDEX IF NOT EXISTS idx_nostr_pubkey_kind ON nostr_events(pubkey, kind, created_at DESC);

            CREATE TABLE IF NOT EXISTS nostr_tags (
                event_id TEXT NOT NULL REFERENCES nostr_events(id) ON DELETE CASCADE,
                tag_name TEXT NOT NULL,
                tag_value TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_nostr_tag ON nostr_tags(tag_name, tag_value);

            CREATE TABLE IF NOT EXISTS photos (
                sha256 TEXT PRIMARY KEY,
                ext TEXT NOT NULL,
                mime TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                width INTEGER,
                height INTEGER,
                source TEXT NOT NULL,
                uploader_pubkey TEXT,
                title TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                board TEXT NOT NULL DEFAULT '',
                captured_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                inbox_acked INTEGER NOT NULL DEFAULT 0,
                published_event_id TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_photos_pubkey ON photos(uploader_pubkey, created_at DESC);

            CREATE TABLE IF NOT EXISTS anchors (
                sha256 TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                network TEXT NOT NULL,
                proof_file TEXT,
                block_height INTEGER,
                block_time INTEGER,
                diagnostics_json TEXT NOT NULL DEFAULT '[]',
                updated_at INTEGER NOT NULL
            );
            """
        )
        self.conn.commit()

    def close(self) -> None:
        self.conn.close()

    # ---- Nostr Events (NIP-01) ----

    def save_event(self, event: dict[str, Any]) -> tuple[bool, str]:
        eid = event["id"]
        pubkey = event["pubkey"]
        kind = int(event["kind"])
        created_at = int(event["created_at"])
        tags: list[list[str]] = event["tags"]
        d_tag = _d_tag(tags) if 30000 <= kind < 40000 else ""

        with self.conn:
            existing = self.conn.execute(
                "SELECT id FROM nostr_events WHERE id = ?", (eid,)
            ).fetchone()
            if existing:
                return True, "duplicate: already stored"

            # Replaceable events: kind 0, 3, or 10000..19999
            if kind in (0, 3) or (10000 <= kind < 20000):
                prev = self.conn.execute(
                    "SELECT id, created_at FROM nostr_events WHERE pubkey = ? AND kind = ?",
                    (pubkey, kind),
                ).fetchall()
                for row in prev:
                    if row["created_at"] > created_at or (
                        row["created_at"] == created_at and row["id"] < eid
                    ):
                        return True, "ignored: newer replaceable event exists"
                for row in prev:
                    self.conn.execute("DELETE FROM nostr_events WHERE id = ?", (row["id"],))

            # Parameterized replaceable events: 30000..39999
            if 30000 <= kind < 40000:
                prev = self.conn.execute(
                    "SELECT id, created_at FROM nostr_events WHERE pubkey = ? AND kind = ? AND d_tag = ?",
                    (pubkey, kind, d_tag),
                ).fetchall()
                for row in prev:
                    if row["created_at"] > created_at or (
                        row["created_at"] == created_at and row["id"] < eid
                    ):
                        return True, "ignored: newer parameterized event exists"
                for row in prev:
                    self.conn.execute("DELETE FROM nostr_events WHERE id = ?", (row["id"],))

            self.conn.execute(
                """
                INSERT INTO nostr_events (id, pubkey, kind, created_at, d_tag, tags_json, content, sig, raw_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    eid,
                    pubkey,
                    kind,
                    created_at,
                    d_tag,
                    json.dumps(tags),
                    event["content"],
                    event["sig"],
                    json.dumps(event),
                ),
            )
            tag_rows = [
                (eid, str(t[0]), str(t[1]))
                for t in tags
                if isinstance(t, list) and len(t) >= 2 and isinstance(t[0], str)
            ]
            if tag_rows:
                self.conn.executemany(
                    "INSERT INTO nostr_tags (event_id, tag_name, tag_value) VALUES (?, ?, ?)",
                    tag_rows,
                )
        return True, ""

    def query_events(self, flt: dict[str, Any]) -> list[dict[str, Any]]:
        clauses: list[str] = []
        params: list[Any] = []

        if "ids" in flt and isinstance(flt["ids"], list) and flt["ids"]:
            marks = ",".join("?" for _ in flt["ids"])
            clauses.append(f"e.id IN ({marks})")
            params.extend(flt["ids"])

        if "authors" in flt and isinstance(flt["authors"], list) and flt["authors"]:
            marks = ",".join("?" for _ in flt["authors"])
            clauses.append(f"e.pubkey IN ({marks})")
            params.extend(flt["authors"])

        if "kinds" in flt and isinstance(flt["kinds"], list) and flt["kinds"]:
            marks = ",".join("?" for _ in flt["kinds"])
            clauses.append(f"e.kind IN ({marks})")
            params.extend(int(k) for k in flt["kinds"])

        if "since" in flt and isinstance(flt["since"], int):
            clauses.append("e.created_at >= ?")
            params.append(flt["since"])

        if "until" in flt and isinstance(flt["until"], int):
            clauses.append("e.created_at <= ?")
            params.append(flt["until"])

        for key, vals in flt.items():
            if key.startswith("#") and len(key) == 2 and isinstance(vals, list) and vals:
                tag_name = key[1]
                marks = ",".join("?" for _ in vals)
                clauses.append(
                    f"EXISTS (SELECT 1 FROM nostr_tags t WHERE t.event_id = e.id AND t.tag_name = ? AND t.tag_value IN ({marks}))"
                )
                params.append(tag_name)
                params.extend(str(v) for v in vals)

        where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
        limit = min(max(int(flt.get("limit", 256)), 1), 500)
        sql = f"SELECT raw_json FROM nostr_events e {where} ORDER BY e.created_at DESC, e.id ASC LIMIT ?"
        params.append(limit)
        rows = self.conn.execute(sql, params).fetchall()
        return [json.loads(r["raw_json"]) for r in rows]

    # ---- Photos & Anchors ----

    def save_photo(
        self,
        *,
        sha256: str,
        ext: str,
        mime: str,
        size_bytes: int,
        width: int | None,
        height: int | None,
        source: str,
        uploader_pubkey: str | None,
        title: str,
        description: str,
        board: str,
        captured_at: int,
    ) -> dict[str, Any]:
        now = int(time.time())
        with self.conn:
            existing = self.conn.execute(
                "SELECT * FROM photos WHERE sha256 = ?", (sha256,)
            ).fetchone()
            if existing:
                if uploader_pubkey and not existing["uploader_pubkey"]:
                    self.conn.execute(
                        "UPDATE photos SET uploader_pubkey = ? WHERE sha256 = ?",
                        (uploader_pubkey, sha256),
                    )
                row = self.conn.execute("SELECT * FROM photos WHERE sha256 = ?", (sha256,)).fetchone()
                return dict(row)
            self.conn.execute(
                """
                INSERT INTO photos (
                    sha256, ext, mime, size_bytes, width, height, source,
                    uploader_pubkey, title, description, board,
                    captured_at, created_at, inbox_acked
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                (
                    sha256,
                    ext,
                    mime,
                    size_bytes,
                    width,
                    height,
                    source,
                    uploader_pubkey,
                    title,
                    description,
                    board,
                    captured_at,
                    now,
                ),
            )
        row = self.conn.execute("SELECT * FROM photos WHERE sha256 = ?", (sha256,)).fetchone()
        return dict(row)

    def get_photo(self, sha256: str) -> dict[str, Any] | None:
        row = self.conn.execute("SELECT * FROM photos WHERE sha256 = ?", (sha256,)).fetchone()
        return dict(row) if row else None

    def get_inbox(self, pubkey: str | None = None) -> list[dict[str, Any]]:
        clauses = ["p.inbox_acked = 0"]
        params: list[Any] = []
        if pubkey:
            clauses.append("(p.uploader_pubkey = ? OR (p.uploader_pubkey IS NULL AND p.source = 'android-photoanchor'))")
            params.append(pubkey)
        else:
            clauses.append("p.source = 'android-photoanchor'")

        sql = f"""
            SELECT p.*, a.status AS anchor_status, a.network AS anchor_network,
                   a.block_height, a.block_time, a.proof_file, a.diagnostics_json
            FROM photos p
            LEFT JOIN anchors a ON a.sha256 = p.sha256
            WHERE {' AND '.join(clauses)}
            ORDER BY p.created_at DESC
            LIMIT 50
        """
        rows = self.conn.execute(sql, params).fetchall()
        return [dict(r) for r in rows]

    def ack_inbox(self, sha256: str, event_id: str | None = None) -> bool:
        with self.conn:
            cur = self.conn.execute(
                "UPDATE photos SET inbox_acked = 1, published_event_id = COALESCE(?, published_event_id) WHERE sha256 = ?",
                (event_id, sha256),
            )
        return cur.rowcount > 0

    def upsert_anchor(
        self,
        *,
        sha256: str,
        status: str,
        network: str,
        proof_file: str | None,
        block_height: int | None,
        block_time: int | None,
        diagnostics: list[dict[str, str]],
    ) -> dict[str, Any]:
        now = int(time.time())
        with self.conn:
            self.conn.execute(
                """
                INSERT INTO anchors (sha256, status, network, proof_file, block_height, block_time, diagnostics_json, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(sha256) DO UPDATE SET
                    status = excluded.status,
                    network = excluded.network,
                    proof_file = COALESCE(excluded.proof_file, anchors.proof_file),
                    block_height = excluded.block_height,
                    block_time = excluded.block_time,
                    diagnostics_json = excluded.diagnostics_json,
                    updated_at = excluded.updated_at
                """,
                (
                    sha256,
                    status,
                    network,
                    proof_file,
                    block_height,
                    block_time,
                    json.dumps(diagnostics),
                    now,
                ),
            )
        anchor = self.get_anchor(sha256)
        assert anchor is not None
        return anchor

    def get_anchor(self, sha256: str) -> dict[str, Any] | None:
        row = self.conn.execute("SELECT * FROM anchors WHERE sha256 = ?", (sha256,)).fetchone()
        if not row:
            return None
        d = dict(row)
        d["diagnostics"] = json.loads(d.pop("diagnostics_json") or "[]")
        return d
