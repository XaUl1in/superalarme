import hashlib
import http.client
import json
from pathlib import Path
import random
import tempfile
import time
import unittest
from minigames import make_game
from streak_server import StreakServer, signature


class MiniGameTests(unittest.TestCase):
    def test_all_game_types_accept_only_correct_answer(self):
        for kind in ("anagram", "math", "typing"):
            game = make_game(random.Random(5), kind)
            self.assertTrue(game.accepts(game.answer, 1))
            self.assertFalse(game.accepts("errado", 1))

    def test_typing_is_exact_and_strictly_under_fifteen_seconds(self):
        game = make_game(random.Random(1), "typing")
        self.assertTrue(game.accepts(game.answer, 14.999))
        for value, elapsed in ((game.answer, 15), (game.answer, -1), (game.answer + " ", 1), (game.answer.lower(), 1)):
            self.assertFalse(game.accepts(value, elapsed))

    def test_math_has_precedence_and_anagrams_are_complex(self):
        self.assertIn("× (", make_game(random.Random(1), "math").prompt)
        self.assertGreater(len(make_game(random.Random(1), "anagram").answer), 10)


class StreakServerTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.secret = bytes.fromhex("AB" * 32)
        self.path = Path(self.folder.name) / "streak.json"
        self.server = StreakServer(lambda: self.secret, self.path, "127.0.0.1", 0)
        self.addCleanup(self.server.close)

    def post(self, data, signed=True):
        body = json.dumps(data, separators=(",", ":")).encode()
        client = http.client.HTTPConnection("127.0.0.1", self.server.http.server_port, timeout=3)
        try:
            client.request("POST", "/streak", body, {"X-Alarm-Signature": signature(self.secret, body) if signed else "bad"})
            response = client.getresponse(); response.read()
            return response.status
        finally:
            client.close()

    def payload(self, **updates):
        return {"streak": 3, "best": 8, "revision": 10, "sentAt": int(time.time()), **updates}

    def test_signed_post_is_persisted_and_queued_for_tk(self):
        data = self.payload()
        self.assertEqual(200, self.post(data))
        self.assertEqual(data, self.server.events.get(timeout=1))
        self.assertNotIn(self.secret.hex(), self.path.read_text())
        self.assertEqual(3, json.loads(json.loads(self.path.read_text())["body"])["streak"])

    def test_rejects_unsigned_replayed_expired_and_invalid_payloads(self):
        self.assertEqual(403, self.post(self.payload(), signed=False))
        self.assertEqual(403, self.post(self.payload(sentAt=1)))
        self.assertEqual(403, self.post(self.payload(streak=True)))
        self.assertEqual(403, self.post([]))
        self.assertEqual(200, self.post(self.payload()))
        self.assertEqual(409, self.post(self.payload()))
        self.assertEqual(200, self.post(self.payload(revision=11, streak=0)))

    def test_saved_state_restores_and_pairing_change_accepts_new_revision(self):
        self.assertEqual(200, self.post(self.payload()))
        restored = StreakServer(lambda: self.secret, self.path, "127.0.0.1", 0)
        try:
            self.assertEqual(3, restored.events.get(timeout=1)["streak"])
        finally:
            restored.close()
        self.secret = hashlib.sha256(b"new pairing").digest()
        self.assertEqual(200, self.post(self.payload(revision=1)))


if __name__ == "__main__":
    unittest.main()
