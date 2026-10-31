# Limites techniques de StopAddict

Ce document est une exigence du projet (ne jamais masquer une limite). Il est tenu
à jour au fur et à mesure du développement et mesuré sur appareil réel, pas supposé.

## 1. Niveaux de protection

| Capacité | Mode standard | Mode renforcé (Device Owner) |
|---|---|---|
| Empêcher la désinstallation | Non — détection + alerte seulement | Oui (`setUninstallBlocked`) |
| VPN permanent non désactivable | Non — l'utilisateur peut le couper | Oui (`setAlwaysOnVpnPackage(lockdown=true)`) |
| Imposer le DNS / bloquer DNS privé | Non | Oui (restrictions + config gérée) |
| Restrictions système (mode sans échec, ADB…) | Non | Oui |
| Réinitialisation d'usine | Possible | Possible (laissée volontairement comme issue de secours) |

Le mode Device Owner n'est obtenable que sur un **téléphone remis à zéro** (QR code à
l'accueil, ou `adb dpm set-device-owner` avant tout compte Google). Il est **impossible**
de l'obtenir sur un téléphone déjà configuré. Le mode standard ne doit jamais être
présenté comme équivalent.

## 2. Filtrage DNS / VPN

- Bloque des **domaines entiers** (sites pour adultes connus, via listes actualisables).
- Ne voit **pas** le contenu à l'intérieur des applications qui servent tout depuis un même
  domaine (Instagram, X, Telegram, TikTok, Reddit…). Le filtrage DNS ne peut donc pas
  distinguer un contenu explicite d'un contenu ordinaire dans ces applications.
- Le DNS chiffré (DoH/DoT) peut contourner le filtre en mode standard ; on le détecte et on alerte.
- Le VPN ne déchiffre, n'intercepte ni ne journalise aucun trafic privé. Seules les
  requêtes DNS sont évaluées.

## 3. Détection par IA

- **Images** : classification locale (LiteRT). Précision à mesurer sur jeu de données réel ;
  faux positifs testés uniquement sur images **non explicites**.
- **Vidéo** : analyse d'images échantillonnées via MediaProjection. Android redemande le
  consentement **à chaque session**, affiche un indicateur permanent, et la capture s'arrête
  au redémarrage. Forte consommation de batterie. Pas d'analyse continue et invisible possible.
- **Texte** : via AccessibilityService, lecture **partielle** selon les applications. Google Play
  encadre strictement cet usage (déclaration + approbation requises, refus possible).

## 4. Confidentialité (non négociable)

StopAddict **n'enregistre aucun contenu privé** et **ne transmet rien** de tel à
l'administrateur : ni URL, ni image, ni message, ni capture. Il n'existe aucune table
ni aucun champ pour cela — c'est une absence par conception, pas seulement une règle d'accès.
L'administrateur ne voit que : statut de protection, alertes techniques, compteurs de
blocages **agrégés**, dernière synchronisation, temps restant. Les analyses IA se font en
mémoire sur l'appareil puis sont effacées.

## 5. Temps et engagement

- La durée fait foi **côté serveur**, en UTC.
- L'avancement local s'appuie sur un compteur monotone, pas sur l'horloge murale ; un
  changement d'heure est détecté et signalé, il ne fait pas avancer l'engagement.
- Après un redémarrage, le temps est marqué « à resynchroniser » tant que le serveur n'a pas
  répondu — aucun faux décompte n'est affiché.
- Aucune protection n'est promise contre une réinitialisation d'usine ; l'incident est
  signalé au serveur à la reconnexion.

## 6. Environnement de build actuel

- `core:model` (Kotlin pur) compile et ses tests passent dans cet environnement.
- Les modules Android ne sont pas encore validés par build complet ici (SDK à installer ;
  pas d'émulateur faute de KVM — les tests instrumentés tourneront en CI ou sur appareil réel).
- Maven Central renvoie des 429 ici ; le build utilise le miroir Google Cloud de Maven Central.
