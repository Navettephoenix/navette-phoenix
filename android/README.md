# Navette Phoenix 28 · V20

Application Android Kotlin et Gradle Kotlin DSL pour Phoenix LTS. L’accueil permet de réserver une navette, de demander un devis et de consulter les destinations. Le parcours de réservation comprend le trajet, les coordonnées et le récapitulatif.

| Réglage | Valeur |
|---|---|
| Version | 20.0, code 20 |
| Identifiant Android | `com.phoenix.navette28` |
| Système minimal | Android 8.0, API 26 |
| Compilation et cible | Android 17, API 37.0 |
| Outils | Android Studio Panda 3 correctif 1 ou ultérieur, AGP 9.1.1, Gradle 9.3.1, JDK intégré |
| Dossier serveur | `firebase` |

L’AAB fourni pour l’ancienne version est un fichier compilé. Son identifiant est `com.phoenix.navette` et son code de version est 19. La V20 utilise le nouvel identifiant demandé et nécessite sa propre fiche Google Play. Une mise à jour de la fiche 1.19 exige l’identifiant original, un code de version supérieur à tous ceux utilisés dans la console et la clé d’importation reconnue. Aucun fichier de clé de production n’a été fourni.

## Ouvrir et essayer

1. Ouvrir le dossier qui contient `settings.gradle.kts` dans Android Studio.
2. Laisser le téléchargement des dépendances se terminer. Installer le SDK 37.0 si Android Studio le demande.
3. Choisir le Pixel dans Android Studio et cliquer sur **Run ▶**.
4. En l’absence de configuration Firebase, le parcours se teste jusqu’au récapitulatif. L’envoi affiche un message d’indisponibilité. La carte externe Google Maps s’ouvre avec les adresses saisies.

Depuis le terminal Windows d’Android Studio, `./gradlew.bat :app:assembleDebug` produit un APK de test. `./gradlew.bat :app:bundleRelease` génère le bundle release. Le guide de configuration se trouve dans [MISE-EN-SERVICE.md](MISE-EN-SERVICE.md).

## Fonctionnement

- Android gère la saisie, le récapitulatif et l’historique. Les suggestions d’adresse et le calcul de l’itinéraire passent par les fonctions serveur. Le prix reste à confirmer par Phoenix LTS.
- Firebase Auth identifie l’appareil de manière anonyme. App Check avec Play Integrity protège les fonctions. Les règles Firestore ferment l’accès direct aux clients. Les fonctions limitent la lecture à l’identité qui a créé la demande.
- La transaction Firestore utilise un identifiant d’envoi stable pour reconnaître la même demande après une coupure réseau. Après enregistrement, la fonction d’e-mail adresse une notification à la boîte de réservation configurée par Phoenix LTS. Le transporteur confirme le trajet ultérieurement.
- Les notifications FCM sont facultatives. Elles signalent un changement de statut et invitent à consulter **Mes demandes**. L’application ne demande pas l’accès à la position du téléphone.
- Les listes communales sont intégrées dans `app/src/main/assets/communes.csv` et proviennent des listes Insee. Le logo est celui fourni dans la version 1.19.

[Forêts du Perche · Insee](https://www.insee.fr/fr/metadonnees/geographie/intercommunalite/200069912-les-forets-du-perche) · [Terres de Perche · Insee](https://www.insee.fr/fr/metadonnees/geographie/intercommunalite/200070167-terres-de-perche)
