import itertools
import unittest

from fingerprinto.evidence_v3 import Outcome as O, Required as R, Anchor as A, guarded_prnu, reduce_evidence


class EvidenceV3Tests(unittest.TestCase):
    def reduce(self, manufacturer, prnu, **kwargs):
        values = dict(enrollment=R.VALID, binding=R.VALID, signature=R.VALID, anchor=A.ABSENT)
        values.update(kwargs)
        return reduce_evidence(manufacturer, prnu, **values)

    def test_every_origin_pair_and_priority(self):
        for manufacturer, prnu in itertools.product(O, repeat=2):
            with self.subTest(manufacturer=manufacturer, prnu=prnu):
                decision = self.reduce(manufacturer, prnu)
                if O.FAIL in (manufacturer, prnu):
                    self.assertEqual(decision.state, "failed")
                    self.assertFalse(decision.can_offer_choice)
                elif O.PENDING in (manufacturer, prnu):
                    self.assertEqual(decision.state, "pending")
                    self.assertFalse(decision.can_offer_choice)
                else:
                    level = "manufacturer" if manufacturer is O.PASS else "prnu" if prnu is O.PASS else "none"
                    self.assertEqual(decision.level, level)
                    self.assertTrue(decision.can_offer_choice)
                self.assertFalse(decision.can_issue)

    def test_issuance_without_manufacturer_is_allowed_only_after_required_checks(self):
        result = self.reduce(O.UNAVAILABLE, O.INCONCLUSIVE, issuance=True, signature=None)
        self.assertEqual(result.level, "none")
        self.assertTrue(result.can_issue)
        self.assertFalse(result.can_offer_choice)  # No stored C/signature yet.
        for status in (R.INVALID, R.PENDING):
            self.assertFalse(self.reduce(O.PASS, O.PASS, enrollment=status, issuance=True, signature=None).can_issue)

    def test_crypto_and_anchor_failures_override_any_positive_level(self):
        for name in ("enrollment", "binding", "signature", "anchor"):
            decision = self.reduce(O.PASS, O.PASS, **{name: A.FAILED if name == "anchor" else R.INVALID})
            self.assertEqual(decision.state, "failed")
            self.assertIn(name, decision.failed_checks)
            self.assertFalse(decision.can_offer_choice)
        decision = self.reduce(O.FAIL, O.PENDING, enrollment=R.PENDING)
        self.assertEqual(decision.state, "failed")
        self.assertEqual(decision.pending_checks, ("prnu", "enrollment"))

    def test_pending_required_checks_and_anchor_semantics(self):
        for name in ("enrollment", "binding", "signature"):
            self.assertEqual(self.reduce(O.PASS, O.PASS, **{name: R.PENDING}).state, "pending")
        self.assertEqual(self.reduce(O.PASS, O.PASS, anchor=A.UNRESOLVED).state, "pending")
        for anchor in (A.ABSENT, A.PENDING, A.VERIFIED):
            self.assertTrue(self.reduce(O.UNAVAILABLE, O.UNAVAILABLE, anchor=anchor).can_offer_choice)

    def test_prnu_calibration_and_quality_gates(self):
        flags = ("trusted_reference", "supported_geometry", "adequate_quality", "acceptance_calibrated", "rejection_calibrated")
        for values in itertools.product((False, True), repeat=5):
            for outcome in O:
                expected = outcome
                if outcome in (O.PASS, O.FAIL):
                    enabled = values[3] if outcome is O.PASS else values[4]
                    if not all(values[:3]) or not enabled:
                        expected = O.INCONCLUSIVE
                self.assertEqual(guarded_prnu(outcome, **dict(zip(flags, values))), expected)

    def test_untyped_inputs_and_unsigned_import_are_rejected(self):
        with self.assertRaises(ValueError):
            self.reduce("pass", O.PASS)
        with self.assertRaises(ValueError):
            self.reduce(O.PASS, O.PASS, signature=None)
        with self.assertRaises(ValueError):
            self.reduce(O.PASS, O.PASS, issuance=True)
        # Personal trust is not an input that can override these results.
        with self.assertRaises(TypeError):
            self.reduce(O.FAIL, O.PASS, personal_trust=True)


if __name__ == "__main__":
    unittest.main()
