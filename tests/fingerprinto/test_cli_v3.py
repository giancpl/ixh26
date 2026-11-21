from contextlib import redirect_stdout
from io import StringIO
import json
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
            self.assertEqual(self.run_cli(args)[0],3)

    def test_experimental_evaluation_does_not_approve_thresholds(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"split.json"
            path.write_text(json.dumps({"enrollment":[],"development":[],"samples":[
                {"asset":"f"*64,"device":"A","mode":"wide","genuine":True,"score":12}]}))
            code,result=self.run_cli(["prnu-evaluate","--data",str(path),"--accept","10"])
            self.assertEqual(code,0)
            self.assertFalse(result["authorises_decisions"])
            self.assertEqual(result["sample_count"],1)
