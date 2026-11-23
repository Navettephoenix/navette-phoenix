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
        "0600000000", "test@example.test", "", "", true,
        departureCommune = "Senonches", arrivalCommune = "La Loupe")

    @Test fun validTripAndContact() {
        assertNull(booking.validateTrip(now))
        assertNull(booking.validateContact())
    }

    @Test fun rejectsPastDuplicateAndInvalidPassengerCount() {
        assertNotNull(booking.copy(date = "2027-04-14").validateTrip(now))
        assertNotNull(booking.copy(arrival = "SENONCHES").validateTrip(now))
        assertNotNull(booking.copy(passengers = 5).validateTrip(now))
    }

    @Test fun sameCommuneWithDifferentPickupAndDropoffIsAValidTrip() {
        val local = booking.copy(
            departure = "Senonches · Mairie",
            arrival = "Senonches · Gare routière",
            departureCommune = "Senonches",
            arrivalCommune = "Senonches",
            departureDetail = "Mairie",
            arrivalDetail = "Gare routière",
            passengers = 3
        )
        assertNull(local.validateTrip(now))
        val fare = local.pricing as PricingCalculator.Resultat.Tarif
        assertEquals(1, fare.zone)
        assertEquals(5, fare.prixParPassager)
        assertEquals(15, fare.prixTotal)
        assertEquals(15, local.payload("test-id")["priceTotal"])
        // A journey from one point to itself is not a valid booking.
        assertNotNull(local.copy(arrival = local.departure).validateTrip(now))
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
        assertNotNull(booking.copy(departureCommune = "").validateTrip(now))
        assertNotNull(booking.copy(arrivalCommune = "").validateTrip(now))
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

    @Test fun transmissionIsNotConfirmation() {
        assertEquals("Enregistrée · En attente de transmission", statusLabel("PENDING"))
        assertEquals("Transmise au service de réservation · En attente de réponse", statusLabel("SENT"))
        assertEquals("Trajet confirmé par Phoenix LTS", statusLabel("CONFIRMED"))
        assertEquals("Demande refusée", statusLabel("DECLINED"))
        assertEquals(Palette.sun, statusTone("SENT"))
        assertEquals(Palette.error, statusTone("INVALID"))
        assertEquals(0xFF17623B.toInt(), statusTone("CONFIRMED"))
    }

    @Test fun shortHistoryStatusDoesNotConfuseTransmissionWithConfirmation() {
        assertEquals("À transmettre", statusShortLabel("PENDING"))
        assertEquals("En attente de confirmation", statusShortLabel("SENT"))
        assertEquals("En attente de confirmation", statusShortLabel("RECEIVED"))
        assertEquals("Réservation confirmée", statusShortLabel("CONFIRMED"))
        assertEquals("Envoi impossible", statusShortLabel("INVALID"))
        assertEquals("Statut à vérifier", statusShortLabel(null))
    }

    @Test fun unknownStatusNeverClaimsConfirmation() {
        assertEquals("Statut à vérifier auprès de Phoenix LTS", statusLabel("UNKNOWN"))
        assertEquals(Palette.muted, statusTone(null))
    }

    @Test fun pointsToTheMissingReturnTimeAndRejectsAMailingListAsContact() {
        assertEquals("returnTime", booking.copy(returnDate = booking.date)
            .validateTrip(now, returnRequested = true)?.field)
        assertEquals("email", booking.copy(email = "client,autre@example.test").validateContact()?.field)
    }
}
