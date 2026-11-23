package com.phoenix.navette28

import org.junit.Assert.assertEquals
import org.junit.Test

class TariffsTest {
    @Test fun officialBookingContactsAreDefined() {
        assertEquals("0685704336", Tariffs.INFORMATION_PHONE)
        assertEquals("0652758286", Tariffs.RESERVATION_PHONE)
        assertEquals("caro.resanavettephoenix@gmail.com", Tariffs.RESERVATION_EMAIL)
    }

    @Test fun zonePricesAreCalculatedPerPassengerNotByDistance() {
        val price = PricingCalculator.calculerTarif("Digny", "Senonches", true, 3)
            as PricingCalculator.Resultat.Tarif
        assertEquals(6, price.prixParPassager)
        assertEquals(18, price.prixTotalTrajet)
        assertEquals(36, price.prixTotal)
    }
}
