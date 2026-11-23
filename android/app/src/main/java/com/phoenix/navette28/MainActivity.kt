package com.phoenix.navette28

import android.Manifest
import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private enum class Screen { HOME, TRIP, CONTACT_FORM, REVIEW, RECEIPT, DESTINATIONS, HISTORY, CONTACT, PRIVACY }

    private val values = mutableMapOf<String, String>()
    private val gateway by lazy { FirebaseGateway.get() }
    private val storage by lazy { getSharedPreferences("reservation-references", MODE_PRIVATE) }
    private val stations = listOf("Gare de La Loupe", "Gare de Verneuil-sur-Avre")
    private val communes by lazy {
        assets.open("communes.csv").bufferedReader().useLines { lines ->
            lines.drop(1).map { it.split(';') }.filter { it.size >= 3 }.groupBy({ it[0] }, { it[2] })
        }
    }
    private val localPlaces by lazy { (communes.values.flatten() + stations).distinct().sorted() }
    private var screen = Screen.HOME
    private var service = Service.NAVETTE
    private var roundTrip = false
    private var consent = false
    private var notifications = false
    private val submission by viewModels<ReservationSubmission>()
    private val sending get() = submission.pending != null
    private var requestingNotifications = false
    private var privacyOrigin = Screen.HOME
    private var beforeEdit: Bundle? = null
    private var feedback = FormFeedback()
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
        state?.let(::restoreDraft)
        beforeEdit = state?.getBundle("beforeEdit")
        screen = Screen.entries.firstOrNull { it.name == state?.getString("screen") } ?: Screen.HOME
        service = Service.entries.firstOrNull { it.name == state?.getString("service") } ?: Service.NAVETTE
        notifications = state?.getBoolean("notifications") ?: false
        requestingNotifications = state?.getBoolean("requestingNotifications") ?: false
        privacyOrigin = Screen.entries.firstOrNull { it.name == state?.getString("privacyOrigin") } ?: Screen.HOME
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        val fromNotification = intent.getBooleanExtra("showHistory", false)
        intent.removeExtra("showHistory")
        render(if (fromNotification && !sending) Screen.HISTORY else screen)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(saveDraft())
        outState.putBundle("beforeEdit", beforeEdit)
        outState.putString("screen", screen.name)
        outState.putString("service", service.name)
        outState.putBoolean("notifications", notifications)
        outState.putBoolean("requestingNotifications", requestingNotifications)
        outState.putString("privacyOrigin", privacyOrigin.name)
    }

    private fun saveDraft() = Bundle().apply {
        putBundle("form", Bundle().also { form -> values.forEach { (key, value) -> form.putString(key, value) } })
        putBoolean("round", roundTrip)
        putBoolean("consent", consent)
    }

    private fun restoreDraft(state: Bundle) {
        values.clear()
        state.getBundle("form")?.let { form ->
            for (key in form.keySet()) values[key] = form.getString(key).orEmpty()
        }
        roundTrip = state.getBoolean("round")
        consent = state.getBoolean("consent")
    }

    override fun onStart() {
        super.onStart()
        ReservationMessaging.syncPendingToken(applicationContext)
        submission.pending?.let(::observeSubmission)
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
        feedback = FormFeedback()
        scroll = ScrollView(this).apply {
            id = R.id.screen_scroll
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

        if (next != Screen.HOME && next != Screen.RECEIPT) {
            column.backLink { back() }.isEnabled = !sending && !requestingNotifications
            column.space(8)
        }
        when (next) {
            Screen.HOME -> home()
            Screen.TRIP -> trip()
            Screen.CONTACT_FORM -> details()
            Screen.REVIEW -> review()
            Screen.RECEIPT -> receipt()
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
        if (screen == Screen.TRIP || screen == Screen.CONTACT_FORM) {
            beforeEdit?.let {
                restoreDraft(it)
                beforeEdit = null
                render(Screen.REVIEW)
                return
            }
        }
        when (screen) {
            Screen.HOME -> finish()
            Screen.CONTACT_FORM -> render(Screen.TRIP)
            Screen.REVIEW -> render(Screen.CONTACT_FORM)
            Screen.PRIVACY -> render(privacyOrigin)
            else -> render(Screen.HOME)
        }
    }

    private fun home() {
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = ShapeableImageView(this).apply {
            setImageResource(R.drawable.phoenix_logo)
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(dp(13).toFloat()).build()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        brand.addView(logo, dp(48), dp(48))
        brand.label(getString(R.string.app_name), 25f, bold = true, heading = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) }
        }
        column.addView(brand, -1, -2)
        column.space(14)
        val header = column().apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF30231D.toInt(), 0xFF542B1C.toInt())).apply { cornerRadius = dp(20).toFloat() }
        }
        val photo = ShapeableImageView(this).apply {
            setImageResource(R.drawable.perche_navette)
            adjustViewBounds = true
            maxHeight = dp(176)
            scaleType = ImageView.ScaleType.CENTER_CROP
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(dp(20).toFloat()).setTopRightCornerSize(dp(20).toFloat()).build()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        header.addView(photo, -1, -2)
        val booking = column().apply { setPadding(dp(16), dp(14), dp(16), dp(16)) }
        header.addView(booking, -1, -2)
        booking.label("Navettes dans le Perche", 22f, Color.WHITE, bold = true)
        booking.label("Transport à la demande et transferts privés", 15f, 0xFFFFE9DC.toInt())
        booking.space(12)
        booking.action(Service.NAVETTE.label, true, R.drawable.ic_navette) { start(Service.NAVETTE) }.apply {
            backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            setTextColor(Palette.ink)
            iconTint = ColorStateList.valueOf(Palette.sun)
        }
        column.addView(header, -1, -2)
        column.space(10)
        column.action("Demander un devis", iconRes = R.drawable.ic_devis) { start(Service.DEVIS) }
        column.space(14)
        val links = column.panel()
        links.setPadding(dp(10), dp(4), dp(10), dp(4))
        links.navigationLink("Mes demandes", R.drawable.ic_devis) { render(Screen.HISTORY) }
        links.rule()
        links.navigationLink("Nos destinations", R.drawable.ic_destinations) { render(Screen.DESTINATIONS) }
        links.rule()
        links.navigationLink("Nous contacter", R.drawable.ic_contact) { render(Screen.CONTACT) }
        column.space(8)
        column.navigationLink("Notre site officiel") { open("https://www.navettephoenix.fr/".toUri()) }.setTextColor(Palette.sun)
        column.navigationLink("Politique de confidentialité") { showPrivacy() }.setTextColor(Palette.sun)
    }

    private fun start(next: Service) {
        beforeEdit = null
        service = next
        render(Screen.TRIP)
    }

    private fun trip() {
        column.stepHeader(service.label, 1, "Trajet")
        val address = column.panel("Départ et arrivée")
        addressField(address, "Départ", "departure")
        addressField(address, "Arrivée", "arrival")
        address.navigationLink(if (BuildConfig.GOOGLE_MAIL) "Voir dans Google Maps" else "Voir l’itinéraire",
            R.drawable.ic_destinations) {
            val current = booking()
            val issue = current.validateAddresses()
            when {
                issue != null -> feedback.show(issue)
                BuildConfig.GOOGLE_MAIL -> open(directionsUri(current.departure, current.arrival))
                else -> startActivity(Intent(this, RouteActivity::class.java)
                    .putExtra("from", current.departure).putExtra("to", current.arrival))
            }
        }
        column.space(10)
        val schedule = column.panel("Horaires")
        schedule.label("Heure de Paris", 14f, Palette.muted)
        schedule.space(8)
        dateControl(schedule, "Date du départ", "date")
        timeControl(schedule, "Heure de prise en charge", "time")
        val round = MaterialCheckBox(this).apply {
            setText(R.string.round_trip)
            isChecked = roundTrip
            minHeight = dp(48)
        }
        schedule.addView(round)
        val returnControls = column()
        schedule.addView(returnControls, -1, -2)
        dateControl(returnControls, "Date du retour", "returnDate")
        timeControl(returnControls, "Heure du retour", "returnTime")
        returnControls.visibility = if (roundTrip) View.VISIBLE else View.GONE
        round.setOnCheckedChangeListener { _, checked ->
            roundTrip = checked
            returnControls.visibility = if (checked) View.VISIBLE else View.GONE
            feedback.changed("returnDate")
            feedback.changed("returnTime")
        }
        column.space(10)
        val group = column.panel("Passagers et bagages")
        val passengers = group.quantity(R.plurals.passengers, R.string.remove_passenger, R.string.add_passenger,
            1..4, value("passengers").toIntOrNull() ?: 1) { values["passengers"] = it.toString() }
        feedback.control("passengers", group, passengers)
        group.space(8)
        group.rule()
        group.space(8)
        val bags = group.quantity(R.plurals.bags, R.string.remove_bag, R.string.add_bag,
            0..16, value("luggage").toIntOrNull() ?: 0) { values["luggage"] = it.toString() }
        feedback.control("luggage", group, bags)
        column.space(14)
        column.action(if (beforeEdit == null) "Continuer" else "Enregistrer les modifications", true) {
            val issue = booking().validateTrip(returnRequested = roundTrip)
            when {
                issue != null -> feedback.show(issue)
                beforeEdit != null -> finishEdit()
                else -> render(Screen.CONTACT_FORM)
            }
        }
    }

    private fun addressField(parent: LinearLayout, title: String, key: String) {
        val useLocalPlaces = BuildConfig.GOOGLE_MAIL || !gateway.available
        val field = bind(parent, title, key, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            suggestions = if (useLocalPlaces) localPlaces else emptyList())
        if (useLocalPlaces) return
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
        column.stepHeader("Vos coordonnées", 2, "Contact")
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
        val agreement = MaterialCheckBox(this).apply {
            setText(R.string.consent_text)
            isChecked = consent
            minHeight = dp(52)
            setOnCheckedChangeListener { _, checked ->
                consent = checked
                feedback.changed("consent")
            }
        }
        column.addView(agreement)
        feedback.control("consent", column, agreement)
        column.space(10)
        column.action("Politique de confidentialité") { showPrivacy() }
        column.space(10)
        column.action(if (beforeEdit == null) "Vérifier ma demande" else "Enregistrer les modifications", true) {
            booking().validateContact()?.let(feedback::show) ?: finishEdit()
        }
    }

    private fun editFromReview(section: Screen) {
        beforeEdit = saveDraft()
        render(section)
    }

    private fun finishEdit() {
        beforeEdit = null
        render(Screen.REVIEW)
    }

    private fun review() {
        val current = booking()
        val busy = sending || requestingNotifications
        column.stepHeader(if (service == Service.DEVIS) "Vérifier votre demande de devis" else "Vérifier votre demande",
            3, "Récapitulatif")
        val trip = column.panel(if (roundTrip) "Trajet aller-retour" else "Trajet aller simple")
        trip.label("Départ", 14f, Palette.muted)
        trip.label(current.departure, bold = true)
        trip.space(6)
        trip.label("Arrivée", 14f, Palette.muted)
        trip.label(current.arrival, bold = true)
        trip.space(12)
        trip.rule()
        trip.space(10)
        trip.label("Aller · ${Booking.displayDate(current.date)} à ${current.time}", 15f)
        if (roundTrip) trip.label("Retour · ${Booking.displayDate(current.returnDate)} à ${current.returnTime}", 15f)
        val passengers = resources.getQuantityString(R.plurals.passengers, current.passengers, current.passengers)
        val luggage = resources.getQuantityString(R.plurals.bags, current.luggage, current.luggage)
        trip.label("$passengers · $luggage", 15f)
        trip.space(8)
        trip.label(if (service == Service.DEVIS) "Tarif communiqué sur devis."
            else "Phoenix LTS confirmera le prix et la disponibilité.", 14f, Palette.muted)
        trip.navigationLink("Modifier le trajet") { editFromReview(Screen.TRIP) }.isEnabled = !busy
        column.space(10)
        val details = column.panel("Contact")
        details.label(current.name)
        details.label(current.phone, 15f)
        details.label(current.email, 15f)
        if (current.needs.isNotEmpty()) details.label("Besoins · ${current.needs}", 15f)
        if (current.notes.isNotEmpty()) details.label("Notes · ${current.notes}", 15f)
        details.navigationLink("Modifier mes coordonnées") { editFromReview(Screen.CONTACT_FORM) }.isEnabled = !busy
        column.space(12)
        if (gateway.available && !BuildConfig.GOOGLE_MAIL) {
            column.addView(MaterialCheckBox(this).apply {
                setText(R.string.notify_status)
                isChecked = notifications
                isEnabled = !busy
                minHeight = dp(48)
                setOnCheckedChangeListener { _, checked -> notifications = checked }
            })
        } else if (!gateway.available) {
            column.label("L’envoi en ligne est indisponible. Vous pouvez contacter Phoenix LTS.", 15f, Palette.sun)
            column.space(8)
        }
        val sendButton = column.action(if (sending) "Envoi en cours…" else "Envoyer ma demande", true) {
            if (requestingNotifications) return@action
            if (notifications && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestingNotifications = true
                render(Screen.REVIEW, preserveScroll = true)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else send(current, notifications)
        }
        sendButton.isEnabled = gateway.available && !busy
        if (!gateway.available) {
            column.space(8)
            column.action("Contacter Phoenix LTS") { render(Screen.CONTACT) }
        }
    }

    private fun send(current: Booking, notify: Boolean) {
        if (sending) return
        current.validateTrip(returnRequested = roundTrip)?.let {
            editFromReview(Screen.TRIP)
            feedback.show(it)
            return
        }
        current.validateContact()?.let {
            editFromReview(Screen.CONTACT_FORM)
            feedback.show(it)
            return
        }
        val fingerprint = org.json.JSONObject(current.payload("")).toString()
        val key = "request." + MessageDigest.getInstance("SHA-256")
            .digest(fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
        val requestId = storage.getString(key, null) ?: UUID.randomUUID().toString()
        if (!storage.edit().putString(key, requestId).commit()) {
            showMessage("Le téléphone ne peut pas enregistrer la référence d’envoi. Réessayez après avoir libéré de l’espace.")
            return
        }
        val operation = submission.start { submitBooking(current, requestId, notify) }
        render(Screen.REVIEW, preserveScroll = true)
        observeSubmission(operation)
    }

    private fun observeSubmission(operation: Task<SubmissionReceipt>) {
        operation.addOnCompleteListener(this) { task ->
            if (!submission.consume(task)) return@addOnCompleteListener
            if (!task.isSuccessful) {
                render(Screen.REVIEW, preserveScroll = true)
                MaterialAlertDialogBuilder(this).setTitle("Envoi à vérifier")
                    .setMessage(errorMessage(task.exception))
                    .setPositiveButton("Revenir à ma demande", null)
                    .setNeutralButton("Voir mes demandes") { _, _ -> render(Screen.HISTORY) }.show()
                return@addOnCompleteListener
            }
            val result = task.result
            if (result.notifications) ReservationMessaging.markEnabled(this)
            storage.edit {
                putString("latest", result.reference)
                putString("latestStatus", result.status)
            }
            values.clear()
            beforeEdit = null
            roundTrip = false
            consent = false
            notifications = false
            render(Screen.RECEIPT)
        }
    }

    private fun receipt() {
        column.label("Demande enregistrée", 28f, bold = true, heading = true)
        column.space(14)
        val receipt = column.panel("Votre référence")
        receipt.label(storage.getString("latest", "").orEmpty(), 20f, Palette.sun, bold = true)
            .setTextIsSelectable(true)
        receipt.space(10)
        val status = storage.getString("latestStatus", null)
        receipt.label(statusLabel(status), 16f)
        if (status == "PENDING") {
            receipt.space(6)
            receipt.label("Phoenix LTS vous recontactera pour le prix et la disponibilité.", 15f, Palette.muted)
        }
        column.space(16)
        column.action("Voir mes demandes", true) { render(Screen.HISTORY) }
        column.space(8)
        column.action("Retour à l’accueil") { render(Screen.HOME) }
    }
    private fun history() {
        column.label("Mes demandes", 28f, bold = true, heading = true)
        column.space(12)
        column.requestHistory(storage.getString("latest", null), if (gateway.available) ::loadHistory else null)
    }

    private fun loadHistory(): Task<List<RequestSummary>> {
        if (BuildConfig.GOOGLE_MAIL) return GoogleMailGateway.list()
        return gateway.call("listReservations", emptyMap()).onSuccessTask { value ->
            val rows = (value as? Map<*, *>)?.get("reservations") as? List<*>
            Tasks.forResult(rows.orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { row ->
                (row["reference"] as? String)?.let { RequestSummary(it, row["status"] as? String) }
            })
        }
    }

    private fun destinations() {
        column.label("Nos destinations", 28f, bold = true, heading = true)
        column.space(10)
        val local = column.panel("Navette dans le Perche")
        for (sector in listOf("Forêts du Perche", "Terres de Perche")) {
            val places = communes[sector].orEmpty().toTypedArray()
            local.action("$sector · ${places.size} communes") {
                MaterialAlertDialogBuilder(this).setTitle(sector).setItems(places) { _, which ->
                    values["departure"] = places[which]
                    start(Service.NAVETTE)
                }.setNegativeButton("Fermer", null).show()
            }
            local.space(7)
        }
        local.label(stations.joinToString("\n"), 15f, Palette.muted)
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
        column.action("Notre site officiel") { open("https://www.navettephoenix.fr/".toUri()) }
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
        info.label(if (BuildConfig.GOOGLE_MAIL)
            "Les suggestions de communes et de gares sont intégrées à l’application. Si vous ouvrez l’itinéraire dans Google Maps, Google reçoit le départ et l’arrivée."
        else "La recherche d’adresses et l’itinéraire transmettent les adresses concernées à Google Maps. Vous pouvez aussi saisir vos adresses à la main.", 15f)
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
                     multiline: Boolean = false, suggestions: List<String> = emptyList()): EditText {
        val layout = parent.field(label, values[key].orEmpty(), type, limit, multiline, suggestions)
        feedback.input(key, layout)
        return requireNotNull(layout.editText).apply {
            if (Build.VERSION.SDK_INT >= 26) {
                when (key) {
                    "name" -> setAutofillHints(View.AUTOFILL_HINT_NAME)
                    "phone" -> setAutofillHints(View.AUTOFILL_HINT_PHONE)
                    "email" -> setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)
                }
            }
            doAfterTextChanged {
                values[key] = it?.toString().orEmpty()
                feedback.changed(key)
            }
        }
    }

    private fun dateControl(parent: LinearLayout, caption: String, key: String) {
        lateinit var button: MaterialButton
        button = parent.action(value(key).takeIf(String::isNotEmpty)?.let {
            getString(R.string.selected_value, caption, Booking.displayDate(it))
        } ?: caption) {
            val today = LocalDate.now(Booking.PARIS)
            val departure = if (key == "returnDate") runCatching { LocalDate.parse(value("date")) }.getOrNull() else null
            val earliest = maxOf(today, departure ?: today)
            val selected = maxOf(earliest, runCatching { LocalDate.parse(value(key)) }.getOrDefault(earliest))
            DatePickerDialog(this, { _, year, month, day ->
                values[key] = LocalDate.of(year, month + 1, day).toString()
                button.text = getString(R.string.selected_value, caption, Booking.displayDate(value(key)))
                feedback.changed(key)
            }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                datePicker.minDate = earliest.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.show()
        }
        feedback.control(key, parent, button)
        parent.space(7)
    }

    private fun timeControl(parent: LinearLayout, caption: String, key: String) {
        lateinit var button: MaterialButton
        button = parent.action(value(key).takeIf(String::isNotEmpty)?.let { getString(R.string.selected_value, caption, it) } ?: caption) {
            val selected = runCatching { LocalTime.parse(value(key)) }.getOrDefault(LocalTime.of(9, 0))
            TimePickerDialog(this, { _, hour, minute ->
                values[key] = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                button.text = getString(R.string.selected_value, caption, value(key))
                feedback.changed(key)
            }, selected.hour, selected.minute, true).show()
        }
        feedback.control(key, parent, button)
        parent.space(7)
    }

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
        val configured = BuildConfig.PRIVACY_URL.toUri()
        val policyUrl = configured.takeIf { it.scheme == "https" && it.host == "www.navettephoenix.fr" }
            ?: "https://www.navettephoenix.fr/politique".toUri()
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
