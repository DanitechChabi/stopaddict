"""Bibliothèque de motifs de la V1 : rumba et seben.

Ces motifs sont des **motifs de départ, écrits pour ce projet** : ils donnent
un groupe jouable tout de suite. Ils seront remplacés ou complétés par les
motifs joués par Daniel et les musiciens partenaires, puis par ceux extraits
des enregistrements (voir README).

Conventions :
- une mesure = 4 temps (4/4) ; positions et durées en temps (noires) ;
- instruments à hauteur : intervalles en demi-tons **par rapport à la
  fondamentale de l'accord en cours** (0 = fondamentale, 4 = tierce, 7 =
  quinte, 12 = octave...). Les accords I, IV et V étant majeurs, on n'utilise
  que 0, 2, 4, 7, 9 (+ octaves), qui restent dans la gamme pour les trois ;
- batterie : numéros de notes General MIDI.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from .ordres import BASSE, BATTERIE, GUITARE_RYTHMIQUE, GUITARE_SOLO

# Batterie General MIDI
GROSSE_CAISSE = 36
CERCLAGE = 37  # rimshot / cross-stick
CAISSE_CLAIRE = 38
CHARLEY_FERME = 42
CHARLEY_OUVERT = 46
TOM_GRAVE = 45
TOM_MEDIUM = 47
TOM_AIGU = 50
CYMBALE_CRASH = 49
CYMBALE_RIDE = 51
BAGUETTES = 31  # décompte


@dataclass(frozen=True)
class Note:
    temps: float  # position dans la mesure, en temps (0 à 4)
    duree: float  # en temps
    hauteurs: tuple[int, ...]  # intervalles (ou notes GM pour la batterie)
    velocite: int = 90


Motif = tuple[Note, ...]


def _n(temps: float, duree: float, hauteurs, velocite: int = 90) -> Note:
    if isinstance(hauteurs, int):
        hauteurs = (hauteurs,)
    return Note(temps, duree, tuple(hauteurs), velocite)


def _croches(hauteurs: list, velocites=(92, 70), duree: float = 0.5) -> Motif:
    """Suite régulière de croches (ou de doubles si duree=0.25)."""
    return tuple(
        _n(i * duree, duree * 0.9, h, velocites[i % len(velocites)])
        for i, h in enumerate(hauteurs)
    )


def _batterie(*couches: Motif) -> Motif:
    notes: list[Note] = []
    for couche in couches:
        notes.extend(couche)
    return tuple(sorted(notes, key=lambda n: n.temps))


def _frappes(note_gm: int, temps: list[float], velocite: int = 90, duree: float = 0.25) -> Motif:
    return tuple(_n(t, duree, note_gm, velocite) for t in temps)


def _doubles_charley(accent_contretemps: bool = True) -> Motif:
    notes = []
    for i in range(16):
        t = i * 0.25
        if i % 2 == 1 and accent_contretemps:
            vel = 88
        elif i % 4 == 0:
            vel = 72
        else:
            vel = 50
        notes.append(_n(t, 0.2, CHARLEY_FERME, vel))
    return tuple(notes)


@dataclass(frozen=True)
class Style:
    nom: str
    tempo_defaut: int
    longueur_phrase: int  # en mesures
    progression: tuple[int, ...]  # degré (demi-tons depuis la tonique) par mesure de phrase
    motifs: dict[str, tuple[Motif, ...]] = field(default_factory=dict)
    remplissages: tuple[Motif, ...] = ()  # batterie, dernière mesure de phrase


I, IV, V = 0, 5, 7

# ---------------------------------------------------------------- rumba ---

RUMBA = Style(
    nom="rumba",
    tempo_defaut=110,
    longueur_phrase=8,
    progression=(I, IV, I, V, I, IV, V, I),
    motifs={
        BATTERIE: (
            _batterie(
                _frappes(CHARLEY_FERME, [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5], 62),
                _frappes(GROSSE_CAISSE, [0, 2], 100),
                _frappes(CERCLAGE, [1, 3], 80),
            ),
            _batterie(
                _frappes(CHARLEY_FERME, [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5], 58),
                _frappes(GROSSE_CAISSE, [0, 1.5, 2], 96),
                _frappes(CERCLAGE, [1, 3], 82),
                _frappes(CERCLAGE, [3.5], 55),
            ),
            _batterie(
                _frappes(CYMBALE_RIDE, [0, 1, 1.5, 2, 3, 3.5], 64),
                _frappes(GROSSE_CAISSE, [0, 2, 2.5], 96),
                _frappes(CERCLAGE, [1, 2.5, 3], 76),
            ),
        ),
        BASSE: (
            (_n(0, 1.4, 0, 100), _n(1.5, 0.45, 7, 84), _n(2, 1.4, 0, 96), _n(3.5, 0.45, 7, 80)),
            (_n(0, 0.9, 0, 100), _n(1, 0.45, 4, 82), _n(1.5, 0.45, 7, 86), _n(2, 0.9, 12, 92),
             _n(3, 0.45, 7, 82), _n(3.5, 0.45, 4, 78)),
            (_n(0, 0.7, 0, 100), _n(0.75, 0.7, 0, 80), _n(1.5, 0.45, 7, 86), _n(2.5, 0.45, 4, 84),
             _n(3, 0.9, 7, 88)),
        ),
        GUITARE_RYTHMIQUE: (
            _croches([0, 7, 12, 16, 12, 7, 0, 7], (80, 62)),
            _croches([0, 12, 7, 16, 7, 12, 0, 12], (78, 60)),
            _croches([4, 7, 12, 7, 16, 12, 7, 4], (76, 64)),
        ),
        GUITARE_SOLO: (
            (_n(0, 0.9, 16, 86), _n(1, 0.45, 14, 76), _n(1.5, 0.45, 12, 78), _n(2, 1.8, 7, 82)),
            (_n(0.5, 0.45, 12, 76), _n(1, 0.45, 14, 78), _n(1.5, 0.9, 16, 86), _n(2.5, 0.45, 19, 82),
             _n(3, 0.9, 16, 80)),
            (_n(2, 0.45, (16, 12), 80), _n(2.5, 0.45, (14, 9), 74), _n(3, 0.9, (12, 7), 82)),
        ),
    },
    remplissages=(
        _batterie(
            _frappes(CHARLEY_FERME, [0, 0.5, 1, 1.5, 2], 60),
            _frappes(GROSSE_CAISSE, [0, 2], 100),
            _frappes(CERCLAGE, [1], 80),
            _frappes(CAISSE_CLAIRE, [3, 3.25, 3.5, 3.75], 84),
        ),
        _batterie(
            _frappes(CHARLEY_FERME, [0, 0.5, 1, 1.5, 2, 2.5], 60),
            _frappes(GROSSE_CAISSE, [0, 2], 100),
            _frappes(TOM_AIGU, [3], 88),
            _frappes(TOM_MEDIUM, [3.25, 3.5], 86),
            _frappes(TOM_GRAVE, [3.75], 92),
        ),
    ),
)

# ---------------------------------------------------------------- seben ---

SEBEN = Style(
    nom="seben",
    tempo_defaut=118,
    longueur_phrase=4,
    progression=(I, IV, V, IV),
    motifs={
        BATTERIE: (
            _batterie(
                _doubles_charley(),
                _frappes(GROSSE_CAISSE, [0, 1, 2, 3], 104),
                _frappes(CAISSE_CLAIRE, [1, 3], 92),
            ),
            _batterie(
                _doubles_charley(),
                _frappes(GROSSE_CAISSE, [0, 1, 2, 3], 104),
                _frappes(CAISSE_CLAIRE, [1, 3], 94),
                _frappes(CAISSE_CLAIRE, [0.75, 1.75, 2.75, 3.75], 44),
            ),
            _batterie(
                _frappes(CHARLEY_FERME, [0, 0.25, 1, 1.25, 2, 2.25, 3, 3.25], 60),
                _frappes(CHARLEY_OUVERT, [0.5, 1.5, 2.5, 3.5], 86, 0.4),
                _frappes(GROSSE_CAISSE, [0, 1, 2, 3], 104),
                _frappes(CAISSE_CLAIRE, [1, 3], 92),
            ),
        ),
        BASSE: (
            _croches([0, 0, 7, 7, 12, 12, 7, 4], (100, 82)),
            _croches([0, 12, 0, 7, 0, 12, 7, 4], (100, 80)),
            (_n(0, 0.45, 0, 100), _n(0.75, 0.2, 0, 80), _n(1, 0.45, 7, 92), _n(1.5, 0.45, 12, 86),
             _n(2, 0.45, 0, 98), _n(2.75, 0.2, 0, 80), _n(3, 0.45, 7, 90), _n(3.5, 0.45, 4, 84)),
        ),
        GUITARE_RYTHMIQUE: (
            _croches([0, 7, 12, 7] * 4, (82, 60, 70, 60), 0.25),
            _croches([12, 7, 16, 7] * 2 + [12, 7, 0, 7] * 2, (80, 58, 72, 58), 0.25),
            _croches([0, 12, 7, 12, 4, 12, 7, 12] * 2, (82, 62, 70, 62), 0.25),
        ),
        GUITARE_SOLO: (
            _croches([(16, 7), (12, 4), (16, 7), (12, 4), (19, 12), (16, 7), (12, 4), (16, 7)], (92, 76)),
            _croches([19, 16, 12, 16] * 4, (88, 66, 74, 66), 0.25),
            (_n(0, 0.7, (16, 7), 92), _n(0.75, 0.7, (19, 12), 88), _n(1.5, 0.45, (16, 7), 84),
             _n(2, 0.7, (12, 4), 90), _n(2.75, 0.7, (16, 7), 86), _n(3.5, 0.45, (19, 12), 84)),
        ),
    },
    remplissages=(
        _batterie(
            _doubles_charley(),
            _frappes(GROSSE_CAISSE, [0, 1, 2], 104),
            _frappes(CAISSE_CLAIRE, [1], 92),
            _frappes(CAISSE_CLAIRE, [3, 3.25, 3.5, 3.75], 90),
        ),
        _batterie(
            _frappes(CHARLEY_FERME, [0, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75], 64),
            _frappes(GROSSE_CAISSE, [0, 1, 2, 3], 104),
            _frappes(CAISSE_CLAIRE, [1], 92),
            _frappes(TOM_AIGU, [2, 2.25], 86),
            _frappes(TOM_MEDIUM, [2.5, 2.75], 88),
            _frappes(TOM_GRAVE, [3, 3.25, 3.5, 3.75], 92),
        ),
    ),
)

STYLES_V1: dict[str, Style] = {RUMBA.nom: RUMBA, SEBEN.nom: SEBEN}

# ------------------------------------------------- motifs hors style ---

DECOMPTE: Motif = tuple(_n(t, 0.2, BAGUETTES, 110 if t == 0 else 85) for t in (0, 1, 2, 3))

# Fin, mesure 1 : coups d'ensemble sur 1, 2 et 3, roulement sur le 4.
FIN_BATTERIE: Motif = _batterie(
    _frappes(GROSSE_CAISSE, [0, 1, 2], 110),
    _frappes(CYMBALE_CRASH, [0], 100, 1),
    _frappes(CAISSE_CLAIRE, [1, 2], 100),
    _frappes(CAISSE_CLAIRE, [3, 3.25, 3.5, 3.75], 96),
)
FIN_HAUTEUR: Motif = (_n(0, 0.9, 0, 100), _n(1, 0.9, 0, 100), _n(2, 0.9, 0, 100))
# Fin, mesure 2 : accord final tenu.
FINAL_BATTERIE: Motif = _batterie(
    _frappes(GROSSE_CAISSE, [0], 115),
    _frappes(CYMBALE_CRASH, [0], 115, 4),
)
FINAL_BASSE: Motif = (_n(0, 3.8, 0, 110),)
FINAL_RYTHMIQUE: Motif = (_n(0, 3.8, (0, 4, 7, 12), 100),)
FINAL_SOLO: Motif = (_n(0, 3.8, (12, 16, 19), 96),)
