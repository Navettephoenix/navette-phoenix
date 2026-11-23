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
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
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
    private enum class Screen { HOME, TRIP, CONTACT_FORM, REVIEW, RECEIPT, DESTINATIONS, TARIFFS, HISTORY, CONTACT, PRIVACY }

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
    private lateinit var page: LinearLayout
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
        column.setPadding(dp(20), dp(14), dp(20), dp(24))

        // La navigation reste fixe au-dessus du contenu défilant, y compris sur Tarifs.
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.paper)
        }
        if (next != Screen.HOME && next != Screen.RECEIPT) {
            val header = column().apply {
                setPadding(dp(20), dp(14), dp(20), dp(2))
            }
            header.backLink { back() }.isEnabled = !sending && !requestingNotifications
            page.addView(header, LinearLayout.LayoutParams(-1, -2))
        }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                maxOf(status.left, navigation.left), status.top,
                maxOf(status.right, navigation.right), maxOf(navigation.bottom, keyboard.bottom)
            )
            insets
        }
        column.isFocusableInTouchMode = true
        setContentView(page)
        ViewCompat.requestApplyInsets(page)
        column.requestFocus()
        when (next) {
            Screen.HOME -> home()
            Screen.TRIP -> trip()
            Screen.CONTACT_FORM -> details()
            Screen.REVIEW -> review()
            Screen.RECEIPT -> receipt()
            Screen.DESTINATIONS -> destinations()
            Screen.TARIFFS -> tariffs()
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
        links.navigationLink("Nos tarifs", R.drawable.ic_devis) { render(Screen.TARIFFS) }
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
        column.label("Choisissez votre point de départ et votre destination.", 15f, Palette.muted)
        column.space(12)
        var updateFare: () -> Unit = {}
        val departurePanel = column.panel("01  ·  Votre départ")
        communeField(departurePanel, "Départ", "departure") { updateFare() }
        column.space(12)
        val arrivalPanel = column.panel("02  ·  Votre destination")
        communeField(arrivalPanel, "Arrivée", "arrival") { updateFare() }
        column.space(8)
        column.navigationLink(if (BuildConfig.GOOGLE_MAIL) "Voir dans Google Maps" else "Voir l’itinéraire",
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
            updateFare()
        }
        column.space(10)
        val group = column.panel("Passagers et bagages")
        val passengers = group.quantity(R.plurals.passengers, R.string.remove_passenger, R.string.add_passenger,
            1..4, value("passengers").toIntOrNull() ?: 1) {
                values["passengers"] = it.toString()
                updateFare()
            }
        feedback.control("passengers", group, passengers)
        group.space(8)
        group.rule()
        group.space(8)
        val bags = group.quantity(R.plurals.bags, R.string.remove_bag, R.string.add_bag,
            0..16, value("luggage").toIntOrNull() ?: 0) { values["luggage"] = it.toString() }
        feedback.control("luggage", group, bags)
        column.space(12)
        val price = column.panel("Tarif estimé")
        val zoneLabel = price.label("", 16f, Palette.ink, bold = true)
        price.space(6)
        val singleLabel = price.label("", 15f)
        price.space(7)
        val totalLabel = price.label("", 25f, Palette.sun, bold = true)
        price.space(6)
        price.label("Prix indicatif. Le montant final sera confirmé avant la réservation.", 15f, Palette.muted)
        val quoteButton = price.action("Demander un devis") {
            service = Service.DEVIS
            val ready = booking().validateTrip(returnRequested = roundTrip) == null
            render(if (ready) Screen.CONTACT_FORM else Screen.TRIP)
        }
        updateFare = {
            val result = PricingCalculator.calculerTarif(
                value("departureCommune"), value("arrivalCommune"), roundTrip,
                value("passengers").toIntOrNull() ?: 1
            )
            if (result is PricingCalculator.Resultat.Tarif) {
                zoneLabel.text = "Zone ${result.zone} · ${result.prixParPassager} € par passager"
                singleLabel.text = "${value("passengers")} passager(s) : ${result.prixTotalTrajet} € le trajet"
                totalLabel.text = if (roundTrip) "Total aller-retour : ${result.prixTotal} €"
                    else "Total : ${result.prixTotal} €"
                quoteButton.visibility = View.GONE
            } else {
                zoneLabel.text = "Tarif sur devis"
                singleLabel.text = "Trajet hors zone, entre secteurs ou commune à préciser."
                totalLabel.text = ""
                quoteButton.visibility = if (service == Service.DEVIS) View.GONE else View.VISIBLE
            }
        }
        updateFare()
        column.space(12)
        column.navigationLink("Consulter les tarifs par zones", R.drawable.ic_devis) { render(Screen.TARIFFS) }
        column.space(8)
        // Action toujours visible, hors du contenu défilant et au-dessus de la navigation Android.
        val footer = column().apply {
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setBackgroundColor(Palette.paper)
        }
        footer.action(if (beforeEdit == null) "Continuer" else "Enregistrer les modifications", true) {
            val issue = booking().validateTrip(returnRequested = roundTrip)
            when {
                issue != null -> feedback.show(issue)
                beforeEdit != null -> finishEdit()
                else -> render(Screen.CONTACT_FORM)
            }
        }
        page.addView(footer, LinearLayout.LayoutParams(-1, -2))
    }

    /** A commune is selected from a searchable list; detailed pickup points remain free-form. */
    private fun communeField(parent: LinearLayout, label: String, key: String, changed: () -> Unit) {
        val communeKey = "${key}Commune"
        val detailKey = "${key}Detail"
        if (value(communeKey).isEmpty() && value(key).isNotEmpty()) {
            values[communeKey] = PricingCalculator.commune(value(key))?.commune
                ?: PricingCalculator.AUTRE_COMMUNE
            if (value(communeKey) == PricingCalculator.AUTRE_COMMUNE) {
                values[detailKey] = value(key)
            }
        }

        fun updateAddress() {
            val commune = value(communeKey)
            val detail = value(detailKey)
            values[key] = if (commune == PricingCalculator.AUTRE_COMMUNE) {
                detail.ifBlank { commune }
            } else listOf(commune, detail).filter(String::isNotBlank).joinToString(" · ")
            feedback.changed(key)
            changed()
        }

        lateinit var selector: EditText
        selector = parent.pickerField("$label · Commune", value(communeKey), R.drawable.ic_destinations) {
            val search = EditText(this).apply {
                hint = "Rechercher une commune"
                setSingleLine(true)
                minHeight = dp(56)
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            val list = ListView(this)
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, PricingCalculator.communes.toMutableList())
            list.adapter = adapter
            val popup = column().apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(search, -1, -2)
                addView(list, LinearLayout.LayoutParams(-1, dp(320)))
            }
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle("Choisir la commune de $label")
                .setView(popup)
                .setNegativeButton("Annuler", null).create()
            search.doAfterTextChanged { editable ->
                val query = PricingCalculator.normaliser(editable?.toString().orEmpty())
                val matches = PricingCalculator.communes.filter {
                    PricingCalculator.normaliser(it).contains(query)
                }
                adapter.clear()
                adapter.addAll(matches)
                adapter.notifyDataSetChanged()
            }
            list.setOnItemClickListener { _, _, position, _ ->
                values[communeKey] = adapter.getItem(position).orEmpty()
                selector.setText(value(communeKey))
                selector.contentDescription = "$label · Commune : ${value(communeKey)}"
                updateAddress()
                dialog.dismiss()
            }
            dialog.show()
        }
        feedback.control(key, parent, selector)
        val detail = bind(parent, "$label · Adresse précise",
            detailKey, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS, 150)
        detail.doAfterTextChanged { updateAddress() }
        val help = parent.label(
            "Facultatif : précisez une rue, une gare ou un lieu de rendez-vous.",
            14f, Palette.muted
        )
        parent.space(2)
        val searchOptions = column()
        parent.addView(searchOptions, LinearLayout.LayoutParams(-1, -2))
        searchOptions.visibility = View.GONE
        val toggleSearch = parent.navigationLink("Recherche d’adresse (optionnelle)  +", R.drawable.ic_destinations) {
            val expanded = searchOptions.visibility != View.VISIBLE
            searchOptions.visibility = if (expanded) View.VISIBLE else View.GONE
        }
        lateinit var searchButton: MaterialButton
        searchButton = searchOptions.navigationLink("Rechercher une adresse (IGN)", R.drawable.ic_destinations) {
            val query = detail.text.toString().trim()
            if (value(communeKey).isBlank()) {
                feedback.show(BookingIssue(key, "Choisissez d’abord votre commune."))
                return@navigationLink
            }
            if (query.length < 3) {
                detail.error = "Saisissez au moins trois caractères avant de rechercher."
                detail.requestFocus()
                return@navigationLink
            }
            detail.error = null
            searchButton.isEnabled = false
            help.text = "Recherche d’adresses en cours…"
            val selectedCommune = value(communeKey)
            AddressLookup.search(query, selectedCommune) { result ->
                searchButton.isEnabled = true
                if (screen != Screen.TRIP || !detail.isAttachedToWindow ||
                    value(communeKey) != selectedCommune || detail.text.toString().trim() != query) {
                    return@search
                }
                help.text = "Choisissez une adresse proposée ou conservez votre saisie manuelle."
                val addresses = result.getOrNull().orEmpty()
                if (addresses.isEmpty()) {
                    showMessage(
                        if (result.isFailure)
                            "Le service d’adresses est indisponible. Conservez une adresse précise et vérifiez-la sur Google Maps."
                        else
                            "Aucune adresse correspondante dans cette commune. Vous pouvez préciser le lieu manuellement."
                    )
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Choisir l’adresse de $label")
                        .setItems(addresses.toTypedArray()) { _, index ->
                            detail.setText(addresses[index])
                            detail.setSelection(detail.text.length)
                            updateAddress()
                        }
                        .setNegativeButton("Conserver ma saisie", null)
                        .show()
                }
            }
        }
        searchOptions.label("Les termes saisis sont transmis au service public IGN.", 14f, Palette.muted)
        updateAddress()
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
        extra.label("Précisez uniquement les informations utiles à votre trajet.", 15f, Palette.muted)
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
        val fare = current.pricing
        if (fare is PricingCalculator.Resultat.Tarif) {
            val tarif = trip.panel("Tarif estimé")
            tarif.label("Zone ${fare.zone} · ${fare.secteur.label}", 16f, bold = true)
            tarif.label("${fare.prixParPassager} € par passager × ${current.passengers} passager(s)", 15f)
            tarif.label("${fare.prixTotalTrajet} € par trajet", 15f)
            tarif.label("Total${if (roundTrip) " aller-retour" else ""} : ${fare.prixTotal} €",
                24f, Palette.sun, bold = true)
        } else {
            trip.label("Tarif sur devis", 20f, Palette.sun, bold = true)
        }
        trip.label("Prix indicatif : votre trajet reste à confirmer par notre équipe.", 15f, Palette.muted)
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
                if ((task.exception as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    android.util.Log.w("NavettePhoenix", "Demande refusée par Firestore : vérifier le déploiement des règles V20.")
                }
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
        column.space(10)
        column.label("Demande enregistrée", 27f, bold = true, heading = true)
        column.space(8)
        column.label("Merci ! Votre demande a bien été enregistrée. Retrouvez son suivi à tout moment.", 16f, Palette.muted)
        column.space(18)

        val receipt = column.panel("Référence de votre demande")
        receipt.label(storage.getString("latest", "").orEmpty(), 21f, Palette.sun, bold = true)
            .setTextIsSelectable(true)
        receipt.space(14)
        receipt.label("Suivi de votre demande", 18f, bold = true)
        receipt.space(10)
        receipt.label("✓  1. Demande enregistrée", 17f, bold = true)
        receipt.space(8)
        val status = storage.getString("latestStatus", null)
        if (status == "PENDING") {
            receipt.label("◷  2. Transmission en attente", 16f, Palette.sun, bold = true)
            receipt.space(8)
            receipt.label("3. Confirmation par notre équipe", 15f, Palette.muted)
            receipt.space(14)
            receipt.label("Votre demande est bien enregistrée. La transmission peut prendre quelques minutes. Consultez « Mes demandes » pour connaître son statut actuel.", 16f, Palette.muted)
        } else {
            receipt.label("✓  2. " + statusLabel(status), 16f, Palette.sun, bold = true)
            receipt.space(8)
            receipt.label("3. Confirmation par notre équipe", 15f, Palette.muted)
        }
        column.space(16)
        column.label("Votre trajet n’est confirmé qu’après validation par notre service de réservation.", 15f, Palette.muted)
        column.space(18)
        column.action("Suivre ma demande", true) { render(Screen.HISTORY) }
        column.space(10)
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
                (row["reference"] as? String)?.let {
                    RequestSummary(it, row["status"] as? String, row["service"] as? String,
                        row["departure"] as? String, row["arrival"] as? String,
                        row["date"] as? String, row["time"] as? String,
                        row["pricingType"] as? String, row["pricingSector"] as? String,
                        (row["pricingZone"] as? Number)?.toInt(),
                        (row["passengers"] as? Number)?.toInt(),
                        (row["pricePerPassenger"] as? Number)?.toInt(),
                        (row["priceTotal"] as? Number)?.toInt())
                }
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
        column.space(12)
        column.action("Consulter les tarifs", iconRes = R.drawable.ic_devis) { render(Screen.TARIFFS) }
    }

    private fun tariffs() {
        column.label("Nos tarifs", 28f, bold = true, heading = true)
        column.space(7)
        column.label("Prix par passager pour un aller simple, selon la zone desservie.", 16f, Palette.muted)
        column.space(12)

        fun sector(title: String, centre: String, secteur: PricingCalculator.Secteur) {
            val panel = column.panel(title)
            panel.label("Point central : $centre", 16f, Palette.sun, bold = true)
            panel.space(9)
            for (zone in 1..4) {
                val price = listOf(5, 6, 8, 10)[zone - 1]
                panel.label("Zone $zone · $price € par passager", 16f, bold = true)
                panel.space(5)
                val places = PricingCalculator.localites.filter {
                    it.secteur == secteur && it.zone == zone
                }.joinToString(", ") { it.commune }
                panel.label(places, 16f, Palette.muted)
                panel.space(9)
                if (zone != 4) {
                    panel.rule()
                    panel.space(9)
                }
            }
            panel.rule()
            panel.space(9)
            panel.label("Trajet hors zone ou spécifique : devis personnalisé.", 16f, bold = true)
            column.space(12)
        }

        sector("Forêts du Perche", "Senonches", PricingCalculator.Secteur.FORETS_DU_PERCHE)
        sector("Terres du Perche", "La Loupe", PricingCalculator.Secteur.TERRES_DE_PERCHE)
        val conditions = column.panel("Comment sont calculés les tarifs ?")
        conditions.label("Le prix dépend de la zone desservie, du nombre de passagers et du type de trajet.", 16f)
        conditions.space(10)
        conditions.label("Entre deux communes du même secteur, le tarif de la zone la plus élevée s’applique.", 16f)
        conditions.space(7)
        conditions.label("Exemple : 2 passagers en zone 4, pour un aller simple : 10 € × 2 = 20 €.", 16f, bold = true)
        conditions.space(10)
        conditions.label("Pour un aller-retour, le prix est multiplié par deux.", 16f)
        conditions.space(7)
        conditions.label("Les bagages sont inclus, sans supplément. Les trajets entre secteurs, hors zones ou spécifiques font l’objet d’un devis personnalisé.", 16f)
        conditions.space(7)
        conditions.label("Le tarif affiché est une estimation. Le montant définitif est confirmé par le service de réservation.", 16f, Palette.muted)
        column.space(12)
        column.action("Demander une navette", true, R.drawable.ic_navette) { start(Service.NAVETTE) }
        column.space(7)
        column.action("Demander un devis") { start(Service.DEVIS) }
    }

    private fun contact() {
        column.label("Contact et informations", 27f, bold = true, heading = true)
        column.space(12)
        val panel = column.panel("Phoenix LTS")
        panel.label("Transport de personnes, transport à la demande et transferts privés.", 16f)
        panel.space(12)
        panel.label("Renseignements", 15f, Palette.muted)
        panel.label("06 85 70 43 36", 20f, bold = true)
        panel.space(6)
        panel.action("Appeler pour un renseignement", iconRes = R.drawable.ic_contact) {
            open(Uri.fromParts("tel", Tariffs.INFORMATION_PHONE, null))
        }
        panel.space(12)
        panel.label("Service réservation", 15f, Palette.muted)
        panel.label("06 52 75 82 86", 20f, bold = true)
        panel.space(6)
        panel.action("Appeler les réservations", true, R.drawable.ic_contact) {
            open(Uri.fromParts("tel", Tariffs.RESERVATION_PHONE, null))
        }
        panel.space(12)
        panel.label("E-mail réservation", 15f, Palette.muted)
        panel.label(Tariffs.RESERVATION_EMAIL, 15f).setTextIsSelectable(true)
        panel.space(6)
        panel.action("Écrire au service réservation") {
            open(Uri.fromParts("mailto", Tariffs.RESERVATION_EMAIL, null))
        }
        column.space(12)
        column.action("Consulter les tarifs", iconRes = R.drawable.ic_devis) { render(Screen.TARIFFS) }
        column.space(9)
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
            "Les suggestions de communes sont intégrées à l’application. Si vous lancez une recherche d’adresse, les mots saisis sont transmis à la Géoplateforme IGN. Si vous ouvrez l’itinéraire dans Google Maps, Google reçoit le départ et l’arrivée."
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
        lateinit var field: EditText
        field = parent.pickerField(caption, value(key).takeIf(String::isNotEmpty)
            ?.let(Booking::displayDate).orEmpty(), R.drawable.ic_calendar) {
            val today = LocalDate.now(Booking.PARIS)
            val departure = if (key == "returnDate") runCatching {
                LocalDate.parse(value("date"))
            }.getOrNull() else null
            val earliest = maxOf(today, departure ?: today)
            val selected = maxOf(earliest, runCatching {
                LocalDate.parse(value(key))
            }.getOrDefault(earliest))
            DatePickerDialog(this, { _, year, month, day ->
                values[key] = LocalDate.of(year, month + 1, day).toString()
                field.setText(Booking.displayDate(value(key)))
                field.contentDescription = "$caption : ${Booking.displayDate(value(key))}"
                feedback.changed(key)
            }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                datePicker.minDate = earliest.atStartOfDay(Booking.PARIS).toInstant().toEpochMilli()
            }.show()
        }
        feedback.control(key, parent, field)
    }

    private fun timeControl(parent: LinearLayout, caption: String, key: String) {
        lateinit var field: EditText
        field = parent.pickerField(caption, value(key), R.drawable.ic_clock) {
            val selected = runCatching { LocalTime.parse(value(key)) }.getOrDefault(LocalTime.of(9, 0))
            TimePickerDialog(this, { _, hour, minute ->
                values[key] = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                field.setText(value(key))
                field.contentDescription = "$caption : ${value(key)}"
                feedback.changed(key)
            }, selected.hour, selected.minute, true).show()
        }
        feedback.control(key, parent, field)
    }

    private fun value(key: String) = values[key].orEmpty().trim()

    private fun booking() = Booking(service, value("departure"), value("arrival"), value("date"), value("time"),
        if (roundTrip) value("returnDate") else "", if (roundTrip) value("returnTime") else "",
        (values["passengers"] ?: "1").toIntOrNull() ?: -1, (values["luggage"] ?: "0").toIntOrNull() ?: -1,
        value("name"), value("phone"), value("email"), value("needs"), value("notes"), consent,
        value("departureCommune"), value("arrivalCommune"),
        value("departureDetail"), value("arrivalDetail"))

    private fun showMessage(text: String) {
        MaterialAlertDialogBuilder(this).setMessage(text).setPositiveButton("OK", null).show()
    }

    private fun errorMessage(error: Exception?): String =
        if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "Le service n’a pas autorisé l’enregistrement de cette demande. Aucun e-mail n’est confirmé. Consultez « Mes demandes » avant de réessayer ou contactez le service de réservation."
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
