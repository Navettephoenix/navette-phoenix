# Navette Phoenix

Projet Android natif en Java. La fiche publique Google Play a servi de référence fonctionnelle.

## Installation de développement

1. Décompressez l’archive et ouvrez le dossier `NavettePhoenix` dans Android Studio.
2. Installez Android SDK Platform 37.0 dans le SDK Manager et sélectionnez le JDK intégré pour Gradle.
3. Synchronisez avec Gradle 9.3.1. Le projet utilise Android Gradle Plugin 9.1.1.
4. Lancez le module `app` sur un appareil ou un émulateur Android 8.0 ou supérieur. Pour produire un APK de test, utilisez `gradlew.bat :app:assembleDebug` sous Windows ou `./gradlew :app:assembleDebug` sous Linux et macOS.

## Parcours

Le passager indique ses coordonnées et son trajet. Après validation du récapitulatif, l’application envoie la demande au serveur Phoenix LTS. Une référence de réception est affichée lorsque le serveur confirme l’enregistrement. Le service confirme ensuite la disponibilité par téléphone. La notification Gmail est traitée côté serveur.

La version 1.20 utilise l’identifiant Android de la fiche existante (`com.phoenix.navette`) et le code de version 20. Pour publier le projet, générez un Android App Bundle signé avec la clé d’importation autorisée. L’historique conserve la référence reçue. Le suivi ultérieur du statut opérateur nécessite une API de consultation privée par passager.

## Configuration du service

Pour activer l’envoi, renseigner `navetteReservationUrl` dans le fichier Gradle personnel `~/.gradle/gradle.properties`. La valeur doit être l’URL HTTPS complète de l’API de réservation Navette Phoenix. Les propriétés `navetteBookingEmail` et `navetteBookingPhone` alimentent l’écran Contact. Ces réglages sont propres à la compilation. L’envoi automatique de notifications Gmail se configure côté serveur.


## Estimation pour les 37 communes

Le bouton « Calculateur de tarifs » ouvre le calculateur hors ligne. Les noms et les codes INSEE des 22 communes de Terres de Perche et des 15 communes des Forêts du Perche ont été vérifiés le 21 novembre 2026.

- [Terres de Perche (Insee)](https://www.insee.fr/fr/metadonnees/geographie/intercommunalite/200070167-terres-de-perche)
- [Forêts du Perche (Insee)](https://www.insee.fr/fr/metadonnees/geographie/intercommunalite/200069912-les-forets-du-perche)
- [Centres géographiques de Terres de Perche](https://geo.api.gouv.fr/epcis/200070167/communes?fields=nom,code,centre)
- [Centres géographiques des Forêts du Perche](https://geo.api.gouv.fr/epcis/200069912/communes?fields=nom,code,centre)

Le fichier `app/src/main/assets/communes.csv` contient la distance à vol d’oiseau entre le centre géographique de chaque commune et celui de La Loupe ou de Senonches. La distance est calculée avec la formule de Haversine et un rayon terrestre moyen de 6 371 008,8 m. La dernière colonne reprend la zone du dessin de l’affiche pour comparaison. Une cellule vide signale Morvilliers, absent de l’affiche. La zone calculée 0 signifie que le centre est au-delà du dernier palier.

L’estimation applique le plus élevé des deux paliers selon les affiches (jusqu’à 3, 8, 13 et 20 km). Elle multiplie le prix par le nombre de passagers et le double pour un aller-retour entre les mêmes communes. Chaque commune est rattachée à son secteur administratif. Les trajets entre secteurs et les écarts avec le dessin sont signalés à l’écran. Exemples : Digny–Senonches donne 8 € estimés pour un passager, alors que l’affiche indique 6 €. Chassant, Frazé et La Croix-du-Perche dépassent 20 km entre centres géographiques. Le plafond de 30 km annoncé par le serveur n’a pas été repris dans ce calculateur fondé sur les affiches.

Le calculateur sert d’estimation par commune. Pour un tarif définitif, le service doit vérifier l’adresse exacte et les distances routières, notamment pour les gares. Le module ne collecte aucun paiement.

## Les trois onglets de réservation

- **Forêts du Perche** propose les 15 communes du secteur et les deux gares locales.
- **Terres de Perche** propose les 22 communes du secteur et les deux gares locales.
- **Devis hors secteur** propose les catégories aéroport, gare à Paris et autre destination. Le départ, la destination, le créneau et le nombre de passagers figurent dans le récapitulatif.

Chaque onglet conserve sa saisie pendant la navigation. Les demandes et les coordonnées du service sont accessibles sous le formulaire. La réservation locale affiche une estimation pour un aller simple, avec règlement à bord. Le calculateur séparé permet aussi de simuler un aller-retour.
