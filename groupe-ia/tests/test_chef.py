from groupe_ia.chef import Chef, Phase, TypeMesure
from groupe_ia.commandes import analyser
from groupe_ia.ordres import BATTERIE, GUITARE_RYTHMIQUE, GUITARE_SOLO, INSTRUMENTS, TEMPO_MAX


def dire(chef, texte):
    return chef.recevoir(analyser(texte))


def chef_en_jeu(style="rumba", tempo=110):
    chef = Chef(style, tempo)
    dire(chef, "Daniel on y va")
    assert chef.mesure_suivante().type is TypeMesure.DECOMPTE
    return chef


def avancer(chef, n):
    return [chef.mesure_suivante() for _ in range(n)]


def test_preparation_puis_decompte_puis_jeu():
    chef = Chef("rumba")
    assert chef.phase is Phase.PREPARATION
    assert chef.mesure_suivante() is None  # rien ne joue avant « on y va »
    dire(chef, "Daniel seben tempo 125")
    assert (chef.etat.style, chef.etat.tempo) == ("seben", 125)  # appliqué tout de suite
    dire(chef, "Daniel on y va")
    decompte = chef.mesure_suivante()
    assert decompte.type is TypeMesure.DECOMPTE and decompte.tempo == 125
    premiere = chef.mesure_suivante()
    assert premiere.numero == 0 and premiere.style == "seben" and premiere.type is TypeMesure.JEU


def test_changement_de_style_a_la_prochaine_mesure():
    chef = chef_en_jeu()
    avancer(chef, 3)  # mesures 0, 1, 2
    dire(chef, "Daniel va au seben")  # pendant la mesure 2
    m = chef.mesure_suivante()
    assert m.numero == 3 and m.style == "seben" and m.changement_style
    assert m.position_phrase == 0  # la phrase du seben commence ici


def test_changement_a_la_phrase_attend_le_debut_de_phrase():
    chef = chef_en_jeu("rumba")  # phrase de 8 mesures
    avancer(chef, 3)
    dire(chef, "Daniel seben à la prochaine phrase")
    styles = [m.style for m in avancer(chef, 6)]  # mesures 3 à 8
    assert styles == ["rumba"] * 5 + ["seben"]  # bascule pile en mesure 8


def test_phrase_du_seben_dure_4_mesures():
    chef = chef_en_jeu("seben")
    avancer(chef, 1)
    dire(chef, "Daniel rumba à la prochaine phrase")
    mesures = avancer(chef, 4)  # 1, 2, 3, 4
    assert [m.style for m in mesures] == ["seben", "seben", "seben", "rumba"]
    assert mesures[-1].numero == 4


def test_tempo():
    chef = chef_en_jeu(tempo=110)
    dire(chef, "Daniel plus vite")
    assert chef.mesure_suivante().tempo == 115
    dire(chef, "Daniel tempo 300")  # ignoré par l'analyse (hors limites)
    dire(chef, "Daniel tempo 180")
    dire(chef, "Daniel beaucoup plus vite")
    assert chef.mesure_suivante().tempo == TEMPO_MAX


def test_break_une_mesure_puis_reprise():
    chef = chef_en_jeu()
    avancer(chef, 2)
    dire(chef, "Daniel break")
    m1, m2 = avancer(chef, 2)
    assert m1.type is TypeMesure.BREAK
    assert m2.type is TypeMesure.JEU and m2.actifs == frozenset(INSTRUMENTS)


def test_puis_enchaine_sur_deux_mesures():
    chef = chef_en_jeu()
    dire(chef, "Daniel va au seben puis break")
    m1, m2, m3 = avancer(chef, 3)
    assert (m1.style, m1.type) == ("seben", TypeMesure.JEU)
    assert (m2.style, m2.type) == ("seben", TypeMesure.BREAK)
    assert m3.type is TypeMesure.JEU


def test_garder_seuls_et_tout_le_monde():
    chef = chef_en_jeu()
    dire(chef, "Daniel laisse la guitare seule")
    assert chef.mesure_suivante().actifs == {GUITARE_RYTHMIQUE, GUITARE_SOLO}
    dire(chef, "Daniel tout le monde revient")
    assert chef.mesure_suivante().actifs == frozenset(INSTRUMENTS)


def test_on_ne_coupe_jamais_le_dernier_instrument():
    chef = chef_en_jeu()
    dire(chef, "Daniel laisse la batterie seule")
    chef.mesure_suivante()
    dire(chef, "Daniel coupe la batterie")
    assert chef.mesure_suivante().actifs == {BATTERIE}


def test_fin_deux_mesures_puis_arret():
    chef = chef_en_jeu()
    avancer(chef, 2)
    dire(chef, "Daniel on arrête")
    dire(chef, "Daniel va au seben")  # pendant la même mesure : appliqué avec la fin
    fin = chef.mesure_suivante()
    final = chef.mesure_suivante()
    assert fin.type is TypeMesure.FIN and final.type is TypeMesure.FINAL
    assert chef.mesure_suivante() is None and chef.phase is Phase.ARRET
    assert dire(chef, "Daniel plus vite") == []  # plus d'ordre après l'arrêt


def test_annule_un_ordre_en_attente():
    chef = chef_en_jeu()
    dire(chef, "Daniel va au seben")
    assert chef.en_attente()
    assert dire(chef, "Daniel annule")
    assert chef.en_attente() == []
    assert chef.mesure_suivante().style == "rumba"


def test_annule_retire_toute_la_derniere_phrase_en_attente():
    chef = chef_en_jeu(tempo=110)
    dire(chef, "Daniel tempo 100")
    dire(chef, "Daniel seben et tempo 130")
    dire(chef, "Daniel annule")
    m = chef.mesure_suivante()
    assert (m.style, m.tempo) == ("rumba", 100)


def test_annule_un_ordre_deja_applique_une_seule_fois():
    chef = chef_en_jeu(tempo=110)
    dire(chef, "Daniel tempo 120")
    chef.mesure_suivante()
    dire(chef, "Daniel va au seben")
    chef.mesure_suivante()
    dire(chef, "Daniel annule")
    m = chef.mesure_suivante()
    assert (m.style, m.tempo) == ("rumba", 120)  # retour à l'état d'avant le seben
    assert dire(chef, "Daniel annule") == []  # un seul retour en arrière


def test_un_ordre_incompris_ne_change_rien():
    chef = chef_en_jeu()
    avant = chef.etat
    assert chef.recevoir([]) == []
    assert dire(chef, "Daniel bonjour") == []
    assert chef.mesure_suivante().style == avant.style


def test_arret_urgence_puis_reinitialisation():
    chef = chef_en_jeu("seben", 120)
    avancer(chef, 3)
    chef.arret_urgence()
    assert chef.mesure_suivante() is None
    chef.reinitialiser()
    assert chef.phase is Phase.PREPARATION and chef.etat.style == "seben"
