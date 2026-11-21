"""Experimental fixed-geometry PRNU core; scores alone never establish a match.

No universal acceptance/rejection threshold. See PRNU-EXPERIMENT.md before use.
All arithmetic uses binary64; loops/order are mirrored by the JVM module.
"""

from dataclasses import dataclass
from hashlib import sha256
import math
import struct

METHOD = "fingerprinto/prnu-haar-green/1"


def _ordered_sum(values):
    # Python 3.12 changed sum(float)'s algorithm. Keep the binary64 operation
    # order of the Java implementation and the original method-1 contract.
    total = 0.0
    for value in values:
        total += value
    return total


@dataclass(frozen=True)
class Geometry:
    camera: str
    mode: str
    width: int
    height: int
    decoder: str
    source_width: int | None = None
    source_height: int | None = None

    def __post_init__(self):
        for v in (self.camera, self.mode, self.decoder):
            if type(v) is not str or not 1 <= len(v.encode()) <= 255:
                raise ValueError("Invalid camera/mode/decoder")
        for v in (self.width, self.height):
            if type(v) is not int or not 16 <= v <= 512 or v % 2:
                raise ValueError("Expected even crop dimension in [16,512]")
        if (self.source_width is None) != (self.source_height is None):
            raise ValueError("Both source dimensions are required")
        if self.source_width is not None:
            if (type(self.source_width) is not int or type(self.source_height) is not int
                    or self.source_width < self.width or self.source_height < self.height
                    or self.source_width * self.source_height > 32 * 1024 * 1024):
                raise ValueError("Invalid source geometry")


def intensity(values, geometry):
    if len(values) != geometry.width * geometry.height:
        raise ValueError("Unsupported geometry")
    result = tuple(float(v) for v in values)
    if any(not math.isfinite(v) or not 0 <= v <= 255 for v in result):
        raise ValueError("Invalid green-channel intensity")
    return result


def center(values, mask, width, height):
    """One row-mean subtraction followed by one column-mean subtraction."""
    out = list(values)
    for y in range(height):
        ids = [y * width + x for x in range(width) if mask[y * width + x]]
        mean = _ordered_sum(out[p] for p in ids) / len(ids) if ids else 0.0
        for p in ids:
            out[p] -= mean
    for x in range(width):
        ids = [y * width + x for y in range(height) if mask[y * width + x]]
        mean = _ordered_sum(out[p] for p in ids) / len(ids) if ids else 0.0
        for p in ids:
            out[p] -= mean
    return tuple(out[p] if mask[p] else 0.0 for p in range(len(out)))


def residual(values, geometry):
    i = intensity(values, geometry)
    w, h = geometry.width, geometry.height
    out = [0.0] * len(i)
    # Orthonormal single-level 2-D Haar, soft threshold 3.0 on detail bands.
    # Exact inverse, no padding; even stored-coordinate crop anchored at (0,0).
    def shrink(v):
        return math.copysign(max(abs(v) - 3.0, 0.0), v)
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            p = y * w + x
            a, b, c, d = i[p], i[p + 1], i[p + w], i[p + w + 1]
            ll = (a + b + c + d) / 2
            lh = shrink((a - b + c - d) / 2)
            hl = shrink((a + b - c - d) / 2)
            hh = shrink((a - b - c + d) / 2)
            denoised = ((ll + lh + hl + hh) / 2, (ll - lh + hl - hh) / 2,
                        (ll + lh - hl - hh) / 2, (ll - lh - hl + hh) / 2)
            for idx, value in zip((p, p + 1, p + w, p + w + 1), denoised):
                out[idx] = i[idx] - value
    mask = tuple(8 <= v <= 247 for v in i)
    return i, center(out, mask, w, h), mask


@dataclass(frozen=True)
class Reference:
    geometry: Geometry
    pattern: tuple[float, ...]
    mask: tuple[bool, ...]
    frames: tuple[str, ...]

    def __post_init__(self):
        n = self.geometry.width * self.geometry.height
        if (type(self.pattern) is not tuple or type(self.mask) is not tuple
                or len(self.pattern) != n or len(self.mask) != n
                or any(type(v) is not float or not math.isfinite(v) for v in self.pattern)
                or any(type(v) is not bool for v in self.mask)
                or any(v != 0.0 for v, m in zip(self.pattern, self.mask) if not m)
                or type(self.frames) is not tuple or not 2 <= len(self.frames) <= 10000
                or len(set(self.frames)) != len(self.frames)):
            raise ValueError("Invalid reference arrays/frames")
        for frame in self.frames:
            if type(frame) is not str or len(frame) != 64 or any(c not in "0123456789abcdef" for c in frame):
                raise ValueError("Invalid enrollment asset digest")


