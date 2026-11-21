"""Compare all typed reducer states on a standalone JVM; no image/SDK operation."""

from itertools import product
from pathlib import Path
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from fingerprinto.evidence_v3 import Outcome, Required, Anchor, reduce_evidence

with tempfile.TemporaryDirectory() as directory:
    path = Path(directory) / "decisions.tsv"
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        for issuance in (False, True):
            for m, p, e, b, s, a in product(Outcome, Outcome, Required, Required, (None,) if issuance else Required, Anchor):
                result = reduce_evidence(m, p, enrollment=e, binding=b, signature=s, anchor=a, issuance=issuance)
                values = [m.name, p.name, e.name, b.name, s.name if s else "NONE", a.name, str(issuance).lower(),
                          result.state, result.level if result.level else "null", result.label,
                          str(result.can_issue).lower(), str(result.can_offer_choice).lower(),
                          ",".join(result.failed_checks), ",".join(result.pending_checks)]
                stream.write("\t".join(values) + "\n")
    subprocess.run(["java", "-cp", sys.argv[1], "EvidenceV3Test", str(path)], check=True)
