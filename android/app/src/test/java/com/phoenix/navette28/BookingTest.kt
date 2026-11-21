package com.phoenix.navette28

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class BookingTest {
    private val now = LocalDateTime.of(2027, 4, 15, 10, 0)
    private val booking = Booking(Service.NAVETTE, "Senonches", "Gare de La Loupe",
        "2027-04-16", "11:30", "", "", 2, 3, "Passager test",
        "0600000000", "test@example.test", "", "", true)

    @Test fun validTripAndContact() {
        assertNull(booking.validateTrip(now))
        assertNull(booking.validateContact())
    }

    @Test fun rejectsPastDuplicateAndInvalidPassengerCount() {
        assertNotNull(booking.copy(date = "2027-04-14").validateTrip(now))
        assertNotNull(booking.copy(arrival = "SENONCHES").validateTrip(now))
        assertNotNull(booking.copy(passengers = 5).validateTrip(now))
    }

    @Test fun validatesReturnAndConsent() {
        assertNull(booking.copy(returnDate = "2027-04-16", returnTime = "18:00").validateTrip(now))
        assertNotNull(booking.copy(returnDate = "2027-04-16", returnTime = "09:00").validateTrip(now))
        assertNotNull(booking.copy(consent = false).validateContact())
        assertNotNull(booking.copy(email = "invalide").validateContact())
    }

    @Test fun mapsAllReservationFields() {
        val payload = booking.payload("test-id")
        assertEquals("test-id", payload["requestId"])
        assertEquals(2, payload["passengers"])
        assertEquals(3, payload["luggage"])
        assertEquals("NAVETTE", payload["service"])
    }
}
