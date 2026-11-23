package com.phoenix.navette28

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.button.MaterialButton
import java.util.Date
import java.text.DateFormat
import java.util.Locale
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
    val lastUpdated = label("", 14f, Palette.muted).apply { visibility = View.GONE }
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
            lastUpdated.text = "Actualisé à " + DateFormat.getTimeInstance(DateFormat.SHORT, Locale.FRANCE).format(Date())
            lastUpdated.visibility = View.VISIBLE
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
                // Statut clairement identifiable, sans confondre transmission et confirmation.
                val statusBox = details.context.column().apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                    background = GradientDrawable().apply {
                        setColor(when (entry.status) {
                            "CONFIRMED" -> 0xFFE7F4EB.toInt()
                            "DECLINED", "CANCELLED", "INVALID" -> 0xFFFBE9E6.toInt()
                            else -> 0xFFFFF1E9.toInt()
                        })
                        cornerRadius = context.dp(12).toFloat()
                    }
                }
                statusBox.label("STATUT DE LA DEMANDE", 14f, Palette.muted, bold = true)
                statusBox.space(5)
                statusBox.label(statusLabel(entry.status), 16f, statusTone(entry.status), bold = true)
                details.addView(statusBox, -1, -2)
                details.space(16)

                fun detailLine(heading: String, value: String) {
                    details.label(heading, 14f, Palette.muted, bold = true)
                    details.space(4)
                    details.label(value, 16f, Palette.ink)
                    details.space(12)
                }

                if (from.isNotEmpty() || to.isNotEmpty()) {
                    details.label("TRAJET", 14f, Palette.sun, bold = true)
                    details.space(10)
                    if (from.isNotEmpty()) detailLine("Départ", from)
                    if (to.isNotEmpty()) detailLine("Arrivée", to)
                }
                if (entry.pricingType == "FIXED" && entry.pricingZone != null &&
                    entry.pricePerPassenger != null && entry.passengers != null) {
                    details.label("TARIFICATION", 14f, Palette.sun, bold = true)
                    details.space(8)
                    detailLine("Détail du tarif", "Zone ${entry.pricingZone} · ${entry.pricePerPassenger} € par passager · ${entry.passengers} passager(s)")
                }
                details.label("RÉFÉRENCE DE LA DEMANDE", 14f, Palette.muted, bold = true)
                details.space(5)
                details.label(entry.reference, 16f, Palette.ink, bold = true)
                    .setTextIsSelectable(true)
                details.space(8)
                val copyButton = details.action("Copier la référence") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Référence Navette Phoenix", entry.reference))
                    android.widget.Toast.makeText(context, "Référence copiée", android.widget.Toast.LENGTH_SHORT).show()
                }
                copyButton.contentDescription = "Copier la référence ${entry.reference}"
                details.space(14)
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
