"""Lancement du groupe : processus musical (celui-ci) + processus vocal supervisé.

    python -m groupe_ia --voix clavier            # ordres tapés au clavier
    python -m groupe_ia --voix vosk --modele-vosk chemin/vers/vosk-model-small-fr

En mode clavier, le mot d'appel est facultatif ; une ligne vide = arrêt
d'urgence. Ctrl+C quitte.
"""

from __future__ import annotations

import argparse
import multiprocessing as mp
import queue
import sys
import threading
import time

from .chef import Chef, Mesure, Phase, TypeMesure
from .commandes import analyser
from .lecteur import Lecteur, SortieFluidSynth
from .musiciens import Groupe
from .rendu import trouver_soundfont
from .voix import MessageVoix, processus_voix

BLOC_BOIS_AIGU = 76  # son court de confirmation (General MIDI)


class Superviseur:
    """Garde le processus vocal en vie et transmet ses ordres au chef."""

    def __init__(self, chef: Chef, a_la_reception, **parametres_voix):
        self.chef = chef
        self.a_la_reception = a_la_reception
        self.parametres = parametres_voix
        self.file: mp.Queue = mp.Queue()
        self.processus: mp.Process | None = None
        self.redemarrages = 0
        self._stop = threading.Event()

    def _lancer(self) -> None:
        self.processus = mp.Process(target=processus_voix, args=(self.file,), kwargs=self.parametres, daemon=True)
        self.processus.start()

    def demarrer(self) -> None:
        self._lancer()
        threading.Thread(target=self._boucle, daemon=True).start()

    def arreter(self) -> None:
        self._stop.set()
        if self.processus is not None and self.processus.is_alive():
            self.processus.terminate()

    def _boucle(self) -> None:
        while not self._stop.is_set():
            try:
                message: MessageVoix = self.file.get(timeout=0.2)
                self.a_la_reception(message.texte, self.chef.recevoir(message.ordres))
            except queue.Empty:
                pass
            except Exception:
                pass  # un message illisible est ignoré
            if self.processus is not None and not self.processus.is_alive() and not self._stop.is_set():
                self.redemarrages += 1
                time.sleep(1.0)
                self._lancer()


def ligne_etat(chef: Chef, mesure: Mesure) -> str:
    if mesure.type is TypeMesure.DECOMPTE:
        return "  1 . 2 . 3 . 4"
    actifs = " ".join(i.replace("guitare_", "g.")[:9] for i in sorted(mesure.actifs))
    attente = ", ".join(f"{texte} dans {max(0, m - mesure.numero)} mes." for texte, m in chef.en_attente())
    ligne = f"[{mesure.numero:>4}] {mesure.style.upper():<6} {mesure.tempo} BPM  phrase {mesure.position_phrase + 1}/{mesure.longueur_phrase}  | {actifs}"
    if mesure.type is not TypeMesure.JEU:
        ligne += f"  <{mesure.type.value.upper()}>"
    if attente:
        ligne += f"  | en attente : {attente}"
    return ligne


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="groupe_ia", description="Groupe IA à commande vocale (V1)")
    p.add_argument("--voix", choices=("clavier", "vosk"), default="clavier")
    p.add_argument("--modele-vosk", help="dossier du modèle Vosk français")
    p.add_argument("--micro", default=None, help="numéro ou nom du micro (sounddevice)")
    p.add_argument("--soundfont", default=None, help="banque de sons .sf2")
    p.add_argument("--style", default="rumba", choices=("rumba", "seben"))
    p.add_argument("--tempo", type=int, default=None)
    p.add_argument("--tonalite", default="E")
    p.add_argument("--mot-appel", default="daniel")
    args = p.parse_args(argv)

    sf2 = trouver_soundfont(args.soundfont)
    if sf2 is None:
        print("Banque de sons introuvable : précisez --soundfont chemin/FluidR3_GM.sf2", file=sys.stderr)
        return 2

    chef = Chef(args.style, args.tempo)
    groupe = Groupe(args.tonalite)
    try:
        sortie = SortieFluidSynth(str(sf2))
    except RuntimeError as erreur:
        print(f"{erreur}. Vérifiez la sortie audio (casque, enceinte).", file=sys.stderr)
        return 3
    lecteur = Lecteur(chef, groupe, sortie, a_chaque_mesure=lambda m, _t: print(ligne_etat(chef, m), flush=True))

    def a_la_reception(texte: str, confirmations: list[str]) -> None:
        if confirmations:
            sortie.planifier(sortie.maintenant() + 0.02, 9, BLOC_BOIS_AIGU, 60, 0.1)
            print(f"  ✓ « {texte} » → {' · '.join(confirmations)}", flush=True)
        else:
            print(f"  · « {texte} » (ignoré)", flush=True)

    threading.Thread(target=lecteur.boucle, daemon=True).start()
    print(f"Prêt : {chef.etat.style} à {chef.etat.tempo} BPM. Dites « {args.mot_appel}, on y va ».")

    superviseur = None
    try:
        if args.voix == "vosk":
            if not args.modele_vosk:
                print("--modele-vosk est obligatoire avec --voix vosk", file=sys.stderr)
                return 2
            superviseur = Superviseur(chef, a_la_reception, mot_appel=args.mot_appel, source="vosk",
                                      chemin_modele=args.modele_vosk, peripherique=args.micro)
            superviseur.demarrer()
            while True:
                time.sleep(0.5)
        else:
            print("Tapez un ordre (ex. : « va au seben »), ligne vide = arrêt d'urgence, Ctrl+C pour quitter.")
            for ligne in sys.stdin:
                ligne = ligne.strip()
                if not ligne:
                    lecteur.arret_urgence()
                    print("  ■ Arrêt d'urgence", flush=True)
                    continue
                try:
                    ordres = analyser(ligne, args.mot_appel, exiger_mot_appel=False)
                except Exception:
                    ordres = []
                a_la_reception(ligne, chef.recevoir(ordres))
    except KeyboardInterrupt:
        pass
    finally:
        if superviseur is not None:
            superviseur.arreter()
        lecteur.arreter()
        time.sleep(0.2)
        sortie.fermer()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
