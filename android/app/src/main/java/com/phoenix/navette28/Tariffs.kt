package com.phoenix.navette28

/**
 * Grille officielle communiquée par Phoenix LTS pour les secteurs Forêts et Terres du Perche.
 * Les zones dépendent de la distance routière réelle. La commune seule ne suffit pas.
 */
internal object Tariffs {
    const val INFORMATION_PHONE = "0685704336"
    const val RESERVATION_PHONE = "0652758286"
    const val RESERVATION_EMAIL = "caro.resanavettephoenix@gmail.com"

    data class Zone(val number: Int, val maxKm: Double, val oneWayEuros: Int)
    data class Quote(val zone: Int, val oneWayEuros: Int, val totalEuros: Int, val trips: Int)

    val zones = listOf(
        Zone(1, 3.0, 5),
        Zone(2, 8.0, 6),
        Zone(3, 13.0, 8),
        Zone(4, 20.0, 10)
    )

    /**
     * Distance EN KM calculée pour le trajet routier, et non à vol d'oiseau.
     * Les seuils sont non superposés : 3 km = zone 1 ; 8 km = zone 2, etc.
     * Un parcours hors zone / distance inconnue doit faire l'objet d'un devis.
     */
    fun quote(distanceKm: Double, roundTrip: Boolean = false): Quote? {
        if (!distanceKm.isFinite() || distanceKm < 0.0) return null
        val zone = zones.firstOrNull { distanceKm <= it.maxKm } ?: return null
        val trips = if (roundTrip) 2 else 1
        return Quote(zone.number, zone.oneWayEuros, zone.oneWayEuros * trips, trips)
    }
}
