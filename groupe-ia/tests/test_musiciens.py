import pytest

from groupe_ia.chef import Chef, Mesure, TypeMesure
from groupe_ia.motifs import STYLES_V1
from groupe_ia.musiciens import CANAUX, Groupe, decalage_tonalite
from groupe_ia.ordres import BATTERIE, INSTRUMENTS


def mesure(style="rumba", type_=TypeMesure.JEU, actifs=frozenset(INSTRUMENTS), position=1, degre=0, changement=False):
    s = STYLES_V1[style]
    return Mesure(5, type_, style, 110, actifs, position, s.longueur_phrase, degre, changement)


@pytest.mark.parametrize("style", sorted(STYLES_V1))
def test_au_moins_3_variations_par_instrument(style):
    for instrument in INSTRUMENTS:
        assert len(STYLES_V1[style].motifs[instrument]) >= 3


@pytest.mark.parametrize("style", sorted(STYLES_V1))
def test_motifs_dans_la_mesure(style):
    s = STYLES_V1[style]
    assert len(s.progression) == s.longueur_phrase
    for variations in list(s.motifs.values()) + [s.remplissages]:
        for motif in variations:
            for note in motif:
                assert 0 <= note.temps < 4 and note.duree > 0


def test_notes_valides_et_dans_la_mesure():
    groupe = Groupe(graine=3)
    for style in STYLES_V1:
        for position in range(STYLES_V1[style].longueur_phrase):
            for n in groupe.jouer(mesure(style, position=position)):
                assert 0 <= n.temps < 4
                assert 0 <= n.note <= 127 and 1 <= n.velocite <= 127


def test_seuls_les_instruments_actifs_jouent():
    groupe = Groupe(graine=1)
    notes = groupe.jouer(mesure(actifs=frozenset({BATTERIE})))
    assert {n.canal for n in notes} == {CANAUX[BATTERIE]}


def test_break_batterie_seule():
    notes = Groupe(graine=1).jouer(mesure(type_=TypeMesure.BREAK))
    assert notes and {n.canal for n in notes} == {CANAUX[BATTERIE]}


def test_changement_de_style_marque_le_premier_temps():
    notes = Groupe(graine=1).jouer(mesure("seben", position=0, changement=True))
    premier_temps = [n for n in notes if n.temps == 0]
    assert {n.canal for n in premier_temps} == set(CANAUX.values())  # tout le groupe attaque sur le 1


def test_variations_ne_tournent_pas_en_boucle():
    chef = Chef("seben", 120)
    chef.recevoir([])
    from groupe_ia.commandes import analyser
    chef.recevoir(analyser("Daniel on y va"))
    chef.mesure_suivante()
    groupe = Groupe(graine=7, humaniser=False)
    signatures = {tuple(groupe.jouer(chef.mesure_suivante())) for _ in range(32)}
    assert len(signatures) > 6


def test_tonalite():
    assert decalage_tonalite("E") == 0
    assert decalage_tonalite("A") == 5
    assert decalage_tonalite("D") == -2
    assert decalage_tonalite("sol") == 3
    with pytest.raises(ValueError):
        decalage_tonalite("H")
    basse_e = min(n.note for n in Groupe("E", 1).jouer(mesure()) if n.canal == 0)
    basse_a = min(n.note for n in Groupe("A", 1).jouer(mesure()) if n.canal == 0)
    assert basse_a - basse_e == 5
