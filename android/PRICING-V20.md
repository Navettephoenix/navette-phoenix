# Tarification V20 — zones communales et prix par passager

## Code intégré

- **Application Android** : `app/src/main/java/com/phoenix/navette28/PricingCalculator.kt` (calcul pur), `MainActivity.kt` (sélection avec recherche, montant immédiat, récapitulatif), `Booking.kt` (données envoyées), `GoogleMailGateway.kt` et `RequestHistory.kt` (suivi des demandes).
- **Google Apps Script** : `google-apps-script/Code.gs` recalcule et contrôle la tarification des nouvelles demandes avant l'e-mail.
- **Firestore** : `firebase/firestore.rules` accepte les champs de prix et contrôle les montants arithmétiques.
- **Firebase Functions alternatif** : `firebase/functions/pricing.mjs`, `booking.mjs`, `index.mjs` prennent en charge les mêmes champs.
- **Tests** : `app/src/test/java/com/phoenix/navette28/PricingCalculatorTest.kt`, `MainActivityTest.kt`, `firebase/functions/test/pricing.test.mjs`, `google-apps-script/relay.test.mjs`, `firebase/functions/test/integration/google-mail.test.mjs`.

## Grille par commune

Les zones, fournies par Phoenix LTS, sont définies par les communes du secteur Forêts du Perche (centre Senonches) et du secteur Terres de Perche (centre La Loupe). La zone d'un trajet local est le maximum des deux zones communales. **Ne pas calculer la zone depuis la distance kilométrique.**

| Zone | Prix par passager pour un trajet simple |
|---|---:|
| 1 | 5 € |
| 2 | 6 € |
| 3 | 8 € |
| 4 | 10 € |

Total = prix unitaire × nombre de passagers (1 à 4) × (2 pour un aller-retour, sinon 1). Aucun supplément bagages. Communes de deux secteurs différents, inconnues ou « Autre commune » : `QUOTE`, prix non chiffré (« sur devis »).

Le prix est **estimatif et soumis à confirmation du service de réservation**. Aucun paiement n'est intégré.

## Trajets dans une même zone

Le tarif **ne dépend pas du passage par Senonches ou La Loupe** : pour deux communes classées dans la même zone et le même secteur, on facture simplement cette zone **par passager**. Si les zones diffèrent, on applique la plus élevée. Le calcul utilise `maxOf(zoneDépart, zoneArrivée)` : deux communes de zone 2 donnent donc zone 2, **6 € par passager**.

| Exemple de trajet | Zone | Passagers | Aller-retour | Prix |
|---|---:|---:|---|---:|
| Digny → La Framboisière | 2 | 3 | Non | 18 € |
| La Chapelle-Fortin → Digny | 2 | 3 | Oui | 36 € |
| Jaudrais → Lamblore | 3 | 2 | Non | 16 € |
| La Ferté-Vidame → La Saucelle | 4 | 4 | Oui | 80 € |
| Manou → Montireau | 3 | 3 | Non | 24 € |
| Combres → Frazé | 4 | 2 | Non | 20 € |
| Senonches (mairie) → Senonches (gare) | 1 | 2 | Non | 10 € |

Les trajets au sein d'une même commune nécessitent deux lieux de prise en charge et de dépose différents ; sinon la validation refuse les adresses identiques.

**Correctif de compilation du 23 novembre 2026** : `PricingCalculator` est public afin que la propriété `Booking.pricing` ne révèle plus un type interne. Les tests Kotlin, Firebase et Apps Script incluent désormais les trajets dans une même zone. Recompiler après `git pull`.

## Mise en service — ordre impératif

1. **Synchroniser d'abord les règles Firestore** : depuis `android/firebase`, lancer avec le compte autorisé `firebase deploy --only firestore --project navette-phoenix`. Attendre le succès avant de tester l'envoi de l'APK V20 : sinon le serveur rejette ses nouveaux champs.
2. **Synchroniser le Google Apps Script existant** : remplacer uniquement le contenu `Code.gs` du projet « Navette Phoenix V20 - Reservations » avec `google-apps-script/Code.gs` de cette branche. Conserver `Installation.gs` et le déclencheur toutes les cinq minutes. L'adresse officielle destinataire est `caro.resanavettephoenix@gmail.com`.
3. **Tests Android** : ouvrir `android/` dans Android Studio, compiler `assembleDebug` et lancer `testDebugUnitTest` sur un JDK reconnu par Android Studio ; vérifier l'écran sur le Pixel, y compris l'heure, le clavier, les sélecteurs, « Autre commune », les 4 passagers et le retour.
4. **Tests du relais** : depuis `android/`, lancer `node --test google-apps-script/relay.test.mjs`. Pour le mode alternatif Functions, lancer `npm --prefix firebase/functions test` ; le déploiement Functions exige une configuration de facturation Firebase appropriée et n'est pas nécessaire en mode Google Mail.
5. **Tests de règles** : depuis `android/firebase`, utiliser l'émulateur Firestore et les tests d'intégration avec `firebase emulators:exec --only firestore --project demo-navette-phoenix "npm --prefix functions run test:emulator"`.
6. **Recette de bout en bout** : envoyer **une seule nouvelle demande de test** identifiée, puis vérifier la même référence et le même montant dans « Mes demandes », Firestore et l'e-mail reçu. Ne pas renvoyer une demande en état `SENDING` sans vérifier la boîte Gmail.

Les anciennes réservations sans champs de tarification restent consultables ; elles ne doivent pas recevoir de prix historique inventé. Les changements de code GitHub seuls ne déploient ni les règles Firestore ni le fichier Apps Script. Aucun déploiement de production ni test instrumenté réel n'a été effectué depuis cette session.

## Exemples à vérifier

| Trajet | Passagers | Aller-retour | Montant |
|---|---:|---|---:|
| Digny → Senonches | 1 | Non | 6 € |
| Digny → Senonches | 3 | Non | 18 € |
| Digny → Senonches | 3 | Oui | 36 € |
| Saint-Éliph → Fontaine-Simon | 2 | Non | 12 € |
| Saint-Éliph → Fontaine-Simon | 2 | Oui | 24 € |
| Digny → La Loupe | 2 | Non | Sur devis |
