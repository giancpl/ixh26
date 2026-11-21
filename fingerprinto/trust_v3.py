"""Verifier-owned durable, authority-signed enrollment/reference associations.

The authority is a provisioner chosen OUTSIDE the bundle, not the installation
key itself. Signing a record is an administrative trust act; this module cannot
witness a physical camera or manufacture evidence of Android hardware.
"""

from datetime import datetime
from contextlib import closing
import math
import sqlite3

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec

from .core import digest, domain
from .enrollment_requests import instant
from .prnu import Geometry
from .prnu_records import json_bytes
from .receipt_v3 import json_object
from .verification import load_installation_key, verify_installation_signature


def _hex(value):
    return type(value) is str and len(value) == 64 and all(c in "0123456789abcdef" for c in value)


def validate_record(body):
    required = {"schema", "installation", "package", "signer", "minimumVersion", "sequence",
                "notBefore", "expires", "status", "attestationPolicy", "references"}
    if type(body) is not dict or set(body) != required or body["schema"] != "fingerprinto/enrollment-v3/1":
        raise ValueError("Invalid enrollment record schema")
    if not all(_hex(body[k]) for k in ("installation", "signer", "attestationPolicy")):
        raise ValueError("Invalid enrollment digest")
    if type(body["package"]) is not str or not 1 <= len(body["package"].encode()) <= 255:
        raise ValueError("Invalid enrolled package")
    for key in ("minimumVersion", "sequence", "notBefore", "expires"):
        if type(body[key]) is not int or not 0 <= body[key] < 2**63:
            raise ValueError("Invalid record integer")
    if body["notBefore"] >= body["expires"] or body["status"] not in ("active", "revoked"):
        raise ValueError("Invalid record validity/status")
    if type(body["references"]) is not list or len(body["references"]) > 32:
        raise ValueError("Invalid reference associations")
    seen = set()
    for ref in body["references"]:
        if (type(ref) is not dict or set(ref) != {"hash", "geometry", "policyHash", "calibrationHash", "accept", "reject"}
                or not all(_hex(ref[k]) for k in ("hash", "policyHash", "calibrationHash"))):
            raise ValueError("Invalid reference/calibration identifiers")
        if type(ref["geometry"]) is not dict:
            raise ValueError("Invalid reference geometry")
        Geometry(**ref["geometry"])
        if ref["hash"] in seen:
            raise ValueError("Duplicate reference association")
        seen.add(ref["hash"])
        for key in ("accept", "reject"):
            value = ref[key]
            if value is not None and (type(value) not in (int, float) or not math.isfinite(value)):
                raise ValueError("Invalid calibrated threshold")
        if ref["accept"] is not None and ref["reject"] is not None and ref["reject"] >= ref["accept"]:
            raise ValueError("Overlapping calibrated regions")
    return body


def sign_record(body, authority_private_key):
    """Admin primitive: only after verified enrollment/provisioning/calibration.

    This deliberately accepts no receipt-controlled 'trusted' flags. Holding
    the separately pinned authority secret grants the right to approve records.
    """
    raw = json_bytes(validate_record(body))
    if not isinstance(authority_private_key, ec.EllipticCurvePrivateKey) or not isinstance(authority_private_key.curve, ec.SECP256R1):
        raise ValueError("P-256 provisioning authority required")
    signature = authority_private_key.sign(domain("enrollment-v3/1", digest(raw)), ec.ECDSA(hashes.SHA256()))
    return json_bytes({"body": raw.decode(), "signature": signature.hex()})


