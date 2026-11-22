import pytest

from groupe_ia.commandes import analyser, nombre_francais, normaliser
from groupe_ia.ordres import (
    BASSE, GUITARE_RYTHMIQUE, GUITARE_SOLO, PAS_TEMPO, Annule, Break, ChangerStyle, Couper, Demarrer,
    Fin, GarderSeuls, Moment, Revenir, Tempo, ToutLeMonde,
)


def ordres(texte, **kw):
    return [o.ordre for o in analyser(texte, **kw)]


@pytest.mark.parametrize("texte, attendu", [
    ("Daniel, va au seben", [ChangerStyle("seben")]),
    ("Daniel va au sébène", [ChangerStyle("seben")]),
    ("Daniel rumba à la prochaine phrase", [ChangerStyle("rumba", Moment.PHRASE)]),
    ("Daniel seben à la fin de la phrase", [ChangerStyle("seben", Moment.PHRASE)]),
    ("Daniel plus vite", [Tempo(delta=PAS_TEMPO)]),
    ("Daniel moins vite", [Tempo(delta=-PAS_TEMPO)]),
    ("Daniel tempo 120", [Tempo(valeur=120)]),
    ("Daniel tempo cent vingt", [Tempo(valeur=120)]),
    ("Daniel tempo quatre-vingt-dix", [Tempo(valeur=90)]),
    ("Daniel tempo soixante dix huit", [Tempo(valeur=78)]),
    ("Daniel break", [Break()]),
    ("Daniel laisse la guitare seule", [GarderSeuls((GUITARE_RYTHMIQUE, GUITARE_SOLO))]),
    ("Daniel laisse la solo seule", [GarderSeuls((GUITARE_SOLO,))]),
    ("Daniel laisse la basse seule", [GarderSeuls((BASSE,))]),
    ("Daniel tout le monde revient", [ToutLeMonde()]),
    ("Daniel coupe la basse", [Couper(BASSE)]),
    ("Daniel la basse revient", [Revenir(BASSE)]),
    ("Daniel fin", [Fin()]),
    ("Daniel, on arrête", [Fin()]),
    ("Daniel annule", [Annule()]),
    ("Daniel on y va", [Demarrer()]),
    ("Daniel change de style", [ChangerStyle(None)]),
])
def test_commandes_de_base(texte, attendu):
    assert ordres(texte) == attendu


def test_sans_mot_d_appel_aucun_ordre():
    assert analyser("va au seben") == []
    assert analyser("on chante la rumba, plus vite, break") == []


def test_mot_d_appel_configurable():
    assert ordres("Mbongo va au seben", mot_appel="Mbongo") == [ChangerStyle("seben")]
    assert analyser("Daniel va au seben", mot_appel="Mbongo") == []


def test_phrase_incomprise_ignoree():
    assert analyser("Daniel euh bonjour tout va bien") == []
    assert analyser("Daniel") == []


def test_un_seul_ordre_pour_change_de_style_et_va_au_seben():
    assert ordres("Daniel change de style à la prochaine mesure et va au seben") == [ChangerStyle("seben")]


def test_puis_decale_d_une_mesure():
    resultat = analyser("Daniel va au seben, puis break")
    assert [(o.ordre, o.decalage) for o in resultat] == [(ChangerStyle("seben"), 0), (Break(), 1)]


def test_et_meme_mesure():
    resultat = analyser("Daniel seben et tempo 125")
    assert {o.decalage for o in resultat} == {0}
    assert set(o.ordre for o in resultat) == {ChangerStyle("seben"), Tempo(valeur=125)}


def test_preparation_style_tempo_et_depart():
    assert ordres("Daniel, rumba, tempo 110, on y va") == [Tempo(valeur=110), ChangerStyle("rumba"), Demarrer()]


def test_tempo_hors_limites_ignore():
    assert ordres("Daniel tempo 400") == []


def test_normaliser_et_nombres():
    assert normaliser("Sébène, À LA prochaine-phrase !") == "sebene a la prochaine phrase"
    assert nombre_francais(["cent", "quatre", "vingt"]) == 180
    assert nombre_francais(["bonjour"]) is None
