"""Run shared Java/Python receipt checks; pass the compiled Java classes path."""

from dataclasses import replace
import json
from pathlib import Path
import subprocess
import sys
import tempfile

# Executed as a script from the checkout root, without installing the package.
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from fingerprinto import Receipt, commitments


vectors = json.loads(Path("tests/fingerprinto/fixtures/v1.json").read_text())
cases = []
for case in vectors["cases"]:
    cases.append((Receipt.from_bytes(bytes.fromhex(case["receipt_cbor"])),
                  bytes.fromhex(case["J"]), bytes.fromhex(case["A"]) if case["A"] else None,
                  case["expected"], True))
base, asset, credential, _, _ = cases[0]
for version, time in ((0, -2**63), (23, -25), (24, -24), (255, -1),
                      (256, 0), (65535, 24), (65536, 2**32), (2**63 - 1, 2**63 - 1)):
    receipt = replace(base, versionCode=version, localTime=time, model="é" * 127)
    expected = {k: v.hex() for k, v in commitments(receipt, asset).items()}
    cases.append((receipt, asset, None, expected, False))
with tempfile.TemporaryDirectory() as directory:
    for i, (receipt, asset, credential, expected, valid) in enumerate(cases):
        values = {
            "pk_I": receipt.pk_I.hex(), "chain": receipt.chain[0].hex(),
            "packageName": receipt.packageName, "versionCode": receipt.versionCode,
            "signerDigest": receipt.signerDigest.hex(), "manufacturer": receipt.manufacturer,
            "model": receipt.model, "localTime": receipt.localTime,
            "sessionChallenge": receipt.sessionChallenge.hex() if receipt.sessionChallenge is not None else "null",
            "placement": receipt.placement, "J": asset.hex(),
            "A": credential.hex() if credential is not None else "null",
            "receipt_cbor": receipt.to_bytes().hex(), "signature_valid": str(valid).lower(), **expected,
        }
        # Java Properties.load uses Latin-1; escape non-ASCII explicitly.
        content = "\n".join(f"{k}={v}" for k, v in values.items())
        escaped = "".join(c if ord(c) < 128 else f"\\u{ord(c):04x}" for c in content)
        Path(directory, f"{i:02}.properties").write_text(escaped, encoding="ascii")
    subprocess.run(["java", "-cp", sys.argv[1], "ReceiptV1Test", directory], check=True)
