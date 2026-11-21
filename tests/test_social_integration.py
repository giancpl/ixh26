"""Explicitly enabled; real relay, calendar, API and Bitcoin Core."""
import os
from pathlib import Path

import pytest


@pytest.mark.social
@pytest.mark.skipif(os.environ.get("PHOTOANCHOR_SOCIAL_TEST") != "1", reason="Opt-in Docker social integration")
def test_real_social_lifecycle():
    from scripts.social_demo import run_demo
    result = run_demo(Path("/artifacts"))
    assert result["after_mining"] == "CONFIRMED"
    assert result["modified_photo"] == "INVALID"
