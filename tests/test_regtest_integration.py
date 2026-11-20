import os

import pytest


@pytest.mark.regtest
@pytest.mark.skipif(os.environ.get("UPP_REGTEST_TEST") != "1", reason="Run the Docker regtest integration service explicitly")
def test_real_local_lifecycle() -> None:
    from scripts.regtest_demo import run_demo
    result = run_demo()
    assert result["network"] == "regtest"
    assert result["before_mining"] == "PENDING"
    assert result["after_mining"] == "CONFIRMED"
    assert result["modified_payload"] == "INVALID"
    assert result["bitcoin"]["block_height"] >= 102
