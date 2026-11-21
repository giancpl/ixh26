"""Run after compiling CaptureEvidenceTest; pass its classes directory."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile


with tempfile.TemporaryDirectory() as directory:
    archive = Path(directory) / "capture.zip"
    subprocess.run(["java", "-cp", sys.argv[1], "CaptureEvidenceTest", str(archive)], check=True)
    result = subprocess.run([sys.executable, "-B", "-m", "fingerprinto", "check-capture", str(archive)],
                            capture_output=True, text=True, check=False)
    report = json.loads(result.stdout)
    assert result.returncode == 2, result.stderr
    assert report["file_binding"]["status"] == "valid"
    assert report["computed_size_bytes"] == 200000
    assert report["origin"]["status"] == "indeterminate"
    assert report["report_authenticated"] is False
    print("Java export / Python CLI interoperability passed")
