"""Cross-verify real BC/PQClean ML-DSA and BC/cryptography Ed25519 signatures."""

from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from fingerprinto.hybrid import HybridPublicKey, HybridPrivateKey, HybridSignature, verify, verify_hardware_binding
from fingerprinto.hybrid_receipt import HybridReceipt

java_output = Path(sys.argv[1])
public = HybridPublicKey.from_bytes((java_output / "public.cbor").read_bytes())
signature = HybridSignature.from_bytes((java_output / "signature.cbor").read_bytes())
root = (java_output / "root.bin").read_bytes()
verify(public, root, signature, expected_device_id=public.device_id)
verify_hardware_binding(public, (java_output / "binding.der").read_bytes())
print("Java hybrid signature and hardware-binding fixture verified by Python")
receipt = HybridReceipt.from_bytes((java_output / "receipt.cbor").read_bytes())
commitment = receipt.verify(b"original", expected_device_id=public.device_id)
assert commitment == (java_output / "commitment.bin").read_bytes()
assert receipt.to_bytes() == (java_output / "receipt.cbor").read_bytes()
print("Java v2 receipt root, wire bytes and commitment match Python")

output = java_output / "python"
output.mkdir(exist_ok=True)
key = HybridPrivateKey.generate(public.hardware_spki)
signature = key.sign(root)
files = {"hardware.der": public.hardware_spki, "ed.pub": key.public.ed25519, "ml.pub": key.public.ml_dsa,
         "ed.sig": signature.ed25519, "ml.sig": signature.ml_dsa, "root.bin": root, "device.bin": key.public.device_id}
for name, data in files.items():
    (output / name).write_bytes(data)
