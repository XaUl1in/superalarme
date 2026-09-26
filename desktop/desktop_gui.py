"""Interface do desktop, sem dependências além do Tkinter fornecido pelo Python."""
import json
import math
import os
import queue
import socket
import threading
from pathlib import Path
import sys
import time
import tkinter as tk
from tkinter import messagebox

from alarm import CONFIG, PERIOD, parse_secret, setup, totp
from minigames import make_game
from streak_server import StreakServer

# A distribuição Python 3.14 deste PC omitiu os scripts Tcl/Tk.
# Só usa a cópia incluída quando ela corresponde à versão das DLLs.
installed_tcl = [Path(sys.base_prefix) / "tcl" / "tcl9.0" / "init.tcl",
                 Path(sys.base_prefix) / "lib" / "tcl9.0" / "init.tcl"]
if tk.TclVersion == 9.0 and not any(path.exists() for path in installed_tcl):
    vendor = Path(__file__).with_name("vendor")
    if (vendor / "tcl9.0" / "init.tcl").exists():
        os.environ.setdefault("TCL_LIBRARY", str(vendor / "tcl9.0"))
        os.environ.setdefault("TK_LIBRARY", str(vendor / "tk9.0"))

BG = "#14111f"
PANEL = "#292239"
PURPLE = "#d7acf7"
TEXT = "#f8f2ff"
MUTED = "#b1a5c2"


