"""Recebe apenas estatísticas assinadas na LAN. Nunca recebe ou revela PIN/chave."""
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import os
from pathlib import Path
import queue
import threading
import time


def signature(secret, body):
    return hmac.new(secret, b"streak-v1\n" + body, hashlib.sha256).hexdigest()


def decode(secret, body, supplied, fresh=True):
    if not hmac.compare_digest(signature(secret, body), supplied):
        raise ValueError("Assinatura inválida")
    data = json.loads(body)
    if not isinstance(data, dict):
        raise ValueError("Formato inválido")
    for key in ("streak", "best", "revision", "sentAt"):
        if type(data.get(key)) is not int:
            raise ValueError("Formato inválido")
    if not 0 <= data["streak"] <= data["best"] <= 1_000_000 or not 0 < data["revision"] < 2**63:
        raise ValueError("Valores inválidos")
    if fresh and abs(time.time() - data["sentAt"]) > 120:
        raise ValueError("Envio expirado")
    return data


class StreakServer:
    def __init__(self, read_key, path=None, host="0.0.0.0", port=8080):
        self.read_key = read_key
        self.path = Path(path) if path else Path(__file__).with_name("streak.json")
        self.events = queue.Queue(maxsize=1)
        self.lock = threading.Lock()
        self.latest = {}
        self.key_id = None
        try:
            saved = json.loads(self.path.read_text(encoding="utf-8"))
            key = read_key()
            self.latest = decode(key, saved["body"].encode("utf-8"), saved["signature"], fresh=False)
            self.key_id = hashlib.sha256(key).digest()
            self.events.put_nowait(self.latest)
        except (OSError, ValueError, TypeError, KeyError):
            pass
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def setup(self):
                super().setup()
                self.connection.settimeout(3)

            def log_message(self, *_):
                pass

            def do_POST(self):
                if self.path != "/streak":
                    self.send_error(404); return
                peer = ipaddress.ip_address(self.client_address[0])
                if not (peer.is_private or peer.is_loopback):
                    self.send_error(403); return
                try:
                    length = int(self.headers.get("Content-Length", "0"))
                    if not 0 < length <= 4096:
                        self.send_error(413); return
                    body = self.rfile.read(length)
                    supplied = self.headers.get("X-Alarm-Signature", "")
                    with owner.lock:
                        key = owner.read_key()
                        data = decode(key, body, supplied)
                        key_id = hashlib.sha256(key).digest()
                        previous = owner.latest if owner.key_id == key_id else {}
                        if data["revision"] <= previous.get("revision", 0):
                            self.send_error(409); return
                        temp = owner.path.with_suffix(".tmp")
                        temp.write_text(json.dumps({"body": body.decode("utf-8"), "signature": supplied}), encoding="utf-8")
                        os.replace(temp, owner.path)
                        owner.latest = data; owner.key_id = key_id
                        try:
                            owner.events.get_nowait()
                        except queue.Empty:
                            pass
                        owner.events.put_nowait(data)
                except (ValueError, TypeError, KeyError, UnicodeError):
                    self.send_error(403); return
                except OSError:
                    self.send_error(503); return
                self.send_response(200)
                self.send_header("Content-Length", "2")
                self.end_headers(); self.wfile.write(b"OK")

        self.http = ThreadingHTTPServer((host, port), Handler)
        self.http.daemon_threads = True
        self.thread = threading.Thread(target=self.http.serve_forever, kwargs={"poll_interval": .1}, daemon=True)
        self.thread.start()

    def close(self):
        self.http.shutdown(); self.http.server_close(); self.thread.join(timeout=2)
