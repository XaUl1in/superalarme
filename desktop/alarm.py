"""Alarme Hardcore: puzzles e PIN offline. Somente biblioteca padrão."""
from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import random
import secrets
import struct
import time
import unicodedata
from dataclasses import dataclass

CONFIG = Path(__file__).with_name("config.json")
PERIOD = 60


def parse_secret(value: str) -> bytes:
    if not isinstance(value, str):
        raise ValueError("A chave deve ser um texto hexadecimal.")
    value = "".join(value.split())
    if len(value) != 64:
        raise ValueError("A chave deve conter exatamente 64 caracteres hexadecimais.")
    try:
        return bytes.fromhex(value)
    except ValueError as exc:
        raise ValueError("Use apenas os caracteres 0–9 e A–F na chave.") from exc


def totp(secret: bytes, timestamp: float, period: int = PERIOD, digits: int = 6) -> str:
    counter = int(timestamp) // period
    digest = hmac.new(secret, struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 15
    number = int.from_bytes(digest[offset:offset + 4], "big") & 0x7fffffff
    return str(number % (10 ** digits)).zfill(digits)


def normalize(value: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", value.strip().lower())
                   if unicodedata.category(c) != "Mn")


@dataclass(frozen=True)
class Puzzle:
    prompt: str
    answer: str

    def accepts(self, value: str) -> bool:
        return normalize(value) == normalize(self.answer)


def make_puzzle(rng=None) -> Puzzle:
    rng = rng or random.SystemRandom()
    kind = rng.randrange(3)
    if kind == 0:
        a, b, c = rng.randint(12, 35), rng.randint(3, 9), rng.randint(5, 40)
        return Puzzle(f"Calcule: ({a} × {b}) − {c} = ?", str(a * b - c))
    if kind == 1:
        word, hint = rng.choice([
            ("teclado", "usado para digitar"), ("janela", "abertura na parede"),
            ("garrafa", "recipiente com gargalo para líquidos"),
            ("planeta", "corpo celeste que orbita uma estrela"),
            ("mochila", "carregada nas costas"), ("cortina", "cobre uma janela"),
        ])
        letters = list(word)
        while "".join(letters) == word:
            rng.shuffle(letters)
        return Puzzle(f"Desembaralhe: {' '.join(letters).upper()}\nDica: {hint}.", word)
    start, step = rng.randint(2, 20), rng.randint(3, 12)
    numbers = [start + step * i for i in range(5)]
    return Puzzle(f"Some sempre o mesmo valor. Complete: {', '.join(map(str, numbers))}, ?",
                  str(start + 5 * step))


def solve(puzzle: Puzzle, read=input, write=print) -> None:
    write(puzzle.prompt)
    while not puzzle.accepts(read("Resposta: ")):
        write("Ainda não. Tente novamente.")


def setup(path: Path, read=input, write=print) -> None:
    if path.exists():
        if read("Já existe uma chave. Substituir exige reconfigurar o celular. Digite SUBSTITUIR: ") != "SUBSTITUIR":
            write("Configuração preservada.")
            return
    secret = secrets.token_hex(32).upper()
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps({"version": 1, "secret": secret}, indent=2) + "\n", encoding="utf-8")
    os.replace(temp, path)
    write("Cole ou digite esta chave no aplicativo Android e salve:")
    write(secret)
    write("Guarde a chave apenas nos seus dispositivos. Não publique config.json.")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--setup", action="store_true", help="Cria a chave de pareamento")
    args = parser.parse_args(argv)
    try:
        if args.setup:
            setup(CONFIG)
            return 0
        if not CONFIG.exists():
            print("Primeiro execute: python desktop/alarm.py --setup")
            return 1
        data = json.loads(CONFIG.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or data.get("version") != 1:
            raise ValueError("Versão de configuração não suportada.")
        secret = parse_secret(data["secret"])
        print("ALARME HARDCORE — resolva para liberar o PIN\n")
        solve(make_puzzle())
        now = time.time()
        remaining = PERIOD - int(now) % PERIOD
        print(f"\nCódigo de desativação: {totp(secret, now)}")
        print(f"Troca em {remaining}s; o celular aceita mais 60s de tolerância.")
        print("Se expirar, execute novamente. Mantenha a hora automática nos dois aparelhos.")
        return 0
    except (EOFError, KeyboardInterrupt):
        print("\nCancelado. Nenhum novo código foi liberado.")
        return 130
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print(f"Não foi possível continuar: {exc}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
