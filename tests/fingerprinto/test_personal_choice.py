from datetime import datetime,timezone
from pathlib import Path
import tempfile
import unittest

from fingerprinto.personal_choice import PersonalChoices
from fingerprinto.evidence_v3 import Anchor,Outcome,Required,reduce_evidence


class PersonalChoiceTests(unittest.TestCase):
    def test_preference_never_changes_decision_or_allows_failure(self):
        complete=reduce_evidence(Outcome.UNAVAILABLE,Outcome.INCONCLUSIVE,enrollment=Required.VALID,
                                 binding=Required.VALID,signature=Required.VALID,anchor=Anchor.ABSENT)
        failed=reduce_evidence(Outcome.FAIL,Outcome.PASS,enrollment=Required.VALID,
                              binding=Required.VALID,signature=Required.VALID,anchor=Anchor.ABSENT)
        with tempfile.TemporaryDirectory() as tmp:
            choices=PersonalChoices(Path(tmp)/"local.db")
            c=b"c"*32
            choices.remember(c,"trust",decision=complete,now=datetime.now(timezone.utc))
            self.assertEqual(choices.get(c).value,"trust")
            self.assertEqual(complete.level,"none")
            self.assertEqual(failed.state,"failed")
            with self.assertRaises(ValueError):
                choices.remember(c,"trust",decision=failed,now=datetime.now(timezone.utc))
            self.assertIsNone(choices.get(b"d"*32))
            choices.forget(c)
            self.assertIsNone(choices.get(c))
