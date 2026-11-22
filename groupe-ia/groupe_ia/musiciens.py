"""Les musiciens virtuels : transforment une mesure en notes MIDI.

Chaque musicien choisit une variation de son motif (au moins 3 par style),
évite de rester trop longtemps sur la même, et joue avec de légères
variations de timing et de nuance pour ne pas sonner mécanique. Le premier
temps reste toujours exact (critère de recette 3).
"""

from __future__ import annotations

import random
from dataclasses import dataclass

from . import motifs as m
from .chef import Mesure, TypeMesure
from .motifs import STYLES_V1, Motif, Style
from .ordres import BASSE, BATTERIE, GUITARE_RYTHMIQUE, GUITARE_SOLO

CANAUX = {BASSE: 0, GUITARE_RYTHMIQUE: 1, GUITARE_SOLO: 2, BATTERIE: 9}
# Programmes General MIDI (numérotation à partir de 0).
PROGRAMMES = {BASSE: 33, GUITARE_RYTHMIQUE: 27, GUITARE_SOLO: 27}
# Fondamentale de chaque instrument quand la tonique est Mi (E).
REGISTRES = {BASSE: 28, GUITARE_RYTHMIQUE: 52, GUITARE_SOLO: 64}

TONALITES = {
    "C": 0, "DO": 0, "C#": 1, "DB": 1, "DO#": 1, "D": 2, "RE": 2, "D#": 3, "EB": 3, "MIB": 3,
    "E": 4, "MI": 4, "F": 5, "FA": 5, "F#": 6, "GB": 6, "FA#": 6, "G": 7, "SOL": 7,
    "G#": 8, "AB": 8, "LAB": 8, "A": 9, "LA": 9, "A#": 10, "BB": 10, "SIB": 10, "B": 11, "SI": 11,
}


@dataclass(frozen=True)
class NoteMidi:
    temps: float  # position dans la mesure, en temps
    duree: float  # en temps
    canal: int
    note: int
    velocite: int


def decalage_tonalite(tonalite: str) -> int:
    """Demi-tons à ajouter aux registres (définis en Mi), entre -6 et +5."""
    cle = tonalite.strip().upper().replace("♯", "#").replace("♭", "B")
    if cle not in TONALITES:
        raise ValueError(f"Tonalité inconnue : {tonalite}")
    ecart = (TONALITES[cle] - TONALITES["E"]) % 12
    return ecart - 12 if ecart > 5 else ecart


class Groupe:
    def __init__(self, tonalite: str = "E", graine: int | None = None,
                 styles: dict[str, Style] | None = None, humaniser: bool = True):
        self.styles = styles or STYLES_V1
        self.transposition = decalage_tonalite(tonalite)
        self.hasard = random.Random(graine)
        self.humaniser = humaniser
        self._derniere: dict[str, int] = {}
        self._repetitions: dict[str, int] = {}

    # ------------------------------------------------------------ choix

    def _choisir(self, instrument: str, variations: tuple[Motif, ...], position: int) -> Motif:
        """Variation pour cette mesure : stable sur 2 mesures, jamais plus de 4 fois de suite."""
        cle = instrument
        precedente = self._derniere.get(cle)
        repetitions = self._repetitions.get(cle, 0)
        if precedente is not None and position % 2 == 1 and repetitions < 4:
            choix = precedente
        else:
            possibles = list(range(len(variations)))
            if precedente is not None and repetitions >= 2 and len(possibles) > 1:
                possibles.remove(precedente)
            choix = self.hasard.choice(possibles)
        self._repetitions[cle] = repetitions + 1 if choix == precedente else 1
        self._derniere[cle] = choix
        return variations[choix]

    # ------------------------------------------------------------ rendu

    def _notes(self, instrument: str, motif: Motif, degre: int) -> list[NoteMidi]:
        canal = CANAUX[instrument]
        notes = []
        for n in motif:
            for h in n.hauteurs:
                if instrument == BATTERIE:
                    hauteur = h
                else:
                    hauteur = REGISTRES[instrument] + self.transposition + degre + h
                notes.append(NoteMidi(n.temps, n.duree, canal, max(0, min(127, hauteur)), n.velocite))
        return notes

    def _humaniser(self, notes: list[NoteMidi]) -> list[NoteMidi]:
        if not self.humaniser:
            return notes
        resultat = []
        for n in notes:
            temps = n.temps
            if temps > 0:  # le premier temps reste exact
                temps = max(0.0, temps + self.hasard.uniform(-0.015, 0.015))
            velocite = max(1, min(127, n.velocite + self.hasard.randint(-6, 6)))
            resultat.append(NoteMidi(temps, n.duree, n.canal, n.note, velocite))
        return resultat

    def jouer(self, mesure: Mesure) -> list[NoteMidi]:
        """Notes de la mesure, positions en temps depuis le premier temps."""
        if mesure.type is TypeMesure.DECOMPTE:
            return self._notes(BATTERIE, m.DECOMPTE, 0)

        style = self.styles[mesure.style]
        notes: list[NoteMidi] = []

        if mesure.type is TypeMesure.FIN:
            for instrument in mesure.actifs:
                motif = m.FIN_BATTERIE if instrument == BATTERIE else m.FIN_HAUTEUR
                notes += self._notes(instrument, motif, mesure.degre)
            return sorted(notes, key=lambda n: n.temps)

        if mesure.type is TypeMesure.FINAL:
            finals = {BATTERIE: m.FINAL_BATTERIE, BASSE: m.FINAL_BASSE,
                      GUITARE_RYTHMIQUE: m.FINAL_RYTHMIQUE, GUITARE_SOLO: m.FINAL_SOLO}
            for instrument in mesure.actifs:
                notes += self._notes(instrument, finals[instrument], 0)
            return sorted(notes, key=lambda n: n.temps)

        derniere_de_phrase = mesure.position_phrase == mesure.longueur_phrase - 1

        if mesure.type is TypeMesure.BREAK:
            # Seule la batterie joue, avec un remplissage qui relance le groupe.
            notes += self._notes(BATTERIE, self.hasard.choice(style.remplissages), 0)
            return sorted(notes, key=lambda n: n.temps)

        for instrument in mesure.actifs:
            if instrument == BATTERIE and derniere_de_phrase and style.remplissages and self.hasard.random() < 0.7:
                motif = self.hasard.choice(style.remplissages)
            else:
                motif = self._choisir(instrument, style.motifs[instrument], mesure.position_phrase)
            notes += self._notes(instrument, motif, mesure.degre)

        if BATTERIE in mesure.actifs and (mesure.changement_style or (mesure.position_phrase == 0 and mesure.numero > 0)):
            notes.append(NoteMidi(0, 1, CANAUX[BATTERIE], m.CYMBALE_CRASH, 100 if mesure.changement_style else 80))

        return sorted(self._humaniser(notes), key=lambda n: n.temps)
