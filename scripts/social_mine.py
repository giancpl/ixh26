"""Mine two local blocks after the calendar has submitted its transaction."""
import json
import time

from settlement import OTSConfig
from settlement.ots_backend import OTSBackend


def mine(timeout: float = 120) -> dict:
    backend = OTSBackend(OTSConfig(network="regtest", bitcoin_conf="/config/client.conf", timeout=30))
    node = backend._bitcoin_proxy(wallet="ots")
    try:
        deadline = time.monotonic() + timeout
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
    print(json.dumps(mine()))
