"""Internal typed failures, mapped to structured public results."""
from .models import Status


class SettlementFailure(Exception):
    def __init__(self, code: str, message: str, status: Status = Status.ERROR) -> None:
        super().__init__(message)
        self.code = code
        self.status = status


class InvalidProof(SettlementFailure):
    def __init__(self, message: str = "Malformed or incompatible SHA-256 OTS proof.") -> None:
        super().__init__("invalid_proof", message, Status.INVALID)