class TrustStore:
    def __init__(self, path, *, authority_spki):
        load_installation_key(authority_spki)
        self.authority = authority_spki
        self.path = str(path)
        if not self.path or self.path == ":memory:":
            raise ValueError("Persistent verifier-owned trust database required")
        with closing(sqlite3.connect(self.path)) as db, db:
            db.execute("CREATE TABLE IF NOT EXISTS enrollment_v3 (installation TEXT PRIMARY KEY, sequence INTEGER NOT NULL, record BLOB NOT NULL)")
            db.execute("""CREATE TABLE IF NOT EXISTS enrollment_v3_history (
                installation TEXT NOT NULL, sequence INTEGER NOT NULL, record BLOB NOT NULL,
                PRIMARY KEY (installation,sequence))""")
            # Upgrade existing stores by retaining their authenticated current
            # records. Earlier overwritten versions cannot be reconstructed.
            for identity, sequence, record in db.execute("SELECT installation,sequence,record FROM enrollment_v3").fetchall():
                self._archive(db, identity, sequence, record)

    def authenticate(self, record):
        envelope = json_object(record)
        if set(envelope) != {"body", "signature"} or type(envelope["body"]) is not str or type(envelope["signature"]) is not str:
            raise ValueError("Invalid signed enrollment envelope")
        raw = envelope["body"].encode()
        body = validate_record(json_object(raw))
        verify_installation_signature(self.authority, bytes.fromhex(envelope["signature"]), domain("enrollment-v3/1", digest(raw)))
        return body

    def _archive(self, db, identity, sequence, record):
        body = self.authenticate(record)
        if body["installation"] != identity or body["sequence"] != sequence:
            raise ValueError("Enrollment index/signed record mismatch")
        old = db.execute("SELECT record FROM enrollment_v3_history WHERE installation=? AND sequence=?",
                         (identity, sequence)).fetchone()
        if old is not None and old[0] != record:
            raise ValueError("Conflicting authenticated enrollment history")
        db.execute("INSERT OR IGNORE INTO enrollment_v3_history VALUES (?,?,?)", (identity, sequence, record))

    def retain(self, record):
        body = self.authenticate(record)
        with closing(sqlite3.connect(self.path, timeout=10)) as db, db:
            db.execute("PRAGMA synchronous=FULL")
            db.execute("BEGIN IMMEDIATE")
            old = db.execute("SELECT sequence,record FROM enrollment_v3 WHERE installation=?", (body["installation"],)).fetchone()
            latest = db.execute("SELECT sequence,record FROM enrollment_v3_history WHERE installation=? ORDER BY sequence DESC LIMIT 1",
                                (body["installation"],)).fetchone()
            if latest is not None and old != latest:
                raise ValueError("Enrollment current/history mismatch")
            if old is not None:
                self._archive(db, body["installation"], old[0], old[1])
                if old[1] == record:
                    return
                if body["sequence"] <= old[0]:
                    raise ValueError("Stale/conflicting enrollment update")
            self._archive(db, body["installation"], body["sequence"], record)
            db.execute("INSERT OR REPLACE INTO enrollment_v3 VALUES (?,?,?)", (body["installation"], body["sequence"], record))

    def history(self, installation_id: bytes):
        """Return original signed updates, authenticated and ordered by sequence.

        This is retained history, not proof of status at an arbitrary past date;
        whole-database rollback remains outside this local mechanism.
        """
        if type(installation_id) is not bytes or len(installation_id) != 32:
            raise ValueError("Expected installation ID")
        identity = installation_id.hex()
        with closing(sqlite3.connect(self.path)) as db:
            rows = db.execute("SELECT sequence,record FROM enrollment_v3_history WHERE installation=? ORDER BY sequence",
                              (identity,)).fetchall()
        for sequence, record in rows:
            body = self.authenticate(record)
            if body["installation"] != identity or body["sequence"] != sequence:
                raise ValueError("Corrupt enrollment history index")
        return tuple(record for _, record in rows)

    def lookup(self, fields, *, now: datetime):
        at = instant(now)
        with closing(sqlite3.connect(self.path)) as db:
            db.execute("BEGIN")
            row = db.execute("SELECT sequence,record FROM enrollment_v3 WHERE installation=?", (digest(fields.pk_I).hex(),)).fetchone()
            latest = db.execute("SELECT sequence,record FROM enrollment_v3_history WHERE installation=? ORDER BY sequence DESC LIMIT 1",
                                (digest(fields.pk_I).hex(),)).fetchone()
        if row != latest:
            raise ValueError("Enrollment current/history mismatch")
        if row is None:
            return None, "enrollment_record_missing"
        body = self.authenticate(row[1])
        if row[0] != body["sequence"]:
            raise ValueError("Enrollment record/database sequence mismatch")
        if body["installation"] != digest(fields.pk_I).hex():
            raise ValueError("Enrollment identity mismatch")
        if body["status"] == "revoked":
            return None, "installation_revoked"
        if not body["notBefore"] <= at < body["expires"]:
            return None, "enrollment_record_not_current"
        if (body["package"] != fields.packageName or body["signer"] != fields.signerDigest.hex()
                or fields.versionCode < body["minimumVersion"]):
            return None, "enrolled_release_mismatch"
        return body, "enrollment_record_valid"
