"""Exact-byte experimental references and separate held-out calibration reports."""

import base64
from dataclasses import asdict
import json
import math
import os
from pathlib import Path
import struct
import tempfile
from urllib.parse import quote

from .prnu import METHOD, Geometry, Reference
from .receipt_v3 import json_object

REFERENCE_LIMIT = 4 * 1024 * 1024


def json_bytes(obj):
    return json.dumps(obj, ensure_ascii=False, allow_nan=False, sort_keys=True,
                      separators=(",", ":")).encode("utf-8")


def reference_bytes(ref):
    raw = json_bytes({"schema": "fingerprinto/reference/1", "method": METHOD,
                      "geometry": asdict(ref.geometry), "frames": list(ref.frames),
                      "pattern": base64.b64encode(struct.pack("<" + "d" * len(ref.pattern), *ref.pattern)).decode(),
                      "mask": base64.b64encode(bytes(ref.mask)).decode()})
    if len(raw) > REFERENCE_LIMIT:
        raise ValueError("Reference exceeds size limit")
    return raw


def read_reference(raw):
    if type(raw) is not bytes or len(raw) > REFERENCE_LIMIT:
        raise ValueError("Oversized reference")
    obj = json_object(raw, limit=REFERENCE_LIMIT)
    if set(obj) != {"schema", "method", "geometry", "frames", "pattern", "mask"} or obj["schema"] != "fingerprinto/reference/1" or obj["method"] != METHOD:
        raise ValueError("Unsupported reference schema/method")
    try:
        geom = Geometry(**obj["geometry"])
        n = geom.width * geom.height
        pattern = base64.b64decode(obj["pattern"], validate=True)
        mask = base64.b64decode(obj["mask"], validate=True)
        if len(pattern) != n * 8 or len(mask) != n or any(v not in (0, 1) for v in mask):
            raise ValueError("Invalid reference binary arrays")
        ref = Reference(geom, struct.unpack("<" + "d" * n, pattern), tuple(bool(v) for v in mask), tuple(obj["frames"]))
    except (TypeError, KeyError, struct.error) as exc:
        raise ValueError("Invalid reference fields") from exc
    if reference_bytes(ref) != raw:
        raise ValueError("Reference is not in canonical record encoding")
    return ref


def save_reference(ref, path):
    """Atomic non-overwriting publication. Caller controls directory permissions."""
    raw = reference_bytes(ref)
    read_reference(raw)
    path = Path(path)
    fd, tmp = tempfile.mkstemp(prefix=".prnu-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(raw); stream.flush(); os.fsync(stream.fileno())
        os.link(tmp, path)
    finally:
        os.unlink(tmp)
    return raw


def evaluate_samples(samples, *, enrollment_ids, development_ids, accept=None, reject=None):
    """Final-test counts only; never turns experimental data into trusted policy.

    Input rows: {asset, device, mode, genuine, score}. None is inconclusive.
    Development selection and error-policy approval remain independent acts.
    """
    for ids in (enrollment_ids, development_ids):
        if type(ids) is not list or len(set(ids)) != len(ids) or any(type(v) is not str or len(v) != 64 or any(c not in "0123456789abcdef" for c in v) for v in ids):
            raise ValueError("Invalid calibration split identifiers")
    if type(samples) is not list or not samples or set(enrollment_ids) & set(development_ids):
        raise ValueError("Missing test set or overlapping enrollment/development")
    for threshold in (accept, reject):
        if threshold is not None and (type(threshold) not in (int, float) or not math.isfinite(threshold)):
            raise ValueError("Invalid threshold")
    if accept is not None and reject is not None and reject >= accept:
        raise ValueError("Acceptance/rejection regions overlap")
    seen, groups = set(enrollment_ids) | set(development_ids), {}
    for row in samples:
        asset = row["asset"]
        if type(asset) is not str or len(asset) != 64 or any(c not in "0123456789abcdef" for c in asset) or asset in seen:
            raise ValueError("Reused/invalid final test image")
        seen.add(asset)
        if type(row["genuine"]) is not bool or not all(type(row[k]) is str and row[k] for k in ("device", "mode")):
            raise ValueError("Invalid test identity")
        s = row["score"]
        if s is not None and (type(s) not in (int, float) or not math.isfinite(s)):
            raise ValueError("Invalid final-test score")
        result = "inconclusive"
        if s is not None and accept is not None and s >= accept:
            result = "pass"
        elif s is not None and reject is not None and s <= reject:
            result = "fail"
        key = quote(row["device"], safe="") + "/" + quote(row["mode"], safe="")
        counts = groups.setdefault(key, {"genuine": 0, "impostor": 0, "false_match": 0,
                                         "false_reject": 0, "inconclusive_genuine": 0,
                                         "inconclusive_impostor": 0})
        kind = "genuine" if row["genuine"] else "impostor"
        counts[kind] += 1
        counts["false_match"] += int(not row["genuine"] and result == "pass")
        counts["false_reject"] += int(row["genuine"] and result == "fail")
        if result == "inconclusive":
            counts["inconclusive_" + kind] += 1
    for counts in groups.values():
        counts["false_match_rate"] = counts["false_match"] / counts["impostor"] if counts["impostor"] else None
        counts["false_reject_rate"] = counts["false_reject"] / counts["genuine"] if counts["genuine"] else None
    return {"schema": "fingerprinto/calibration-evaluation/1", "method": METHOD,
            "accept": accept, "reject": reject, "groups": groups,
            "sample_count": len(samples), "authorises_decisions": False}
