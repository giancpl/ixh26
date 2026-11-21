"""Optional, pinned experimental JPEG decoder. No orientation/scale correction."""

from io import BytesIO
import warnings
import re
from hashlib import sha256

from .prnu import Geometry

DECODER = "pillow-13.0.0/rgb-green-stored/1"
PIXEL_SRGB = "33c34ecfeb998459e0075ba26a0f1ea5042e20e2419c5962e495eba3208576f2"


def decoder_profile(identifier):
    if identifier == DECODER:
        return 1, None
    profile = re.fullmatch(r"pillow-13\.0\.0/rgb-green-stored-exif-([1-8])(?:-icc-([0-9a-f]{64}))?/1", identifier)
    if not profile or profile[2] not in (None, PIXEL_SRGB):
        raise ValueError("Unsupported decoder profile")
    return int(profile[1]), profile[2]


def decode_green(asset, geometry, *, source_width, source_height):
    from PIL import Image, __version__
    expected_orientation, expected_icc = decoder_profile(geometry.decoder)
    if __version__ != "13.0.0":
        raise ValueError("Decoder version/profile mismatch; revalidate before changing")
    if type(asset) is not bytes or not 1 <= len(asset) <= 128 * 1024 * 1024:
        raise ValueError("Invalid JPEG bytes")
    if (type(source_width) is not int or type(source_height) is not int
            or (source_width, source_height) != (geometry.source_width, geometry.source_height)
            or source_width < geometry.width or source_height < geometry.height
            or source_width * source_height > 32 * 1024 * 1024):
        raise ValueError("Unsupported source dimensions")
    with warnings.catch_warnings():
        warnings.simplefilter("error", Image.DecompressionBombWarning)
        with Image.open(BytesIO(asset), formats=["JPEG"]) as image:
            if image.size != (source_width, source_height) or image.mode != "RGB":
                raise ValueError("Unsupported JPEG dimensions/color mode")
            # Stored-coordinate processing, no EXIF transposition. The original
            # profile permits 1/absent; each explicit profile pins one orientation.
            icc = image.info.get("icc_profile")
            actual_icc = sha256(icc).hexdigest() if icc else None
            if image.getexif().get(274, 1) != expected_orientation or actual_icc != expected_icc:
                raise ValueError("Unsupported orientation/color profile")
            image.load()
            x, y = (source_width - geometry.width) // 2, (source_height - geometry.height) // 2
            crop = image.crop((x, y, x + geometry.width, y + geometry.height))
            return tuple(float(v) for v in crop.getchannel("G").get_flattened_data())
