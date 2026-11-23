package com.phoenix.navette28

import java.text.Normalizer
import java.util.Locale

/** Tarification forfaitaire officielle, sur la base de deux communes du même secteur. */
internal object PricingCalculator {
    const val AUTRE_COMMUNE = "Autre commune"

    enum class Secteur(val label: String) {
        FORETS_DU_PERCHE("Forêts du Perche"),
        TERRES_DE_PERCHE("Terres de Perche")
    }

    data class Localite(val commune: String, val secteur: Secteur, val zone: Int)

    sealed interface Resultat {
        data class Tarif(
            val zone: Int,
            val secteur: Secteur,
            val prixParPassager: Int,
            val prixTotalTrajet: Int,
            val prixTotal: Int
        ) : Resultat
        data object SurDevis : Resultat
    }

    // Les listes proviennent des deux grilles communiquées par Phoenix LTS.
    private fun secteur(secteur: Secteur, vararg niveaux: List<String>): List<Localite> =
        niveaux.flatMapIndexed { index, communes ->
            communes.map { Localite(it, secteur, index + 1) }
        }

    val localites: List<Localite> = secteur(
        Secteur.FORETS_DU_PERCHE,
        listOf("Senonches"),
        listOf("Digny", "La Framboisière", "La Chapelle-Fortin"),
        listOf("Jaudrais", "Lamblore", "La Puisaye", "Le Mesnil-Thomas", "Les Ressuintes"),
        listOf("Boissy-lès-Perche", "Louvilliers-lès-Perche", "La Ferté-Vidame", "La Saucelle", "Rohaire")
    ) + secteur(
        Secteur.TERRES_DE_PERCHE,
        listOf("La Loupe"),
        listOf("Meaucé", "Saint-Éliph", "Fontaine-Simon", "Vaupillon", "Saint-Maurice-Saint-Germain", "Les Corvées-les-Yys"),
        listOf("Belhomert-Guéhouville", "Champrond-en-Gâtine", "Manou", "Montireau", "Montlandon", "Saint-Victor-de-Buthon"),
        listOf("Chassant", "Combres", "Frazé", "Happonvilliers", "La Croix-du-Perche",
            "Marolles-les-Buis", "Nonvilliers-Grandhoux", "Saintigny", "Thiron-Gardais")
    )

    val communes: List<String> = localites.map(Localite::commune).sortedWith(String.CASE_INSENSITIVE_ORDER) + AUTRE_COMMUNE

    /** Supprime accents, variations d'espacement, traits d'union et casse. */
    fun normaliser(nom: String): String {
        val ascii = Normalizer.normalize(nom.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
        return ascii.replace(Regex("[^a-z0-9]"), "")
    }

    private val index = localites.associateBy { normaliser(it.commune) }

    fun commune(nom: String): Localite? = index[normaliser(nom)]

    fun calculerTarif(
        depart: String, arrivee: String, allerRetour: Boolean, passagers: Int
    ): Resultat {
        require(passagers in 1..4) { "Le nombre de passagers doit être compris entre 1 et 4." }
        val origine = commune(depart) ?: return Resultat.SurDevis
        val destination = commune(arrivee) ?: return Resultat.SurDevis
        if (origine.secteur != destination.secteur) return Resultat.SurDevis
        val zone = maxOf(origine.zone, destination.zone)
        val prix = when (zone) {
            1 -> 5
            2 -> 6
            3 -> 8
            4 -> 10
            else -> error("Zone inconnue")
        }
        val parTrajet = prix * passagers
        return Resultat.Tarif(zone, origine.secteur, prix, parTrajet,
            parTrajet * if (allerRetour) 2 else 1)
    }
}
