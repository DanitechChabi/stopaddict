"""Ordres compris par le groupe.

Les ordres sont de simples données (dataclasses gelées) : ils traversent la
frontière entre le processus vocal et le processus musical par
``multiprocessing.Queue``, ils doivent donc rester sérialisables (pickle).
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

STYLES = ("rumba", "seben")

BATTERIE = "batterie"
BASSE = "basse"
GUITARE_RYTHMIQUE = "guitare_rythmique"
GUITARE_SOLO = "guitare_solo"
INSTRUMENTS = (BATTERIE, BASSE, GUITARE_RYTHMIQUE, GUITARE_SOLO)

TEMPO_MIN = 60
TEMPO_MAX = 180
PAS_TEMPO = 5


class Moment(Enum):
    """Quand un ordre s'applique."""

    MESURE = "mesure"  # premier temps de la prochaine mesure (défaut)
    PHRASE = "phrase"  # premier temps de la prochaine phrase


@dataclass(frozen=True)
class ChangerStyle:
    style: str | None  # None = « change de style » sans cible : l'autre style
    moment: Moment = Moment.MESURE


@dataclass(frozen=True)
class Tempo:
    valeur: int | None = None  # tempo absolu (« tempo 120 »)
    delta: int = 0  # variation relative (« plus vite » = +5)


@dataclass(frozen=True)
class Break:
    pass


@dataclass(frozen=True)
class GarderSeuls:
    """« Laisse la guitare seule » : seuls ces instruments continuent."""

    instruments: tuple[str, ...]


@dataclass(frozen=True)
class Couper:
    instrument: str


@dataclass(frozen=True)
class Revenir:
    instrument: str


@dataclass(frozen=True)
class ToutLeMonde:
    pass


@dataclass(frozen=True)
class Fin:
    pass


@dataclass(frozen=True)
class Annule:
    pass


@dataclass(frozen=True)
class Demarrer:
    """« On y va » : lance le décompte 1, 2, 3, 4 depuis la préparation."""


Ordre = (
    ChangerStyle | Tempo | Break | GarderSeuls | Couper | Revenir | ToutLeMonde
    | Fin | Annule | Demarrer
)


@dataclass(frozen=True)
class OrdreDate:
    """Un ordre et son décalage en mesures dans la phrase prononcée.

    « va au seben, puis break » donne seben (décalage 0) puis break
    (décalage 1) : le break tombe une mesure après le changement de style.
    """

    ordre: Ordre
    decalage: int = 0
