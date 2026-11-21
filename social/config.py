"""Application settings; the settlement package keeps its own public defaults."""
from dataclasses import dataclass
import os
from pathlib import Path
from urllib.parse import urlsplit

from settlement import OTSConfig


@dataclass(frozen=True)
class Config:
    data_dir: Path = Path(".photoanchor")
    public_url: str = "http://127.0.0.1:8080"
    relay_url: str = "ws://127.0.0.1:7777"
    network: str = "mainnet"
    calendars: tuple[str, ...] = ()
    bitcoin_conf: str | None = None
    timeout: int = 10
    evidence_config: str | None = None
    retry_seconds: float = 5
    lease_seconds: float = 120

    def __post_init__(self):
        object.__setattr__(self, "data_dir", Path(self.data_dir))
        object.__setattr__(self, "public_url", self.public_url.rstrip("/"))
        url = urlsplit(self.public_url)
        if url.scheme not in {"http", "https"} or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise ValueError("A public HTTP media base URL is required.")
        if urlsplit(self.relay_url).scheme not in {"ws", "wss"}:
            raise ValueError("A WebSocket relay URL is required.")
        if self.retry_seconds <= 0 or self.lease_seconds < 1:
            raise ValueError("Positive retry and lease intervals are required.")
        self.ots_config()

    def ots_config(self):
        return OTSConfig(network=self.network, calendars=self.calendars,
                         bitcoin_conf=self.bitcoin_conf, timeout=self.timeout)

    @classmethod
    def from_env(cls):
        raw = os.environ.get("PHOTOANCHOR_CALENDARS")
        return cls(data_dir=Path(os.environ.get("PHOTOANCHOR_DATA_DIR", ".photoanchor")),
                   public_url=os.environ.get("PHOTOANCHOR_PUBLIC_URL", "http://127.0.0.1:8080"),
                   relay_url=os.environ.get("PHOTOANCHOR_RELAY_URL", "ws://127.0.0.1:7777"),
                   network=os.environ.get("PHOTOANCHOR_NETWORK", "mainnet"),
                   calendars=tuple(x.strip() for x in raw.split(",") if x.strip()) if raw is not None else (),
                   bitcoin_conf=os.environ.get("PHOTOANCHOR_BITCOIN_CONF"),
                   evidence_config=os.environ.get("PHOTOANCHOR_EVIDENCE_CONFIG"),
                   timeout=int(os.environ.get("PHOTOANCHOR_TIMEOUT", "10")))
