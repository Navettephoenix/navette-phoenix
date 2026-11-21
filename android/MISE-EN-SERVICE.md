# Activer l’envoi et préparer Google Play

## 1. Firebase et réservations

Utiliser le projet Firebase appartenant à Phoenix LTS. Ajouter l’application Android `com.phoenix.navette28` et placer son fichier `google-services.json` dans `app/`. Ce fichier reste hors Git. Activer Authentication anonyme, Cloud Firestore et App Check avec Play Integrity. Pour l’essai sur appareil virtuel, utiliser un projet de test et un fournisseur App Check de débogage limité à cette compilation, ou passer par le test interne Google Play avec les empreintes requises.

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

1. Ouvrir le projet dans Android Studio et choisir **Build → Generate Signed Bundle / APK → Android App Bundle**.
2. Choisir la clé d’importation de la fiche concernée. La clé et ses mots de passe restent sur le PC ou dans un coffre autorisé. Pour une nouvelle fiche, suivre le choix proposé par Play App Signing après vérification des accès.
3. Téléverser l’AAB signé dans **Test et publication → Test interne**. Vérifier l’identifiant de paquet et le code de version dans le résumé.
4. Ajouter dans Play Console l’adresse de la politique de confidentialité, les déclarations **Sécurité des données** reflétant les opérations réelles et les éléments demandés pour la fiche. Organiser les tests éventuellement exigés par le type de compte avant une publication.
5. Essayer le trajet complet et vérifier la réception d’un message de test. Passer à la publication quand cette vérification et la revue Play sont terminées.

Le texte de confidentialité intégré explique les flux de l’application. Phoenix LTS doit valider la durée de conservation, le point de contact, les sous-traitants et la politique publiée avant d’ouvrir les réservations au public.

[Politique de données Google Play](https://support.google.com/googleplay/android-developer/answer/10144311) · [Signature d’application Play](https://support.google.com/googleplay/android-developer/answer/9842756)
