"""Rendu hors temps réel : joue un scénario d'ordres et l'écrit en fichier MIDI.

Sert à écouter le groupe sans micro (fichier .mid lisible par n'importe quel
lecteur, ou .wav via FluidSynth), et à vérifier par des tests que chaque
changement tombe sur le premier temps.

Un scénario associe un numéro de mesure à une phrase prononcée pendant cette
mesure : l'ordre s'applique donc à la mesure suivante.
"""

from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

import mido

from .chef import Chef, Mesure
from .commandes import analyser
from .musiciens import CANAUX, PROGRAMMES, Groupe, NoteMidi

TICKS_PAR_TEMPS = 480
SOUNDFONTS_CONNUES = (
    "/usr/share/sounds/sf2/FluidR3_GM.sf2",
    "/usr/share/soundfonts/FluidR3_GM.sf2",
    "C:/soundfonts/FluidR3_GM.sf2",
)


@dataclass
class MesureJouee:
    mesure: Mesure
    debut_ticks: int
    notes: list[NoteMidi]


def jouer_scenario(
    scenario: dict[int, str],
    style: str = "rumba",
    tempo: int | None = None,
    tonalite: str = "E",
    graine: int = 1,
    preparation: tuple[str, ...] = (),
    max_mesures: int = 400,
    mot_appel: str = "daniel",
) -> tuple[list[MesureJouee], Chef]:
    """Joue un scénario. Les phrases de ``preparation`` sont dites avant le départ.

    Le départ (« on y va ») est ajouté s'il n'est pas dans la préparation.
    """
    chef = Chef(style, tempo)
    groupe = Groupe(tonalite, graine)
    for phrase in preparation:
        chef.recevoir(analyser(phrase, mot_appel))
    if chef.phase.name == "PREPARATION":
        chef.recevoir(analyser(f"{mot_appel} on y va", mot_appel))

    jouees: list[MesureJouee] = []
    ticks = 0
    numero_courant = -1  # pendant le décompte
    for _ in range(max_mesures):
        if numero_courant in scenario:
            chef.recevoir(analyser(scenario[numero_courant], mot_appel))
        mesure = chef.mesure_suivante()
        if mesure is None:
            break
        jouees.append(MesureJouee(mesure, ticks, groupe.jouer(mesure)))
        ticks += 4 * TICKS_PAR_TEMPS
        numero_courant = mesure.numero
    return jouees, chef


def ecrire_midi(jouees: list[MesureJouee], chemin: str | Path) -> Path:
    fichier = mido.MidiFile(ticks_per_beat=TICKS_PAR_TEMPS)
    piste = mido.MidiTrack()
    fichier.tracks.append(piste)
    evenements: list[tuple[int, int, mido.Message | mido.MetaMessage]] = []

    for instrument, programme in PROGRAMMES.items():
        evenements.append((0, 0, mido.Message("program_change", channel=CANAUX[instrument], program=programme)))

    tempo_courant = None
    for jm in jouees:
        if jm.mesure.tempo != tempo_courant:
            tempo_courant = jm.mesure.tempo
            evenements.append((jm.debut_ticks, 0, mido.MetaMessage("set_tempo", tempo=mido.bpm2tempo(tempo_courant))))
        for n in jm.notes:
            debut = jm.debut_ticks + round(n.temps * TICKS_PAR_TEMPS)
            fin = debut + max(1, round(n.duree * TICKS_PAR_TEMPS))
            evenements.append((debut, 2, mido.Message("note_on", channel=n.canal, note=n.note, velocity=n.velocite)))
            evenements.append((fin, 1, mido.Message("note_off", channel=n.canal, note=n.note, velocity=0)))

    evenements.sort(key=lambda e: (e[0], e[1]))
    precedent = 0
    for instant, _, message in evenements:
        piste.append(message.copy(time=instant - precedent))
        precedent = instant
    piste.append(mido.MetaMessage("end_of_track", time=4 * TICKS_PAR_TEMPS))

    chemin = Path(chemin)
    chemin.parent.mkdir(parents=True, exist_ok=True)
    fichier.save(chemin)
    return chemin


def trouver_soundfont(chemin: str | None = None) -> Path | None:
    for candidat in ((chemin,) if chemin else ()) + SOUNDFONTS_CONNUES:
        if candidat and Path(candidat).is_file():
            return Path(candidat)
    return None


def midi_vers_wav(midi: str | Path, wav: str | Path, soundfont: str | None = None) -> Path:
    """Convertit en .wav avec la commande ``fluidsynth`` (doit être installée)."""
    sf2 = trouver_soundfont(soundfont)
    if sf2 is None:
        raise FileNotFoundError("Aucune banque de sons SoundFont (.sf2) trouvée")
    if shutil.which("fluidsynth") is None:
        raise FileNotFoundError("La commande fluidsynth n'est pas installée")
    subprocess.run(
        ["fluidsynth", "-ni", "-g", "0.4", "-r", "44100", "-F", str(wav), str(sf2), str(midi)],
        check=True, capture_output=True,
    )
    return Path(wav)
