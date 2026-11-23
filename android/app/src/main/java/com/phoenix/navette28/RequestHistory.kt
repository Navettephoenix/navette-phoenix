package com.phoenix.navette28

import android.view.View
import android.widget.LinearLayout
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

internal data class RequestSummary(
    val reference: String, val status: String?, val service: String? = null,
    val departure: String? = null, val arrival: String? = null, val date: String? = null, val time: String? = null,
    val pricingType: String? = null, val pricingSector: String? = null,
    val pricingZone: Int? = null, val passengers: Int? = null,
    val pricePerPassenger: Int? = null, val priceTotal: Int? = null,
)

internal fun statusLabel(status: String?): String = when (status) {
    "PENDING" -> "Enregistrée · En attente de transmission"
    "SENDING" -> "Transmission en cours de vérification"
    "SENT" -> "Transmise au service de réservation · En attente de réponse"
    "INVALID" -> "Transmission impossible · Contactez Phoenix LTS"
    "RECEIVED" -> "Demande reçue · En attente de réponse"
    "CONFIRMED" -> "Trajet confirmé par Phoenix LTS"
    "DECLINED" -> "Demande refusée"
    "CANCELLED" -> "Demande annulée"
    else -> "Statut à vérifier auprès de Phoenix LTS"
}

/** Libellé court pour la lecture rapide des cartes ; le détail reste affiché dessous. */
internal fun statusShortLabel(status: String?): String = when (status) {
    "PENDING" -> "À transmettre"
    "SENDING" -> "Transmission en cours"
    "SENT", "RECEIVED" -> "En attente de confirmation"
    "CONFIRMED" -> "Réservation confirmée"
    "DECLINED" -> "Demande refusée"
    "CANCELLED" -> "Demande annulée"
    "INVALID" -> "Envoi impossible"
    else -> "Statut à vérifier"
}

/** Distingue un accusé de transmission d'une réservation réellement confirmée. */
internal fun statusTone(status: String?): Int = when (status) {
    "CONFIRMED" -> 0xFF17623B.toInt()
    "DECLINED", "CANCELLED", "INVALID" -> Palette.error
    "PENDING", "SENDING", "SENT", "RECEIVED" -> Palette.sun
    else -> Palette.muted
}

internal fun LinearLayout.requestHistory(latest: String?, load: (() -> Task<List<RequestSummary>>)?) {
    val state = label("", 16f, Palette.muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    val reference = latest?.let {
        label(context.getString(R.string.latest_reference, it), 15f).apply { setTextIsSelectable(true) }
    }
    if (load == null) {
        state.setText(R.string.history_unavailable)
        return
    }

    val cards = context.column()
    lateinit var reload: MaterialButton
    fun refresh() {
        reload.isEnabled = false
        state.setText(R.string.history_loading)
        val operation = try {
            Tasks.withTimeout(load(), 25, TimeUnit.SECONDS)
        } catch (error: Exception) {
            Tasks.forException<List<RequestSummary>>(error)
        }
        operation.addOnCompleteListener { task ->
            reload.isEnabled = true
            if (!task.isSuccessful) {
                state.setText(R.string.history_retry)
                return@addOnCompleteListener
            }
            cards.removeAllViews()
            val entries = task.result
            state.setText(if (entries.isEmpty()) R.string.history_empty else R.string.history_recent)
            reference?.visibility = if (entries.any { it.reference == latest }) View.GONE else View.VISIBLE
            for (entry in entries) {
                val title = when (entry.service) {
                    "NAVETTE" -> "Demande de navette"
                    "DEVIS" -> "Demande de devis"
                    else -> "Demande"
                }
                val card = cards.panel()
                card.label(title, 17f, Palette.ink, bold = true)
                card.space(8)
                val from = entry.departure?.trim().orEmpty()
                val to = entry.arrival?.trim().orEmpty()
                if (from.isNotEmpty() || to.isNotEmpty()) {
                    val shortFrom = from.substringBefore(" · ").ifBlank { from }
                    val shortTo = to.substringBefore(" · ").ifBlank { to }
                    card.label("$shortFrom  →  $shortTo", 18f, Palette.ink, bold = true)
                    card.space(6)
                }
                if (!entry.date.isNullOrBlank()) {
                    card.label(listOfNotNull(Booking.displayDate(entry.date), entry.time?.takeIf { it.isNotBlank() })
                        .joinToString(" à "), 15f, Palette.muted)
                    card.space(8)
                }
                if (entry.pricingType == "FIXED" && entry.priceTotal != null) {
                    card.label("Tarif estimé  ·  ${entry.priceTotal} €", 20f, Palette.sun, bold = true)
                    card.space(6)
                } else if (entry.pricingType == "QUOTE") {
                    card.label("Tarif sur devis", 18f, Palette.sun, bold = true)
                    card.space(6)
                }
                card.label(statusShortLabel(entry.status), 16f, statusTone(entry.status), bold = true)
                card.space(10)

                // La fiche détaillée est consultable à la demande : la liste reste compacte.
                val details = card.context.column().apply { visibility = View.GONE }
                card.addView(details, -1, -2)
                details.label(statusLabel(entry.status), 15f, Palette.muted)
                details.space(10)
                if (from.isNotEmpty()) {
                    details.label("Départ  ·  $from", 16f, Palette.ink)
                    details.space(6)
                }
                if (to.isNotEmpty()) {
                    details.label("Arrivée  ·  $to", 16f, Palette.ink)
                    details.space(6)
                }
                if (entry.pricingType == "FIXED" && entry.pricingZone != null &&
                    entry.pricePerPassenger != null && entry.passengers != null) {
                    details.label("Zone ${entry.pricingZone} · ${entry.pricePerPassenger} € par passager · ${entry.passengers} passager(s)", 15f, Palette.muted)
                    details.space(8)
                }
                details.label("Référence  ·  ${entry.reference}", 15f, Palette.muted)
                    .setTextIsSelectable(true)
                details.space(8)
                val toggle = card.action("Voir les détails") {
                    val expanded = details.visibility != View.VISIBLE
                    details.visibility = if (expanded) View.VISIBLE else View.GONE
                }
                toggle.setOnClickListener {
                    val expanded = details.visibility != View.VISIBLE
                    details.visibility = if (expanded) View.VISIBLE else View.GONE
                    toggle.text = if (expanded) "Masquer les détails" else "Voir les détails"
                }
                cards.space(10)
            }
        }
    }
    space(8)
    reload = action("Actualiser") { refresh() }
    space(12)
    addView(cards, -1, -2)
    refresh()
}
