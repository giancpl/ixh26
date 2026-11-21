"""Byte binding only: no origin, enrollment, signature or ledger trust claims."""

from dataclasses import dataclass
from hashlib import sha256

from .cbor import decode, encode


def digest(data: bytes) -> bytes:
    return sha256(data).digest()


def domain(label: str, *fields: object) -> bytes:
    return digest(encode([label, *fields]))


def node(left: bytes, right: bytes) -> bytes:
    return domain("node", left, right)


@dataclass(frozen=True)
class Receipt:
    """Wire fields. Parsing validates types/limits, not cryptographic evidence.

    SPKI, X.509 and ECDSA checks belong to fingerprinto.verification.
    Successful parsing MUST NOT be presented as a valid signature.
    """

    pk_I: bytes
    chain: tuple[bytes, ...]
    packageName: str
    versionCode: int
    signerDigest: bytes
    manufacturer: str
    model: str
    localTime: int
    sessionChallenge: bytes | None
    placement: int
    sigma: bytes

    def __post_init__(self) -> None:
        for name, low, high in (("pk_I", 91, 91), ("signerDigest", 32, 32),
                                ("sigma", 8, 72)):
            value = getattr(self, name)
            if type(value) is not bytes or not low <= len(value) <= high:
                raise ValueError(f"Invalid {name} bytes")
        if (type(self.chain) is not tuple or not 1 <= len(self.chain) <= 8
                or any(type(c) is not bytes or not 1 <= len(c) <= 16384 for c in self.chain)):
            raise ValueError("Invalid certificate chain size or type")
        for name in ("packageName", "manufacturer", "model"):
            value = getattr(self, name)
            if type(value) is not str or not 1 <= len(value.encode("utf-8")) <= 255:
                raise ValueError(f"Invalid {name}")
        if type(self.versionCode) is not int or not 0 <= self.versionCode < 2**63:
            raise ValueError("Invalid versionCode")
        if type(self.localTime) is not int or not -2**63 <= self.localTime < 2**63:
            raise ValueError("Invalid localTime")
        if self.sessionChallenge is not None and (
                type(self.sessionChallenge) is not bytes or len(self.sessionChallenge) != 32):
            raise ValueError("Invalid sessionChallenge")
        if type(self.placement) is not int or self.placement not in (0, 1):
            raise ValueError("Invalid credential placement")

    def to_bytes(self) -> bytes:
        return encode([1, self.pk_I, self.chain, self.packageName, self.versionCode,
                       self.signerDigest, self.manufacturer, self.model,
                       self.localTime, self.sessionChallenge, self.placement, self.sigma])

    @classmethod
    def from_bytes(cls, data: bytes) -> "Receipt":
        fields = decode(data)
        if type(fields) is not list or len(fields) != 12:
            raise ValueError("Invalid receipt arity")
        if type(fields[0]) is not int or fields[0] != 1:
            raise ValueError("Unsupported receipt version")
        if type(fields[2]) is not list:
            raise ValueError("Invalid chain array")
        fields[2] = tuple(fields[2])
        return cls(*fields[1:])


def commitments(receipt: Receipt, asset: bytes, credential: bytes | None = None) -> dict[str, bytes]:
    """Reconstruct hashes; does NOT accept the origin or verify the signature.

    Returned C is exactly the content of commitment.bin. Its OTS file digest
    is OTS_SHA256_C. Callers must validate all evidence independently.
    """
    if type(asset) is not bytes or len(asset) > 128 * 1024 * 1024:
        raise ValueError("Invalid or oversized asset")
    if receipt.placement == 0 and credential is not None:
        raise ValueError("Unexpected detached credential")
    if receipt.placement == 1 and (type(credential) is not bytes
                                  or not 1 <= len(credential) <= 16 * 1024 * 1024):
        raise ValueError("Missing or oversized detached credential")
    r = {"SHA256_J": digest(asset), "ID_I": digest(receipt.pk_I)}
    r["F_I"] = domain("install", r["ID_I"], receipt.packageName,
                      receipt.versionCode, receipt.signerDigest)
    photo = [r["F_I"], r["SHA256_J"]]
    if credential is not None:
        r["SHA256_A"] = digest(credential)
        photo.append(r["SHA256_A"])
    r["F_P"] = domain("photo", *photo)
    values = [("ID_I", r["ID_I"]), ("manufacturer", receipt.manufacturer),
              ("model", receipt.model), ("localTime", receipt.localTime),
              ("sessionChallenge", receipt.sessionChallenge)]
    for i, (name, value) in enumerate(values):
        r[f"L{i}"] = domain("field", name, value)
    r["N01"] = node(r["L0"], r["L1"])
    r["N23"] = node(r["L2"], r["L3"])
    r["N0123"] = node(r["N01"], r["N23"])
    r["F_D"] = node(r["N0123"], r["L4"])
    r["N_IP"] = node(r["F_I"], r["F_P"])
    r["T"] = node(r["N_IP"], r["F_D"])
    r["R"] = domain("fingerprinto/v1", r["T"])
    r["sigma"] = receipt.sigma
    r["C"] = domain("receipt", r["R"], receipt.sigma)
    r["OTS_SHA256_C"] = digest(r["C"])
    return r
