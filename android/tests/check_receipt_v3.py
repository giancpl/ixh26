import json
from pathlib import Path
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from fingerprinto.receipt_v3 import ReceiptV3

lines = []
for c in json.loads(Path("tests/fingerprinto/fixtures/v3.json").read_text())["cases"]:
    r = ReceiptV3.from_bytes(bytes.fromhex(c["receipt"]))
    f, e = r.fields, r.evidence
    values = [f.pk_I, f.chain[0], f.packageName, f.versionCode, f.signerDigest,
              f.manufacturer, f.model, f.localTime, f.sessionChallenge, *e.array(),
              bytes.fromhex(c["asset"]), None if c["credential"] is None else bytes.fromhex(c["credential"]),
              f.sigma, bytes.fromhex(c["receipt"])]
    line = ["-" if v is None else v.hex() if type(v) is bytes else str(v) for v in values]
    line += [k + "=" + v for k, v in c["roots"].items() if k not in ("sigma", "C", "OTS_SHA256_C")]
    lines.append("\t".join(line))
with tempfile.TemporaryDirectory() as tmp:
    path = Path(tmp) / "vectors.tsv"
    path.write_text("\n".join(lines))
    subprocess.run(["java", "-cp", sys.argv[1], "com.example.photoanchor.ReceiptV3Test", str(path)], check=True)
