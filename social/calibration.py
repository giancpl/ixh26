"""Private-frame preflight using the frozen experimental PRNU decoder/mask."""
from fingerprinto.prnu import residual
from fingerprinto.prnu_image import decode_green


class UnsupportedFrame(ValueError):
    pass


class UnusableFrame(ValueError):
    pass


def check_frame(raw, geometry):
    try:
        values = decode_green(raw, geometry, source_width=geometry.source_width,
                              source_height=geometry.source_height)
    except (ValueError, OSError) as exc:
        raise UnsupportedFrame() from exc
    _, noise, mask = residual(values, geometry)
    if sum(mask) < .75 * len(mask) or not any(v != 0 for v, valid in zip(noise, mask) if valid):
        raise UnusableFrame()
