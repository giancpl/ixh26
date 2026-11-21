"""NIP-98 authorization; replay records are shared across API processes."""
import base64
import hashlib
import json
import time
import sqlite3
from nostr_sdk import Event


class AuthError(ValueError):
    pass


def authorize(header, *, url, method, body, db, now=None):
    now = time.time() if now is None else now
    try:
        if not header or not header.startswith('Nostr ') or len(header) > 16384:
            raise AuthError()
        raw = base64.b64decode(header[6:], validate=True)
        obj = json.loads(raw)
        event = Event.from_json(raw.decode())
        if not event.verify() or obj['kind'] != 27235 or obj['content'] != '' or type(obj['created_at']) is not int or abs(now-obj['created_at']) > 60:
            raise AuthError()
        def tag(name):
            found = [t for t in obj['tags'] if t and t[0] == name]
            if len(found) != 1 or len(found[0]) != 2:
                raise AuthError()
            return found[0][1]
        if tag('u') != url or tag('method') != method or tag('payload') != hashlib.sha256(body).hexdigest():
            raise AuthError()
        db.execute('DELETE FROM http_auth_replay WHERE expires < ?', (now,))
        db.execute('INSERT INTO http_auth_replay VALUES (?,?)', (obj['id'], now+121))
        return obj['pubkey']
    except sqlite3.OperationalError:
        raise
    except Exception:
        raise AuthError('Authorization is invalid, expired or already used.') from None
