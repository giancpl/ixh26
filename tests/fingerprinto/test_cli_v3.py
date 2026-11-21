from contextlib import redirect_stdout
from io import StringIO
import json
import subprocess
import sys
from pathlib import Path
import tempfile
import unittest

from fingerprinto.cli import main


class CliV3Tests(unittest.TestCase):
    def run_cli(self,args):
        output=StringIO()
        with redirect_stdout(output):
            code=main(["v3",*args])
        return code,json.loads(output.getvalue())

    def test_explicit_pack_inspect_and_pending_verification(self):
        c=json.loads((Path(__file__).parent/"fixtures/v3.json").read_text())["cases"][0]
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)
            (p/"r").write_bytes(bytes.fromhex(c["receipt"]))
            (p/"j").write_bytes(bytes.fromhex(c["asset"]))
            (p/"policy").write_text(c["policy"],encoding="utf-8")
            (p/"report").write_text(c["report"],encoding="utf-8")
            args=["pack","--receipt",str(p/"r"),"--asset",str(p/"j"),"--policy",str(p/"policy"),"--report",str(p/"report"),"--out",str(p/"v3.zip")]
            self.assertEqual(self.run_cli(args)[0],0)
            code,result=self.run_cli(["inspect",str(p/"v3.zip")])
            self.assertEqual(code,0)
            self.assertFalse(result["trust_evaluated"])
            code,result=self.run_cli(["verify",str(p/"v3.zip")])
            self.assertEqual(code,2)
            self.assertEqual(result["decision"]["state"],"pending")
            self.assertFalse(result["decision"]["can_offer_choice"])
            self.assertEqual(self.run_cli(args)[0],1)  # Existing output is an operational conflict.

    def test_operational_errors_are_json_without_tracebacks(self):
        from fingerprinto.receipt_v3 import ReceiptV3
        c=json.loads((Path(__file__).parent/"fixtures/v3.json").read_text())["cases"][0]
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)
            authority=p/"authority.der"
            authority.write_bytes(ReceiptV3.from_bytes(bytes.fromhex(c["receipt"])).fields.pk_I)
            commands = [
                ["verify",str(p/"missing.zip")],
                ["trust-import","--record",str(p/"missing.json"),"--trust-db",str(p/"missing-parent"/"trust.db"),
                 "--authority-spki",str(authority)],
            ]
            for args in commands:
                with self.subTest(command=args[0]):
                    result=subprocess.run([sys.executable,"-m","fingerprinto","v3",*args],
                                          text=True,capture_output=True,timeout=15)
                    self.assertEqual(result.returncode,1,result.stderr)
                    self.assertEqual(json.loads(result.stdout)["code"],"operation_unavailable")
                    self.assertEqual(result.stderr,"")
                    self.assertNotIn(tmp,result.stdout)
            bad=p/"malformed.zip"
            bad.write_bytes(b"invalid archive")
            code,result=self.run_cli(["verify",str(bad)])
            self.assertEqual(code,3)
            self.assertEqual(result["code"],"invalid_evidence_or_operation")

    def test_experimental_evaluation_does_not_approve_thresholds(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"split.json"
            path.write_text(json.dumps({"enrollment":[],"development":[],"samples":[
                {"asset":"f"*64,"device":"A","mode":"wide","genuine":True,"score":12}]}))
            code,result=self.run_cli(["prnu-evaluate","--data",str(path),"--accept","10"])
            self.assertEqual(code,0)
            self.assertFalse(result["authorises_decisions"])
            self.assertEqual(result["sample_count"],1)
