package com.phoenix.navette28

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID

class MainActivity : FragmentActivity() {
    private enum class Screen { HOME, TRIP, CONTACT_FORM, REVIEW, DESTINATIONS, HISTORY, CONTACT, PRIVACY }

    private val values = mutableMapOf<String, String>()
    private val gateway by lazy { FirebaseGateway(applicationContext) }
    private val storage by lazy { getSharedPreferences("reservation-references", MODE_PRIVATE) }
    private var screen = Screen.HOME
    private var service = Service.NAVETTE
    private var roundTrip = false
    private var consent = false
    private var notifications = false
    private var sending = false
    private lateinit var column: LinearLayout
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        send(booking(), it)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        state?.getBundle("form")?.let { form ->
            for (key in form.keySet()) values[key] = form.getString(key).orEmpty()
        }
        screen = Screen.entries.firstOrNull { it.name == state?.getString("screen") } ?: Screen.HOME
        service = Service.entries.firstOrNull { it.name == state?.getString("service") } ?: Service.NAVETTE
        roundTrip = state?.getBoolean("round") ?: false
        consent = state?.getBoolean("consent") ?: false
        notifications = state?.getBoolean("notifications") ?: false
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        val fromNotification = intent.getBooleanExtra("showHistory", false)
        intent.removeExtra("showHistory")
        render(if (fromNotification) Screen.HISTORY else screen)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("form", Bundle().also { form -> values.forEach { (key, value) -> form.putString(key, value) } })
        outState.putString("screen", screen.name)
        outState.putString("service", service.name)
        outState.putBoolean("round", roundTrip)
        outState.putBoolean("consent", consent)
        outState.putBoolean("notifications", notifications)
    }

    private fun render(next: Screen) {
        screen = next
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = FrameLayout(this).apply { setBackgroundColor(Palette.paper) }
        column = column()
        val width = minOf(resources.displayMetrics.widthPixels, dp(560))
        root.addView(column, FrameLayout.LayoutParams(width, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(root)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            column.setPadding(dp(19) + bars.left, dp(14) + bars.top,
                dp(19) + bars.right, dp(24) + maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        column.isFocusableInTouchMode = true
        setContentView(scroll)
        column.requestFocus()

        if (next != Screen.HOME) {
            column.action("‹  Retour") { back() }
            column.space(16)
        }
        when (next) {
            Screen.HOME -> home()
            Screen.TRIP -> trip()
            Screen.CONTACT_FORM -> details()
            Screen.REVIEW -> review()
            Screen.DESTINATIONS -> destinations()
            Screen.HISTORY -> history()
            Screen.CONTACT -> contact()
            Screen.PRIVACY -> privacy()
        }
        if (ValueAnimator.areAnimatorsEnabled()) {
            root.alpha = .65f
            root.animate().alpha(1f).setDuration(140).start()
        }
    }

    private fun back() {
        if (sending) return
        when (screen) {
            Screen.HOME -> finish()
            Screen.CONTACT_FORM -> render(Screen.TRIP)
            Screen.REVIEW -> render(Screen.CONTACT_FORM)
            Screen.PRIVACY -> render(Screen.CONTACT)
            else -> render(Screen.HOME)
        }
    }

    private fun home() {
        val menuButton = column.action("Menu") {}
        menuButton.setOnClickListener {
            PopupMenu(this, menuButton).apply {
                menu.add("Mes demandes")
                menu.add("Contact et informations")
                menu.add("Confidentialité")
                setOnMenuItemClickListener { item ->
                    render(when (item.title) {
                        "Mes demandes" -> Screen.HISTORY
                        "Confidentialité" -> Screen.PRIVACY
                        else -> Screen.CONTACT
                    })
                    true
                }
            }.show()
        }
        column.space(12)
        val header = column().apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF372821.toInt(), 0xFF251E1B.toInt(), 0xFF452519.toInt()))
                .apply { cornerRadius = dp(24).toFloat() }
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.phoenix_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = surface(Palette.sun, 16)
            clipToOutline = true
        }
        header.addView(logo, dp(65), dp(65))
        header.space(15)
        header.label("Navette Phoenix 28", 32f, Color.WHITE, true)
        header.space(6)
        header.label("Transport à la demande dans le Perche et transferts privés", 16f, 0xFFF9D9C6.toInt())
        column.addView(header, -1, -2)
        column.space(16)
        column.action("Réserver une navette", true) { start(Service.NAVETTE) }
            .setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_navette, 0, 0, 0)
        column.space(9)
        column.action("Demander un devis") { start(Service.DEVIS) }
            .setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_devis, 0, 0, 0)
        column.space(9)
        column.action("Nos destinations") { render(Screen.DESTINATIONS) }
            .setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_destinations, 0, 0, 0)
        column.space(16)
        column.label("1 à 4 passagers · Service Phoenix LTS", 14f, Palette.muted)
    }

    private fun start(next: Service) {
        service = next
        render(Screen.TRIP)
    }

    private fun trip() {
        column.label(service.label, 27f, bold = true)
        column.space(5)
        column.label("Étape 1 sur 3 · Trajet", 14f, Palette.muted)
        column.space(12)
        val address = column.panel("Départ et arrivée")
        addressField(address, "Départ", "departure")
        addressField(address, "Arrivée", "arrival")
        address.action("Voir l’itinéraire") {
            val from = value("departure")
            val to = value("arrival")
            if (from.length < 3 || to.length < 3) showMessage("Saisissez le départ et l’arrivée.")
            else startActivity(Intent(this, RouteActivity::class.java)
                .putExtra("from", from).putExtra("to", to))
        }
        column.space(10)
        val schedule = column.panel("Date et passagers")
        dateControl(schedule, "Date du départ", "date")
        timeControl(schedule, "Heure de prise en charge", "time")
        val round = CheckBox(this).apply {
            text = "Aller-retour"
            isChecked = roundTrip
            minHeight = dp(48)
            setOnCheckedChangeListener { _, checked ->
                roundTrip = checked
                render(Screen.TRIP)
            }
        }
        schedule.addView(round)
        if (roundTrip) {
            dateControl(schedule, "Date du retour", "returnDate")
            timeControl(schedule, "Heure du retour", "returnTime")
        }
        bind(schedule, "Passagers (1 à 4)", "passengers", InputType.TYPE_CLASS_NUMBER, 1, initial = "1")
        bind(schedule, "Bagages (0 à 16)", "luggage", InputType.TYPE_CLASS_NUMBER, 2, initial = "0")
        column.space(14)
        column.action("Continuer", true) {
            tripError(booking())?.let(::showMessage) ?: render(Screen.CONTACT_FORM)
        }
    }

    private fun addressField(parent: LinearLayout, title: String, key: String) {
        val field = bind(parent, title, key)
        if (!gateway.available) return
        val search = parent.action("Rechercher l’adresse $title".lowercase(Locale.FRANCE).replaceFirstChar { it.uppercase() }) {
            val query = field.text.toString().trim()
            if (query.length < 3) {
                field.error = "Saisissez au moins trois caractères."
                return@action
            }
            gateway.call("suggestAddresses", mapOf("query" to query)).addOnCompleteListener(this) { task ->
                if (screen != Screen.TRIP || !field.isAttachedToWindow) return@addOnCompleteListener
                if (!task.isSuccessful) {
                    showMessage("La recherche est indisponible. Vous pouvez compléter l’adresse à la main.")
                    return@addOnCompleteListener
                }
                val items = ((task.result as? Map<*, *>)?.get("addresses") as? List<*>)
                    .orEmpty().filterIsInstance<String>().toTypedArray()
                if (items.isEmpty()) showMessage("Aucune adresse trouvée. Ajoutez la commune ou le code postal.")
                else AlertDialog.Builder(this).setTitle("Choisir une adresse").setItems(items) { _, which ->
                    field.setText(items[which])
                }.setNegativeButton("Annuler", null).show()
            }
        }
        search.textSize = 14f
        parent.space(9)
    }

    private fun details() {
        column.label("Vos coordonnées", 27f, bold = true)
        column.space(5)
        column.label("Étape 2 sur 3 · Contact", 14f, Palette.muted)
        column.space(12)
        val person = column.panel("Pour vous recontacter")
        bind(person, "Nom et prénom", "name",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, 100)
        bind(person, "Téléphone", "phone", InputType.TYPE_CLASS_PHONE, 24)
        bind(person, "E-mail", "email",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 120)
        column.space(10)
        val extra = column.panel("Informations utiles au trajet")
        bind(extra, "Besoins particuliers (facultatif)", "needs", limit = 300, multiline = true)
        bind(extra, "Commentaires (facultatif)", "notes", limit = 500, multiline = true)
        extra.label("Indiquez seulement les détails nécessaires à votre prise en charge.", 13f, Palette.muted)
        column.space(12)
        column.addView(CheckBox(this).apply {
            text = "J’autorise Phoenix LTS à utiliser ces informations pour traiter ma demande."
            isChecked = consent
            minHeight = dp(52)
            setOnCheckedChangeListener { _, checked -> consent = checked }
        })
        column.space(10)
        column.action("Vérifier ma demande", true) {
            booking().validateContact()?.let(::showMessage) ?: render(Screen.REVIEW)
        }
    }

    private fun review() {
        val current = booking()
        column.label("Vérifier votre demande", 27f, bold = true)
        column.space(5)
        column.label("Étape 3 sur 3 · Récapitulatif", 14f, Palette.muted)
        column.space(12)
        val trip = column.panel("Votre trajet")
        trip.label(current.departure, bold = true)
        trip.label("↓", 18f, Palette.sun)
        trip.label(current.arrival, bold = true)
        trip.space(8)
        trip.label("Aller · ${Booking.displayDate(current.date)} à ${current.time}", 15f)
        if (roundTrip) trip.label("Retour · ${Booking.displayDate(current.returnDate)} à ${current.returnTime}", 15f)
        trip.label("${current.passengers} passager(s) · ${current.luggage} bagage(s)", 15f)
        trip.space(8)
        trip.label(if (service == Service.DEVIS) "Tarif communiqué sur devis."
            else "Phoenix LTS confirmera le prix et la disponibilité.", 14f, Palette.muted)
        column.space(10)
        val details = column.panel("Contact")
        details.label(current.name)
        details.label(current.phone, 15f)
        details.label(current.email, 15f)
        if (current.needs.isNotEmpty()) details.label("Besoins · ${current.needs}", 15f)
        if (current.notes.isNotEmpty()) details.label("Notes · ${current.notes}", 15f)
        column.space(12)
        if (gateway.available) {
            column.addView(CheckBox(this).apply {
                text = "Recevoir une notification quand le statut change"
                isChecked = notifications
                minHeight = dp(48)
                setOnCheckedChangeListener { _, checked -> notifications = checked }
            })
        } else {
            column.label("L’envoi est indisponible tant que Firebase n’est pas configuré.", 15f, Palette.sun)
            column.space(8)
        }
        column.action("Envoyer ma demande", true) {
            if (!gateway.available) {
                showMessage("Le service de réservation doit être configuré avant l’envoi.")
                return@action
            }
            if (notifications && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else send(current, notifications)
        }
    }

    private fun send(current: Booking, notify: Boolean) {
        if (sending) return
        (tripError(current) ?: current.validateContact())?.let {
            showMessage(it)
            return
        }
        sending = true
        val fingerprint = org.json.JSONObject(current.payload("")).toString()
        val key = "request." + MessageDigest.getInstance("SHA-256")
            .digest(fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
        val requestId = storage.getString(key, null) ?: UUID.randomUUID().toString()
        if (!storage.edit().putString(key, requestId).commit()) {
            sending = false
            showMessage("Le téléphone ne peut pas enregistrer la référence d’envoi. Réessayez après avoir libéré de l’espace.")
            return
        }
        val progress = AlertDialog.Builder(this).setTitle("Envoi de la demande")
            .setMessage("Nous attendons la réponse du serveur.").setCancelable(false).show()
        val token = if (notify) FirebaseMessaging.getInstance().token.continueWith {
            if (it.isSuccessful) it.result else ""
        } else Tasks.forResult("")
        token.onSuccessTask { pushToken ->
            gateway.call("createReservation", current.payload(requestId) + ("pushToken" to pushToken))
        }.addOnCompleteListener { task ->
            sending = false
            if (isDestroyed || isFinishing) return@addOnCompleteListener
            progress.dismiss()
            val reply = if (task.isSuccessful) task.result as? Map<*, *> else null
            val reference = reply?.get("reference") as? String
            if (reference.isNullOrBlank()) {
                showMessage(errorMessage(task.exception))
                return@addOnCompleteListener
            }
            storage.edit().putString("latest", reference).apply()
            values.clear()
            roundTrip = false
            consent = false
            notifications = false
            render(Screen.HISTORY)
            AlertDialog.Builder(this).setTitle("Demande enregistrée")
                .setMessage("Référence ${reference}. Phoenix LTS doit encore confirmer la disponibilité.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun history() {
        column.label("Mes demandes", 28f, bold = true)
        column.space(12)
        val panel = column.panel()
        val latest = storage.getString("latest", null)
        val status = panel.label(if (latest != null) "Dernière référence enregistrée · $latest" else "Aucune référence enregistrée sur cet appareil.")
        if (!gateway.available) return
        panel.space(9)
        gateway.call("listReservations", emptyMap()).addOnCompleteListener(this) { task ->
            if (screen != Screen.HISTORY || !panel.isAttachedToWindow) return@addOnCompleteListener
            if (!task.isSuccessful) {
                panel.label("L’actualisation est momentanément indisponible.", 14f, Palette.muted)
                return@addOnCompleteListener
            }
            val rows = ((task.result as? Map<*, *>)?.get("reservations") as? List<*>).orEmpty()
            status.text = if (rows.isEmpty()) "Aucune demande envoyée depuis cet appareil." else "Dernières demandes"
            for (row in rows.filterIsInstance<Map<*, *>>()) {
                val label = when (row["status"]) {
                    "CONFIRMED" -> "Trajet confirmé par Phoenix LTS"
                    "DECLINED" -> "Demande refusée"
                    "CANCELLED" -> "Demande annulée"
                    else -> "Demande reçue · en attente"
                }
                panel.space(10)
                panel.label("${row["reference"]} · $label", 15f, bold = true)
            }
        }
    }

    private fun destinations() {
        column.label("Nos destinations", 28f, bold = true)
        column.space(10)
        val local = column.panel("Navette dans le Perche")
        val communes = assets.open("communes.csv").bufferedReader().useLines { lines ->
            lines.drop(1).map { it.split(';') }.filter { it.size >= 3 }.toList()
        }
        for (sector in listOf("Forêts du Perche", "Terres de Perche")) {
            val places = communes.filter { it[0] == sector }.map { it[2] }.toTypedArray()
            local.action("$sector · ${places.size} communes") {
                AlertDialog.Builder(this).setTitle(sector).setItems(places) { _, which ->
                    values["departure"] = places[which]
                    start(Service.NAVETTE)
                }.setNegativeButton("Fermer", null).show()
            }
            local.space(7)
        }
        local.label("Gare de La Loupe\nGare de Verneuil-sur-Avre", 15f, Palette.muted)
        column.space(12)
        val privateTrip = column.panel("Transferts privés et événements")
        for (destination in listOf("Paris-Orly", "Paris-Charles-de-Gaulle", "Gares parisiennes", "Longue distance en France", "Événements et mariages")) {
            privateTrip.label(destination, 16f, bold = true)
            privateTrip.space(4)
        }
        privateTrip.space(10)
        privateTrip.action("Demander un devis", true) { start(Service.DEVIS) }
    }

    private fun contact() {
        column.label("Contact et informations", 27f, bold = true)
        column.space(12)
        val panel = column.panel("Phoenix LTS")
        panel.label("Transport de personnes, transport à la demande et transferts privés.", 16f)
        if (BuildConfig.BOOKING_EMAIL.isNotBlank()) {
            panel.space(10)
            panel.label(BuildConfig.BOOKING_EMAIL, 15f)
            panel.action("Écrire à Phoenix LTS") { open(Uri.fromParts("mailto", BuildConfig.BOOKING_EMAIL, null)) }
        }
        column.space(12)
        column.action("Confidentialité") { render(Screen.PRIVACY) }
    }

    private fun privacy() {
        column.label("Confidentialité", 27f, bold = true)
        column.space(12)
        val info = column.panel("Vos informations")
        info.label("Phoenix LTS utilise vos coordonnées, vos adresses et les détails du trajet pour traiter votre demande. Les demandes sont stockées sur le serveur et transmises au service de réservation. Si vous activez les notifications, un identifiant technique permet de prévenir cet appareil.", 15f)
        info.space(10)
        info.label("La recherche d’adresses et l’itinéraire transmettent les adresses concernées à Google Maps. Vous pouvez les saisir manuellement et envoyer votre demande sans ouvrir la carte.", 15f)
        if (BuildConfig.PRIVACY_URL.startsWith("https://")) {
            info.space(10)
            info.action("Lire la politique de confidentialité") { open(Uri.parse(BuildConfig.PRIVACY_URL)) }
        }
        if (gateway.available) {
            column.space(12)
            column.action("Demander la suppression de mes données") {
                AlertDialog.Builder(this).setTitle("Supprimer mes données")
                    .setMessage("Une demande de suppression sera transmise à Phoenix LTS pour les demandes de cet appareil.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Demander") { _, _ ->
                        gateway.call("requestDeletion", emptyMap()).addOnCompleteListener(this) { task ->
                            showMessage(if (task.isSuccessful) "Demande de suppression enregistrée."
                                else "La demande n’a pas pu être enregistrée. Contactez Phoenix LTS.")
                        }
                    }.show()
            }
        }
    }

    private fun bind(parent: LinearLayout, label: String, key: String,
                     type: Int = InputType.TYPE_CLASS_TEXT, limit: Int = 250,
                     multiline: Boolean = false, initial: String = "") =
        parent.field(label, values[key] ?: initial, type, limit, multiline).apply {
            doAfterTextChanged { values[key] = it?.toString().orEmpty() }
        }

    private fun dateControl(parent: LinearLayout, caption: String, key: String) {
        parent.action(value(key).takeIf(String::isNotEmpty)?.let { "$caption · ${Booking.displayDate(it)}" } ?: caption) {
            val today = LocalDate.now(Booking.PARIS)
            val selected = runCatching { LocalDate.parse(value(key)) }.getOrDefault(today)
            DatePickerDialog(this, { _, year, month, day ->
                values[key] = LocalDate.of(year, month + 1, day).toString()
                render(Screen.TRIP)
            }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
        }
        parent.space(7)
    }

    private fun timeControl(parent: LinearLayout, caption: String, key: String) {
        parent.action(value(key).takeIf(String::isNotEmpty)?.let { "$caption · $it" } ?: caption) {
            val selected = runCatching { LocalTime.parse(value(key)) }.getOrDefault(LocalTime.of(9, 0))
            TimePickerDialog(this, { _, hour, minute ->
                values[key] = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                render(Screen.TRIP)
            }, selected.hour, selected.minute, true).show()
        }
        parent.space(7)
    }

    private fun tripError(current: Booking): String? =
        if (roundTrip && (current.returnDate.isBlank() || current.returnTime.isBlank())) {
            "Choisissez la date et l’heure du retour."
        } else current.validateTrip()

    private fun value(key: String) = values[key].orEmpty().trim()

    private fun booking() = Booking(service, value("departure"), value("arrival"), value("date"), value("time"),
        if (roundTrip) value("returnDate") else "", if (roundTrip) value("returnTime") else "",
        (values["passengers"] ?: "1").toIntOrNull() ?: -1, (values["luggage"] ?: "0").toIntOrNull() ?: -1,
        value("name"), value("phone"), value("email"), value("needs"), value("notes"), consent)

    private fun showMessage(text: String) {
        AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK", null).show()
    }

    private fun errorMessage(error: Exception?): String =
        when ((error as? FirebaseFunctionsException)?.code) {
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "Revérifiez les champs et le créneau."
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "Trop de demandes récentes. Réessayez plus tard."
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> "Le service n’a pas pu vérifier cet appareil."
            else -> "Aucun accusé de réception pour le moment. Vérifiez votre connexion et réessayez. La même demande conserve sa référence d’envoi."
        }

    private fun open(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: android.content.ActivityNotFoundException) {
            showMessage("Aucune application ne peut ouvrir ce lien.")
        }
    }
}
