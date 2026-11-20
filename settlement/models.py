"""JSON metadata; the portable .ots file remains the actual proof."""
from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any


class Status(str, Enum):
    PENDING = "PENDING"
    CONFIRMED = "CONFIRMED"
    INVALID = "INVALID"
    UNVERIFIABLE = "UNVERIFIABLE"
    ERROR = "ERROR"


@dataclass(frozen=True)
class Diagnostic:
    code: str
    message: str


@dataclass(frozen=True)
class BitcoinEvidence:
    block_height: int | None = None
    block_time: int | None = None  # Unix seconds from the verified header


@dataclass
class SettlementResult:
    status: Status
    payload_sha256: str | None = None
    proof_file: str | None = None
    proof_sha256: str | None = None
    binding_checked: bool = False
    bitcoin: BitcoinEvidence = field(default_factory=BitcoinEvidence)
    diagnostics: list[Diagnostic] = field(default_factory=list)
    version: str = "upp-settlement/0.1"
    backend: str = "opentimestamps-bitcoin"
    network: str = "mainnet"

    def to_dict(self) -> dict[str, Any]:
        result = asdict(self)
        result["status"] = self.status.value
        return result
