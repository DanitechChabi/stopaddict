"""Le « cerveau » : transforme le texte reconnu en ordres.

Règles + mots-clés, local et instantané (section 7 du cahier des charges).
Un texte sans mot d'appel, ou incompris, donne une liste vide : le groupe
continue de jouer (règle générale 3).
"""

from __future__ import annotations

import re
import unicodedata

from .ordres import (
    BASSE,
    BATTERIE,
    GUITARE_RYTHMIQUE,
    GUITARE_SOLO,
    INSTRUMENTS,
    PAS_TEMPO,
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

MOT_APPEL_DEFAUT = "daniel"

# Variantes que la reconnaissance vocale peut produire pour chaque style.
_STYLES = {
    "seben": ("seben", "sebene", "sebenes", "sebben", "seb", "sebenne"),
    "rumba": ("rumba", "rumbas", "roumba"),
}

# Désignations des instruments. « la guitare » = les deux guitares.
_INSTRUMENTS = [
    (("guitare solo", "solo", "soliste", "la solo"), (GUITARE_SOLO,)),
    (("guitare rythmique", "rythmique", "accompagnement", "la rythmique"), (GUITARE_RYTHMIQUE,)),
    (("guitares", "guitare"), (GUITARE_RYTHMIQUE, GUITARE_SOLO)),
    (("basse", "bassiste"), (BASSE,)),
    (("batterie", "batteur", "drums"), (BATTERIE,)),
]

_UNITES = {
    "zero": 0, "un": 1, "une": 1, "deux": 2, "trois": 3, "quatre": 4, "cinq": 5,
    "six": 6, "sept": 7, "huit": 8, "neuf": 9, "dix": 10, "onze": 11,
    "douze": 12, "treize": 13, "quatorze": 14, "quinze": 15, "seize": 16,
}
_DIZAINES = {
    "vingt": 20, "vingts": 20, "trente": 30, "quarante": 40, "cinquante": 50,
    "soixante": 60,
}


def normaliser(texte: str) -> str:
    """Minuscules, sans accents ni ponctuation, espaces simples."""
    texte = unicodedata.normalize("NFD", texte.lower())
    texte = "".join(c for c in texte if unicodedata.category(c) != "Mn")
    texte = texte.replace("-", " ").replace("'", " ").replace("’", " ")
    texte = re.sub(r"[^a-z0-9 ]", " ", texte)
    return re.sub(r"\s+", " ", texte).strip()


def _lire_nombre(mots: list[str]) -> int | None:
    """Premier nombre trouvé dans les mots (chiffres ou lettres)."""
    for mot in mots:
        if mot.isdigit():
            return int(mot)
    # Plus longue suite de mots-nombres.
    mots_nombre = set(_UNITES) | set(_DIZAINES) | {"cent", "cents", "et"}
    i = 0
    while i < len(mots):
        if mots[i] in mots_nombre and mots[i] != "et":
            j = i
            while j < len(mots) and mots[j] in mots_nombre:
                j += 1
            suite = mots[i:j]
            valeur = nombre_francais(suite)
            if valeur is not None:
                return valeur
            i = j
        else:
            i += 1
    return None


def nombre_francais(mots: list[str]) -> int | None:
    """Lit un nombre écrit en lettres (« cent vingt », « quatre vingt dix »).

    Renvoie None si les mots ne forment pas un nombre.
    """
    mots = [m for m in mots if m != "et"]
    if not mots:
        return None
    total = 0
    courant = 0
    k = 0
    while k < len(mots):
        mot = mots[k]
        if mot in ("cent", "cents"):
            total += (courant or 1) * 100
            courant = 0
        elif mot in ("vingt", "vingts") and courant == 4:
            courant = 80  # « quatre vingt »
        elif mot in _DIZAINES:
            courant += _DIZAINES[mot]
        elif mot in _UNITES:
            courant += _UNITES[mot]
        else:
            return None
        k += 1
    return total + courant


def _contient(texte: str, expression: str) -> bool:
    return re.search(rf"(^| ){re.escape(expression)}( |$)", texte) is not None


def _trouver_style(texte: str) -> str | None:
    for style, variantes in _STYLES.items():
        if any(_contient(texte, v) for v in variantes):
            return style
    return None


def _trouver_instruments(texte: str) -> tuple[str, ...] | None:
    for expressions, instruments in _INSTRUMENTS:
        if any(_contient(texte, e) for e in expressions):
            return instruments
    return None


def _analyser_segment(texte: str) -> list:
    """Ordres contenus dans un segment sans « puis » ni « et »."""
    ordres: list = []
    mots = texte.split()

    if _contient(texte, "annule") or _contient(texte, "annuler") or _contient(texte, "annulez"):
        return [Annule()]

    if any(_contient(texte, e) for e in ("tout le monde", "tous ensemble", "tout le groupe")):
        return [ToutLeMonde()]

    if _contient(texte, "break") or _contient(texte, "brake") or _contient(texte, "breack"):
        ordres.append(Break())

    # La phrase musicale : « à la prochaine phrase », « fin de la phrase ».
    a_la_phrase = bool(re.search(r"(prochaine|fin de la|a la|nouvelle) phrase", texte))
    texte_sans_phrase = re.sub(r"(a la |la )?(prochaine|fin de la|nouvelle) phrase", " ", texte)

    if (
        _contient(texte_sans_phrase, "fin")
        or _contient(texte_sans_phrase, "on arrete")
        or _contient(texte_sans_phrase, "arrete")
        or _contient(texte_sans_phrase, "termine")
        or _contient(texte_sans_phrase, "on termine")
        or _contient(texte_sans_phrase, "stop")
    ):
        ordres.append(Fin())
        return ordres

    # Tempo.
    if "tempo" in mots or "bpm" in mots:
        valeur = _lire_nombre(mots)
        if valeur is not None and TEMPO_MIN <= valeur <= TEMPO_MAX:
            ordres.append(Tempo(valeur=valeur))
    elif _contient(texte, "beaucoup plus vite"):
        ordres.append(Tempo(delta=2 * PAS_TEMPO))
    elif _contient(texte, "beaucoup moins vite") or _contient(texte, "beaucoup plus lent"):
        ordres.append(Tempo(delta=-2 * PAS_TEMPO))
    elif any(_contient(texte, e) for e in ("plus vite", "accelere", "accelerez", "plus rapide")):
        ordres.append(Tempo(delta=PAS_TEMPO))
    elif any(_contient(texte, e) for e in ("moins vite", "ralentis", "ralenti", "ralentissez", "plus lent", "doucement")):
        ordres.append(Tempo(delta=-PAS_TEMPO))

    # Instruments : garder seuls, couper, faire revenir.
    instruments = _trouver_instruments(texte_sans_phrase)
    if instruments is not None:
        if _contient(texte, "seul") or _contient(texte, "seule") or _contient(texte, "seuls") or _contient(texte, "seules"):
            ordres.append(GarderSeuls(instruments))
        elif any(_contient(texte, e) for e in ("coupe", "coupez", "sans", "enleve", "retire", "silence")):
            ordres.extend(Couper(i) for i in instruments)
        elif any(_contient(texte, e) for e in ("revient", "reviens", "reprend", "reprends", "remets", "ajoute", "avec")):
            ordres.extend(Revenir(i) for i in instruments)

    # Style.
    moment = Moment.PHRASE if a_la_phrase else Moment.MESURE
    style = _trouver_style(texte)
    if style is not None:
        ordres.append(ChangerStyle(style, moment))
    elif _contient(texte, "change de style") or _contient(texte, "changement de style") or _contient(texte, "autre style"):
        ordres.append(ChangerStyle(None, moment))

    if any(_contient(texte, e) for e in ("on y va", "on commence", "c est parti", "go", "partez", "demarre", "demarrez")):
        ordres.append(Demarrer())

    return ordres


def _fusionner_styles(ordres: list) -> list:
    """« change de style ... et va au seben » ne forme qu'un seul ordre.

    Un changement de style sans cible suivi (ou précédé) d'un changement avec
    cible dans le même groupe est fusionné ; le moment le plus tardif gagne
    (« à la prochaine phrase » l'emporte sur la mesure).
    """
    styles = [o for o in ordres if isinstance(o, ChangerStyle)]
    if len(styles) < 2:
        return ordres
    cible = next((o.style for o in reversed(styles) if o.style is not None), None)
    moment = Moment.PHRASE if any(o.moment is Moment.PHRASE for o in styles) else Moment.MESURE
    fusion = ChangerStyle(cible, moment)
    resultat = []
    place = False
    for o in ordres:
        if isinstance(o, ChangerStyle):
            if not place:
                resultat.append(fusion)
                place = True
        else:
            resultat.append(o)
    return resultat


def analyser(texte: str, mot_appel: str = MOT_APPEL_DEFAUT, exiger_mot_appel: bool = True) -> list[OrdreDate]:
    """Analyse une phrase reconnue et renvoie les ordres datés.

    - Sans mot d'appel (quand il est exigé) : aucun ordre.
    - « puis » décale les ordres suivants d'une mesure.
    - « et » ou une virgule regroupe des ordres appliqués à la même mesure.
    """
    texte = normaliser(texte)
    mot_appel = normaliser(mot_appel)
    if exiger_mot_appel:
        position = re.search(rf"(^| ){re.escape(mot_appel)}( |$)", texte)
        if position is None:
            return []
        texte = texte[position.end():].strip()
    if not texte:
        return []

    resultat: list[OrdreDate] = []
    for decalage, bloc in enumerate(re.split(r"\b(?:puis|ensuite|apres)\b", texte)):
        groupe: list = []
        for segment in re.split(r"\bet\b", bloc):
            segment = segment.strip()
            if segment:
                groupe.extend(_analyser_segment(segment))
        groupe = _fusionner_styles(groupe)
        resultat.extend(OrdreDate(o, decalage) for o in groupe)

    # Les décalages ne comptent que les blocs qui contiennent des ordres.
    blocs = sorted({o.decalage for o in resultat})
    renumerotation = {ancien: nouveau for nouveau, ancien in enumerate(blocs)}
    return [OrdreDate(o.ordre, renumerotation[o.decalage]) for o in resultat]


def vocabulaire(mot_appel: str = MOT_APPEL_DEFAUT) -> list[str]:
    """Mots attendus, pour limiter la reconnaissance vocale (grammaire Vosk)."""
    mots = {
        mot_appel, "va", "au", "a", "la", "le", "les", "prochaine", "phrase",
        "mesure", "change", "de", "style", "et", "puis", "ensuite", "tempo",
        "plus", "moins", "vite", "lent", "beaucoup", "break", "laisse", "seul",
        "seule", "seuls", "tout", "monde", "revient", "fin", "on", "arrete",
        "annule", "y", "commence", "go", "coupe", "sans", "reprends", "avec",
        "guitare", "guitares", "solo", "rythmique", "basse", "batterie",
        "cent", "et",
    }
    for variantes in _STYLES.values():
        mots.update(variantes)
    mots.update(_UNITES)
    mots.update(_DIZAINES)
    return sorted(mots)


__all__ = ["analyser", "normaliser", "nombre_francais", "vocabulaire", "INSTRUMENTS"]
