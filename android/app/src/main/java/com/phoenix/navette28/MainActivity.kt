package com.phoenix.navette28

import android.Manifest
import android.animation.ValueAnimator
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private enum class Screen { HOME, TRIP, CONTACT_FORM, REVIEW, DESTINATIONS, HISTORY, CONTACT, PRIVACY }

    private val values = mutableMapOf<String, String>()
    private val gateway by lazy { FirebaseGateway.get() }
    private val storage by lazy { getSharedPreferences("reservation-references", MODE_PRIVATE) }
    private var screen = Screen.HOME
    private var service = Service.NAVETTE
    private var roundTrip = false
    private var consent = false
    private var notifications = false
    private var sending = false
    private var requestingNotifications = false
    private var privacyOrigin = Screen.HOME
    private lateinit var scroll: ScrollView
    private lateinit var column: LinearLayout
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestingNotifications = false
        if (screen == Screen.REVIEW) {
            notifications = it
            render(Screen.REVIEW, preserveScroll = true)
            send(booking(), it)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        state?.getBundle("form")?.let { form ->
            for (key in form.keySet()) values[key] = form.getString(key).orEmpty()
        }
        screen = Screen.entries.firstOrNull { it.name == state?.getString("screen") } ?: Screen.HOME
        service = Service.entries.firstOrNull { it.name == state?.getString("service") } ?: Service.NAVETTE
        roundTrip = state?.getBoolean("round") ?: false
        consent = state?.getBoolean("consent") ?: false
        notifications = state?.getBoolean("notifications") ?: false
        requestingNotifications = state?.getBoolean("requestingNotifications") ?: false
        privacyOrigin = Screen.entries.firstOrNull { it.name == state?.getString("privacyOrigin") } ?: Screen.HOME
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
        outState.putBoolean("requestingNotifications", requestingNotifications)
        outState.putString("privacyOrigin", privacyOrigin.name)
    }

    override fun onStart() {
        super.onStart()
        ReservationMessaging.syncPendingToken(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("showHistory", false) && !sending && !requestingNotifications) {
            render(Screen.HISTORY)
        }
    }

    private fun render(next: Screen, preserveScroll: Boolean = false) {
        val previousOffset = if (preserveScroll && ::scroll.isInitialized) scroll.scrollY else 0
        screen = next
        scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Palette.paper)
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Palette.paper) }
        column = column()
        val width = if (resources.configuration.screenWidthDp >= 560) dp(560) else -1
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
            column.action("Retour") { back() }
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
        if (previousOffset > 0) scroll.post { scroll.scrollTo(0, previousOffset) }
        if (!preserveScroll && Build.VERSION.SDK_INT >= 26 && ValueAnimator.areAnimatorsEnabled()) {
            root.alpha = .65f
            root.animate().alpha(1f).setDuration(140).start()
        }
    }

    private fun back() {
        if (sending || requestingNotifications) return
        when (screen) {
            Screen.HOME -> finish()
            Screen.CONTACT_FORM -> render(Screen.TRIP)
            Screen.REVIEW -> render(Screen.CONTACT_FORM)
            Screen.PRIVACY -> render(privacyOrigin)
            else -> render(Screen.HOME)
        }
    }

    private fun home() {
        val header = column().apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF291D19.toInt(), 0xFF4C2117.toInt(), 0xFF782711.toInt()))
                .apply { cornerRadius = dp(28).toFloat() }
            setPadding(dp(22), dp(22), dp(22), dp(24))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.phoenix_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = surface(Palette.sun, 20)
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        header.addView(logo, dp(92), dp(92))
        header.space(15)
        header.label("Navette Phoenix 28", 35f, Color.WHITE, bold = true, heading = true)
        header.space(6)
        header.label("Transport à la demande dans le Perche et transferts privés", 16f, 0xFFF9D9C6.toInt())
        column.addView(header, -1, -2)
        column.space(16)
        column.action("Réserver une navette", true, R.drawable.ic_navette) { start(Service.NAVETTE) }
        column.space(9)
        column.action("Demander un devis", iconRes = R.drawable.ic_devis) { start(Service.DEVIS) }
        column.space(9)
        column.action("Nos destinations", iconRes = R.drawable.ic_destinations) { render(Screen.DESTINATIONS) }
        column.space(16)
        column.action("Mes demandes") { render(Screen.HISTORY) }
        column.space(9)
        column.action("Nous contacter et informations pratiques") { render(Screen.CONTACT) }
        column.space(9)
        column.action("Notre site officiel") { open(Uri.parse("https://www.navettephoenix.fr/")) }
        column.space(9)
        column.action("Politique de confidentialité") { showPrivacy() }
    }

    private fun start(next: Service) {
        service = next
        render(Screen.TRIP)
    }

    private fun trip() {
        column.label(service.label, 27f, bold = true, heading = true)
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
        val round = MaterialCheckBox(this).apply {
            text = "Aller-retour"
            isChecked = roundTrip
            minHeight = dp(48)
            setOnCheckedChangeListener { _, checked ->
                roundTrip = checked
                render(Screen.TRIP, preserveScroll = true)
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
        if (!gateway.available || BuildConfig.GOOGLE_MAIL) return
        var searching = false
        val search = parent.action("Suggestions pour « $title »") {
            if (searching) return@action
            val query = field.text.toString().trim()
            if (query.length < 3) {
                field.error = "Saisissez au moins trois caractères."
                return@action
            }
            searching = true
            gateway.call("suggestAddresses", mapOf("query" to query)).addOnCompleteListener(this) { task ->
                searching = false
                if (screen != Screen.TRIP || !field.isAttachedToWindow || field.text.toString().trim() != query) {
                    return@addOnCompleteListener
                }
                if (!task.isSuccessful) {
                    showMessage("La recherche est indisponible. Vous pouvez compléter l’adresse à la main.")
                    return@addOnCompleteListener
                }
                val items = ((task.result as? Map<*, *>)?.get("addresses") as? List<*>)
                    .orEmpty().filterIsInstance<String>().toTypedArray()
                if (items.isEmpty()) showMessage("Aucune adresse trouvée. Ajoutez la commune ou le code postal.")
                else MaterialAlertDialogBuilder(this).setTitle("Choisir une adresse").setItems(items) { _, which ->
                    field.setText(items[which])
                }.setNegativeButton("Annuler", null).show()
            }
        }
        search.textSize = 14f
        parent.space(9)
    }

    private fun details() {
        column.label("Vos coordonnées", 27f, bold = true, heading = true)
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
        column.addView(MaterialCheckBox(this).apply {
            text = "J’ai lu les informations sur l’utilisation de mes données."
            isChecked = consent
            minHeight = dp(52)
            setOnCheckedChangeListener { _, checked -> consent = checked }
        })
        column.space(10)
        column.action("Politique de confidentialité") { showPrivacy() }
        column.space(10)
        column.action("Vérifier ma demande", true) {
            booking().validateContact()?.let(::showMessage) ?: render(Screen.REVIEW)
        }
    }

    private fun review() {
        val current = booking()
        column.label("Vérifier votre demande", 27f, bold = true, heading = true)
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
        if (gateway.available && !BuildConfig.GOOGLE_MAIL) {
            column.addView(MaterialCheckBox(this).apply {
                text = "Recevoir une notification quand le statut change"
                isChecked = notifications
                minHeight = dp(48)
                setOnCheckedChangeListener { _, checked -> notifications = checked }
            })
        } else if (!gateway.available) {
            column.label("L’envoi en ligne est indisponible. Vous pouvez contacter Phoenix LTS.", 15f, Palette.sun)
            column.space(8)
        }
        val sendButton = column.action("Envoyer ma demande", true) {
            if (requestingNotifications) return@action
            if (notifications && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestingNotifications = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else send(current, notifications)
        }
        sendButton.isEnabled = gateway.available && !requestingNotifications
        if (!gateway.available) {
            column.space(8)
            column.action("Contacter Phoenix LTS") { render(Screen.CONTACT) }
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
        val progress = MaterialAlertDialogBuilder(this).setTitle("Envoi de la demande")
            .setMessage("Nous attendons la réponse du serveur.").setCancelable(false).show()
        val operation: Task<Pair<Any?, Boolean>> = if (BuildConfig.GOOGLE_MAIL) {
            GoogleMailGateway.submit(current, requestId)
                .onSuccessTask { response -> Tasks.forResult(response to false) }
        } else {
            val token = if (notify) {
                Tasks.withTimeout(FirebaseMessaging.getInstance().token, 5, TimeUnit.SECONDS)
                    .continueWith { if (it.isSuccessful) it.result else "" }
            } else Tasks.forResult("")
            token.onSuccessTask { pushToken ->
                gateway.call("createReservation", current.payload(requestId) + ("pushToken" to pushToken))
                    .onSuccessTask { response -> Tasks.forResult(response to pushToken.isNotEmpty()) }
            }
        }
        operation.addOnCompleteListener { task ->
            sending = false
            if (isDestroyed || isFinishing) return@addOnCompleteListener
            progress.dismiss()
            val result = if (task.isSuccessful) task.result else null
            val reply = result?.first as? Map<*, *>
            val reference = reply?.get("reference") as? String
            if (reference.isNullOrBlank()) {
                showMessage(errorMessage(task.exception))
                return@addOnCompleteListener
            }
            if (result?.second == true) ReservationMessaging.markEnabled(this)
            storage.edit().putString("latest", reference).apply()
            values.clear()
            roundTrip = false
            consent = false
            notifications = false
            render(Screen.HISTORY)
            MaterialAlertDialogBuilder(this).setTitle("Demande enregistrée")
                .setMessage("${statusLabel(reply?.get("status"))}.\n\nRéférence : $reference")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun history() {
        column.label("Mes demandes", 28f, bold = true, heading = true)
        column.space(12)
        val panel = column.panel()
        val latest = storage.getString("latest", null)
        val status = panel.label(if (latest != null) "Dernière référence enregistrée · $latest" else "Aucune référence enregistrée sur cet appareil.")
        if (!gateway.available) return
        panel.space(9)
        val requests: Task<out List<*>> = if (BuildConfig.GOOGLE_MAIL) GoogleMailGateway.list()
            else gateway.call("listReservations", emptyMap()).onSuccessTask { value ->
                Tasks.forResult(((value as? Map<*, *>)?.get("reservations") as? List<*>).orEmpty())
            }
        requests.addOnCompleteListener(this) { task ->
            if (screen != Screen.HISTORY || !panel.isAttachedToWindow) return@addOnCompleteListener
            if (!task.isSuccessful) {
                panel.label("L’actualisation est momentanément indisponible.", 14f, Palette.muted)
                return@addOnCompleteListener
            }
            val rows = task.result
            status.text = if (rows.isEmpty()) "Aucune demande envoyée depuis cet appareil." else "Dernières demandes"
            for (row in rows.filterIsInstance<Map<*, *>>()) {
                panel.space(10)
                panel.label("${row["reference"]} · ${statusLabel(row["status"])}", 15f, bold = true)
            }
        }
    }

    private fun statusLabel(status: Any?): String = when (status) {
        "CONFIRMED" -> "Trajet confirmé par Phoenix LTS"
        "DECLINED" -> "Demande refusée"
        "CANCELLED" -> "Demande annulée"
        "PENDING" -> "Demande enregistrée · En attente de transmission"
        "SENDING" -> "Transmission en cours de vérification"
        "SENT" -> "Demande transmise · En attente de confirmation par Phoenix LTS"
        "INVALID" -> "Transmission impossible · Contactez Phoenix LTS"
        else -> "Demande reçue, en attente de confirmation par Phoenix LTS"
    }

    private fun destinations() {
        column.label("Nos destinations", 28f, bold = true, heading = true)
        column.space(10)
        val local = column.panel("Navette dans le Perche")
        val communes = assets.open("communes.csv").bufferedReader().useLines { lines ->
            lines.drop(1).map { it.split(';') }.filter { it.size >= 3 }.toList()
        }
        for (sector in listOf("Forêts du Perche", "Terres de Perche")) {
            val places = communes.filter { it[0] == sector }.map { it[2] }.toTypedArray()
            local.action("$sector · ${places.size} communes") {
                MaterialAlertDialogBuilder(this).setTitle(sector).setItems(places) { _, which ->
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
        column.label("Contact et informations", 27f, bold = true, heading = true)
        column.space(12)
        val panel = column.panel("Phoenix LTS")
        panel.label("Transport de personnes, transport à la demande et transferts privés.", 16f)
        if (BuildConfig.BOOKING_EMAIL.isNotBlank()) {
            panel.space(10)
            panel.label(BuildConfig.BOOKING_EMAIL, 15f)
            panel.action("Écrire à Phoenix LTS") { open(Uri.fromParts("mailto", BuildConfig.BOOKING_EMAIL, null)) }
        }
        column.space(12)
        column.action("Notre site officiel") { open(Uri.parse("https://www.navettephoenix.fr/")) }
        column.space(9)
        column.action("Politique de confidentialité") { showPrivacy() }
    }

    private fun showPrivacy() {
        privacyOrigin = screen
        render(Screen.PRIVACY)
    }

    private fun privacy() {
        column.label("Confidentialité et données personnelles", 25f, bold = true, heading = true)
        column.space(12)
        val info = column.panel("Traitement de votre demande")
        info.label("Phoenix LTS utilise vos coordonnées et les détails du trajet pour répondre à votre demande, établir un devis ou organiser le transport.", 15f)
        info.space(10)
        info.label(if (BuildConfig.GOOGLE_MAIL)
            "Les demandes sont enregistrées dans Firebase et transmises par e-mail à Phoenix LTS via Google."
        else "Les demandes envoyées sont hébergées par Firebase. Le service de réservation reçoit les informations utiles au trajet. Si vous activez les notifications, un identifiant technique permet de prévenir cet appareil.", 15f)
        info.space(10)
        info.label("La recherche d’adresses et l’itinéraire transmettent les adresses concernées à Google Maps. Vous pouvez aussi saisir vos adresses à la main.", 15f)
        info.space(10)
        info.label("Pour exercer vos droits d’accès, de rectification ou d’effacement, contactez Phoenix LTS via la rubrique Contact.", 15f)
        info.space(10)
        info.action("Lire la politique de confidentialité") { openPrivacyPolicy() }
        info.space(8)
        info.action("Contacter Phoenix LTS") { render(Screen.CONTACT) }
        if (gateway.available) {
            column.space(12)
            column.action("Demander la suppression de mes données") {
                MaterialAlertDialogBuilder(this).setTitle("Supprimer mes données")
                    .setMessage("Une demande de suppression sera transmise à Phoenix LTS pour les demandes de cet appareil.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Demander") { _, _ ->
                        val deletion = if (BuildConfig.GOOGLE_MAIL) GoogleMailGateway.requestDeletion()
                            else gateway.call("requestDeletion", emptyMap())
                        deletion.addOnCompleteListener(this) { task ->
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
            val departure = if (key == "returnDate") runCatching { LocalDate.parse(value("date")) }.getOrNull() else null
            val earliest = maxOf(today, departure ?: today)
            val selected = maxOf(earliest, runCatching { LocalDate.parse(value(key)) }.getOrDefault(earliest))
            DatePickerDialog(this, { _, year, month, day ->
                values[key] = LocalDate.of(year, month + 1, day).toString()
                render(Screen.TRIP, preserveScroll = true)
            }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                datePicker.minDate = earliest.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.show()
        }
        parent.space(7)
    }

    private fun timeControl(parent: LinearLayout, caption: String, key: String) {
        parent.action(value(key).takeIf(String::isNotEmpty)?.let { "$caption · $it" } ?: caption) {
            val selected = runCatching { LocalTime.parse(value(key)) }.getOrDefault(LocalTime.of(9, 0))
            TimePickerDialog(this, { _, hour, minute ->
                values[key] = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                render(Screen.TRIP, preserveScroll = true)
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
        MaterialAlertDialogBuilder(this).setMessage(text).setPositiveButton("OK", null).show()
    }

    private fun errorMessage(error: Exception?): String =
        if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "L’envoi n’a pas été autorisé. Si vous venez d’envoyer une demande, attendez une minute puis réessayez."
        } else when ((error as? FirebaseFunctionsException)?.code) {
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "Revérifiez les champs et le créneau."
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "Trop de demandes récentes. Réessayez plus tard."
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> "Le service n’a pas pu vérifier cet appareil."
            else -> "Aucun accusé de réception pour le moment. Vérifiez votre connexion et réessayez. La même demande conserve sa référence d’envoi."
        }

    private fun openPrivacyPolicy() {
        val configured = Uri.parse(BuildConfig.PRIVACY_URL)
        val policyUrl = configured.takeIf { it.scheme == "https" && it.host == "www.navettephoenix.fr" }
            ?: Uri.parse("https://www.navettephoenix.fr/politique")
        open(policyUrl)
    }

    private fun open(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: android.content.ActivityNotFoundException) {
            showMessage("Aucune application ne peut ouvrir ce lien.")
        }
    }
}
