from hashlib import sha256
from pathlib import Path
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
sys.path.insert(0, str(Path(__file__).resolve().parents[2]/"tests/fingerprinto"))
from test_prnu import trial
from fingerprinto.prnu import score

g, e, frames, query, other = trial()
ref=e.finish()
csv=lambda a: ",".join(repr(v) for v in a)
rows=["f\t"+sha256(str(n).encode()).hexdigest()+"\t"+csv(f) for n,f in enumerate(frames)]
rows.append("k\t"+csv(ref.pattern))
for values, identity in ((query,b"q"*32),(other,b"o"*32),([0.0]*1024,b"d"*32),
                         (query,sha256(b"0").digest())):
    s=score(values,g,ref,asset_digest=identity)
    rows.append("\t".join(["q",identity.hex(),s.reason,csv(values),"-" if s.value is None else repr(s.value)]))
with tempfile.TemporaryDirectory() as tmp:
    path=Path(tmp)/"numeric.tsv"
    path.write_text("\n".join(rows))
    subprocess.run(["java","-cp",sys.argv[1],"com.example.photoanchor.PrnuExperimentTest",str(path)],check=True)
