package com.phoenix.navette28

import android.os.Handler
import android.os.Looper
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Suggestions d'adresses de la Géoplateforme IGN (BAN et points d'intérêt).
 *
 * Recherche déclenchée par l'utilisateur, jamais en arrière-plan à chaque touche.
 * Les adresses sont filtrées pour respecter la commune sélectionnée et la zone tarifaire.
 * Saisie manuelle maintenue lorsque le lieu n'est pas répertorié.
 */
internal object AddressLookup {
    private const val SERVICE = "https://data.geopf.fr/geocodage/search"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun search(query: String, commune: String, callback: (Result<List<String>>) -> Unit) {
        val input = query.trim().take(120)
        if (input.length < 3) {
            callback(Result.failure(IllegalArgumentException("Saisissez au moins trois caractères.")))
            return
        }
        val town = commune.takeIf { it.isNotBlank() && it != PricingCalculator.AUTRE_COMMUNE }
        io.execute {
            val result = runCatching {
                val address = lookup(input, town, "address")
                if (address.isNotEmpty()) address else lookup(input, town, "poi")
            }
            main.post { callback(result) }
        }
    }

    private fun lookup(query: String, commune: String?, index: String): List<String> {
        val text = if (commune == null) query else "$query $commune"
        val uri = Uri.parse(SERVICE).buildUpon()
            .appendQueryParameter("q", text)
            .appendQueryParameter("index", index)
            .appendQueryParameter("autocomplete", "1")
            .appendQueryParameter("limit", "8")
            .build()
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = false
        }
        try {
            check(connection.responseCode == 200) { "Service d'adresses indisponible" }
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseSuggestions(json, commune)
        } finally {
            connection.disconnect()
        }
    }

    /** Extrait uniquement les résultats de la commune demandée, sans inventer d'adresse. */
    fun parseSuggestions(json: String, commune: String?): List<String> {
        val results = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return (0 until minOf(results.length(), 12)).mapNotNull { index ->
            val properties = results.optJSONObject(index)?.optJSONObject("properties")
                ?: return@mapNotNull null
            val label = properties.optString("label").trim()
            val city = properties.optString("city").trim()
            if (label.isBlank()) return@mapNotNull null
            if (commune != null && PricingCalculator.normaliser(city) !=
                PricingCalculator.normaliser(commune)) return@mapNotNull null
            label.take(150)
        }.distinct().take(6)
    }
}
