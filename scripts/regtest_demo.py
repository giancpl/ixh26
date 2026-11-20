"""Real local OTS lifecycle. No fake attestations or calendar/RPC mocks."""
from __future__ import annotations

import fcntl
import hashlib
import json
from pathlib import Path
import secrets
import subprocess
import sys
import time
from typing import Any
import uuid

from settlement.ots_backend import OTSBackend, OTSConfig


def run_demo(root: Path = Path("/artifacts")) -> dict[str, Any]:
    root.mkdir(parents=True, exist_ok=True)
    with (root / ".demo.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        return _run(root)


def _run(root: Path) -> dict[str, Any]:
    directory = root / ("run-" + uuid.uuid4().hex)
    directory.mkdir()
    payload = directory / "payload.bin"
    payload.write_bytes(secrets.token_bytes(64))
    proof = directory / "payload.bin.ots"
    deadline = time.monotonic() + 120
    options = ["--network", "regtest", "--bitcoin-conf", "/config/client.conf", "--timeout", "3"]

    def command(operation: str, args: list[str], filename: str, expected_exit: int) -> dict[str, Any]:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("Regtest demo exceeded its 120 second deadline")
        argv = [sys.executable, "-m", "settlement", operation, *args, *options]
        if operation in ("stamp", "upgrade"):
            argv.extend(["--calendar", "http://calendar:14788"])
        process = subprocess.run(argv, capture_output=True, timeout=remaining, check=False)
        result = json.loads(process.stdout)
        (directory / filename).write_text(json.dumps(result, indent=2) + "\n")
        if process.returncode != expected_exit:
            raise RuntimeError(f"{operation} failed: {json.dumps(result)}")
        return result

    backend = OTSBackend(OTSConfig(network="regtest", bitcoin_conf="/config/client.conf", timeout=3))
    rpc = backend._bitcoin_proxy()
    mining_backend = OTSBackend(OTSConfig(network="regtest", bitcoin_conf="/config/client.conf", timeout=30))
    wallet = mining_backend._bitcoin_proxy(wallet="ots")
    try:
        initial_mempool = set(rpc._call("getrawmempool"))
        stamp = command("stamp", [str(payload), "--out", str(directory)], "stamp.json", 0)
        if stamp["status"] != "PENDING":
            raise RuntimeError("Stamp must not claim Bitcoin confirmation")
        before = command("verify", [str(payload), str(proof)], "before-mining.json", 2)
        if before["status"] != "PENDING":
            raise RuntimeError("Proof must remain pending before mining")
        while not (set(rpc._call("getrawmempool")) - initial_mempool):
            if time.monotonic() >= deadline:
                raise TimeoutError("Calendar did not broadcast a timestamp transaction")
            time.sleep(0.5)
        wallet._call("generatetoaddress", 2, wallet._call("getnewaddress", "", "bech32"))
        while True:
            command("upgrade", [str(proof)], "upgrade.json", 0)
            # A pending result is expected until the calendar has processed both blocks.
            process = subprocess.run([sys.executable, "-m", "settlement", "verify", str(payload), str(proof), *options],
                                     capture_output=True, timeout=max(0.1, deadline - time.monotonic()), check=False)
            confirmed = json.loads(process.stdout)
            if confirmed["status"] == "CONFIRMED" and process.returncode == 0:
                break
            if confirmed["status"] != "PENDING":
                raise RuntimeError("Unexpected verification result: " + json.dumps(confirmed))
            if time.monotonic() >= deadline:
                raise TimeoutError("Calendar did not publish a completed Bitcoin proof")
            time.sleep(0.5)
        if (confirmed["network"] != "regtest" or not confirmed["binding_checked"]
                or confirmed["payload_sha256"] != hashlib.sha256(payload.read_bytes()).hexdigest()
                or confirmed["bitcoin"]["block_height"] is None
                or confirmed["bitcoin"]["block_time"] is None
                or not any(d["code"] == "regtest_only" for d in confirmed["diagnostics"])):
            raise RuntimeError("Missing independently verified regtest evidence")
        (directory / "confirmed.json").write_text(json.dumps(confirmed, indent=2) + "\n")
        modified = directory / "modified.bin"
        modified.write_bytes(payload.read_bytes() + b"modified")
        invalid = command("verify", [str(modified), str(proof)], "modified.json", 3)
        if invalid["status"] != "INVALID":
            raise RuntimeError("Modified payload was not rejected")
        summary = {"network": "regtest", "artifacts": str(directory), "before_mining": before["status"],
                   "after_mining": confirmed["status"], "modified_payload": invalid["status"],
                   "bitcoin": confirmed["bitcoin"]}
        (directory / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        return summary
    finally:
        rpc.close()
        wallet.close()


if __name__ == "__main__":
    print(json.dumps(run_demo()))
