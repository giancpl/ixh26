"""Opt-in real Core/calendar/server HTTP integration; no mocks."""
import os
import pytest


@pytest.mark.regtest
@pytest.mark.skipif(os.environ.get('IXH26_REGTEST_TEST') != '1', reason='Explicit server Docker integration')
def test_real_image_byte_server_regtest():
    from scripts.server_regtest_demo import run_demo
    result = run_demo(os.environ.get('IXH26_TEST_MODE', 'manual'))
    assert result['status'] == result['independent_verification'] == 'CONFIRMED'
    assert result['modified_photo'] == result['fake_proof'] == 'INVALID'
