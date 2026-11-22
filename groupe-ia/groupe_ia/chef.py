"""Le chef d'orchestre : il tient la mesure et décide quand chaque ordre s'applique.

Le chef ne connaît pas l'heure : le lecteur lui demande la mesure suivante
juste avant de la jouer (``mesure_suivante``). Tout ordre reçu avant cette
demande s'applique au premier temps de cette mesure. C'est ce qui masque le
délai de la reconnaissance vocale (section 6 du cahier des charges).

Le chef est protégé par un verrou : la file d'ordres est remplie par le fil
qui écoute la voix, et vidée par le fil du lecteur.
"""

from __future__ import annotations

import threading
from dataclasses import dataclass, field, replace
from enum import Enum

from .motifs import STYLES_V1, Style
from .ordres import (
    INSTRUMENTS,
    STYLES,
    TEMPO_MAX,
    TEMPO_MIN,
    Annule,
    Break,
    ChangerStyle,
    Couper,
    Demarrer,
    Fin,
    GarderSeuls,
    Moment,
    OrdreDate,
    Revenir,
    Tempo,
    ToutLeMonde,
)


class Phase(Enum):
    PREPARATION = "préparation"  # le groupe attend « on y va »
    DECOMPTE = "décompte"
    JEU = "jeu"
    FIN = "fin"  # les deux mesures de fin sont en cours
    ARRET = "arrêt"


class TypeMesure(Enum):
    DECOMPTE = "décompte"
    JEU = "jeu"
    BREAK = "break"
    FIN = "fin"  # mesure 1 de la fin
    FINAL = "final"  # mesure 2 : accord tenu


@dataclass(frozen=True)
class Etat:
    style: str
    tempo: int
    actifs: frozenset[str]


@dataclass(frozen=True)
class Mesure:
    """Tout ce que les musiciens doivent savoir pour jouer une mesure."""

    numero: int  # 0 = première mesure jouée après le décompte ; -1 = décompte
    type: TypeMesure
    style: str
    tempo: int
    actifs: frozenset[str]
    position_phrase: int
    longueur_phrase: int
    degre: int  # fondamentale de l'accord, en demi-tons depuis la tonique
    changement_style: bool = False
    appliques: tuple[str, ...] = ()  # ordres appliqués sur cette mesure


@dataclass
class _Attente:
    ordre: object
    groupe: int  # identifiant de la phrase prononcée (pour « annule »)
    mesure_min: int  # première mesure possible
    a_la_phrase: bool = False


@dataclass
class _Retour:
    """Ordre interne : revenir à un état (« annule » d'un ordre déjà appliqué)."""

    etat: Etat


def decrire(ordre) -> str:
    """Texte court affiché ou annoncé pour confirmer un ordre."""
    match ordre:
        case ChangerStyle(style=None, moment=m):
            return "Changement de style" + (" à la phrase" if m is Moment.PHRASE else "")
        case ChangerStyle(style=s, moment=m):
            return s.capitalize() + (" à la phrase" if m is Moment.PHRASE else "")
        case Tempo(valeur=v) if v is not None:
            return f"Tempo {v}"
        case Tempo(delta=d):
            return "Plus vite" if d > 0 else "Moins vite"
        case Break():
            return "Break"
        case GarderSeuls(instruments=i):
            return " + ".join(x.replace("_", " ") for x in i) + " seul(s)"
        case Couper(instrument=i):
            return f"Sans {i.replace('_', ' ')}"
        case Revenir(instrument=i):
            return f"Retour {i.replace('_', ' ')}"
        case ToutLeMonde():
            return "Tout le monde"
        case Fin():
            return "Fin"
        case Annule():
            return "Annule"
        case Demarrer():
            return "On y va"
        case _Retour():
            return "Retour à l'état précédent"
    return str(ordre)


