"""Restricted RFC 8949 deterministic CBOR used by FingerprinTO v1."""

MAX_BYTES = 256 * 1024


def _head(major: int, value: int) -> bytes:
    if value < 24:
        return bytes([major * 32 + value])
    for size, tag in ((1, 24), (2, 25), (4, 26), (8, 27)):
        if value < 1 << (8 * size):
            return bytes([major * 32 + tag]) + value.to_bytes(size, "big")
    raise ValueError("CBOR integer out of range")


def encode(value: object) -> bytes:
    if value is None:
        return b"\xf6"
    if type(value) is int:
        return _head(0, value) if value >= 0 else _head(1, -1 - value)
    if type(value) is bytes:
        return _head(2, len(value)) + value
    if type(value) is str:
        data = value.encode("utf-8", errors="strict")
        return _head(3, len(data)) + data
    if type(value) in (list, tuple):
        return _head(4, len(value)) + b"".join(encode(item) for item in value)
    raise ValueError("Unsupported CBOR type")


def decode(data: bytes) -> object:
    if type(data) is not bytes or len(data) > MAX_BYTES:
        raise ValueError("Invalid or oversized CBOR input")
    offset = 0

    def take(size: int) -> bytes:
        nonlocal offset
        if size > len(data) - offset:
            raise ValueError("Truncated CBOR input")
        result = data[offset:offset + size]
        offset += size
        return result

    def item(depth: int = 0) -> object:
        if depth > 2:
            raise ValueError("Excessive CBOR nesting")
        first = take(1)[0]
        if first == 0xf6:
            return None
        major, info = first >> 5, first & 31
        if major > 4 or info > 27:
            raise ValueError("Unsupported CBOR encoding")
        if info < 24:
            value = info
        else:
            size = 1 << (info - 24)
            value = int.from_bytes(take(size), "big")
            if value < (24 if size == 1 else 1 << (8 * (size // 2))):
                raise ValueError("Non-minimal CBOR encoding")
        if major == 0:
            return value
        if major == 1:
            return -1 - value
        if major in (2, 3):
            raw = take(value)
            return raw if major == 2 else raw.decode("utf-8", errors="strict")
        if value > len(data) - offset:
            raise ValueError("Truncated CBOR array")
        return [item(depth + 1) for _ in range(value)]

    result = item()
    if offset != len(data):
        raise ValueError("Trailing CBOR bytes")
    return result
