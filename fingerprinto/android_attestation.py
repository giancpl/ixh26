"""Bounded DER reader for Android KeyDescription fields used by enrollment policy.

Not a general ASN.1 library. Unknown authorization tags are retained as opaque
signed fields and cannot satisfy any required policy condition.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class TLV:
    cls: int
    constructed: bool
    tag: int
    data: bytes
    raw: bytes


def items(data: bytes) -> list[TLV]:
    if type(data) is not bytes or len(data) > 16384:
        raise ValueError("Invalid attestation size")
    result = []
    offset = 0
    while offset < len(data):
        start = offset
        first = data[offset]
        offset += 1
        tag = first & 31
        if tag == 31:
            tag = 0
            count = 0
            while True:
                if offset == len(data) or count == 4:
                    raise ValueError("Invalid DER tag")
                part = data[offset]
                offset += 1
                if count == 0 and part & 127 == 0:
                    raise ValueError("Non-minimal DER tag")
                tag = tag * 128 + (part & 127)
                count += 1
                if not part & 128:
                    break
            if tag < 31:
                raise ValueError("Non-minimal DER tag")
        if offset == len(data):
            raise ValueError("Missing DER length")
        length = data[offset]
        offset += 1
        if length & 128:
            count = length & 127
            if not 1 <= count <= 2 or offset + count > len(data) or data[offset] == 0:
                raise ValueError("Invalid DER length")
            length = int.from_bytes(data[offset:offset + count], "big")
            offset += count
            if length < 128 or (count == 2 and length < 256):
                raise ValueError("Non-minimal DER length")
        if offset + length > len(data):
            raise ValueError("Truncated DER")
        body = data[offset:offset + length]
        offset += length
        result.append(TLV(first >> 6, bool(first & 32), tag, body, data[start:offset]))
        if len(result) > 256:
            raise ValueError("Excessive DER elements")
    return result


def expect(item: TLV, tag: int, constructed=False) -> bytes:
    if (item.cls, item.tag, item.constructed) != (0, tag, constructed):
        raise ValueError("Unexpected ASN.1 type")
    return item.data


def sequence(data: bytes) -> list[TLV]:
    outer = items(data)
    if len(outer) != 1:
        raise ValueError("Expected one DER sequence")
    return items(expect(outer[0], 16, True))


def number(item: TLV, tag=2) -> int:
    value = expect(item, tag)
    if not value or len(value) > 9 or value[0] & 128 or (len(value) > 1 and value[0] == 0 and not value[1] & 128):
        raise ValueError("Invalid nonnegative DER integer")
    return int.from_bytes(value, "big")


def members(item: TLV) -> list[TLV]:
    result = items(expect(item, 17, True))
    raw = [member.raw for member in result]
    if raw != sorted(set(raw)):
        raise ValueError("Non-canonical or duplicate DER SET elements")
    return result


def numbers(item: TLV) -> frozenset[int]:
    return frozenset(number(member) for member in members(item))


def authorizations(item: TLV) -> dict[int, TLV]:
    result = {}
    previous = -1
    for field in items(expect(item, 16, True)):
        if field.cls != 2 or not field.constructed or field.tag <= previous:
            raise ValueError("Duplicate, unordered or invalid authorization tag")
        value = items(field.data)
        if len(value) != 1:
            raise ValueError("Expected one explicit authorization value")
        result[field.tag] = value[0]
        previous = field.tag
    return result


@dataclass(frozen=True)
class Attestation:
    version: int
    attestation_level: int
    keymaster_version: int
    keymaster_level: int
    challenge: bytes
    software: dict[int, TLV]
    hardware: dict[int, TLV]


def parse(data: bytes) -> Attestation:
    fields = sequence(data)
    if len(fields) != 8:
        raise ValueError("Invalid KeyDescription arity")
    expect(fields[5], 4)  # uniqueId is not an installation identity.
    return Attestation(number(fields[0]), number(fields[1], 10), number(fields[2]),
                       number(fields[3], 10), expect(fields[4], 4),
                       authorizations(fields[6]), authorizations(fields[7]))


def application_id(item: TLV) -> tuple[dict[str, int], frozenset[bytes]]:
    fields = sequence(expect(item, 4))
    if len(fields) != 2:
        raise ValueError("Invalid application ID")
    packages = {}
    for package in members(fields[0]):
        info = items(expect(package, 16, True))
        if len(info) != 2:
            raise ValueError("Invalid package info")
        name = expect(info[0], 4).decode("utf-8", errors="strict")
        if not name or name in packages:
            raise ValueError("Duplicate or missing package name")
        packages[name] = number(info[1])
    digests = frozenset(expect(digest, 4) for digest in members(fields[1]))
    if not packages or not digests or any(len(digest) != 32 for digest in digests):
        raise ValueError("Invalid app signer digests")
    return packages, digests


def boot_state(item: TLV) -> tuple[bytes, bool, int, bytes]:
    fields = items(expect(item, 16, True))
    if len(fields) != 4:
        raise ValueError("Unsupported RootOfTrust arity")
    locked = expect(fields[1], 1)
    if locked not in (b"\x00", b"\xff"):
        raise ValueError("Invalid DER boolean")
    return expect(fields[0], 4), locked == b"\xff", number(fields[2], 10), expect(fields[3], 4)
