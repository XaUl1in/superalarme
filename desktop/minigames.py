"""Mini-jogos offline; sem eval e com tempo monotônico para digitação."""
from dataclasses import dataclass
import random
from alarm import normalize


@dataclass(frozen=True)
class MiniGame:
    kind: str
    prompt: str
    answer: str
    time_limit: float | None = None

    def accepts(self, value, elapsed=None):
        if self.kind == "typing":
            return elapsed is not None and 0 <= elapsed < 15 and value == self.answer
        return normalize(value) == normalize(self.answer)


def make_game(rng=None, kind=None):
    rng = rng or random.SystemRandom()
    kind = kind or rng.choice(("anagram", "math", "typing"))
    if kind == "anagram":
        word, hint = rng.choice([
            ("extraordinario", "fora do comum"), ("responsabilidade", "dever de responder pelos próprios atos"),
            ("desenvolvimento", "processo de crescimento"), ("perseveranca", "persistência diante das dificuldades"),
            ("planejamento", "preparação organizada para um objetivo"),
        ])
        letters = list(word)
        rng.shuffle(letters)
        if "".join(letters) == word:
            letters = letters[1:] + letters[:1]
        return MiniGame(kind, f"Desembaralhe: {' '.join(letters).upper()}\nDica: {hint}.", word)
    if kind == "math":
        a, b, c, d = rng.randint(12, 40), rng.randint(3, 9), rng.randint(8, 15), rng.randint(2, 7)
        return MiniGame(kind, f"Respeite a precedência:\n{a} + {b} × ({c} − {d}) = ?", str(a + b * (c - d)))
    if kind == "typing":
        phrase = rng.choice(("Hoje eu escolho sair da cama.", "Um passo de cada vez, sem desistir.",
                             "Minha rotina começa com uma escolha."))
        return MiniGame(kind, f"Digite exatamente em menos de 15 segundos:\n{phrase}", phrase, 15)
    raise ValueError("Mini-jogo desconhecido")
