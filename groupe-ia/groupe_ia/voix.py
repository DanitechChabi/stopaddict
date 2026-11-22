"""La chaîne vocale, dans son propre processus.

Micro → reconnaissance (Vosk limité au vocabulaire des commandes) → cerveau
(``commandes.analyser``) → messages vers le processus musical.

Si ce processus plante ou est tué, le processus musical continue de jouer et
le relance (critère de recette 8). Seuls des messages simples traversent la
frontière : le texte reconnu et la liste des ordres.

Mot d'appel : en V1, tant que le modèle openWakeWord « Daniel » n'est pas
entraîné, le mot d'appel est vérifié dans le texte reconnu (il fait partie
du vocabulaire de Vosk). Le modèle openWakeWord viendra filtrer en amont.
"""

from __future__ import annotations

import json
import queue
import time
from dataclasses import dataclass, field
from typing import Iterable, Iterator

from .commandes import analyser, vocabulaire
from .ordres import OrdreDate


@dataclass
class MessageVoix:
    texte: str
    ordres: list[OrdreDate] = field(default_factory=list)


def textes_vosk(chemin_modele: str, mot_appel: str, peripherique: int | str | None = None) -> Iterator[str]:
    """Phrases reconnues au micro par Vosk, limité au vocabulaire des commandes."""
    import sounddevice
    import vosk

    vosk.SetLogLevel(-1)
    modele = vosk.Model(chemin_modele)
    grammaire = json.dumps(vocabulaire(mot_appel) + ["[unk]"], ensure_ascii=False)
    reconnaisseur = vosk.KaldiRecognizer(modele, 16000, grammaire)
    blocs: queue.Queue[bytes] = queue.Queue()

    def rappel(donnees, _frames, _instant, _statut):
        blocs.put(bytes(donnees))

    with sounddevice.RawInputStream(samplerate=16000, blocksize=4000, device=peripherique,
                                    dtype="int16", channels=1, callback=rappel):
        while True:
            if reconnaisseur.AcceptWaveform(blocs.get()):
                texte = json.loads(reconnaisseur.Result()).get("text", "")
                texte = texte.replace("[unk]", " ").strip()
                if texte:
                    yield texte


def textes_simules(script: Iterable[tuple[float, str]]) -> Iterator[str]:
    """Pour les tests : (délai en secondes, phrase)."""
    for delai, texte in script:
        time.sleep(delai)
        yield texte


def processus_voix(sortie, mot_appel: str, source: str = "vosk", chemin_modele: str | None = None,
                   peripherique=None, script=None) -> None:
    """Point d'entrée du processus vocal. ``sortie`` : multiprocessing.Queue."""
    if source == "vosk":
        textes = textes_vosk(chemin_modele, mot_appel, peripherique)
    else:
        textes = textes_simules(script or [])
    for texte in textes:
        try:
            ordres = analyser(texte, mot_appel)
        except Exception:
            ordres = []  # un ordre incompris est ignoré
        sortie.put(MessageVoix(texte, ordres))
