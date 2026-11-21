"""Mine two local blocks after the calendar has submitted its transaction."""
import json
import time

from settlement import OTSConfig
from settlement.ots_backend import OTSBackend


def mine(timeout: float = 120, event_id: str | None = None) -> dict:
    backend = OTSBackend(OTSConfig(network="regtest", bitcoin_conf="/config/client.conf", timeout=30))
    node = backend._bitcoin_proxy(wallet="ots")
    try:
        deadline = time.monotonic() + timeout
        if event_id is not None:
            import re, httpx, os
            if not re.fullmatch(r"[0-9a-f]{64}", event_id):
                raise ValueError("Expected a signed event ID")
            base = os.environ.get("PHOTOANCHOR_API_URL", "http://api:8080")
            blocks = []
            with httpx.Client(timeout=10) as client:
                while time.monotonic() < deadline:
                    response = client.get(base + "/v1/posts/" + event_id)
                    response.raise_for_status()
                    snapshot = response.json()
                    # Wait for both submissions before mining the calendar batch.
                    ready = snapshot.get("settlement") is not None and (snapshot.get("evidence") is None or snapshot["evidence"].get("settlement") is not None)
                    if ready:
                        verified = client.post(base + "/v1/posts/" + event_id + "/verify", json={})
                        if verified.status_code == 200:
                            current = verified.json()
                            receipts = [current["settlement"]]
                            if current.get("evidence") is not None:
                                evidence = client.post(base + "/v1/posts/" + event_id + "/evidence/verify", json={})
                                evidence.raise_for_status()
                                receipts.append(evidence.json().get("settlement"))
                            if all(r and r["status"] == "CONFIRMED" for r in receipts):
                                return {"network":"regtest", "event_id":event_id, "blocks":blocks, "proofs_confirmed":len(receipts)}
                            if any(r and r["status"] == "INVALID" for r in receipts):
                                raise ValueError("An attached proof is invalid")
                        if node._call("getrawmempool"):
                            blocks.extend(node._call("generatetoaddress", 2, node._call("getnewaddress")))
                    time.sleep(1)
            raise TimeoutError("Both timestamp proofs did not confirm within the deadline")
        while not node._call("getrawmempool"):
            if time.monotonic() >= deadline:
                raise TimeoutError("No calendar transaction reached the regtest mempool within 120 seconds.")
            time.sleep(1)
        address = node._call("getnewaddress")
        blocks = node._call("generatetoaddress", 2, address)
        return {"network": "regtest", "blocks": blocks}
    finally:
        node.close()


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event", help="Wait for and confirm both post/evidence proofs, with bounded explicit mining")
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args()
    if not 1 <= args.timeout <= 300:
        parser.error("Timeout must be 1–300 seconds")
    print(json.dumps(mine(args.timeout, args.event)))
