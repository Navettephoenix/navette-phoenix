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

    @Test fun rejectsMissingAddressesAndIncompleteReturn() {
        assertNotNull(booking.copy(departure = "").validateTrip(now))
        assertNotNull(booking.copy(returnDate = booking.date).validateTrip(now))
        assertNotNull(booking.copy(returnTime = "18:00").validateTrip(now))
    }

    @Test fun rejectsInvalidDatesAndDstGapsAndOverlapsInParis() {
        val beforeDstChange = LocalDateTime.of(2027, 1, 1, 10, 0)
        assertNotNull(booking.copy(date = "2027-02-30").validateTrip(beforeDstChange))
        assertNotNull(booking.copy(date = "2027-03-28", time = "02:20").validateTrip(beforeDstChange))
        assertNotNull(booking.copy(date = "2027-10-31", time = "02:20").validateTrip(beforeDstChange))
    }

    @Test fun allowsEmptyOptionalFieldsAndRejectsOversizedText() {
        assertNull(booking.validateContact())
        assertNotNull(booking.copy(needs = "a".repeat(301)).validateContact())
        assertNotNull(booking.copy(notes = "a".repeat(501)).validateContact())
    }

    @Test fun pointsToTheMissingReturnTimeAndRejectsAMailingListAsContact() {
        assertEquals("returnTime", booking.copy(returnDate = booking.date)
            .validateTrip(now, returnRequested = true)?.field)
        assertEquals("email", booking.copy(email = "client,autre@example.test").validateContact()?.field)
    }
}
