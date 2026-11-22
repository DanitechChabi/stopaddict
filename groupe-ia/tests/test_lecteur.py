import multiprocessing as mp
import threading
import time

from groupe_ia.chef import Chef
from groupe_ia.commandes import analyser
from groupe_ia.lecteur import Lecteur, SortieMemoire
from groupe_ia.musiciens import Groupe
from groupe_ia.voix import processus_voix


def test_ordre_pendant_la_mesure_applique_a_la_suivante():
    chef = Chef("rumba", 120)  # 1 mesure = 2 s
    chef.recevoir(analyser("Daniel on y va"))
    sortie = SortieMemoire()
    mesures = []

    def a_chaque_mesure(mesure, debut):
        mesures.append((mesure.numero, mesure.style, debut))
        if mesure.numero == 2:
            chef.recevoir(analyser("Daniel va au seben"))
        if mesure.numero == 5:
            chef.recevoir(analyser("Daniel fin"))

    Lecteur(chef, Groupe(graine=1), sortie, a_chaque_mesure=a_chaque_mesure).jouer_morceau(depart=0.0)
    debuts = {n: d for n, _, d in mesures}
    assert [s for n, s, _ in mesures if n >= 0][:5] == ["rumba", "rumba", "rumba", "seben", "seben"]
    assert debuts[3] == 2.0 * 4  # décompte + 3 mesures de 2 s : pile sur la barre
    # Toutes les notes du premier temps de la mesure 3 tombent exactement à 8,0 s.
    assert any(abs(t - 8.0) < 1e-9 for t, *_ in sortie.notes)


class GroupeFragile(Groupe):
    def jouer(self, mesure):
        if mesure.numero == 3:
            raise RuntimeError("motif corrompu")
        return super().jouer(mesure)


def test_une_erreur_de_preparation_ne_coupe_pas_la_musique():
    chef = Chef("seben", 120)
    chef.recevoir(analyser("Daniel on y va"))
    sortie = SortieMemoire()
    numeros = []

    def a_chaque_mesure(mesure, _debut):
        numeros.append(mesure.numero)
        if mesure.numero == 6:
            chef.recevoir(analyser("Daniel fin"))

    lecteur = Lecteur(chef, GroupeFragile(graine=1), sortie, a_chaque_mesure=a_chaque_mesure)
    lecteur.jouer_morceau(depart=0.0)
    assert lecteur.erreurs and "mesure 3" in lecteur.erreurs[0]
    # La mesure 3 (de 8 s à 10 s) a quand même des notes : la mesure 2 est rejouée.
    assert any(8.0 <= t < 10.0 for t, *_ in sortie.notes)
    assert numeros[-1] == 8


def test_processus_vocal_tue_la_musique_continue():
    """Critère 8 : on tue le processus vocal pendant le jeu."""
    contexte = mp.get_context("spawn")
    file = contexte.Queue()
    script = [(0.2, "Daniel on y va"), (0.5, "Daniel va au seben")] + [(0.3, "Daniel plus vite")] * 100
    voix = contexte.Process(target=processus_voix, args=(file,),
                            kwargs=dict(mot_appel="daniel", source="script", script=script), daemon=True)
    voix.start()

    chef = Chef("rumba", 180)  # mesures courtes (1,33 s) pour un test rapide
    sortie = SortieMemoire(temps_reel=True)
    lecteur = Lecteur(chef, Groupe(graine=1), sortie, avance=0.1)
    threading.Thread(target=lecteur.boucle, kwargs={"attente": 0.01}, daemon=True).start()

    def relayer():
        while True:
            try:
                message = file.get(timeout=0.1)
            except Exception:
                if not voix.is_alive():
                    return
                continue
            chef.recevoir(message.ordres)

    threading.Thread(target=relayer, daemon=True).start()

    limite = time.time() + 20
    while chef.etat.style != "seben" and time.time() < limite:
        time.sleep(0.05)
    assert chef.etat.style == "seben"

    voix.kill()
    voix.join(5)
    assert not voix.is_alive()
    notes_avant = len(sortie.notes)
    time.sleep(3.0)  # plus de deux mesures après la mort du processus vocal
    assert len(sortie.notes) > notes_avant + 20
    lecteur.arreter()
