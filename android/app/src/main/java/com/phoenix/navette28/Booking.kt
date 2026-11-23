package com.phoenix.navette28

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Service(val label: String) {
    NAVETTE("Réserver une navette"), DEVIS("Demander un devis")
}

data class BookingIssue(val field: String, val message: String)

data class Booking(
    val service: Service,
    val departure: String,
    val arrival: String,
    val date: String,
    val time: String,
    val returnDate: String,
    val returnTime: String,
    val passengers: Int,
    val luggage: Int,
    val name: String,
    val phone: String,
    val email: String,
    val needs: String,
    val notes: String,
    val consent: Boolean,
) {
    fun validateAddresses(): BookingIssue? {
        if (departure.length !in 3..250) return BookingIssue("departure", "Indiquez votre adresse de départ.")
        if (arrival.length !in 3..250) return BookingIssue("arrival", "Indiquez votre adresse d’arrivée.")
        if (departure.equals(arrival, ignoreCase = true)) {
            return BookingIssue("arrival", "Choisissez une arrivée différente du départ.")
        }
        return null
    }

    fun validateTrip(
        now: LocalDateTime = LocalDateTime.now(PARIS),
        returnRequested: Boolean = returnDate.isNotEmpty() || returnTime.isNotEmpty(),
    ): BookingIssue? {
        validateAddresses()?.let { return it }
        if (date.isBlank()) return BookingIssue("date", "Choisissez votre date de départ.")
        if (time.isBlank()) return BookingIssue("time", "Choisissez votre heure de départ.")
        val pickup = parse(date, time)
        if (pickup == null || pickup.toLocalDate().isBefore(now.toLocalDate())) {
            return BookingIssue("date", "Choisissez une date de départ valide, aujourd’hui ou plus tard.")
        }
        if (!pickup.isAfter(now) || PARIS.rules.getValidOffsets(pickup).size != 1) {
            return BookingIssue("time", "Choisissez une heure de départ valide et à venir (heure de Paris).")
        }
        if (returnRequested) {
            if (returnDate.isBlank()) return BookingIssue("returnDate", "Choisissez votre date de retour.")
            if (returnTime.isBlank()) return BookingIssue("returnTime", "Choisissez votre heure de retour.")
            val back = parse(returnDate, returnTime)
            if (back == null || back.toLocalDate().isBefore(pickup.toLocalDate())) {
                return BookingIssue("returnDate", "Choisissez un retour le jour de l’aller ou plus tard.")
            }
            if (!back.isAfter(pickup) || PARIS.rules.getValidOffsets(back).size != 1) {
                return BookingIssue("returnTime", "Choisissez une heure de retour valide, après l’aller.")
            }
        }
        if (passengers !in 1..4) return BookingIssue("passengers", "Indiquez entre 1 et 4 passagers.")
        if (luggage !in 0..16) return BookingIssue("luggage", "Indiquez entre 0 et 16 bagages.")
        return null
    }

    fun validateContact(): BookingIssue? {
        if (name.length !in 2..100) return BookingIssue("name", "Saisissez votre nom et votre prénom.")
        if (!phone.matches(Regex("[+0-9 ().-]{7,24}")) || phone.count(Char::isDigit) !in 8..15) {
            return BookingIssue("phone", "Vérifiez votre numéro de téléphone, indicatif compris si nécessaire.")
        }
        if (email.length !in 5..120 || !email.matches(Regex("[^@\\s,;<>]+@[^@\\s,;<>]+\\.[^@\\s,;<>]+"))) {
            return BookingIssue("email", "Saisissez une adresse e-mail, par exemple nom@exemple.fr.")
        }
        if (needs.length > 300) return BookingIssue("needs", "Limitez les besoins particuliers à 300 caractères.")
        if (notes.length > 500) return BookingIssue("notes", "Limitez le commentaire à 500 caractères.")
        if (!consent) return BookingIssue("consent", "Lisez les informations sur vos données, puis cochez la case.")
        return null
    }

    fun payload(requestId: String): Map<String, Any> = mapOf(
        "requestId" to requestId,
        "service" to service.name,
        "departure" to departure, "arrival" to arrival,
        "date" to date, "time" to time,
        "returnDate" to returnDate, "returnTime" to returnTime,
        "passengers" to passengers, "luggage" to luggage,
        "name" to name, "phone" to phone, "email" to email,
        "needs" to needs, "notes" to notes, "consent" to consent,
    )

    companion object {
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
        private val readable = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRANCE)
        fun displayDate(date: String): String = runCatching {
            LocalDate.parse(date).format(readable)
        }.getOrDefault(date)

        private fun parse(date: String, time: String): LocalDateTime? = runCatching {
            LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
        }.getOrNull()
    }
}
