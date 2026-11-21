"""Opt-in real v3 OTS/Core exercise; synthetic identity, no sensor-origin claim."""
import os
import pytest


@pytest.mark.regtest
@pytest.mark.skipif(os.environ.get("PHOTOANCHOR_CRYPTO_TEST") != "1", reason="Explicit Docker regtest opt-in")
def test_real_crypto_receipt_anchor():
    from scripts.crypto_regtest_demo import run_demo
    result = run_demo()
    assert result["before"] == "pending" and result["after"] == "verified"
    assert result["network"] == "regtest" and result["modified"] == "failed"
    assert result["origin"] == "none" and result["hardware_tested"] is False
