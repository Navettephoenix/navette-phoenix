# Reprendre Navette Phoenix V20

Le projet Android se trouve dans `android/` sur la branche [refonte-v20-verifiee](https://github.com/Navettephoenix/navette-phoenix/tree/refonte-v20-verifiee/android). **L’identifiant de l’application Google Play est `com.phoenix.navette`.** La variante de test utilise `com.phoenix.navette28` pour pouvoir cohabiter avec la version du Play Store.

## Réservation et envoi par Google

- La V20 enregistre les demandes dans Firestore. Le relais Google Apps Script les transmet à la boîte de réservation configurée en privé. Le numéro de référence permet de suivre une demande après une coupure. Le service a été ajouté dans le [commit du relais](https://github.com/Navettephoenix/navette-phoenix/commit/6b1ebda364abe85b42808f04066c255bfe8723ab).
- Le projet Firebase est `navette-phoenix`. La base Firestore `(default)` existe. La console du propriétaire a confirmé le déploiement des règles et des index.
- Le projet Apps Script **Navette Phoenix V20 - Reservations** contient `Code.gs` et `Installation.gs`. La copie de déploiement sur le PC se trouve dans `%LOCALAPPDATA%\NavettePhoenixV20\relais-google`. Elle conserve le lien avec le projet Google existant.
- Lors de la dernière vérification sur le PC, le 22 novembre 2026, l’exécution de `installerNavettePhoenix` a démarré. Son résultat final, le déclencheur toutes les cinq minutes et la réception d’un premier e-mail restent à vérifier.

## Parcours client

L’accueil donne accès à la navette, au devis et au suivi. Le formulaire propose les 37 communes et les deux gares en local, accepte une adresse libre et signale les erreurs au niveau du champ. Le récapitulatif permet de modifier ou d’annuler une correction. L’accusé de réception affiche une référence et le suivi indique quand la demande a été transmise. Phoenix LTS confirme ensuite le trajet avec le client.

## Prochaine vérification sur le PC

1. Dans le projet Apps Script déjà ouvert, lire le **Journal d’exécution** de `installerNavettePhoenix`. Le message attendu est `Relais actif, verification des demandes toutes les cinq minutes.`
2. Dans le terminal PowerShell du dossier Android, exécuter `git pull --ff-only origin refonte-v20-verifiee`, puis lancer l’application avec **▶** dans Android Studio. Le fichier Firebase local du PC contient la configuration des deux variantes.
3. Envoyer une demande identifiée **TEST** depuis l’application, relever sa référence et vérifier la réception du même numéro dans la boîte de réservation. Le bouton **Actualiser** de **Mes demandes** permet de consulter l’état. La publication sur Google Play reste à faire avec la clé d’importation et le code de version vérifiés dans Play Console.

Les commandes de compilation, les tests et la configuration de diffusion sont dans le [guide Android](README.md) et le [guide de mise en service](MISE-EN-SERVICE.md).
