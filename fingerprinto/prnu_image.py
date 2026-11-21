"""Optional, pinned experimental JPEG decoder. No orientation/scale correction."""

from io import BytesIO
import warnings

from .prnu import Geometry

DECODER = "pillow-13.0.0/rgb-green-stored/1"


def decode_green(asset, geometry, *, source_width, source_height):
    from PIL import Image, __version__
    if __version__ != "13.0.0" or geometry.decoder != DECODER:
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
            # Stored-coordinate processing, no EXIF transposition. Restrict the
            # trial to orientation 1/absent; reject ambiguity rather than guess.
            if image.getexif().get(274, 1) != 1 or image.info.get("icc_profile"):
                raise ValueError("Unsupported orientation/color profile")
            image.load()
            x, y = (source_width - geometry.width) // 2, (source_height - geometry.height) // 2
            crop = image.crop((x, y, x + geometry.width, y + geometry.height))
            return tuple(float(v) for v in crop.getchannel("G").get_flattened_data())
