# Groupe IA à commande vocale — V1

Un groupe virtuel (batterie, basse, guitare rythmique, guitare solo) qui joue
la **rumba** et le **seben** et obéit à la voix du musicien, en direct.
Projet de Daniel CHABI BOUKO (Archivexa), indépendant de StopAddict.

Le cahier des charges est la référence. Ce dossier contient la V1 : tout ce qui
se teste sans micro ni Windows est écrit et testé ; la partie micro
(Vosk + mot d'appel) est câblée mais reste à essayer sur le PC cible.

## Ce qui marche

| Brique | Fichier | État |
|---|---|---|
| Cerveau : texte → ordres (mot d'appel, « et », « puis », nombres en lettres) | `groupe_ia/commandes.py` | testé |
| Chef d'orchestre : mesures, phrases, ordres en attente, break, fin, annule, préparation | `groupe_ia/chef.py` | testé |
| Motifs rumba et seben (3 variations par instrument + remplissages) | `groupe_ia/motifs.py` | jouable, à remplacer par les vrais motifs |
| Musiciens virtuels : choix des variations, accords, tonalité, humanisation | `groupe_ia/musiciens.py` | testé |
| Lecteur temps réel : notes confiées au séquenceur de FluidSynth | `groupe_ia/lecteur.py` | testé (horloge simulée + vrai FluidSynth) |
| Chaîne vocale dans un processus séparé, relancée si elle plante | `groupe_ia/voix.py`, `groupe_ia/app.py` | testé avec une voix simulée ; micro à essayer |
| Rendu hors ligne en MIDI / WAV pour écouter | `groupe_ia/rendu.py`, `scripts/demo.py` | testé |
| Écran de jeu (fenêtre lisible à 2 m) | — | à faire ; en attendant, état affiché dans la console |

## Choix appliqués aux points restés ouverts

- « Plus vite » / « moins vite » : ±5 BPM (« beaucoup » : ±10), tempo entre 60 et 180.
- « La guitare » = les deux guitares ; « la solo », « la rythmique » pour une seule.
- La fin dure 2 mesures : coups d'ensemble, puis accord final tenu.
- « Annule » : retire la dernière phrase en attente ; sinon un seul retour à l'état précédent.
- « Puis » décale l'ordre suivant d'une mesure (« seben puis break ») ; « et » = même mesure.
- Préparation : avant « on y va », style, tempo et instruments s'appliquent tout de suite.
- On ne peut pas couper le dernier instrument qui joue (la musique ne s'arrête jamais).

Tout se change dans `ordres.py` (limites) et `commandes.py` (vocabulaire).

## Installation (PC Windows)

1. Python 3.11 ou plus récent.
2. FluidSynth pour Windows (publications GitHub de FluidSynth) : décompresser,
   puis ajouter son dossier `bin` au `PATH`.
3. Une banque de sons General MIDI, par exemple `FluidR3_GM.sf2` (gratuite).
4. Le modèle Vosk français léger `vosk-model-small-fr` (alphacephei.com/vosk/models),
   à décompresser dans `modeles/`.
5. Dans ce dossier :

```
pip install -r requirements-dev.txt
```

## Utilisation

Écouter la démo sans micro (écrit `sorties/demo.mid`, et `demo.wav` si la
commande `fluidsynth` est installée) :

```
python scripts/demo.py
```

Jouer en tapant les ordres au clavier (mot d'appel facultatif, ligne vide =
arrêt d'urgence) :

```
python -m groupe_ia --soundfont C:\soundfonts\FluidR3_GM.sf2 --style rumba --tempo 110
```

Jouer à la voix :

```
python -m groupe_ia --voix vosk --modele-vosk modeles\vosk-model-small-fr-0.22 --soundfont C:\soundfonts\FluidR3_GM.sf2
```

Puis : « Daniel, rumba, tempo 110 » → « Daniel, on y va » → « Daniel, va au seben »…

## Tests

```
python -m pytest -q
```

Critères de recette (section 12) couverts par les tests automatiques, côté
logique : changement de style sur le premier temps (3), ordre incompris sans
effet (5), chant sans mot d'appel ignoré (4, au niveau du texte), morceau long
sans mesure vide (1), processus vocal tué pendant le jeu (8). Les autres
(précision sur 50 ordres, écoute à l'aveugle, 10 minutes sur le PC cible) se
font sur le PC avec le micro-casque.

## Prochaines étapes

1. Essayer la chaîne micro sur l'EliteBook : Vosk limité aux commandes.
2. Écran de jeu et écran de réglages (section 9), arrêt d'urgence par la barre d'espace.
3. Entraîner le mot d'appel « Daniel » (notebook Colab d'openWakeWord).
4. Comparatif Vosk / faster-whisper sur les 50 ordres de test.
5. Remplacer les motifs de départ par ceux de Daniel et des musiciens partenaires,
   puis par ceux extraits des enregistrements (Demucs → Basic Pitch → découpage en mesures).
