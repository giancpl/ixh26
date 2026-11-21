"""Opt-in actual HTTP/relay/OTS/Core application flow; synthetic device identity."""
import os
import pytest


@pytest.mark.social
@pytest.mark.skipif(os.environ.get('PHOTOANCHOR_V3_APP_TEST')!='1',reason='Explicit isolated Docker application opt-in')
def test_real_v3_app_flow():
    from scripts.v3_app_demo import run
    result=run()
    assert result['synthetic_identity'] and result['hardware_origin_verified'] is False
    assert result['origin']=='none' and result['calibration_approved'] is False
    assert result['nostr']['status']==result['evidence']['status']=='CONFIRMED'
    assert result['both_cli_verified'] and result['modified_photo']=='INVALID' and result['restart_persistence']
