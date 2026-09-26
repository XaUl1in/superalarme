import tempfile
import queue
import tkinter as tk
import unittest
from pathlib import Path
from unittest.mock import patch, Mock

from desktop_gui import DesktopApp
from minigames import make_game


class DesktopGuiTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        config = Path(self.folder.name) / "config.json"
        config.write_text('{"version": 1, "secret": "' + 'AB' * 32 + '"}', encoding="utf-8")
        self.patch = patch("desktop_gui.CONFIG", config)
        self.patch.start()
        self.addCleanup(self.patch.stop)
        self.root = tk.Tk()
        self.root.withdraw()
        self.app = DesktopApp(self.root, start_server=False)
        self.addCleanup(self.app.close)

    def test_wrong_answer_does_not_show_pin(self):
        self.app.answer.insert(0, "errado")
        self.app.submit()
        self.assertIn("Ainda não", self.app.notice.cget("text"))
        self.assertFalse(hasattr(self.app, "pin_label"))

    def test_correct_answer_shows_pin_and_expiration_hides_it(self):
        self.app.answer.insert(0, self.app.puzzle.answer)
        with patch("desktop_gui.time.time", return_value=6000):
            self.app.submit()
        self.assertEqual(len(self.app.pin_label.cget("text").replace(" ", "")), 6)
        with patch("desktop_gui.time.time", return_value=6120):
            self.app.tick()
        self.assertIn("expirado", self.app.countdown.cget("text"))

    def test_pairing_screen_preserves_existing_key(self):
        self.app.configure()
        self.assertEqual(self.app.key, "AB" * 32)
        self.assertIn("ABABABAB", self.app.key_text.get("1.0", "end"))

    def test_expired_typing_cannot_reveal_pin(self):
        with patch("desktop_gui.make_game", return_value=make_game(kind="typing")):
            self.app.challenge()
        self.app.answer.insert(0, self.app.puzzle.answer)
        with patch("desktop_gui.time.monotonic", return_value=self.app.game_started + 15):
            self.app.submit()
        self.assertFalse(hasattr(self.app, "pin_label"))
        self.assertIn("esgotado", self.app.notice.cget("text"))

    def test_each_mini_game_reveals_pin_after_success(self):
        for kind in ("math", "anagram", "typing"):
            with patch("desktop_gui.make_game", return_value=make_game(kind=kind)):
                self.app.challenge()
            self.app.answer.insert(0, self.app.puzzle.answer)
            self.app.submit()
            self.assertEqual(6, len(self.app.pin_label.cget("text").replace(" ", "")))

    def test_received_streak_is_applied_by_ui_poll(self):
        self.app.server = Mock()
        self.app.server.events = queue.Queue()
        self.app.server.events.put({"streak": 7, "best": 12})
        self.app.addresses = queue.Queue()
        self.app.poll_sync()
        self.assertIn("7 / 21", self.app.streak_label.cget("text"))
        self.assertIn("12 dias", self.app.streak_label.cget("text"))


if __name__ == "__main__":
    unittest.main()
