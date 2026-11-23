# V20.1 — fiabilisation des transmissions et accusés client (23 novembre 2026)

**Statut : code ajouté au dépôt, tests de simulation du relais réussis, mais nouvel APK et vrai Apps Script non encore validés sur le PC / Google.**

## 1. Surveillance des demandes et prévention des doublons

- L'historique peut avertir l'utilisateur si une demande `PENDING` attend plus de **20 minutes**. Il ne crée ni renvoi ni nouvelle demande.
- Si une demande `SENDING` reste bloquée plus de **20 minutes depuis la prise en charge** (nouveau champ `mailStatusUpdatedAt`), l'application conseille de vérifier avec Phoenix LTS. Les anciens dossiers sans cette date ne déclenchent pas de fausse alerte.
- Le relais ajoute `mailStatusUpdatedAt` à ses changements d'état et `customerReceiptUpdatedAt` aux accusés client ; ces champs sont modifiés côté serveur, pas depuis Android.
- Les deux circuits ont un protocole `PENDING → SENDING → SENT`. `SENDING` signifie **livraison incertaine** : ne jamais remettre automatiquement à `PENDING`, même si un script a échoué juste après l'envoi Gmail.
- Le diagnostic `diagnostiquerNavettePhoenix` calcule les volumes des 100 premiers dossiers par statut, les files qui dépassent 20 minutes et quelques références à vérifier, **sans noms, adresses mail ou téléphones**. Ce n'est pas une alerte push automatique : il faut exécuter le diagnostic pour lire son résultat.
- La récupération d'une demande `SENDING` exige une vérification des e-mails effectivement reçus et de Firestore par la personne responsable.

## 2. Accusé de réception au client — déploiement contrôlé

Le script `android/google-apps-script/Code.gs` contient un e-mail HTML + texte à l'adresse fournie par le client, avec la référence, le trajet, les horaires, le retour éventuel, les passagers, le tarif indicatif, un bouton de réponse via `replyTo`, et **EN ATTENTE DE CONFIRMATION**. L'envoi professionnel part toujours en premier. Les anciens accusés `PENDING` sont traités en priorité, et un quota est réservé pour les e-mails clients des nouvelles demandes.

**Le drapeau `ENABLE_CUSTOMER_RECEIPTS_ = false` est toujours désactivé.** Aucun changement n'est déployé dans l'Apps Script actif simplement en mettant GitHub à jour. Pour l'activer :

1. Tester localement les scripts sans e-mail réel depuis `android/` :
   ```powershell
   node .\google-apps-script\relay.test.js
   .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
   ```
   Les simulations contrôlent notamment l'absence de doublons après panne Gmail ou Firestore, les quotas et le contenu de l'accusé client.
2. Dans le dossier `android/firebase/`, faire déployer par le responsable Firebase les **index** ajoutés au dépôt, notamment `customerReceiptStatus` :
   ```powershell
   cd .\firebase
   firebase deploy --only firestore:indexes --project navette-phoenix
   ```
   Attendre l'état « activé/prêt ». Ne pas remplacer les règles de sécurité par erreur.
3. Vérifier la politique de confidentialité publiée, l'information sur les messages transactionnels, le compte Google qui envoie et la boîte professionnelle utilisée pour les réponses. Chaque demande consomme jusqu'à **2 destinataires du quota quotidien** au lieu de 1. Vérifier le quota disponible et prévoir son épuisement.
4. **Sauvegarder le Code.gs actuel dans Apps Script**, puis y copier le Code.gs du dépôt en conservant `ENABLE_CUSTOMER_RECEIPTS_ = false` et le `Installation.gs` existant. **Ne pas créer de deuxième déclencheur.**
5. Depuis l'éditeur Apps Script, exécuter dans cet ordre `testerAccuseClientSansEnvoi`, `verifierActivationAccuseClient`, puis `diagnostiquerNavettePhoenix`. Le deuxième doit renvoyer `technicalPreflightOk: true` et `customerReceiptIndexReady: true`. Le nombre de déclencheurs doit être **1**. Aucune de ces fonctions n'envoie d'e-mail ou n'active le relais. Le rapport ne valide pas juridiquement la politique de confidentialité.
6. **Seulement après ces contrôles**, mettre `ENABLE_CUSTOMER_RECEIPTS_ = true` dans Apps Script et enregistrer. Pas besoin de déployer le script comme application Web : le déclencheur existant exécutera le code enregistré. Avec une seule nouvelle réservation de test vers une adresse que vous contrôlez, vérifier la réception dans les **deux boîtes**, puis les états `mailStatus=SENT` et `customerReceiptStatus=SENT`.
7. En cas de problème ou de quota insuffisant, revenir à `ENABLE_CUSTOMER_RECEIPTS_ = false` pour empêcher tout nouvel accusé, **sans effacer ni remettre en file** les messages `SENDING`. Ne jamais renvoyer sans contrôle manuel.

