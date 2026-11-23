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
                val card = cards.panel(title)
                if (!entry.date.isNullOrBlank()) {
                    card.label(listOfNotNull(Booking.displayDate(entry.date), entry.time?.takeIf { it.isNotBlank() })
                        .joinToString(" à "), 16f, bold = true)
                    card.space(5)
                }
                if (!entry.departure.isNullOrBlank()) card.label("Départ · ${entry.departure}", 15f)
                if (!entry.arrival.isNullOrBlank()) card.label("Arrivée · ${entry.arrival}", 15f)
                card.space(7)
                if (entry.pricingType == "FIXED" && entry.pricingZone != null &&
                    entry.pricePerPassenger != null && entry.passengers != null && entry.priceTotal != null) {
                    card.label("Zone ${entry.pricingZone} · ${entry.pricePerPassenger} € par passager · ${entry.passengers} passager(s)", 14f)
                    card.label("Tarif estimé : ${entry.priceTotal} €", 18f, Palette.sun, bold = true)
                } else if (entry.pricingType == "QUOTE") {
                    card.label("Tarif sur devis", 16f, Palette.sun, bold = true)
                }
                card.space(6)
                card.label(statusLabel(entry.status), 15f, Palette.sun, bold = true)
                card.label(entry.reference, 14f, Palette.muted).setTextIsSelectable(true)
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
