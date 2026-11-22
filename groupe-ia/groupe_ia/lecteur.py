"""Lecteur temps réel : la chaîne musicale.

Juste avant la fin de chaque mesure (``avance``, 0,25 s par défaut), le
lecteur demande la mesure suivante au chef, la fait jouer par les musiciens
et confie toutes ses notes, avec leur heure exacte, au séquenceur de
FluidSynth. Ce n'est donc pas Python qui joue les notes à l'heure : c'est le
séquenceur, piloté par l'horloge audio (section 7, « Horloge et séquenceur »).

Une petite avance garde le sens de « prochaine mesure » : un ordre compris
jusqu'à 0,25 s avant la barre de mesure s'applique dès la mesure suivante.

Fiabilité (section 5) : si la préparation d'une mesure échoue, le lecteur
rejoue la mesure précédente au lieu de s'arrêter.
"""

from __future__ import annotations

import threading
import time
from typing import Callable, Protocol

from .chef import Chef, Mesure, Phase
from .musiciens import CANAUX, PROGRAMMES, Groupe, NoteMidi


class Sortie(Protocol):
    def maintenant(self) -> float: ...  # secondes, horloge de la sortie
    def attendre_jusqu_a(self, instant: float) -> None: ...
    def planifier(self, instant: float, canal: int, note: int, velocite: int, duree: float) -> None: ...
    def silence(self) -> None: ...


class SortieMemoire:
    """Sortie de test : horloge simulée, notes enregistrées en mémoire."""

    def __init__(self, temps_reel: bool = False):
        self.temps_reel = temps_reel
        self._t = 0.0
        self._origine = time.perf_counter()
        self.notes: list[tuple[float, int, int, int, float]] = []
        self._verrou = threading.Lock()

    def maintenant(self) -> float:
        return time.perf_counter() - self._origine if self.temps_reel else self._t

    def attendre_jusqu_a(self, instant: float) -> None:
        if self.temps_reel:
            reste = instant - self.maintenant()
            if reste > 0:
                time.sleep(reste)
        else:
            self._t = max(self._t, instant)

    def planifier(self, instant, canal, note, velocite, duree) -> None:
        with self._verrou:
            self.notes.append((instant, canal, note, velocite, duree))

    def silence(self) -> None:
        pass


class SortieFluidSynth:
    """Son des instruments par FluidSynth + SoundFont (pyfluidsynth)."""

    def __init__(self, soundfont: str, pilote: str | None = None, gain: float = 0.6,
                 volumes: dict[str, int] | None = None):
        import sys

        import fluidsynth  # paquet pyfluidsynth

        self._fs = fluidsynth.Synth(gain=gain, samplerate=44100.0)
        if pilote is None:
            pilote = "wasapi" if sys.platform == "win32" else None
        self._fs.start(driver=pilote) if pilote else self._fs.start()
        sfid = self._fs.sfload(soundfont)
        if sfid == -1:
            raise FileNotFoundError(f"SoundFont illisible : {soundfont}")
        for instrument, programme in PROGRAMMES.items():
            self._fs.program_select(CANAUX[instrument], sfid, 0, programme)
        self._fs.program_select(9, sfid, 128, 0)  # batterie
        for instrument, volume in (volumes or {}).items():
            self._fs.cc(CANAUX[instrument], 7, max(0, min(127, volume)))
        # Séquenceur cadencé par l'horloge audio de FluidSynth, en millisecondes.
        self._seq = fluidsynth.Sequencer(time_scale=1000, use_system_timer=False)
        self._dest = self._seq.register_fluidsynth(self._fs)
        # Le séquenceur avance au rythme de la carte son : s'il n'avance pas,
        # le pilote audio n'a pas démarré et rien ne jouerait.
        debut = self._seq.get_tick()
        time.sleep(0.3)
        if self._seq.get_tick() == debut:
            self._fs.delete()
            raise RuntimeError(f"La sortie audio ne démarre pas (pilote : {pilote or 'par défaut'})")

    def maintenant(self) -> float:
        return self._seq.get_tick() / 1000.0

    def attendre_jusqu_a(self, instant: float) -> None:
        while True:
            reste = instant - self.maintenant()
            if reste <= 0:
                return
            time.sleep(min(reste, 0.01))

    def planifier(self, instant, canal, note, velocite, duree) -> None:
        self._seq.note(int(instant * 1000), canal, note, velocite, max(1, int(duree * 1000)),
                       dest=self._dest, absolute=True)

    def silence(self) -> None:
        """Arrêt d'urgence : supprime les notes déjà planifiées et coupe le son."""
        import fluidsynth

        self._seq.delete()
        self._seq = fluidsynth.Sequencer(time_scale=1000, use_system_timer=False)
        self._dest = self._seq.register_fluidsynth(self._fs)
        for canal in range(16):
            self._fs.cc(canal, 123, 0)  # toutes les notes coupées

    def fermer(self) -> None:
        self.silence()
        self._seq.delete()
        self._fs.delete()


class Lecteur:
    def __init__(self, chef: Chef, groupe: Groupe, sortie: Sortie, avance: float = 0.25,
                 a_chaque_mesure: Callable[[Mesure, float], None] | None = None):
        self.chef = chef
        self.groupe = groupe
        self.sortie = sortie
        self.avance = avance
        self.a_chaque_mesure = a_chaque_mesure
        self.erreurs: list[str] = []
        self._precedentes: list[NoteMidi] = []
        self._stop = threading.Event()
        self._verrou = threading.Lock()  # préparation d'une mesure / arrêt d'urgence

    def arreter(self) -> None:
        """Arrête la boucle (fermeture du logiciel)."""
        self._stop.set()
        self.arret_urgence()

    def arret_urgence(self) -> None:
        """Barre d'espace / bouton d'arrêt : silence immédiat."""
        with self._verrou:
            self.chef.arret_urgence()
            try:
                self.sortie.silence()
            except Exception as erreur:
                self.erreurs.append(f"silence: {erreur!r}")

    def _preparer(self, mesure: Mesure) -> list[NoteMidi]:
        try:
            notes = self.groupe.jouer(mesure)
            self._precedentes = notes
            return notes
        except Exception as erreur:  # la musique ne doit jamais s'arrêter
            self.erreurs.append(f"mesure {mesure.numero}: {erreur!r}")
            return self._precedentes

    def jouer_morceau(self, depart: float | None = None) -> None:
        """Joue depuis le décompte jusqu'à la fin (ou l'arrêt d'urgence)."""
        t = depart if depart is not None else self.sortie.maintenant() + 0.1
        while not self._stop.is_set():
            with self._verrou:
                mesure = self.chef.mesure_suivante()
                if mesure is None:
                    break
                duree_temps = 60.0 / mesure.tempo
                for n in self._preparer(mesure):
                    self.sortie.planifier(t + n.temps * duree_temps, n.canal, n.note, n.velocite,
                                          n.duree * duree_temps)
            if self.a_chaque_mesure is not None:
                try:
                    self.a_chaque_mesure(mesure, t)
                except Exception:
                    pass
            t += 4 * duree_temps
            self.sortie.attendre_jusqu_a(t - self.avance)

    def boucle(self, attente: float = 0.05) -> None:
        """Attend « on y va », joue le morceau, recommence. Jusqu'à ``arreter``."""
        while not self._stop.is_set():
            if self.chef.phase is Phase.DECOMPTE:
                self.jouer_morceau()
                if self.chef.phase is Phase.ARRET and not self._stop.is_set():
                    self.chef.reinitialiser()  # prêt pour le morceau suivant
            else:
                time.sleep(attente)