**Limite technique** : aucun appel à Gmail et Firestore ne forme une transaction atomique. On ne peut pas promettre une livraison « exactement une fois » en toutes circonstances ; notre protocole privilégie **aucun renvoi automatique en état incertain**, le contrôle manuel et une référence unique.

---

# V20.1 — nouvelles améliorations progressives (23 novembre 2026)

Ce document complète les instructions ci-dessous. **Le circuit officiel Firebase → Apps Script → Gmail reste prioritaire** : ne pas le redéployer sans sauvegarde et vérification.

## Trajets favoris et accessibilité

- Depuis le formulaire de réservation, sélectionner deux communes et utiliser « Mémoriser ce trajet sur cet appareil ». Cette action est volontaire.
- « Reprendre mon trajet favori » recharge seulement le départ et l'arrivée (communes et lieux précis) ; aucune coordonnée personnelle, date ou horaire n'est mémorisé.
- « Supprimer ce trajet mémorisé » efface le favori sur l'appareil.
- Tester après retour à l'accueil, fermeture et réouverture de l'application. Le prix doit être recalculé selon le trajet et le nombre de passagers en cours.
- L'historique affiche l'heure de la dernière actualisation réussie. Une erreur réseau ne doit pas effacer les anciennes demandes.
- Les cartes indiquent un état de réservation distinct : `PENDING` = à transmettre ; `SENT` = e-mail transmis, **en attente de confirmation** ; `CONFIRMED` seulement = réservation confirmée.

## Accusé automatique au client : préparé, désactivé par défaut

Le dépôt contient un projet d'accusé de réception transactionnel distinct du courriel reçu par Phoenix LTS. Le fichier `google-apps-script/Code.gs` définit `ENABLE_CUSTOMER_RECEIPTS_ = false`. **Ne pas passer à true tant que les prérequis ne sont pas terminés.** Le code n'est pas installé automatiquement dans le projet Google Apps Script existant.

Préparation nécessaire avant une activation explicite :

