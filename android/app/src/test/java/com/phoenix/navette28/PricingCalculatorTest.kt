package com.phoenix.navette28

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingCalculatorTest {
    private fun price(from: String, to: String, returnTrip: Boolean, passengers: Int) =
        PricingCalculator.calculerTarif(from, to, returnTrip, passengers) as PricingCalculator.Resultat.Tarif

    @Test fun foretExamples() {
        val one = price("Digny", "Senonches", false, 1)
        assertEquals(2, one.zone)
        assertEquals(PricingCalculator.Secteur.FORETS_DU_PERCHE, one.secteur)
        assertEquals(6, one.prixParPassager)
        assertEquals(6, one.prixTotalTrajet)
        assertEquals(6, one.prixTotal)
        assertEquals(18, price("Digny", "Senonches", false, 3).prixTotal)
        val returnTrip = price("Digny", "Senonches", true, 3)
        assertEquals(18, returnTrip.prixTotalTrajet)
        assertEquals(36, returnTrip.prixTotal)
    }

    @Test fun terresExamples() {
        val one = price("Saint-Éliph", "Fontaine-Simon", false, 2)
        assertEquals(2, one.zone)
        assertEquals(PricingCalculator.Secteur.TERRES_DE_PERCHE, one.secteur)
        assertEquals(6, one.prixParPassager)
        assertEquals(12, one.prixTotalTrajet)
        assertEquals(24, price("Saint-Éliph", "Fontaine-Simon", true, 2).prixTotal)
    }

    @Test fun highestZoneWinsAndLuggageHasNoInfluence() {
        assertEquals(10, price("Senonches", "La Ferté-Vidame", false, 1).prixTotal)
        assertEquals(8, price("Jaudrais", "Digny", false, 1).prixTotal)
        assertEquals(5, price("La Loupe", "La Loupe", false, 1).prixTotal)
    }

    @Test fun sameZoneAlwaysCostsItsOwnZoneRatePerPassengerInForets() {
        val zone2 = price("Digny", "La Framboisière", false, 3)
        assertEquals(2, zone2.zone)
        assertEquals(6, zone2.prixParPassager)
        assertEquals(18, zone2.prixTotalTrajet)
        assertEquals(18, zone2.prixTotal)
        assertEquals(36, price("La Chapelle-Fortin", "Digny", true, 3).prixTotal)

        val zone3 = price("Jaudrais", "Lamblore", false, 2)
        assertEquals(3, zone3.zone)
        assertEquals(8, zone3.prixParPassager)
        assertEquals(16, zone3.prixTotal)

        val zone4 = price("La Ferté-Vidame", "La Saucelle", true, 4)
        assertEquals(4, zone4.zone)
        assertEquals(10, zone4.prixParPassager)
        assertEquals(40, zone4.prixTotalTrajet)
        assertEquals(80, zone4.prixTotal)
    }

    @Test fun sameZoneAlwaysCostsItsOwnZoneRatePerPassengerInTerres() {
        val zone2 = price("Saint-Éliph", "Fontaine-Simon", false, 2)
        assertEquals(2, zone2.zone)
        assertEquals(12, zone2.prixTotal)

        val zone3 = price("Manou", "Montireau", false, 3)
        assertEquals(3, zone3.zone)
        assertEquals(8, zone3.prixParPassager)
        assertEquals(24, zone3.prixTotal)

        val zone4 = price("Combres", "Frazé", false, 2)
        assertEquals(4, zone4.zone)
        assertEquals(10, zone4.prixParPassager)
        assertEquals(20, zone4.prixTotal)
    }

    @Test fun sameCommuneMayBePricedWhenPickupAndDropoffAreDifferent() {
        val result = price("Senonches", "Senonches", false, 2)
        assertEquals(1, result.zone)
        assertEquals(5, result.prixParPassager)
        assertEquals(10, result.prixTotal)
        assertEquals(20, price("La Loupe", "La Loupe", true, 2).prixTotal)
    }

    @Test fun accentHyphenAndCaseNormalisation() {
        assertEquals("sainteliph", PricingCalculator.normaliser(" SAINT - ÉLIPH "))
        assertEquals(6, price("SAINT-ELIPH", "fontaine simon", false, 1).prixTotal)
        assertEquals(10, price("boissy les perche", "SENONCHES", false, 1).prixTotal)
    }

    @Test fun differentSectorsUnknownAndOtherNeedQuote() {
        assertTrue(PricingCalculator.calculerTarif("Digny", "La Loupe", false, 2) is PricingCalculator.Resultat.SurDevis)
        assertTrue(PricingCalculator.calculerTarif("Inconnue", "Senonches", false, 1) is PricingCalculator.Resultat.SurDevis)
        assertTrue(PricingCalculator.calculerTarif("Autre commune", "Senonches", false, 1) is PricingCalculator.Resultat.SurDevis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesZeroPassengers() {
        PricingCalculator.calculerTarif("Digny", "Senonches", false, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesFivePassengers() {
        PricingCalculator.calculerTarif("Digny", "Senonches", false, 5)
    }

    @Test fun coversEveryListedCommuneExactlyOnce() {
        assertEquals(22, PricingCalculator.localites.filter { it.secteur == PricingCalculator.Secteur.TERRES_DE_PERCHE }.size)
        assertEquals(14, PricingCalculator.localites.filter { it.secteur == PricingCalculator.Secteur.FORETS_DU_PERCHE }.size)
        assertEquals(PricingCalculator.localites.size,
            PricingCalculator.localites.map { PricingCalculator.normaliser(it.commune) }.distinct().size)
    }
}
