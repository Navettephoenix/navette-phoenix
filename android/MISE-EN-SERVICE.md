# Activer l’envoi et préparer Google Play

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

Les demandes sont accessibles aux personnes habilitées de Phoenix LTS. Les services configurés peuvent inclure Firebase Authentication, App Check, Cloud Firestore, Cloud Functions, Firebase Cloud Messaging, Google Maps Platform et le fournisseur d’envoi d’e-mails de réservation. Les données communiquées à chaque prestataire dépendent de la fonctionnalité utilisée.

Phoenix LTS doit fixer les durées ou critères de conservation pour les devis, les demandes, l’historique, les e-mails, les journaux techniques et les demandes de droits. Confirmer les régions et les conditions contractuelles des prestataires, les transferts éventuels hors Espace économique européen ainsi que les garanties et modalités d’accès à ces garanties.

### Sécurité et droits

Les échanges utilisent HTTPS. L’application accède aux demandes au travers de fonctions sécurisées et les règles Firestore bloquent l’accès direct par les clients. Le suivi est limité à l’identité qui a envoyé la demande. L’application exclut ses données des sauvegardes et des transferts Android.

Les personnes peuvent demander l’accès, la rectification, l’effacement ou la limitation du traitement et, selon la base applicable, exercer leurs droits d’opposition, de portabilité ou de retrait du consentement. Elles peuvent adresser une réclamation à la CNIL. Une demande de suppression envoyée dans l’application est transmise à Phoenix LTS pour traitement. Phoenix LTS doit confirmer la procédure de vérification, de suppression et les exceptions légales applicables.

[Transparence et information des personnes · CNIL](https://www.cnil.fr/fr/conformite-rgpd-information-des-personnes-et-transparence) · [Données et sécurité Firebase](https://firebase.google.com/support/privacy/)

[Politique de données Google Play](https://support.google.com/googleplay/android-developer/answer/10144311) · [Signature d’application Play](https://support.google.com/googleplay/android-developer/answer/9842756)
