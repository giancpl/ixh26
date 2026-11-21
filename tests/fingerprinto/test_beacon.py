"""Synthetic tests only: valid pixels do not establish physical acquisition."""
from io import BytesIO
import json
from pathlib import Path
import unittest
import tempfile
from contextlib import redirect_stdout
from io import StringIO
from unittest.mock import patch

from PIL import Image
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization

from fingerprinto import beacon as b
from fingerprinto.cbor import decode, encode


class BeaconTests(unittest.TestCase):
    def setUp(self):
        self.key = ec.derive_private_key(1, ec.SECP256R1())  # PUBLIC TEST KEY ONLY
        self.public = b.spki(self.key.public_key())
        self.options = dict(trusted_keys={b.digest(self.public): self.public}, challenge=bytes(range(32)),
                            installation_id=b"i"*32, now=1005)
        self.vector = json.loads((Path(__file__).parent / "fixtures/beacon-v1.json").read_text())
        self.packet = bytes.fromhex(self.vector["packet"])

    def test_token_and_image(self):
        payload, _ = b.parse(self.packet)
        self.assertEqual(b.domain("beacon-code/1", payload).hex(), self.vector["root"])
        self.assertEqual(encode(payload).hex(), self.vector["payloadCbor"])
        for report in (b.verify_token(self.packet, **self.options),
                       b.verify_image(b.render(self.packet), **self.options)):
            self.assertEqual(report["status"], "pass")
            self.assertFalse(report["physicalOriginVerified"])
            self.assertIsNone(report["conditionalContentWindow"])
            self.assertEqual(report["evidenceClass"], "S")

    def test_fresh_issuance(self):
        packet = b.issue(self.key, beacon_time=1000, challenge=self.options["challenge"],
                         installation_id=self.options["installation_id"])
        self.assertEqual(b.parse(packet)[0], b.parse(self.packet)[0])
        self.assertEqual(b.verify_token(packet, **self.options)["status"], "pass")

    def test_signature_slot_binding_and_key_tampering(self):
        fields = decode(self.packet)
        for index, value in ((0,"wrong"),(1,b"x"*32),(2,101),(3,b"x"*32)):
            changed = decode(self.packet); changed[0][index] = value
            self.assertEqual(b.verify_token(encode(changed), **self.options)["status"], "fail")
        fields[1] = fields[1][:-1] + bytes([fields[1][-1]^1])
        self.assertEqual(b.verify_token(encode(fields), **self.options)["status"], "fail")
        for overrides, reason in (({"trusted_keys":{}}, "unregistered_beacon"),
                                  ({"challenge":b"x"*32}, "wrong_session"),
                                  ({"installation_id":b"x"*32}, "wrong_session"),
                                  ({"now":999}, "future_or_stale_slot"),
                                  ({"now":1201}, "future_or_stale_slot"),
                                  ({"anchor_upper_bound":999}, "slot_after_anchor_bound")):
            self.assertEqual(b.verify_token(self.packet, **(self.options|overrides))["reason"],reason)

    def test_conditional_window(self):
        report = b.verify_token(self.packet, anchor_upper_bound=1004, **self.options)
        self.assertEqual(report["conditionalContentWindow"]["notBefore"],1000)
        self.assertEqual(report["conditionalContentWindow"]["notAfter"],1004)
        self.assertFalse(report["physicalOriginVerified"])

    def test_malformed_packets(self):
        for packet in (b"",bytes(401),self.packet+b"\0",b"\x82\xf5\x41x",b"\xff"):
            self.assertEqual(b.verify_token(packet,**self.options)["status"],"fail")
        for bad in (True,-1,1.2):
            with self.assertRaises(ValueError):
                b.verify_token(self.packet,**(self.options|{"now":bad}))

    def test_absent_and_unsupported_images(self):
        out=BytesIO(); Image.new("L",(576,576),255).save(out,format="PNG")
        self.assertEqual(b.verify_image(out.getvalue(),**self.options)["status"],"unavailable")
        for asset in (b"not an image",):
            self.assertEqual(b.verify_image(asset,**self.options)["status"],"inconclusive")
        self.assertEqual(b.verify_image(b.render(self.packet),roi=[0,0,10,10],**self.options)["status"],"inconclusive")

    def test_splicing_and_relay_are_not_detected(self):
        # Intentionally manufacture a scene on the host and paste a valid code.
        # Expected pass of SOFTWARE token verification is a counterexample to
        # any claimed physical-origin guarantee, not an implementation failure.
        tile=Image.open(BytesIO(b.render(self.packet)))
        scene=Image.new("RGB",(800,700),(100,150,200)); scene.paste(tile,(100,50))
        out=BytesIO(); scene.save(out,format="PNG")
        report=b.verify_image(out.getvalue(),roi=[100,50,576,576],**self.options)
        self.assertEqual(report["status"],"pass")
        self.assertIn("splicing_not_detected",report["limitations"])
        self.assertFalse(report["physicalOriginVerified"])
        # Same session, same slot: a relayed/replayed token is indistinguishable.
        self.assertEqual(b.verify_image(out.getvalue(),roi=[100,50,576,576],**self.options)["status"],"pass")

    def test_synthetic_jpeg_and_blur(self):
        from PIL import ImageFilter
        tile=Image.open(BytesIO(b.render(self.packet)))
        for quality in (95,70,40):
            out=BytesIO(); tile.save(out,format="JPEG",quality=quality)
            self.assertEqual(b.verify_image(out.getvalue(),**self.options)["status"],"pass")
        out=BytesIO(); tile.filter(ImageFilter.GaussianBlur(20)).save(out,format="PNG")
        self.assertNotEqual(b.verify_image(out.getvalue(),**self.options)["status"],"pass")

    def test_optical_tampering(self):
        changed = decode(self.packet); changed[0][2] += 1
        self.assertEqual(b.verify_image(b.render(encode(changed)),**self.options)["status"],"fail")

    def test_cli_issue_verify_and_refuse_overwrite(self):
        def run(args):
            output=StringIO()
            with patch("sys.argv", ["beacon",*args]), redirect_stdout(output):
                code=b.main()
            return code,json.loads(output.getvalue())
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)
            (p/"private.pem").write_bytes(self.key.private_bytes(serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
            (p/"public.der").write_bytes(self.public)
            common=["--challenge",self.options["challenge"].hex(),"--installation-id",self.options["installation_id"].hex()]
            issue=["issue",*common,"--time","1000","--private-key",str(p/"private.pem"),"--out",str(p/"code.png")]
            self.assertEqual(run(issue)[0],0)
            before=(p/"code.png").read_bytes()
            self.assertEqual(run(issue)[0],1)
            self.assertEqual((p/"code.png").read_bytes(),before)
            code,report=run(["verify",str(p/"code.png"),*common,"--time","1005","--public-key",str(p/"public.der")])
            self.assertEqual(code,0)
            self.assertFalse(report["physicalOriginVerified"])


if __name__ == "__main__":
    unittest.main()
