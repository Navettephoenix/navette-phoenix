# Navette Phoenix V20.1 — Point de reprise du 23 novembre 2026

> Document de continuité. Ne pas modifier la logique de réservation, Firebase, les e-mails ou la configuration de publication sans tests. Aucun code applicatif n'a été modifié par cette prise de notes.

## POINT DE REPRISE PRIORITAIRE — Pause après contrôle Google Apps Script (23/11/2026)

**Ce bloc est la référence actuelle et remplace les indications de statut plus anciennes dans le journal ci-dessous. Pause demandée par l'utilisateur, sans action de production supplémentaire.**

### Réalisé et confirmé par les sorties PowerShell / Apps Script

1. Dépôt `Navettephoenix/navette-phoenix`, branche `refonte-v20-verifiee`, dossier Windows `C:\Users\PC\Documents\NavettePhoenix-V20\android`. Dernier commit du code récupéré localement avant cette mise à jour documentaire : `b7c955b`. Ne pas créer de nouvelle branche ni repartir de zéro.
2. **Application Android : `BUILD SUCCESSFUL in 15s`** sur `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` après correction de l'import `assertNotNull` (`85052f5`). Java disponible via `C:\Program Files\Android\Android Studio\jbr`, OpenJDK 21.0.8. Il faut souvent remettre `JAVA_HOME` dans la nouvelle fenêtre PowerShell.
3. **Relais Google Apps Script : 10 tests de simulation réussis** avec `node .\google-apps-script\relay.test.js`. Scénarios : livraison Phoenix LTS unique, accusé client unique, Gmail indisponible, Firestore indisponible après e-mail, quota insuffisant, diagnostic non destructif. Ce sont des tests simulés, pas une preuve d'envoi client réel.
4. **Firebase : index Firestore déployés avec succès** via `firebase.cmd deploy --only firestore:indexes --project navette-phoenix` depuis `android\firebase`. La première tentative a échoué HTTP 400 car l'index `customerReceiptStatus` avait été déclaré comme index composite monochamp ; corrigé dans `firestore.indexes.json` en `fieldOverrides`, confirmé par **`Deploy complete!`**. Cette commande ne déploie pas le code Google Apps Script.
5. **Le code Google Apps Script actualisé est en place pour les tests de préactivation** : journal utilisateur à 07:49 indiquant `testerAccuseClientSansEnvoi` réussi, `enabled:false`, `noEmailSent:true`. `verifierActivationAccuseClient` : `technicalPreflightOk:true`, `customerReceiptIndexReady:true`, `activeRelayTriggers:1`, `remainingDailyMailQuota:89`, `estimatedRemainingReservationsWithReceipt:44`, `blockers:[]`.
6. **CRITIQUE : accusés e-mail clients NON ACTIVÉS** : `ENABLE_CUSTOMER_RECEIPTS_ = false`. Les réservations et e-mails professionnels existants doivent rester inchangés ; il manque encore les validations humaines. Le préflight technique vert ne signifie PAS conformité juridique ni livraison effective d'accusés clients.
7. Le problème à résoudre à la reprise : l'utilisateur **ne retrouve pas son site `www.navettephoenix.fr` dans Google Sites** afin de mettre à jour `https://www.navettephoenix.fr/politique`. **Ne pas présumer que le site est hébergé/édité via Google Sites.** Identifier d'abord le véritable service d'hébergement ou l'outil qui gère le domaine, l'éditeur ou le compte propriétaire. Ne pas créer un second site ni remplacer le domaine.
8. La politique de confidentialité publique a été signalée comme possiblement obsolète face à la V20.1 (réservations intégrées, coordonnées et itinéraires dans Firebase, envois transactionnels). Relire/actualiser la page dans le véritable outil du site, avec base légale, durées de conservation, droits, prestataires et envois d'e-mails. Faire valider les informations par Phoenix LTS avant publication.
9. Après correction et validation de la politique, recontrôler le quota / l'unique déclencheur / l'index / la boîte de réponse. **Seulement ensuite** envisager `ENABLE_CUSTOMER_RECEIPTS_ = true` et faire **une seule réservation contrôlée** vers un e-mail maîtrisé, en vérifiant séparément la boîte Phoenix LTS et la boîte cliente, `mailStatus=SENT` et `customerReceiptStatus=SENT`. Ne jamais forcer un réenvoi pour les états `SENDING` sans vérifier les e-mails reçus.
10. Projet de publication Play Console **pas encore validé** : pas d'AAB release signé confirmé. Conserver `com.phoenix.navette`, clé d'importation autorisée, versionCode supérieur au maximum Play Console.

