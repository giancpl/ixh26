"""Durable relay and OTS jobs. Only the operator mines regtest blocks."""
import asyncio
import fcntl
import os
from datetime import timedelta
import json
import signal
import threading
import time

from nostr_sdk import Client, Event, RelayUrl
from settlement import Diagnostic, SettlementResult, SettlementService, Status
from .config import Config
from .storage import Store


class RelayPublisher:
    def __init__(self, config):
        self.config = config

    async def _publish(self, event):
        client = Client()
        timeout = timedelta(seconds=self.config.timeout)
        try:
            await client.add_relay(RelayUrl.parse(self.config.relay_url))
            await client.connect(timeout)
            output = await client.send_event(Event.from_json(json.dumps(event, ensure_ascii=False)), ok_timeout=timeout)
            if not output.success:
                raise RuntimeError("The configured relay did not acknowledge the event.")
        finally:
            await client.shutdown()

    def publish(self, event):
        # Bound the complete connection/send/shutdown flow, including relay failure.
        async def bounded():
            return await asyncio.wait_for(self._publish(event), timeout=self.config.timeout * 3)
        asyncio.run(bounded())


class Worker:
    def __init__(self, config=None, *, store=None, service=None, relay=None):
        self.config = config or Config.from_env()
        self.store = store or Store(self.config)
        self.service = service or SettlementService(self.config.ots_config())
        self.relay = relay or RelayPublisher(self.config)

    def tick(self):
        job = self.store.claim()
        if job is None:
            return False
        locks = self.config.data_dir / "locks"
        locks.mkdir(parents=True, exist_ok=True)
        lock_fd = os.open(locks / (job["event_id"] + ".lock"), os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            os.close(lock_fd)
            self.store.finish(job)
            return True
        heartbeat_stop = threading.Event()

        def heartbeat():
            while not heartbeat_stop.wait(self.config.lease_seconds / 3):
                try:
                    if not self.store.renew(job):
                        return
                except Exception:
                    # Do not steal another owner's job after a DB outage.
                    return

        heartbeat_thread = threading.Thread(target=heartbeat, daemon=True)
        heartbeat_thread.start()
        try:
            row = self.store.get(job["event_id"])
            event = json.loads(row["event"])
            if job["kind"] == "relay":
                try:
                    self.relay.publish(event)
                except Exception:
                    self.store.finish(job, published=False)
                else:
                    self.store.finish(job, done=True, published=True)
            else:
                self._settle(job, event)
        except Exception:
            # The SQLite lease makes a crashed/failed attempt recoverable.
            self.store.finish(job)
        finally:
            heartbeat_stop.set()
            heartbeat_thread.join(timeout=1)
            os.close(lock_fd)
        return True

    def _settle(self, job, event):
        try:
            payload = self.store.payload_path(event)
            proof = self.store.proof_path(event["id"])
            if not proof.is_file():
                result = self.service.stamp_file(payload, self.store.proofs)
            else:
                upgraded = self.service.upgrade_proof(proof)
                result = self.service.verify_file(payload, proof)
                # Preserve diagnostics from unavailable calendars without
                # replacing independently verified payload/Bitcoin evidence.
                result.diagnostics.extend(d for d in upgraded.diagnostics
                                          if d.code not in {"not_verified"})
            self.store.settlement(event["id"], result)
            self.store.finish(job, done=result.status in (Status.CONFIRMED, Status.INVALID))
        except Exception:
            result = SettlementResult(Status.ERROR, network=self.config.network,
                                      diagnostics=[Diagnostic("operation_failed", "The settlement operation could not be completed.")])
            self.store.settlement(event["id"], result)
            self.store.finish(job)


def main():
    worker = Worker()
    stopped = threading.Event()
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, lambda *_: stopped.set())
    while not stopped.is_set():
        try:
            worked = worker.tick()
        except Exception:
            worked = False
        if not worked:
            stopped.wait(1)


if __name__ == "__main__":
    main()
