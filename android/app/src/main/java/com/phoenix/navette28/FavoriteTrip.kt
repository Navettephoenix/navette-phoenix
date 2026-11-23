package com.phoenix.navette28

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Mémorisation volontaire d'un seul trajet sur l'appareil.
 * Aucune donnée d'identité, date, horaire ou consentement n'est enregistrée.
 */
internal object FavoriteTrip {
    private val keys = listOf(
        "departureCommune", "departureDetail", "departure",
        "arrivalCommune", "arrivalDetail", "arrival"
    )

    fun load(prefs: SharedPreferences): Map<String, String>? {
        if (!prefs.getBoolean("saved", false)) return null
        val route = keys.associateWith { prefs.getString(it, "").orEmpty() }
        return route.takeIf {
            it["departureCommune"].orEmpty().isNotBlank() &&
                it["arrivalCommune"].orEmpty().isNotBlank() &&
                it["departure"].orEmpty().isNotBlank() &&
                it["arrival"].orEmpty().isNotBlank()
        }
    }

    fun save(prefs: SharedPreferences, values: Map<String, String>) {
        require(keysOfRoutePresent(values)) { "Le trajet doit être renseigné avant sa mémorisation." }
        prefs.edit {
            clear()
            keys.forEach { putString(it, values[it].orEmpty()) }
            putBoolean("saved", true)
        }
    }

    fun clear(prefs: SharedPreferences) = prefs.edit { clear() }

    private fun keysOfRoutePresent(values: Map<String, String>) =
        listOf("departureCommune", "arrivalCommune", "departure", "arrival")
            .all { !values[it].isNullOrBlank() }
}