### Commandes de reprise après la pause

```powershell
cd "C:\Users\PC\Documents\NavettePhoenix-V20\android"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
git pull --ff-only origin refonte-v20-verifiee
node .\google-apps-script\relay.test.js
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

**Point exact de reprise : retrouver l'éditeur/le propriétaire de `navettephoenix.fr` et mettre à jour la page de confidentialité.** L'utilisateur demandait une pause ; ne pas pousser de code, activer le relais ou déclencher d'e-mail sans nouvelle demande.

---

## Suivi 1 + accusé client 2 — travaux du 23 novembre 2026

- Le relais `android/google-apps-script/Code.gs` est fiabilisé : gardes `PENDING → SENDING → SENT`, dates de transition, priorité aux accusés client déjà en attente, répartition du quota et refus de renvoyer automatiquement un `SENDING`. Le comportement Google actif n'a **pas** été modifié : le Code.gs GitHub doit être copié manuellement après sauvegarde.
- La fonction `diagnostiquerNavettePhoenix` montre les volumes, l'âge des demandes bloquées et des références, sans coordonnées personnelles. `verifierActivationAccuseClient` contrôle index, quota, déclencheur et modèle sans envoyer d'e-mail.
- L'application affiche, dans « Mes demandes », un avertissement après **20 minutes** de `PENDING`, ou après **20 minutes** en `SENDING` lorsque l'horodatage de traitement est connu. Jamais d'auto-renvoi.
- Le modèle d'accusé client présente trajet, aller/retour, passagers et tarif indicatif et mentionne **EN ATTENTE DE CONFIRMATION**. La variable `ENABLE_CUSTOMER_RECEIPTS_ = false` reste volontairement désactivée dans GitHub et dans le flux non modifié.
- Le banc de tests `android/google-apps-script/relay.test.js` couvre **10 scénarios simulés**, vérifiés sans Gmail ni Firestore réels, notamment panne après envoi et quota faible.
- Les **deux nouveaux tests Android** n'ont pas encore été exécutés sur le PC depuis ces modifications ; demander `git pull`, `node .\\google-apps-script\\relay.test.js`, puis `.\\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.
- **Instructions complètes d'activation** et précautions dans `android/MISE-EN-SERVICE.md` : déployer l'index `customerReceiptStatus`, mettre à jour le Code.gs réel avec drapeau désactivé, tester, valider la confidentialité, puis seulement activer et faire une unique réservation test.

---

## Suite des améliorations lancée après cette sauvegarde

Les commits postérieurs à `961232e` ajoutent de façon **progressive** :

- Un trajet favori explicitement enregistré sur le téléphone (`FavoriteTrip.kt`), avec réutilisation et suppression.
- Une meilleure lisibilité des statuts et l'heure de la dernière actualisation réussie de l'historique.
- Trois nouveaux tests envisagés : deux tests de stockage des favoris et un test de l'interface. Ils **n'ont pas encore été exécutés sur le PC**.
- Un modèle d'accusé de réception au client dans `android/google-apps-script/Code.gs`, **désactivé par défaut** par `ENABLE_CUSTOMER_RECEIPTS_ = false`. Il n'a pas été transféré ni activé dans Apps Script actif. L'index `customerReceiptStatus` doit être déployé et le quota de courriels vérifié avant toute activation.
- `android/MISE-EN-SERVICE.md` précise désormais les étapes de recette et de mise en service.

À la reprise, exécuter `git pull` puis `:app:testDebugUnitTest :app:assembleDebug` et vérifier toutes les nouvelles fonctions sur l'émulateur. **Ne pas supposer que ces commits sont validés tant que le build n'est pas revenu vert**.

---

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
