"""Démo sans micro : rumba → seben → break → rumba à la phrase → fin.

    python scripts/demo.py               # écrit sorties/demo.mid (+ demo.wav si fluidsynth est installé)
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from groupe_ia.rendu import ecrire_midi, jouer_scenario, midi_vers_wav  # noqa: E402

SCENARIO = {
    7: "Daniel, va au seben",
    15: "Daniel, plus vite",
    19: "Daniel break",
    23: "Daniel laisse la guitare seule",
    25: "Daniel, tout le monde revient",
    27: "Daniel, rumba à la prochaine phrase",
    37: "Daniel, seben puis break",
    45: "Daniel, on arrête",
}


def main() -> None:
    sorties = Path(__file__).resolve().parents[1] / "sorties"
    jouees, chef = jouer_scenario(SCENARIO, preparation=("Daniel, rumba, tempo 108",))
    midi = ecrire_midi(jouees, sorties / "demo.mid")
    print(f"{len(jouees)} mesures → {midi}")
    for mesure, ordre in chef.journal:
        print(f"  mesure {mesure:>3} : {ordre}")
    try:
        print("→", midi_vers_wav(midi, sorties / "demo.wav"))
    except FileNotFoundError as erreur:
        print(f"(pas de .wav : {erreur})")


if __name__ == "__main__":
    main()