class DesktopApp:
    def __init__(self, root, configure=False, start_server=True):
        self.root = root
        root.title("Hardcore · Seu despertar começa aqui")
        root.geometry("980x740")
        root.minsize(900, 700)
        root.configure(bg=BG)
        icon = Path(__file__).with_name("assets") / "hardcore.ico"
        if icon.exists():
            root.iconbitmap(str(icon))
        self.timer = None
        self.pin_expires = 0
        self.secret = None
        self.puzzle = None
        self.server = None
        self.sync_timer = None
        self.closed = False
        root.protocol("WM_DELETE_WINDOW", self.close)
        sidebar = tk.Frame(root, bg="#211a30", width=280)
        sidebar.pack(side="left", fill="y")
        sidebar.pack_propagate(False)
        self.label(sidebar, "H A R D C O R E", 13, PURPLE, bg="#211a30").pack(pady=(38, 6))
        self.moon(sidebar)
        self.label(sidebar, "Bom dia.", 34, bg="#211a30").pack(pady=(0, 12))
        self.label(sidebar, "Um dia de cada vez.", 14, MUTED, bg="#211a30").pack()
        self.streak_label = self.label(sidebar, "OFENSIVA\nAguardando celular…", 16, PURPLE, bg="#211a30", wraplength=240)
        self.streak_label.pack(padx=20, pady=(16, 4))
        self.network_label = self.label(sidebar, "Rede local · porta 8080", 10, MUTED, bg="#211a30", wraplength=230)
        self.network_label.pack(padx=20, pady=(0, 8))
        self.button(sidebar, "Parear computador", self.configure, primary=False).pack(side="bottom", padx=24, pady=(8, 12), fill="x")
        self.button(sidebar, "Novo desafio", self.challenge, primary=False).pack(side="bottom", padx=24, pady=(12, 0), fill="x")
        self.label(sidebar, "PIN OFFLINE · OFENSIVA NA LAN", 10, MUTED, bg="#211a30").pack(side="bottom", pady=12)
        self.body = tk.Frame(root, bg=BG, padx=38, pady=38)
        self.body.pack(side="left", fill="both", expand=True)
        if start_server:
            try:
                self.server = StreakServer(self.read_key, CONFIG.with_name("streak.json"))
                # Name resolution is off the Tk thread, just like the HTTP listener.
                self.addresses = queue.Queue()
                threading.Thread(target=self.find_addresses, daemon=True).start()
                self.poll_sync()
            except OSError:
                self.network_label.config(text="Porta 8080 indisponível.\nO PIN continua funcionando.")
        if configure or not CONFIG.exists():
            self.configure()
        else:
            self.challenge()

    def label(self, parent, text, size=14, color=TEXT, bg=BG, **options):
        return tk.Label(parent, text=text, font=("Segoe UI", size), fg=color, bg=bg,
                        justify="left", anchor="w", **options)

    def button(self, parent, text, action, primary=True):
        return tk.Button(parent, text=text, command=action, font=("Segoe UI", 12, "bold"),
                         fg=BG if primary else TEXT, bg=PURPLE if primary else PANEL,
                         activebackground="#e5c5fa", activeforeground=BG, relief="flat", bd=0,
                         padx=18, pady=13, cursor="hand2", takefocus=True)

    def moon(self, parent):
        canvas = tk.Canvas(parent, width=260, height=210, bg="#211a30", highlightthickness=0)
        canvas.pack(pady=(10, 4))
        canvas.create_oval(32, 10, 232, 210, fill="#2c203d", outline="")
        canvas.create_oval(55, 35, 192, 175, fill="#ffd393", outline="")
        canvas.create_oval(92, 10, 219, 140, fill="#2c203d", outline="")
        for x, y, radius in [(171, 101, 16), (147, 49, 8), (211, 65, 6)]:
            points = []
            for i in range(10):
                angle = -math.pi / 2 + i * math.pi / 5
                r = radius if i % 2 == 0 else radius * .45
                points.extend((x + math.cos(angle) * r, y + math.sin(angle) * r))
            canvas.create_polygon(points, fill="#ffda89", outline="")

    def clear(self):
        if self.timer is not None:
            self.root.after_cancel(self.timer)
            self.timer = None
        self.root.unbind("<Return>")
        for child in self.body.winfo_children():
            child.destroy()

    def read_key(self):
        data = json.loads(CONFIG.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or data.get("version") != 1:
            raise ValueError("Configuração não reconhecida.")
        return parse_secret(data.get("secret"))

    def challenge(self):
        try:
            self.secret = self.read_key()
        except (OSError, ValueError, TypeError) as exc:
            self.configure()
            self.notice.config(text=f"Configure a chave para começar. {exc}")
            return
        self.clear()
        self.puzzle = make_game()
        self.label(self.body, "01  /  DESPERTE A MENTE", 11, PURPLE).pack(anchor="w")
        self.label(self.body, "Um desafio antes\ndo seu dia.", 29).pack(anchor="w", pady=(16, 8))
        self.label(self.body, "Acerte para revelar o código do celular.", 13, MUTED).pack(anchor="w", pady=(0, 24))
        card = tk.Frame(self.body, bg=PANEL, padx=22, pady=22)
        card.pack(fill="x")
        self.label(card, self.puzzle.prompt, 19, bg=PANEL, wraplength=450).pack(anchor="w", fill="x")
        self.answer = tk.Entry(self.body, font=("Segoe UI", 20), fg=TEXT, bg=PANEL,
                               insertbackground=PURPLE, relief="flat", highlightthickness=1,
                               highlightbackground="#584466", highlightcolor=PURPLE)
        self.answer.pack(fill="x", ipady=12, pady=(24, 12))
        self.notice = self.label(self.body, "Sua resposta", 12, MUTED, wraplength=450)
        self.notice.pack(anchor="w", pady=(0, 16))
        self.button(self.body, "Revelar meu PIN  →", self.submit).pack(fill="x")
        self.answer.focus_set()
        self.root.bind("<Return>", lambda _: self.submit())
        self.game_started = time.monotonic()
        if self.puzzle.kind == "typing":
            for event in ("<<Paste>>", "<Control-v>", "<Control-V>", "<Shift-Insert>"):
                self.answer.bind(event, lambda _: "break")
            self.typing_tick()

    def submit(self):
        if self.puzzle is None:
            return
        elapsed = time.monotonic() - self.game_started
        if not self.puzzle.accepts(self.answer.get(), elapsed):
            if self.puzzle.kind == "typing" and elapsed >= 15:
                self.notice.config(text="Tempo esgotado. Comece um novo desafio.", fg="#ffb7b7")
                return
            self.notice.config(text="Ainda não. Confira sua resposta e tente de novo.", fg="#ffb7b7")
            self.answer.select_range(0, "end")
            return
        now = time.time()
        pin = totp(self.secret, now)
        self.pin_expires = (int(now) // PERIOD + 2) * PERIOD
        self.clear()
        self.puzzle = None
        self.label(self.body, "02  /  PRONTO PARA DESPERTAR", 11, PURPLE).pack(anchor="w")
        self.label(self.body, "Você conseguiu.", 30).pack(anchor="w", pady=(22, 12))
        self.label(self.body, "Digite este PIN no seu celular.", 15, MUTED).pack(anchor="w", pady=(0, 28))
        card = tk.Frame(self.body, bg=PANEL, padx=20, pady=24)
        card.pack(fill="x")
        self.label(card, "CÓDIGO DE DESATIVAÇÃO", 11, PURPLE, bg=PANEL).pack(anchor="center")
        self.pin_label = self.label(card, pin[:3] + " " + pin[3:], 48, bg=PANEL)
        self.pin_label.pack(anchor="center", pady=(10, 4))
        self.countdown = self.label(self.body, "", 14, PURPLE)
        self.countdown.pack(anchor="w", pady=22)
        self.label(self.body, "Mantenha a hora automática no PC e no celular.\nCada novo código exige outro desafio.", 12, MUTED).pack(anchor="w", pady=(0, 28))
        self.button(self.body, "Novo desafio", self.challenge).pack(fill="x")
        self.tick()

    def tick(self):
        remaining = max(0, math.ceil(self.pin_expires - time.time()))
        if remaining == 0:
            self.pin_label.config(text="— — —", fg=MUTED)
            self.countdown.config(text="Código expirado. Resolva um novo desafio.", fg="#ffb7b7")
            self.timer = None
            return
        self.countdown.config(text=f"Disponível por {remaining // 60:02d}:{remaining % 60:02d}")
        self.timer = self.root.after(250, self.tick)

    def configure(self):
        self.clear()
        self.puzzle = None
        self.label(self.body, "PAREAMENTO", 11, PURPLE).pack(anchor="w")
        self.label(self.body, "Seu PC + seu celular.", 28).pack(anchor="w", pady=(20, 12))
        self.label(self.body, "Copie esta chave para Ajustes no aplicativo.\nEla conecta os códigos dos dois aparelhos offline.", 14, MUTED).pack(anchor="w", pady=(0, 22))
        self.key_text = tk.Text(self.body, height=4, wrap="char", font=("Consolas", 14), bg=PANEL,
                                fg=TEXT, relief="flat", padx=16, pady=16)
        self.key_text.pack(fill="x")
        self.key_text.config(state="disabled")
        self.notice = self.label(self.body, "", 12, MUTED, wraplength=450)
        self.notice.pack(anchor="w", pady=14)
        self.button(self.body, "Copiar chave", self.copy_key).pack(fill="x", pady=(0, 10))
        self.button(self.body, "Gerar chave" if not CONFIG.exists() else "Gerar nova chave", self.generate_key, primary=False).pack(fill="x", pady=(0, 10))
        self.button(self.body, "Ir para o desafio  →", self.challenge).pack(fill="x")
        self.refresh_key()

    def refresh_key(self):
        self.key = ""
        if CONFIG.exists():
            try:
                self.key = self.read_key().hex().upper()
            except (OSError, ValueError, TypeError):
                self.notice.config(text="A configuração não pôde ser lida. Gere uma nova chave.")
        self.key_text.config(state="normal")
        self.key_text.delete("1.0", "end")
        self.key_text.insert("1.0", " ".join(self.key[i:i + 8] for i in range(0, len(self.key), 8)) if self.key else "Sua chave aparecerá aqui.")
        self.key_text.config(state="disabled")

    def generate_key(self):
        if CONFIG.exists() and not messagebox.askyesno("Substituir a chave?", "Você precisará salvar a nova chave no celular também. Continuar?", parent=self.root):
            return
        try:
            setup(CONFIG, read=lambda _: "SUBSTITUIR", write=lambda _: None)
            self.refresh_key()
            self.notice.config(text="Chave salva no PC. Agora copie para o celular.", fg=PURPLE)
        except OSError as exc:
            self.notice.config(text=f"Não foi possível salvar: {exc}", fg="#ffb7b7")

    def copy_key(self):
        if not self.key:
            self.notice.config(text="Gere uma chave primeiro.")
            return
        self.root.clipboard_clear()
        self.root.clipboard_append(self.key)
        self.notice.config(text="Chave copiada. Cole no campo de pareamento do celular.", fg=PURPLE)

    def typing_tick(self):
        if self.puzzle is None or self.puzzle.kind != "typing":
            return
        remaining = max(0, 15 - (time.monotonic() - self.game_started))
        self.notice.config(text=f"Digite sem colar · {remaining:.1f}s restantes")
        if remaining <= 0:
            self.notice.config(text="Tempo esgotado. Comece um novo desafio.", fg="#ffb7b7")
            self.answer.config(state="disabled")
            self.timer = None
        else:
            self.timer = self.root.after(100, self.typing_tick)

    def find_addresses(self):
        try:
            values = sorted({row[4][0] for row in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET)
                             if not row[4][0].startswith("127.")})
            self.addresses.put("IP do PC: " + (", ".join(values) or "consulte a rede") + "\nPorta 8080")
        except OSError:
            self.addresses.put("Consulte o IPv4 do PC na rede.\nPorta 8080")

    def poll_sync(self):
        if self.closed or self.server is None:
            return
        try:
            data = self.server.events.get_nowait()
            self.streak_label.config(text=f"OFENSIVA · {data['streak']} / 21 dias\nRecorde: {data['best']} dias")
            self.streak_label.config(fg=PURPLE)
        except queue.Empty:
            pass
        try:
            self.network_label.config(text=self.addresses.get_nowait())
        except queue.Empty:
            pass
        self.sync_timer = self.root.after(250, self.poll_sync)

    def close(self):
        if self.closed:
            return
        self.closed = True
        if self.timer is not None:
            self.root.after_cancel(self.timer)
        if self.sync_timer is not None:
            self.root.after_cancel(self.sync_timer)
        if self.server is not None:
            threading.Thread(target=self.server.close, daemon=True).start()
        self.root.destroy()


if __name__ == "__main__":
    app_root = tk.Tk()
    DesktopApp(app_root, configure="--setup" in sys.argv)
    app_root.mainloop()
