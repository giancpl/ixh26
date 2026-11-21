from dataclasses import replace
from hashlib import sha256
from io import BytesIO
import json
import math
from pathlib import Path
import random
import tempfile
import unittest

from fingerprinto.prnu import Geometry, Estimator, residual, score
from fingerprinto.prnu_records import reference_bytes, read_reference, save_reference, evaluate_samples


def trial():
    g = Geometry("physical-wide-A", "fixed-jpeg-32", 32, 32, "numeric-test/1")
    rng = random.Random(93461)
    k = [rng.gauss(0, 0.008) for _ in range(1024)]
    frames = [tuple(110 * (1 + v) + rng.gauss(0, 0.3) for v in k) for _ in range(8)]
    estimator = Estimator(g)
    for n, frame in enumerate(frames):
        estimator.add(frame, asset_digest=sha256(str(n).encode()).digest())
    query = tuple(150 * (1 + v) + rng.gauss(0, 0.3) for v in k)
    other = tuple(150 + rng.gauss(0, 1.3) for _ in k)
    return g, estimator, frames, query, other


class PrnuTests(unittest.TestCase):
    def test_numeric_estimation_and_independent_queries(self):
        g, e, frames, query, other = trial()
        ref = e.finish()
        genuine = score(query, g, ref, asset_digest=b"q" * 32)
        impostor = score(other, g, ref, asset_digest=b"o" * 32)
        self.assertEqual(genuine.reason, "experimental_score")
        self.assertGreater(genuine.value, impostor.value)
        # Demonstrates a controlled signal, not camera error rates or threshold.
        self.assertIsNone(score(query, g, ref, asset_digest=sha256(b"0").digest()).value)
        self.assertEqual(score(query, replace(g, camera="wide-B"), ref, asset_digest=b"q"*32).reason, "unsupported_geometry")
        self.assertIsNone(score([0]*1024, g, ref, asset_digest=b"q"*32).value)
        with self.assertRaises(ValueError):
            e.add(frames[0], asset_digest=sha256(b"0").digest())

    def test_constant_nonfinite_and_quality(self):
        g, e, _, _, _ = trial()
        _, residuals, _ = residual([100]*1024, g)
        self.assertEqual(set(residuals), {0.0})
        for bad in ([math.nan]*1024, [256]*1024, [2]*12):
            with self.assertRaises(ValueError):
                residual(bad, g)
        with self.assertRaises(ValueError):
            e.add([0]*1024, asset_digest=b"a"*32)

    def test_reference_bytes_atomic_and_calibration_disjointness(self):
        _, e, _, _, _ = trial()
        ref = e.finish()
        raw = reference_bytes(ref)
        self.assertEqual(ref, read_reference(raw))
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)/"ref.json"
            self.assertEqual(raw, save_reference(ref, p))
            with self.assertRaises(FileExistsError):
                save_reference(ref, p)
        rows = [{"asset": "a"*64, "device": "A", "mode": "wide", "genuine": True, "score": 5},
                {"asset": "b"*64, "device": "B", "mode": "wide", "genuine": False, "score": None}]
        report = evaluate_samples(rows, enrollment_ids=["c"*64], development_ids=["d"*64], accept=4, reject=-1)
        self.assertFalse(report["authorises_decisions"])
        self.assertEqual(report["groups"]["B/wide"]["inconclusive_impostor"], 1)
        for enroll, dev, samples in ((["a"*64], [], rows), ([], ["a"*64], rows), ([], [], rows*2)):
            with self.assertRaises(ValueError):
                evaluate_samples(samples, enrollment_ids=enroll, development_ids=dev)

    def test_optional_jpeg_decoder(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Install the prnu extra to exercise the pinned decoder")
        from fingerprinto.prnu_image import DECODER, decode_green
        g = Geometry("A", "wide", 16, 16, DECODER, 32, 32)
        output = BytesIO()
        Image.new("RGB", (32,32), (70,100,120)).save(output, format="JPEG")
        result = decode_green(output.getvalue(), g, source_width=32, source_height=32)
        self.assertEqual(len(result), 256)
        with self.assertRaises(ValueError):
            decode_green(output.getvalue(), g, source_width=64, source_height=32)