class Estimator:
    def __init__(self, geometry):
        self.geometry = geometry
        n = geometry.width * geometry.height
        self.numerator, self.denominator, self.count = [0.0] * n, [0.0] * n, [0] * n
        self.frames = []

    def add(self, values, *, asset_digest: bytes):
        if type(asset_digest) is not bytes or len(asset_digest) != 32:
            raise ValueError("Expected exact original image SHA-256")
        frame = asset_digest.hex()
        if frame in self.frames or len(self.frames) >= 10000:
            raise ValueError("Duplicate frame or too many enrollment frames")
        i, w, mask = residual(values, self.geometry)
        if sum(mask) / len(mask) < 0.75:
            raise ValueError("Enrollment frame has insufficient valid area")
        for p, valid in enumerate(mask):
            if valid:
                self.numerator[p] += i[p] * w[p]
                self.denominator[p] += i[p] * i[p]
                self.count[p] += 1
        self.frames.append(frame)

    def finish(self):
        if len(self.frames) < 2:
            raise ValueError("At least two frames required by estimator, not proof of sufficiency")
        minimum = max(2, math.ceil(len(self.frames) * 0.8))
        mask = tuple(c >= minimum and d > 0 for c, d in zip(self.count, self.denominator))
        if sum(mask) / len(mask) < 0.75:
            raise ValueError("Reference has insufficient support")
        pattern = tuple(n / d if m else 0.0 for n, d, m in zip(self.numerator, self.denominator, mask))
        return Reference(self.geometry, center(pattern, mask, self.geometry.width, self.geometry.height),
                         mask, tuple(self.frames))


@dataclass(frozen=True)
class Score:
    value: float | None
    reason: str
    valid_fraction: float
    r0: float | None = None


def score(values, geometry, reference, *, asset_digest: bytes):
    if geometry != reference.geometry:
        return Score(None, "unsupported_geometry", 0.0)
    if type(asset_digest) is not bytes or len(asset_digest) != 32:
        raise ValueError("Expected query asset digest")
    if asset_digest.hex() in reference.frames:
        return Score(None, "query_used_for_enrollment", 0.0)
    i, residuals, valid = residual(values, geometry)
    w, h, radius = geometry.width, geometry.height, 3
    predicted = tuple(a * b for a, b in zip(i, reference.pattern))
    surface = []
    lowest = 1.0
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            a, b = [], []
            for y in range(radius, h - radius):
                for x in range(radius, w - radius):
                    p, q = y * w + x, (y + dy) * w + x + dx
                    if valid[p] and valid[q] and reference.mask[p] and reference.mask[q]:
                        a.append(residuals[p]); b.append(predicted[q])
            fraction = len(a) / ((h - 6) * (w - 6))
            lowest = min(lowest, fraction)
            if fraction < 0.75:
                return Score(None, "insufficient_valid_area", lowest)
            ma, mb = _ordered_sum(a) / len(a), _ordered_sum(b) / len(b)
            cross, aa, bb = 0.0, 0.0, 0.0
            for av, bv in zip(a, b):
                av, bv = av - ma, bv - mb
                cross += av * bv; aa += av * av; bb += bv * bv
            if aa <= 0 or bb <= 0:
                return Score(None, "zero_energy", lowest)
            surface.append((dy, dx, cross / math.sqrt(aa * bb)))
    r0 = next(r for dy, dx, r in surface if dy == dx == 0)
    off = [r * r for dy, dx, r in surface if abs(dy) > 1 or abs(dx) > 1]
    energy = _ordered_sum(off) / len(off)
    if energy <= 0:
        return Score(None, "zero_off_peak_energy", lowest, r0)
    value = math.copysign(r0 * r0 / energy, r0)
    if not math.isfinite(value):
        return Score(None, "nonfinite_score", lowest, r0)
    return Score(value, "experimental_score", lowest, r0)


def array_digest(values):
    """Diagnostic numeric vector identity; not a replacement for SHA256(J)."""
    return sha256(struct.pack("<" + "d" * len(values), *values)).digest()
