import json

from settlement import BitcoinEvidence, Diagnostic, SettlementResult, Status


def test_versioned_json_metadata() -> None:
    result = SettlementResult(Status.PENDING, diagnostics=[Diagnostic("pending", "Not verified")])
    data = json.loads(json.dumps(result.to_dict()))
    assert data["version"] == "upp-settlement/0.1"
    assert data["backend"] == "opentimestamps-bitcoin"
    assert data["status"] == "PENDING"
    assert data["binding_checked"] is False
    assert data["bitcoin"] == {"block_height": None, "block_time": None}
    assert BitcoinEvidence(100, 1700000000).block_height == 100
