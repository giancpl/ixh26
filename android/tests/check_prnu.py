from hashlib import sha256
from pathlib import Path
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
sys.path.insert(0, str(Path(__file__).resolve().parents[2]/"tests/fingerprinto"))
from test_prnu import trial
from fingerprinto.prnu import score
from fingerprinto.prnu_records import read_reference

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
    output=Path(tmp)/"java-reference.json"
    subprocess.run(["java","-cp",sys.argv[1],"com.example.photoanchor.PrnuExperimentTest",str(path),str(output)],check=True)
    imported=read_reference(output.read_bytes())
    assert imported.geometry==ref.geometry and imported.mask==ref.mask and imported.frames==ref.frames
    assert all(abs(a-b)<=1e-10*max(1,abs(a)) for a,b in zip(imported.pattern,ref.pattern))
    print("PRNU: Java canonical reference imported and validated independently in Python")