class Chef:
    def __init__(self, style: str = "rumba", tempo: int | None = None, styles: dict[str, Style] | None = None):
        self._styles = styles or STYLES_V1
        if style not in self._styles:
            raise ValueError(f"Style inconnu : {style}")
        self._verrou = threading.RLock()
        self._etat = Etat(style, tempo or self._styles[style].tempo_defaut, frozenset(INSTRUMENTS))
        self._precedent: Etat | None = None
        self.phase = Phase.PREPARATION
        self._prochaine = 0  # numéro de la prochaine mesure à préparer
        self._debut_phrase = 0
        self._attente: list[_Attente] = []
        self._groupe = 0
        self._fin_mesures_restantes = 0
        self.dernier_ordre: str = ""
        self.journal: list[tuple[int, str]] = []  # (mesure, ordre appliqué)

    # ------------------------------------------------------------ lecture

    @property
    def etat(self) -> Etat:
        return self._etat

    @property
    def prochaine_mesure(self) -> int:
        return self._prochaine

    def en_attente(self) -> list[tuple[str, int]]:
        """Ordres en attente et numéro de la mesure prévue (affichage)."""
        with self._verrou:
            resultat = []
            for a in self._attente:
                mesure = a.mesure_min
                if a.a_la_phrase:
                    mesure = self._prochain_debut_phrase(a.mesure_min)
                resultat.append((decrire(a.ordre), mesure))
            return resultat

    def _style(self) -> Style:
        return self._styles[self._etat.style]

    def _prochain_debut_phrase(self, a_partir_de: int) -> int:
        longueur = self._style().longueur_phrase
        ecart = (a_partir_de - self._debut_phrase) % longueur
        return a_partir_de if ecart == 0 else a_partir_de + (longueur - ecart)

    # ------------------------------------------------------------ ordres

    def recevoir(self, ordres: list[OrdreDate]) -> list[str]:
        """Reçoit les ordres d'une phrase prononcée. Renvoie les confirmations.

        Ne lève jamais d'exception : un ordre impossible est ignoré.
        """
        if not ordres:
            return []
        with self._verrou:
            self._groupe += 1
            confirmations: list[str] = []
            for od in ordres:
                try:
                    texte = self._recevoir_un(od)
                except Exception:  # un ordre ne doit jamais casser le groupe
                    texte = None
                if texte:
                    confirmations.append(texte)
            if confirmations:
                self.dernier_ordre = " · ".join(confirmations)
            return confirmations

    def _recevoir_un(self, od: OrdreDate) -> str | None:
        ordre = od.ordre

        if isinstance(ordre, Annule):
            return self._annuler()

        if self.phase is Phase.PREPARATION:
            if isinstance(ordre, Demarrer):
                self.phase = Phase.DECOMPTE
                return decrire(ordre)
            if isinstance(ordre, Fin):
                return None
            # Avant de jouer, style, tempo et instruments s'appliquent tout de suite.
            nouvel = self._appliquer(ordre, self._etat)
            if nouvel is None:
                return None
            self._etat = nouvel
            if isinstance(ordre, ChangerStyle):
                self._debut_phrase = 0
            return decrire(ordre)

        if isinstance(ordre, Demarrer):
            return None  # déjà en train de jouer
        if self.phase in (Phase.FIN, Phase.ARRET):
            return None  # la fin est lancée : plus de changement

        a_la_phrase = isinstance(ordre, ChangerStyle) and ordre.moment is Moment.PHRASE
        self._attente.append(_Attente(ordre, self._groupe, self._prochaine + od.decalage, a_la_phrase))
        return decrire(ordre)

    def _annuler(self) -> str | None:
        if self._attente:
            dernier = self._attente[-1].groupe
            self._attente = [a for a in self._attente if a.groupe != dernier]
            return "Annulé (ordre en attente retiré)"
        if self.phase is Phase.JEU and self._precedent is not None:
            self._attente.append(_Attente(_Retour(self._precedent), self._groupe, self._prochaine))
            self._precedent = None  # un seul retour en arrière
            return "Annulé (retour à l'état précédent)"
        return None

    def _appliquer(self, ordre, etat: Etat) -> Etat | None:
        """Nouvel état après l'ordre, ou None si l'ordre ne change rien."""
        match ordre:
            case ChangerStyle(style=style):
                if style is None:
                    autres = [s for s in self._styles if s != etat.style]
                    style = autres[0] if autres else etat.style
                if style not in self._styles or style == etat.style:
                    return None
                return replace(etat, style=style)
            case Tempo(valeur=v, delta=d):
                tempo = v if v is not None else etat.tempo + d
                tempo = max(TEMPO_MIN, min(TEMPO_MAX, tempo))
                return None if tempo == etat.tempo else replace(etat, tempo=tempo)
            case GarderSeuls(instruments=i):
                return replace(etat, actifs=frozenset(i))
            case Couper(instrument=i):
                actifs = etat.actifs - {i}
                if not actifs:
                    return None  # on ne coupe jamais le dernier instrument
                return replace(etat, actifs=actifs)
            case Revenir(instrument=i):
                return replace(etat, actifs=etat.actifs | {i})
            case ToutLeMonde():
                return replace(etat, actifs=frozenset(INSTRUMENTS))
            case _Retour(etat=ancien):
                return ancien
        return None

    # ------------------------------------------------------------ mesures

    def arret_urgence(self) -> None:
        with self._verrou:
            self.phase = Phase.ARRET
            self._attente.clear()

    def reinitialiser(self) -> None:
        """Après l'arrêt : retour en préparation, même style et même tempo."""
        with self._verrou:
            self.phase = Phase.PREPARATION
            self._prochaine = 0
            self._debut_phrase = 0
            self._attente.clear()
            self._precedent = None
            self._etat = replace(self._etat, actifs=frozenset(INSTRUMENTS))

    def mesure_suivante(self) -> Mesure | None:
        """Prépare la prochaine mesure. None quand le morceau est terminé."""
        with self._verrou:
            if self.phase in (Phase.PREPARATION, Phase.ARRET):
                return None

            style = self._style()
            if self.phase is Phase.DECOMPTE:
                self.phase = Phase.JEU
                return Mesure(-1, TypeMesure.DECOMPTE, self._etat.style, self._etat.tempo,
                              self._etat.actifs, 0, style.longueur_phrase, 0)

            numero = self._prochaine
            if self.phase is Phase.FIN:
                self._fin_mesures_restantes -= 1
                if self._fin_mesures_restantes <= 0:
                    self.phase = Phase.ARRET
                return Mesure(numero, TypeMesure.FINAL, self._etat.style, self._etat.tempo,
                              self._etat.actifs, 0, style.longueur_phrase, 0)

            type_mesure = TypeMesure.JEU
            changement = False
            appliques: list[str] = []
            avant = self._etat
            position = (numero - self._debut_phrase) % style.longueur_phrase

            retour = False
            restants: list[_Attente] = []
            for a in self._attente:
                pret = numero >= a.mesure_min and (not a.a_la_phrase or position == 0)
                if not pret:
                    restants.append(a)
                    continue
                appliques.append(decrire(a.ordre))
                if isinstance(a.ordre, Break):
                    type_mesure = TypeMesure.BREAK
                elif isinstance(a.ordre, Fin):
                    type_mesure = TypeMesure.FIN
                else:
                    retour = retour or isinstance(a.ordre, _Retour)
                    nouvel = self._appliquer(a.ordre, self._etat)
                    if nouvel is not None:
                        if nouvel.style != self._etat.style:
                            changement = True
                            self._debut_phrase = numero
                        self._etat = nouvel
            self._attente = restants

            if retour:
                self._precedent = None  # un seul retour en arrière
            elif self._etat != avant:
                self._precedent = avant
            if type_mesure is TypeMesure.FIN:
                self.phase = Phase.FIN
                self._fin_mesures_restantes = 1
                self._attente.clear()

            style = self._style()
            position = (numero - self._debut_phrase) % style.longueur_phrase
            for texte in appliques:
                self.journal.append((numero, texte))
            self._prochaine = numero + 1
            return Mesure(
                numero, type_mesure, self._etat.style, self._etat.tempo, self._etat.actifs,
                position, style.longueur_phrase, style.progression[position % len(style.progression)],
                changement, tuple(appliques),
            )


__all__ = ["Chef", "Etat", "Mesure", "Phase", "TypeMesure", "decrire", "STYLES"]
