# Navette Phoenix V20.1 — Point de reprise du 23 novembre 2026

> Document de continuité. Ne pas modifier la logique de réservation, Firebase, les e-mails ou la configuration de publication sans tests. Aucun code applicatif n'a été modifié par cette prise de notes.

## État du code et de l'environnement

- Dépôt GitHub : `Navettephoenix/navette-phoenix`
- Branche de travail : `refonte-v20-verifiee`
- Dernier commit applicatif validé localement avant cette note : `961232ed8957d1ef5b919b203faf65bc668588ba`
- Projet Android natif Kotlin + Views sous `android/` ; activité centrale `MainActivity.kt`.
- Dossier de travail Windows : `C:\Users\PC\Documents\NavettePhoenix-V20\android`.
- Java fourni par Android Studio : `C:\Program Files\Android\Android Studio\jbr` (OpenJDK 21.0.8).
- Compilation et tests exécutés sur le PC le 23/11/2026 :
  `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL**, 36 tests automatisés au dernier cycle.
- Dernier déploiement vérifié : APK de **debug** sur l'émulateur Pixel 6 Pro ; **pas** d'AAB release validé ni de mise à jour Play Console réalisée.

## Fonctionnalités validées dans l'échange

1. Accueil : logo Phoenix, photo locale, réservation, devis, destinations, tarifs, contact, confidentialité.
2. Réservation : communes du Perche, adresses précises, recherche IGN optionnelle repliable, itinéraire Google Maps, dates, heures, aller-retour, passagers, bagages, tarif estimé, formulaire contact, récapitulatif et validation.
3. Ergonomie trajet : cartes séparées départ/destination, bouton « Continuer » fixe hors zone défilante, message de tarif informatif lorsque les communes manquent.
4. Firebase : demandes persistées dans Firestore, file `googleMailAccounts/{uid}/googleMailRequests`.
5. Google Apps Script : `Code.gs` du dépôt copié dans le projet existant, `Installation.gs` conservé, déclencheur existant non dupliqué. Diagnostic `diagnostiquerNavettePhoenix` exécuté avec succès ; un déclencheur actif au contrôle.
6. E-mails HTML : **réception réellement vérifiée sur Gmail** avec en-tête Phoenix LTS, référence, trajet, tarif et mention « réservation non confirmée ».
7. Historique « Mes demandes » : cartes compactes avec trajet, heure, tarif et statut ; détails repliables comprenant adresses, tarification, référence, bouton de copie. Capture de l'émulateur confirmant le toast « Référence copiée ».
8. Statuts : `PENDING` = demande enregistrée, **à transmettre** ; `SENT` = transmise, **en attente de confirmation** ; **seul** `CONFIRMED` est une réservation confirmée. Ne pas introduire de confirmation implicite.

## Dernière demande réelle de test

- Référence de test : `NP-ACE38355DBBE409A`.
- Trajet Champrond-en-Gâtine → Champrond-en-Gâtine, 29 novembre 2026 à 15 h, 2 passagers, tarif estimé 16 €.
- Une capture de l'historique montrait `PENDING` / « À transmettre » à 07:10 environ.
- L'utilisateur a ensuite **confirmé l'arrivée de l'e-mail**. Le passage à `SENT` dans « Mes demandes » **n'a pas encore été montré après Actualiser**. À contrôler à la reprise, sans refaire de demande tant que l'existante suffit.

## Prochaines opérations, dans cet ordre

1. Vérifier si la dernière demande montre le statut `SENT` après « Actualiser ». Si elle reste `PENDING` alors que l'e-mail est arrivé, investiguer avant tout changement (risque de renvoi/doublon).
2. Terminer les contrôles manuels essentiels (clavier vs bouton fixe, aller-retour, devis, reprise après redémarrage, erreur réseau). Éviter une refonte inutile qui pourrait provoquer des régressions.
3. Ouvrir **Google Play Console** pour relever le **versionCode le plus élevé** de l'application existante et l'empreinte de la clé d'upload reconnue. Ne pas inventer ces données.
4. Préparer une **mise à jour de l'application existante**, en conservant le package release `com.phoenix.navette`, avec versionCode strictement supérieur, bonne clé de signature, Firebase release et vérifications de sécurité. Le package debug `com.phoenix.navette28` ne remplace pas l'app publiée.
5. Exécuter les contrôles release du projet (voir `android/MISE-EN-SERVICE.md` et `verifyPlayBundle`), produire et tester l'AAB signé, puis envisager le test interne Play Console avant production. Ne jamais versionner un keystore, mots de passe ou jetons.

## Commandes de reprise

```powershell
cd "C:\Users\PC\Documents\NavettePhoenix-V20\android"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
git pull --ff-only origin refonte-v20-verifiee
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Consigne de reprise

Reprendre **sans réécrire ni repartir de zéro**. Lire d'abord ce point de reprise et `android/MISE-EN-SERVICE.md`. Préserver l'envoi Firebase → Apps Script → Gmail déjà testé et les 36 tests automatisés. Vérifier les données Play Console et la signature avant toute génération d'AAB de production.