1. Vérifier la politique de confidentialité (l'adresse e-mail sert aussi à l'accusé de réception client), la boîte de réponse et le quota Google Apps Script. Une demande utilise **jusqu'à deux destinataires** : Phoenix LTS et le client. Respecter les limites du compte Google.
2. Déployer l'index `customerReceiptStatus` dans `firebase/firestore.indexes.json` (répertoire `android/firebase`), via `firebase deploy --only firestore:indexes --project navette-phoenix`, puis attendre que l'index soit prêt.
3. Sauvegarder le `Code.gs` actif ; copier la nouvelle version depuis le dépôt sans écraser `Installation.gs` ni créer un second déclencheur. Avec le drapeau encore à `false`, exécuter `testerAccuseClientSansEnvoi` (test du modèle, **n'envoie aucun e-mail**) puis `diagnostiquerNavettePhoenix`. Vérifier `customerReceiptsEnabled:false`.
4. Quand les contrôles sont terminés, activer volontairement `ENABLE_CUSTOMER_RECEIPTS_ = true` dans Apps Script et enregistrer (ne pas « Déployer » en application Web). Faire **une seule nouvelle réservation de test** et vérifier séparément le courriel Phoenix LTS, l'accusé client et le statut Firestore.
5. Les demandes antérieures à l'activation n'ont pas de réémission automatique. Le relais utilise `customerReceiptStatus: PENDING → SENDING → SENT` ; si une demande reste `SENDING`, **vérifier la messagerie avant toute intervention**. Ne pas réinitialiser ce statut à l'aveugle (risque de doublon).

L'accusé client doit dire *demande transmise, en attente de validation*. Il **ne confirme jamais** le trajet.

## Notifications et Google Play

Le mode `navetteGoogleMail=true` n'a pas encore de mécanisme de notification push automatique à chaque changement de statut ; l'utilisateur consulte « Mes demandes » et peut actualiser. Ne pas présenter cette fonction comme opérationnelle avant d'avoir ajouté un backend et testé FCM. La permission Android 13+ doit rester facultative.

Les nouveaux éléments n'ont pas encore été compilés sur le PC depuis ces commits. Lancer :
```powershell
cd "C:\Users\PC\Documents\NavettePhoenix-V20\android"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
git pull --ff-only origin refonte-v20-verifiee
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Avant tout fichier AAB : relever dans Play Console le `versionCode` maximal et l'empreinte SHA-256 **du certificat de clé d'importation** ; configurer les secrets locaux, la configuration Firebase release de `com.phoenix.navette` et `verifyPlayBundle`. La réussite du build debug n'autorise pas à considérer l'AAB release comme prêt.

---


## V20.1 — contrôle qualité avant mise à jour Play Console

**État du dépôt :** améliorations d'interface et du suivi, tests de statuts ajoutés. Le build, les tests instrumentés et la validation sur Play Console doivent encore être exécutés localement. Aucun fichier AAB de production n'est déclaré prêt tant que ces vérifications ne sont pas terminées.

### 1. Synchronisation et tests Android

Depuis `C:\Users\PC\Documents\NavettePhoenix-V20\android` :

```powershell
git pull --ff-only origin refonte-v20-verifiee
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:clean :app:testDebugUnitTest :app:assembleDebug
```

Si Android Studio est installé ailleurs, sélectionner son Gradle JDK dans les paramètres ou adapter `JAVA_HOME`. Ne pas publier si une tâche échoue.

### 2. Contrôles fonctionnels manuels

- Accueil, tarifs, destinations, formulaire, récapitulatif, confirmation et historique sur petit et grand écran, avec taille de police Android agrandie.
- Vérifier les marges, le défilement, la barre d'état, le clavier, les champs de date/heure et les boutons à l'écran.
- Tester les trajets entre communes, au sein d'une commune, aller simple et aller-retour, tarifs fixes et devis, limites de passagers et bagages.
- Tester les champs manquants, l'e-mail invalide, la date passée, l'absence de connexion et le retour entre les étapes sans perte des valeurs.
- **Ne pas soumettre de demande réelle supplémentaire sans nécessité** : les précédents essais ont déjà validé l'arrivée d'un e-mail. Pour une nouvelle vérification, utiliser un environnement Firebase de test.
- Vérifier que `SENT` signifie « transmis, en attente de réponse », et non « trajet confirmé ». La confirmation commerciale nécessite une action explicite de l'équipe.

### 3. E-mails HTML

Le dépôt contient `google-apps-script/Code.gs` avec `htmlBody` et une version texte de secours. Mettre à jour **uniquement** le fichier `Code.gs` du projet Apps Script existant, sans écraser `Installation.gs` ni recréer les déclencheurs. Exécuter `diagnostiquerNavettePhoenix` ; valider ensuite un e-mail de test en environnement isolé. Le dépôt GitHub ne déploie pas automatiquement Apps Script.

### 4. Mise à jour Google Play sans casser l'application installée

- La version **release** doit conserver `com.phoenix.navette` ; le package de débogage `com.phoenix.navette28` est distinct.
- Relever dans Play Console le plus grand `versionCode` déjà importé. Fournir une valeur strictement supérieure via `navettePlayVersionCode` ; ne pas deviner le numéro.
- Vérifier que le certificat SHA-256 de la clé d'importation correspond à Play Console et conserver la clé privée hors du dépôt. Ne jamais générer une nouvelle clé pour remplacer l'ancienne sans procédure officielle.
- Vérifier le fichier Firebase **release** et la configuration Play Integrity/App Check. L'application release ne doit pas utiliser la configuration de débogage.
- Après `testDebugUnitTest`, construire le bundle signé avec les paramètres de signature locaux et la tâche `verifyPlayBundle` existante. Vérifier le résultat dans Play Console.
- Publier d'abord en **test interne** ; vérifier l'installation, l'ouverture, le parcours complet, l'historique et la mise à jour d'une version déjà installée avant la production.

**Critère de validation :** tests Gradle réussis, interface vérifiée sur plusieurs formats, e-mail HTML reçu, statuts cohérents, AAB signé avec la clé autorisée, import accepté dans Play Console. Une réception Gmail passée ne prouve pas à elle seule la conformité de la nouvelle release.

---

# Activer l’envoi et préparer Google Play

## Envoi via Google sans facturation

1. Dans un projet Firebase **Spark**, activer Authentication anonyme et la base Firestore par défaut. Associer les deux identifiants Android à leurs fichiers `google-services.json`. Activer App Check pour Firestore avec Play Integrity sur la version Play et utiliser le jeton de débogage uniquement dans le projet de test.
2. Dans `firebase`, déployer les règles et les index de ce projet avec `firebase deploy --only firestore --project ID_PROJET`. Attendre que l’index `googleMailRequests` soit prêt.
3. Dans Google Apps Script, créer un projet autonome avec le compte Google qui enverra les e-mails. Copier `google-apps-script/Code.gs` et remplacer son manifeste par `google-apps-script/appsscript.json`. Donner au compte du déclencheur les droits Firestore de lecture et de modification des données dans le projet Firebase, par exemple via le rôle **Cloud Datastore User**. Le script utilise l’autorisation Google de ce compte.
4. La boîte officielle des réservations est `caro.resanavettephoenix@gmail.com`, définie directement dans `google-apps-script/Code.gs`. Le projet Firebase par défaut est `navette-phoenix` ; la propriété `FIREBASE_PROJECT_ID`, si elle existe, peut le remplacer. **Synchroniser le fichier Code.gs du dépôt avec le projet Apps Script existant**, sans écraser son éventuel fichier `Installation.gs`. Exécuter une fois `diagnostiquerNavettePhoenix` pour vérifier les accès et le déclencheur, puis `relayGoogleMailRequests` pour traiter les demandes `PENDING`. Un seul déclencheur toutes les cinq minutes doit appeler le relais. Ce script privé ne nécessite pas de déploiement en application Web.
5. Dans PowerShell, depuis le dossier Android, compiler avec `./gradlew.bat :app:assembleDebug`. Si les propriétés Gradle du PC contiennent déjà `navetteGoogleMail=false`, retirer cette ligne pour utiliser le mode Google de la V20.

L’application affiche une référence après enregistrement dans Firestore. Le relais peut ensuite prendre quelques minutes. **Mes demandes** indique quand Google a accepté la transmission. Phoenix LTS confirme le trajet séparément. Le compte Google grand public dispose actuellement d’un quota de 100 destinataires par jour dans Apps Script, partagé avec ses autres scripts. Les demandes en attente sont conservées lorsque le quota est épuisé.

Le suivi est lié à l’identité anonyme Firebase de cet appareil. Les données de réservation se trouvent dans `googleMailAccounts/{uid}/googleMailRequests`. Si une demande reste en état `SENDING` après plusieurs déclenchements, vérifier la boîte de réception avant tout nouvel envoi manuel. Les demandes d’effacement sont dans `googleMailAccounts/{uid}/privacy/deletion`. Les itinéraires s’ouvrent dans Google Maps. Les étapes Firebase Functions et SMTP ci-dessous concernent le mode activé avec `-PnavetteGoogleMail=false`.

## 1. Firebase et réservations

Utiliser le projet Firebase de Phoenix LTS. Pour l’APK de test `com.phoenix.navette28`, placer sa configuration `google-services.json` dans `app/`. Pour la version Google Play, inscrire `com.phoenix.navette` dans Firebase et placer sa configuration dans `app/src/release/google-services.json`. Les deux fichiers restent hors Git. Activer Authentication anonyme, Cloud Firestore et App Check avec Play Integrity. Pour l’essai sur appareil virtuel, utiliser un projet de test et un fournisseur App Check de débogage limité à cette compilation, ou passer par le test interne Google Play avec les empreintes requises.

Pour l’émulateur Android et un projet Firebase de test, compiler avec `-PnavetteDebugAppCheck=true`, puis enregistrer le jeton émis par le SDK de débogage dans ce projet de test. Conserver ce jeton localement. Le bundle Google Play utilise Play Integrity.

Créer la base dans la région européenne voulue avant d’enregistrer des données. Les fonctions sont réglées pour `europe-west1`. L’équipe habilitée peut contrôler les demandes dans Firestore. Les changements du champ `status` vers `CONFIRMED`, `DECLINED` ou `CANCELLED` apparaissent dans **Mes demandes** et déclenchent une notification si le client l’a autorisée. Traiter les demandes de suppression de données dans `deletionRequests`, avec suppression des documents concernés et vérification d’identité appropriée.

Dans le dossier `firebase`, une session Firebase CLI autorisée configure les secrets `RESERVATION_MAIL_TO` et `RESERVATION_SMTP_URL`. Le premier reçoit l’adresse Gmail de réservation confirmée par Phoenix LTS. Le second reçoit l’URL `smtps://` du fournisseur d’e-mail autorisé. Le compte SMTP doit inclure les identifiants encodés dans l’URL. Le mail part du serveur. La demande est enregistrée avant la notification. Le champ `mailStatus` permet à Phoenix LTS de suivre les envois. Une reprise après interruption entre la livraison SMTP et l’accusé peut produire un second e-mail avec la même référence.

Cloud Functions et Secret Manager nécessitent une configuration de facturation. Les appels cartographiques sont soumis aux tarifs et quotas du projet. Configurer un budget et des quotas avant l’activation.

## 2. Cartes et confidentialité

Activer Maps SDK for Android, Places API (New) et Routes API dans Google Cloud. Utiliser une clé Android restreinte à Maps SDK for Android, à l’identifiant Android et à l’empreinte SHA-1 du certificat de la version correspondante. Utiliser une clé distincte pour Places API (New) et Routes API, limitée à ces deux API, dans le secret `GOOGLE_MAPS_SERVER_KEY`. Ne jamais placer les identifiants SMTP ou la clé serveur dans le projet Android.

Dans le fichier personnel `%USERPROFILE%\.gradle\gradle.properties`, renseigner `navetteMapsAndroidKey`, `navetteBookingEmail` et `navettePrivacyUrl`. Ce dernier doit pointer vers la politique de confidentialité publique de Phoenix LTS. Les clés Android restreintes et les coordonnées affichées sont lisibles dans l’application compilée.

Pour la mise en service avec Firebase CLI, utiliser ces commandes depuis le dossier `firebase`. Remplacer `ID_PROJET` par l’identifiant du projet Phoenix LTS. Chacune des commandes de configuration de secret demande sa valeur dans le terminal.

```powershell
firebase functions:secrets:set RESERVATION_MAIL_TO --project ID_PROJET
firebase functions:secrets:set RESERVATION_SMTP_URL --project ID_PROJET
firebase functions:secrets:set GOOGLE_MAPS_SERVER_KEY --project ID_PROJET
firebase deploy --only firestore --project ID_PROJET
firebase deploy --only functions --project ID_PROJET
```

Pour activer uniquement la prise de demande et l’e-mail, déployer les fonctions de réservation et de suivi avant les fonctions Maps. Pour la recette, confirmer qu’une demande de test est visible dans Firestore et dans Gmail, vérifier le retour et les bagages, relever un statut dans l’app et tester une carte avec un trajet fictif.

## 3. Google Play

1. Dans l’explorateur d’app bundles, relever le plus grand code déjà importé pour `com.phoenix.navette`. Le noter sous `navettePlayHighestVersionCode` dans le fichier Gradle personnel. Si le code 20 est déjà pris, indiquer un code supérieur sous `navettePlayVersionCode`.
2. Relever l’empreinte SHA-256 du **certificat de clé d’importation** dans Play Console et la noter sous `navettePlayUploadSha256`. Configurer sur le PC les variables `NAVETTE_UPLOAD_STORE`, `NAVETTE_UPLOAD_ALIAS`, `NAVETTE_UPLOAD_STORE_PASSWORD` et `NAVETTE_UPLOAD_KEY_PASSWORD` avec la clé d’importation existante. La commande `./gradlew.bat :app:bundleRelease` vérifie ces éléments avant de générer le bundle.
3. Téléverser l’AAB signé dans **Test et publication → Test interne**. Vérifier l’identifiant de paquet et le code de version dans le résumé.
4. Ajouter dans Play Console l’adresse de la politique de confidentialité, les déclarations **Sécurité des données** reflétant les opérations réelles et les éléments demandés pour la fiche. Organiser les tests éventuellement exigés par le type de compte avant une publication.
5. Essayer le trajet complet et vérifier la réception d’un message de test. Passer à la publication quand cette vérification et la revue Play sont terminées.

## Politique de confidentialité V20 à valider

La [page publique actuelle](https://www.navettephoenix.fr/politique) décrit la version sans formulaire intégré. Mettre son contenu à jour avant l’ouverture des réservations V20 et utiliser la même URL dans Play Console.

### Responsable du traitement et contact

Phoenix LTS exploite Navette Phoenix 28 et traite les demandes de navette et de devis. L’utilisateur peut faire une demande dans la rubrique **Confidentialité** de l’application ou contacter Phoenix LTS par le canal indiqué sur le site officiel. Phoenix LTS doit confirmer l’adresse, l’identité juridique complète et le courriel à publier pour l’exercice des droits.

### Données et finalités

Le formulaire utilise le nom, le téléphone, l’adresse électronique, les adresses et horaires du trajet, le nombre de passagers et les bagages. Les besoins particuliers et les commentaires sont facultatifs. Ces données permettent d’évaluer la demande, de répondre, de préparer un devis et d’organiser le transport si Phoenix LTS confirme la demande. Les champs essentiels sont requis pour pouvoir traiter la demande.

Un identifiant de connexion anonyme permet d’afficher le suivi sur l’appareil qui a envoyé la demande. Des identifiants techniques servent à sécuriser l’accès, limiter les demandes répétées et reconnaître une nouvelle tentative d’envoi. Les notifications de statut sont facultatives et utilisent un identifiant technique de messagerie. L’utilisateur peut gérer la réception dans les paramètres Android.

Les suggestions et le calcul d’itinéraire transmettent à Google Maps les adresses saisies lorsque l’utilisateur utilise ces services. La saisie manuelle des adresses permet d’utiliser le formulaire sans autorisation de localisation.

### Bases légales

À valider par Phoenix LTS avant publication. Confirmer notamment la base applicable au traitement du devis et à l’organisation du trajet, à la sécurité du service, aux notifications facultatives et aux éventuelles obligations légales de conservation. Les mesures précontractuelles à la demande de l’utilisateur et l’exécution du contrat sont des bases à examiner pour les demandes et les transports confirmés.

### Destinataires, conservation et transferts

Les demandes sont accessibles aux personnes habilitées de Phoenix LTS. Les services configurés peuvent inclure Firebase Authentication, App Check, Cloud Firestore, Google Apps Script, Cloud Functions, Firebase Cloud Messaging, Google Maps Platform et le fournisseur d’envoi d’e-mails de réservation. Les données communiquées à chaque prestataire dépendent de la fonctionnalité utilisée.

Phoenix LTS doit fixer les durées ou critères de conservation pour les devis, les demandes, l’historique, les e-mails, les journaux techniques et les demandes de droits. Confirmer les régions et les conditions contractuelles des prestataires, les transferts éventuels hors Espace économique européen ainsi que les garanties et modalités d’accès à ces garanties.

### Sécurité et droits

Les échanges utilisent HTTPS. Les règles Firestore et les fonctions limitent le suivi à l’identité qui a envoyé la demande. L’application exclut ses données des sauvegardes et des transferts Android.

Les personnes peuvent demander l’accès, la rectification, l’effacement ou la limitation du traitement et, selon la base applicable, exercer leurs droits d’opposition, de portabilité ou de retrait du consentement. Elles peuvent adresser une réclamation à la CNIL. Une demande de suppression envoyée dans l’application est transmise à Phoenix LTS pour traitement. Phoenix LTS doit confirmer la procédure de vérification, de suppression et les exceptions légales applicables.

[Transparence et information des personnes · CNIL](https://www.cnil.fr/fr/conformite-rgpd-information-des-personnes-et-transparence) · [Données et sécurité Firebase](https://firebase.google.com/support/privacy/)

[Politique de données Google Play](https://support.google.com/googleplay/android-developer/answer/10144311) · [Signature d’application Play](https://support.google.com/googleplay/android-developer/answer/9842756)
