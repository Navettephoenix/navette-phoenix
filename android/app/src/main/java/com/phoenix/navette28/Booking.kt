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
    fun validateTrip(now: LocalDateTime = LocalDateTime.now(PARIS)): String? {
        if (departure.length !in 3..250 || arrival.length !in 3..250) {
            return "Indiquez une adresse de départ et une adresse d’arrivée."
        }
        if (departure.equals(arrival, ignoreCase = true)) return "Choisissez deux adresses différentes."
        val pickup = parse(date, time)
        if (pickup == null || !pickup.isAfter(now) ||
            PARIS.rules.getValidOffsets(pickup).size != 1) {
            return "Choisissez une date et une heure de départ valides, dans le futur."
        }
        if (returnDate.isNotEmpty() || returnTime.isNotEmpty()) {
            val back = parse(returnDate, returnTime)
            if (back == null || !back.isAfter(pickup) ||
                PARIS.rules.getValidOffsets(back).size != 1) {
                return "Le créneau de retour doit être valide et postérieur à l’aller."
            }
        }
        if (passengers !in 1..4) return "Indiquez entre 1 et 4 passagers."
        if (luggage !in 0..16) return "Indiquez entre 0 et 16 bagages."
        return null
    }

    fun validateContact(): String? {
        if (name.length !in 2..100) return "Saisissez votre nom et votre prénom."
        if (!phone.matches(Regex("[+0-9 ().-]{7,24}")) || phone.count(Char::isDigit) !in 8..15) {
            return "Vérifiez le numéro de téléphone."
        }
        if (email.length !in 5..120 || !email.matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) {
            return "Vérifiez l’adresse e-mail."
        }
        if (needs.length > 300 || notes.length > 500) return "Raccourcissez les informations facultatives."
        if (!consent) return "Lisez les informations sur vos données, puis cochez la case."
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
