from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
import sqlite3
from pathlib import Path
import tempfile
import unittest

from fingerprinto.receipt_journal import ReceiptJournal


class JournalTests(unittest.TestCase):
    def test_concurrent_signing_and_restart_reuse_exact_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "journal.db"
            journal = ReceiptJournal(path)
            calls = []
            def producer():
                calls.append(1)
                return b"exact signature and receipt bytes"
            with ThreadPoolExecutor(max_workers=2) as pool:
                results = list(pool.map(lambda _: journal.sign_once("a" * 32, bytes(32), producer), range(2)))
            self.assertEqual(calls, [1])
            self.assertEqual(results[0], results[1])
            self.assertEqual(ReceiptJournal(path).sign_once("a" * 32, bytes(32), lambda: self.fail("must not re-sign")), results[0])
            with self.assertRaises(ValueError):
                journal.sign_once("a" * 32, b"x" * 32, producer)

    def test_failed_signing_rolls_back_and_corruption_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "journal.db"
            journal = ReceiptJournal(path)
            def fail():
                raise OSError("signing interrupted")
            with self.assertRaises(OSError):
                journal.sign_once("b" * 32, bytes(32), fail)
            self.assertEqual(journal.sign_once("b" * 32, bytes(32), lambda: b"signed"), b"signed")
            with closing(sqlite3.connect(path)) as db:
                db.execute("UPDATE signed_receipts SET receipt=?", (b"changed",))
                db.commit()
            with self.assertRaises(ValueError):
                journal.sign_once("b" * 32, bytes(32), lambda: b"replacement")


if __name__ == "__main__":
    unittest.main()
