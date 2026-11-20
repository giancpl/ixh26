import os
from pathlib import Path

import pytest

from settlement import Status, stamp_file


@pytest.mark.network
@pytest.mark.skipif(os.environ.get("UPP_NETWORK_TEST") != "1", reason="Set UPP_NETWORK_TEST=1 to contact public calendars")
def test_real_calendar_submission(tmp_path: Path) -> None:
    payload = tmp_path / "opaque.bin"
    payload.write_bytes(os.urandom(64))
    result = stamp_file(payload, tmp_path / "proofs")
    assert result.status is Status.PENDING, result.to_dict()
    assert Path(result.proof_file).is_file()
