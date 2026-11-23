package com.phoenix.navette28

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TariffsTest {
    @Test fun pricesFollowAllFourZonesAndTheirBoundaries() {
        assertEquals(5, Tariffs.quote(0.0)?.totalEuros)
        assertEquals(5, Tariffs.quote(3.0)?.totalEuros)
        assertEquals(6, Tariffs.quote(3.01)?.totalEuros)
        assertEquals(6, Tariffs.quote(8.0)?.totalEuros)
        assertEquals(8, Tariffs.quote(8.01)?.totalEuros)
        assertEquals(8, Tariffs.quote(13.0)?.totalEuros)
        assertEquals(10, Tariffs.quote(13.01)?.totalEuros)
        assertEquals(10, Tariffs.quote(20.0)?.totalEuros)
    }

    @Test fun returnIsTwoTripsAndOutOfZoneRequiresAQuote() {
        val roundTrip = Tariffs.quote(7.2, roundTrip = true)!!
        assertEquals(2, roundTrip.zone)
        assertEquals(6, roundTrip.oneWayEuros)
        assertEquals(12, roundTrip.totalEuros)
        assertEquals(2, roundTrip.trips)
        assertNull(Tariffs.quote(20.01))
        assertNull(Tariffs.quote(Double.NaN))
        assertNull(Tariffs.quote(Double.POSITIVE_INFINITY))
        assertNull(Tariffs.quote(-1.0))
    }

    @Test fun officialBookingContactsAreDefined() {
        assertEquals("0685704336", Tariffs.INFORMATION_PHONE)
        assertEquals("0652758286", Tariffs.RESERVATION_PHONE)
        assertEquals("caro.resanavettephoenix@gmail.com", Tariffs.RESERVATION_EMAIL)
    }
}
