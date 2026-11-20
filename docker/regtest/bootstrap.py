"""Idempotent initialization of the isolated regtest development environment."""
import os
from pathlib import Path
import time

from settlement.ots_backend import OTSBackend, OTSConfig


def main() -> None:
    backend = OTSBackend(OTSConfig(network="regtest", bitcoin_conf="/config/client.conf", timeout=30))
    deadline = time.monotonic() + 90
    while True:
        try:
            rpc = backend._bitcoin_proxy()
            info = rpc._call("getblockchaininfo")
            if info["chain"] != "regtest":
                raise RuntimeError("Development bootstrap requires regtest")
            break
        except (OSError, ValueError, KeyError):
            if time.monotonic() >= deadline:
                raise TimeoutError("Regtest RPC bootstrap timed out") from None
            time.sleep(1)
    try:
        if "ots" not in rpc._call("listwallets"):
            existing = {wallet["name"] for wallet in rpc._call("listwalletdir")["wallets"]}
            rpc._call("loadwallet" if "ots" in existing else "createwallet", "ots")
    finally:
        rpc.close()
    wallet = backend._bitcoin_proxy(wallet="ots")
    try:
        if wallet._call("getbalance") < 1:
            address = wallet._call("getnewaddress", "", "bech32")
            wallet._call("generatetoaddress", 101, address)
        directory = Path("/calendar")
        directory.mkdir(parents=True, exist_ok=True)
        if not (directory / "uri").exists():
            (directory / "uri").write_text("http://calendar:14788\n")
        if not (directory / "hmac-key").exists():
            fd = os.open(directory / "hmac-key", os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            with os.fdopen(fd, "wb") as out:
                out.write(os.urandom(32))
        if not (directory / "donation_addr").exists():
            (directory / "donation_addr").write_text(wallet._call("getnewaddress", "", "bech32") + "\n")
    finally:
        wallet.close()
    print("Regtest wallet and calendar initialized.", flush=True)


if __name__ == "__main__":
    main()
