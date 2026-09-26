import random
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from alarm import Puzzle, main, make_puzzle, parse_secret, setup, solve, totp


class AlarmTests(unittest.TestCase):
    def test_rfc6238_sha1_vectors(self):
        key = b"12345678901234567890"
        for timestamp, expected in [(59, "94287082"), (1111111109, "07081804"),
                                    (1111111111, "14050471"), (1234567890, "89005924"),
                                    (2000000000, "69279037"), (20000000000, "65353130")]:
            self.assertEqual(totp(key, timestamp, period=30, digits=8), expected)

    def test_production_period_and_padding(self):
        key = b"12345678901234567890"
        self.assertEqual(totp(key, 119), "287082")
        self.assertEqual(totp(key, 60), totp(key, 119))
        self.assertNotEqual(totp(key, 119), totp(key, 120))
        self.assertEqual(len(totp(key, 0)), 6)

    def test_secret_validation(self):
        self.assertEqual(parse_secret("ab " * 32), bytes([171]) * 32)
        for value in ["", "A" * 63, "G" * 64, None, 123]:
            with self.assertRaises(ValueError):
                parse_secret(value)

    def test_puzzle_gate_retries(self):
        puzzle = make_puzzle(random.Random(1))
        responses = iter(["errado", puzzle.answer])
        output = []
        solve(puzzle, lambda _: next(responses), output.append)
        self.assertIn("Ainda não. Tente novamente.", output)

    def test_all_puzzles_accept_own_answer(self):
        for seed in range(100):
            puzzle = make_puzzle(random.Random(seed))
            self.assertTrue(puzzle.accepts(" " + puzzle.answer.upper() + " "))
            self.assertFalse(puzzle.accepts(""))

    def test_setup_does_not_silently_replace_key(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "config.json"
            setup(path, write=lambda _: None)
            before = path.read_bytes()
            setup(path, read=lambda _: "não", write=lambda _: None)
            self.assertEqual(path.read_bytes(), before)

    def test_full_flow_wrong_then_correct_answer(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "config.json"
            path.write_text(json.dumps({"version": 1, "secret": "AB" * 32}), encoding="utf-8")
            output = io.StringIO()
            with patch("alarm.CONFIG", path), patch("alarm.make_puzzle", return_value=Puzzle("2 + 2?", "4")), \
                 patch("alarm.time.time", return_value=6000), patch("sys.stdin", io.StringIO("5\n4\n")), \
                 contextlib.redirect_stdout(output):
                self.assertEqual(main([]), 0)
            text = output.getvalue()
            self.assertLess(text.index("Ainda não"), text.index("Código de desativação"))
            self.assertIn(totp(bytes.fromhex("AB" * 32), 6000), text)

    def test_eof_does_not_release_pin(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "config.json"
            path.write_text(json.dumps({"version": 1, "secret": "AB" * 32}), encoding="utf-8")
            output = io.StringIO()
            with patch("alarm.CONFIG", path), patch("sys.stdin", io.StringIO("")), contextlib.redirect_stdout(output):
                self.assertEqual(main([]), 130)
            self.assertNotIn("Código de desativação:", output.getvalue())


if __name__ == "__main__":
    unittest.main()
