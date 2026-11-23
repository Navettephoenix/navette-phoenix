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
