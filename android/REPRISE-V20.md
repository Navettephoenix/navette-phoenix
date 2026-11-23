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


## Reprise des corrections du 23 novembre 2026

Le formulaire utilise désormais des champs de sélection en lecture seule, avec icônes calendrier/horloge et valeur choisie visible. Les tests simulés ont été adaptés. Ces modifications n'ont **pas encore été recompilées sur Android Studio** dans cette session.

### Diagnostic sécurisé des e-mails

Le dépôt contient désormais `diagnostiquerNavettePhoenix` dans `google-apps-script/Code.gs`. Ce diagnostic ne renvoie **aucune réservation** : il vérifie le nombre de déclencheurs actifs, le quota Gmail restant et le nombre de demandes dans chaque état (limité à 100 par état). Le script déployé sur le compte Google doit être synchronisé avec cette nouvelle version avant que la fonction ne soit disponible dans l'éditeur.

1. Dans Apps Script, ouvrir **Navette Phoenix V20 - Reservations**.
2. Conserver le fichier privé `Installation.gs` et ses fonctions d'installation, s'il est présent. Mettre à jour uniquement `Code.gs` depuis cette branche GitHub.
3. Exécuter **`diagnostiquerNavettePhoenix`**, autoriser les accès si nécessaire, puis lire le **Journal d'exécution**.
4. Si `activeRelayTriggers` vaut zéro, installer le déclencheur d'intervalle de cinq minutes pour `relayGoogleMailRequests`. Si `SENDING` n'est pas zéro, vérifier d'abord la boîte et les journaux avant de toucher aux demandes : l'envoi a pu avoir lieu sans mise à jour du statut.
5. Si `PENDING` est supérieur à zéro et le quota disponible, lancer `relayGoogleMailRequests` et contrôler une demande par sa référence. Ne pas recréer la même réservation pour tester le relais.

Le diagnostic ne garantit pas qu'un message a été distribué ou reçu, ni que la configuration du compte et les autorisations Firestore sont correctes. La mise en service n'est validée qu'après un test avec référence identique dans Firestore, le suivi Android et la boîte destinataire.

## Vérification de réception du 23 novembre 2026

Une capture de la boîte Gmail de réservation officielle a confirmé à 04 h 19 (heure de Paris) la réception d'un courriel « Navette Phoenix 28 · Demande de navette », comprenant la référence NP, les lieux, l'horaire, le nombre de passagers et les coordonnées de contact. La fonction d'installation Apps Script a également affiché « Relais actif, verification des demandes toutes les cinq minutes. » avec exécution terminée.

**Vérifié :** une transmission réelle du relais Apps Script jusqu'à la boîte de réception. **Encore à vérifier :** le statut `SENT` de la même référence dans « Mes demandes » sur Android, les tests de non-doublon en production, la compilation de la dernière interface, et le bundle signé/contrôlé par Play Console. Ne pas placer de données personnelles de réservation dans ce dépôt public.
