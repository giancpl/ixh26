"""Minimal NIP-01 Nostr relay over aiohttp WebSocket for local & LAN photo boards."""
from __future__ import annotations

import hashlib
import json
import re
import time
from typing import Any

from aiohttp import WSMsgType, web

from .storage import SocialStore

HEX64_RE = re.compile(r"^[0-9a-f]{64}$")
HEX128_RE = re.compile(r"^[0-9a-f]{128}$")


def compute_event_id(event: dict[str, Any]) -> str:
    payload = json.dumps(
        [
            0,
            event["pubkey"],
            event["created_at"],
            event["kind"],
            event["tags"],
            event["content"],
        ],
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def validate_event_structure(event: Any) -> str | None:
    if not isinstance(event, dict):
        return "invalid: event must be an object"
    for field in ("id", "pubkey", "sig", "content"):
        if not isinstance(event.get(field), str):
            return f"invalid: missing string field {field}"
    if not HEX64_RE.match(event["id"]) or not HEX64_RE.match(event["pubkey"]):
        return "invalid: id and pubkey must be 32-byte lowercase hex"
    if not HEX128_RE.match(event["sig"]):
        return "invalid: sig must be 64-byte lowercase hex"
    if not isinstance(event.get("kind"), int) or isinstance(event["kind"], bool) or event["kind"] < 0:
        return "invalid: kind must be a non-negative integer"
    if not isinstance(event.get("created_at"), int) or isinstance(event["created_at"], bool):
        return "invalid: created_at must be an integer"
    if event["created_at"] < 0 or event["created_at"] > int(time.time()) + 1800:
        return "invalid: created_at out of bounds"
    tags = event.get("tags")
    if not isinstance(tags, list) or any(not isinstance(t, list) or not all(isinstance(x, str) for x in t) for t in tags):
        return "invalid: tags must be an array of string arrays"
    if len(event["content"]) > 64000:
        return "invalid: content too large"
    if compute_event_id(event) != event["id"]:
        return "invalid: event id does not match canonical sha256"
    return None


def match_filter(flt: dict[str, Any], event: dict[str, Any]) -> bool:
    if not isinstance(flt, dict):
        return False
    if "ids" in flt and event["id"] not in flt["ids"]:
        return False
    if "authors" in flt and event["pubkey"] not in flt["authors"]:
        return False
    if "kinds" in flt and event["kind"] not in flt["kinds"]:
        return False
    if "since" in flt and isinstance(flt["since"], int) and event["created_at"] < flt["since"]:
        return False
    if "until" in flt and isinstance(flt["until"], int) and event["created_at"] > flt["until"]:
        return False
    for key, vals in flt.items():
        if key.startswith("#") and len(key) == 2 and isinstance(vals, list):
            tag_name = key[1]
            wanted = set(str(v) for v in vals)
            if not any(len(t) >= 2 and t[0] == tag_name and t[1] in wanted for t in event["tags"]):
                return False
    return True


class NostrRelay:
    def __init__(self, store: SocialStore) -> None:
        self.store = store
        self.clients: dict[web.WebSocketResponse, dict[str, list[dict[str, Any]]]] = {}

    async def handle_ws(self, request: web.Request) -> web.WebSocketResponse:
        ws = web.WebSocketResponse(max_msg_size=131072)
        await ws.prepare(request)
        self.clients[ws] = {}
        try:
            async for msg in ws:
                if msg.type != WSMsgType.TEXT:
                    continue
                try:
                    frame = json.loads(msg.data)
                except json.JSONDecodeError:
                    await ws.send_json(["NOTICE", "invalid JSON frame"])
                    continue
                if not isinstance(frame, list) or not frame:
                    continue
                cmd = frame[0]
                if cmd == "EVENT" and len(frame) >= 2:
                    await self._on_event(ws, frame[1])
                elif cmd == "REQ" and len(frame) >= 3 and isinstance(frame[1], str):
                    await self._on_req(ws, frame[1], [f for f in frame[2:] if isinstance(f, dict)])
                elif cmd == "CLOSE" and len(frame) >= 2 and isinstance(frame[1], str):
                    self.clients.get(ws, {}).pop(frame[1], None)
                    await ws.send_json(["CLOSED", frame[1], ""])
        finally:
            self.clients.pop(ws, None)
        return ws

    async def _on_event(self, ws: web.WebSocketResponse, event: Any) -> None:
        err = validate_event_structure(event)
        eid = event.get("id", "") if isinstance(event, dict) else ""
        if err:
            await ws.send_json(["OK", eid, False, err])
            return
        ok, detail = self.store.save_event(event)
        await ws.send_json(["OK", eid, ok, detail])
        if ok and "ignored:" not in detail and "duplicate:" not in detail:
            await self._broadcast(event)

    async def _on_req(
        self, ws: web.WebSocketResponse, sub_id: str, filters: list[dict[str, Any]]
    ) -> None:
        subs = self.clients.get(ws)
        if subs is not None:
            subs[sub_id] = filters
        seen: set[str] = set()
        for flt in filters:
            for ev in self.store.query_events(flt):
                if ev["id"] in seen:
                    continue
                seen.add(ev["id"])
                await ws.send_json(["EVENT", sub_id, ev])
        await ws.send_json(["EOSE", sub_id])

    async def _broadcast(self, event: dict[str, Any]) -> None:
        for peer, subs in list(self.clients.items()):
            if peer.closed:
                continue
            for sub_id, filters in list(subs.items()):
                if any(match_filter(f, event) for f in filters):
                    try:
                        await peer.send_json(["EVENT", sub_id, event])
                    except Exception:
                        pass
