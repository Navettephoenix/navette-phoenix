# Navette Phoenix 28 · V20

Application Android Kotlin et Gradle Kotlin DSL pour Phoenix LTS. L’accueil permet de réserver une navette, de demander un devis et de consulter les destinations. Le parcours de réservation comprend le trajet, les coordonnées et le récapitulatif.

| Réglage | Valeur |
|---|---|
| Version | 20.0, code 20 |
| Identifiant de test | `com.phoenix.navette28` |
| Identifiant Google Play | `com.phoenix.navette` |
| Système minimal | Android 7.0, API 24 |
| Compilation et cible | Android 17, API 37.0 |
| Outils | Android Studio Panda 3 correctif 1 ou ultérieur, AGP 9.1.1, Gradle 9.3.1, JDK intégré |
| Dossier serveur | `firebase` |

La version 1.19 en production utilise `com.phoenix.navette` et porte le code 19. La V20 reprend cet identifiant en version Google Play. La mise à jour exige un code supérieur à tous ceux déjà importés et la clé d’importation reconnue par la fiche existante.

## Ouvrir et essayer

1. Ouvrir le dossier qui contient `settings.gradle.kts` dans Android Studio.
2. Laisser le téléchargement des dépendances se terminer. Installer le SDK 37.0 si Android Studio le demande.
3. Choisir le Pixel dans Android Studio et cliquer sur **Run ▶**.
4. En l’absence de configuration Firebase, le parcours se teste jusqu’au récapitulatif. L’envoi affiche un message d’indisponibilité. La carte externe Google Maps s’ouvre avec les adresses saisies.

Depuis le terminal Windows d’Android Studio, `./gradlew.bat :app:assembleDebug` produit un APK de test. `./gradlew.bat :app:bundleRelease` génère le bundle release. Le guide de configuration se trouve dans [MISE-EN-SERVICE.md](MISE-EN-SERVICE.md).

## Fonctionnement

- L’accueil donne accès à la réservation, aux devis, aux destinations, au suivi, aux contacts et aux informations de confidentialité.
- Android gère la saisie, le récapitulatif et l’historique. Les suggestions d’adresse et le calcul de l’itinéraire passent par les fonctions serveur. Le prix reste à confirmer par Phoenix LTS.
- Firebase Auth identifie l’appareil de manière anonyme. App Check avec Play Integrity protège les fonctions. Les règles Firestore ferment l’accès direct aux clients. Les fonctions limitent la lecture à l’identité qui a créé la demande.
- La transaction Firestore utilise un identifiant d’envoi stable pour reconnaître la même demande après une coupure réseau. Après enregistrement, la fonction d’e-mail adresse une notification à la boîte de réservation configurée par Phoenix LTS. Le transporteur confirme le trajet ultérieurement.
- Les notifications FCM sont facultatives. Elles signalent un changement de statut et invitent à consulter **Mes demandes**. L’application ne demande pas l’accès à la position du téléphone.
- Les listes communales sont intégrées dans `app/src/main/assets/communes.csv` et proviennent des listes Insee. Le logo est celui fourni dans la version 1.19.

[Forêts du Perche · Insee](https://www.insee.fr/fr/metadonnees/geographie/intercommunalite/200069912-les-forets-du-perche) · [Terres de Perche · Insee](https://www.insee.fr/fr/metadonnees/geographie/intercommunalite/200070167-terres-de-perche)

## Tests locaux

- Android. Exécuter `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` depuis le dossier Android.
- Fonctions. Dans `firebase/functions`, exécuter `npm ci` puis `npm test` avec Node.js 22.
- Firestore. Dans `firebase`, exécuter `firebase emulators:exec --only firestore --project demo-navette-phoenix "npm --prefix functions run test:emulator"`. Les tests utilisent des données fictives et un projet de démonstration local.

## Bilan du 22 novembre 2026

| Vérification | Résultat |
|---|---|
| APK de test | Compilé, signature de débogage et alignement ZIP 16 Ko vérifiés. 19,1 Mo. |
| Android | Sept tests unitaires réussis. Analyse Android Lint sans erreur, 18 avertissements. |
| Serveur | Quatre tests unitaires et trois tests Firestore locaux réussis. |
| Configuration Google Play | Manifeste `com.phoenix.navette`, minimum API 24, cible API 37. Code 20 provisoire. |
| Territoire | Les 37 codes et noms de communes correspondent au référentiel officiel des deux intercommunalités. |
| Reste à vérifier | Rendu réel sur Android, configuration Firebase et Maps de production, réception de l’e-mail, certificat d’importation local et validation Play Console. |

Le test Firestore couvre l’enregistrement concurrent d’une même demande, le suivi limité au propriétaire, le blocage des lectures et écritures directes, la demande de suppression et l’actualisation du jeton de notification.

[Communes des Forêts du Perche](https://geo.api.gouv.fr/epcis/200069912/communes?fields=nom,code) · [Communes des Terres de Perche](https://geo.api.gouv.fr/epcis/200070167/communes?fields=nom,code)
