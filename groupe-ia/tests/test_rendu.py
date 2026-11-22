import mido

from groupe_ia.chef import TypeMesure
from groupe_ia.rendu import TICKS_PAR_TEMPS, ecrire_midi, jouer_scenario

SCENARIO = {
    3: "Daniel va au seben",
    9: "Daniel break",
    13: "Daniel rumba à la prochaine phrase",
    30: "Daniel on arrête",
}


def test_rumba_seben_rumba_sur_les_premiers_temps(tmp_path):
    jouees, chef = jouer_scenario(SCENARIO, graine=2)
    styles = {jm.mesure.numero: jm.mesure.style for jm in jouees if jm.mesure.numero >= 0}
    assert styles[3] == "rumba" and styles[4] == "seben"
    # Seben à partir de 4 ; phrase de 4 mesures → la rumba revient en 16.
    assert styles[15] == "seben" and styles[16] == "rumba"
    types = [jm.mesure.type for jm in jouees]
    assert types[0] is TypeMesure.DECOMPTE
    assert types[-2:] == [TypeMesure.FIN, TypeMesure.FINAL]

    # Chaque mesure commence exactement sur une barre de mesure.
    for jm in jouees:
        assert jm.debut_ticks % (4 * TICKS_PAR_TEMPS) == 0
    # Le changement de style attaque sur le premier temps (temps 0).
    changement = next(jm for jm in jouees if jm.mesure.changement_style)
    assert min(n.temps for n in changement.notes) == 0

    chemin = ecrire_midi(jouees, tmp_path / "test.mid")
    fichier = mido.MidiFile(chemin)
    assert fichier.length > 30
    notes_on = [m for m in fichier.tracks[0] if m.type == "note_on"]
    assert len(notes_on) > 500


def test_morceau_de_10_minutes_sans_trou():
    """Critère 1 (partie logique) : 10 min à 110 BPM ≈ 275 mesures, aucune vide."""
    scenario = {i: ("Daniel va au seben" if (i // 16) % 2 == 0 else "Daniel rumba à la prochaine phrase")
                for i in range(0, 280, 8)}
    scenario[279] = "Daniel fin"
    jouees, _ = jouer_scenario(scenario, tempo=110, graine=5, max_mesures=400)
    assert len(jouees) >= 275
    assert all(jm.notes for jm in jouees)
