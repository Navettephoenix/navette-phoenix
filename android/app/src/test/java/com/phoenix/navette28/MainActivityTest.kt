package com.phoenix.navette28

import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import android.widget.TimePicker
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], qualifiers = "fr-rFR-w360dp-h640dp")
class MainActivityTest {
    @Test fun homeOffersDirectAccessToTheMainActions() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val window = activity.window.decorView
            assertTrue(window.labels().contains("Navette Phoenix 28"))
            for (label in listOf(
                "Réserver une navette", "Demander un devis", "Nos destinations", "Mes demandes",
                "Notre site officiel", "Politique de confidentialité",
            )) {
                assertTrue(window.button(label).minHeight >= activity.dp(48))
            }
            window.button("Politique de confidentialité").performClick()
            assertTrue(window.labels().contains("Confidentialité et données personnelles"))
            window.button("Retour").performClick()
            assertTrue(window.labels().contains("Navette Phoenix 28"))
        }
    }

    @Test fun addressSearchRequiresCommuneAndThreeCharactersBeforeConnecting() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val window = controller.get().window.decorView
            window.button("Réserver une navette").performClick()
            // Deux sections possèdent le même libellé : ouvrir celle du départ.
            window.descendants().filterIsInstance<MaterialButton>()
                .first { it.text.toString() == "Recherche d’adresse (optionnelle)  +" }
                .performClick()
            val search = window.descendants().filterIsInstance<MaterialButton>()
                .first { it.text.toString() == "Rechercher une adresse (IGN)" }
            search.performClick()
            assertTrue(window.labels().contains("Choisissez d’abord votre commune."))
            window.chooseCommune("Départ", "Senonches")
            val detail = window.field("Départ · Adresse précise").editText!!
            detail.setText("ru")
            search.performClick()
            assertEquals("Saisissez au moins trois caractères avant de rechercher.", detail.error.toString())
        }
    }

    @Test fun bookingHighlightsMissingCommuneAndUpdatesEstimatedPrice() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val window = controller.get().window.decorView
            window.button("Réserver une navette").performClick()
            window.button("Continuer").performClick()
            assertTrue(window.labels().contains("Choisissez votre commune de départ."))
            window.chooseCommune("Départ", "Digny")
            window.chooseCommune("Arrivée", "Senonches")
            assertTrue(window.labels().contains("Zone 2 · 6 € par passager"))
            assertTrue(window.labels().contains("Total : 6 €"))
            val increase = window.descendants().single { it.contentDescription == "Ajouter un passager" }
            repeat(2) { increase.performClick() }
            assertTrue(window.labels().contains("Total : 18 €"))
            window.descendants().filterIsInstance<MaterialCheckBox>().single().isChecked = true
            assertTrue(window.labels().contains("Total aller-retour : 36 €"))
        }
    }

    @Test fun quoteCanBeReviewedAndEditedWithoutReenteringDetails() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            var window = controller.get().window.decorView
            window.button("Demander un devis").performClick()
            window.chooseCommune("Départ", "Senonches")
            window.chooseCommune("Arrivée", "La Loupe")
            window.field("Date du départ").editText!!.performClick()
            val day = LocalDate.now(Booking.PARIS).plusDays(7)
            val datePicker = ShadowDialog.getLatestDialog() as DatePickerDialog
            datePicker.datePicker.updateDate(day.year, day.monthValue - 1, day.dayOfMonth)
            datePicker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Booking.displayDate(day.toString()), window.field("Date du départ").editText!!.text.toString())
            window.field("Heure de prise en charge").editText!!.performClick()
            val timePicker = ShadowDialog.getLatestDialog() as TimePickerDialog
            timePicker.window!!.decorView.descendants().filterIsInstance<TimePicker>().single().apply {
                hour = 10
                minute = 15
            }
            timePicker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("10:15", window.field("Heure de prise en charge").editText!!.text.toString())
            val increase = window.descendants().single { it.contentDescription == "Ajouter un passager" }
            repeat(3) { increase.performClick() }
            assertFalse(increase.isEnabled)
            window.button("Continuer").performClick()
            window.field("Nom et prénom").editText!!.setText("Client démo")
            window.field("Téléphone").editText!!.setText("0600000000")
            window.field("E-mail").editText!!.setText("client@example.test")
            window.descendants().filterIsInstance<MaterialCheckBox>().single().isChecked = true
            window.button("Vérifier ma demande").performClick()
            assertTrue(window.labels().containsAll(listOf("4 passagers · 0 bagage", "Client démo", "Tarif sur devis")))
            window.button("Modifier le trajet").performClick()
            window.chooseCommune("Arrivée", "Autre commune")
            window.field("Arrivée · Adresse précise").editText!!.setText("Gare de Chartres")
            controller.recreate()
            window = controller.get().window.decorView
            assertEquals("Gare de Chartres", window.field("Arrivée · Adresse précise").editText!!.text.toString())
            window.button("Enregistrer les modifications").performClick()
            assertTrue(window.labels().containsAll(listOf("Gare de Chartres", "client@example.test")))
            window.button("Modifier mes coordonnées").performClick()
            window.field("Nom et prénom").editText!!.setText("Édition annulée")
            window.button("Retour").performClick()
            assertTrue(window.labels().contains("Client démo"))
            assertFalse(window.labels().contains("Édition annulée"))
        }
    }

    @Test fun anOtherCommuneCanStillBeUsedForMapsAndQuote() {
        if (!BuildConfig.GOOGLE_MAIL) return
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val window = controller.get().window.decorView
            window.button("Réserver une navette").performClick()
            window.chooseCommune("Départ", "Autre commune")
            window.field("Départ · Adresse précise").editText!!.setText("8 rue de l’Église & du Parc")
            window.chooseCommune("Arrivée", "La Loupe")
            window.field("Arrivée · Adresse précise").editText!!.setText("Gare")
            assertTrue(window.labels().contains("Tarif sur devis"))
            window.button("Voir dans Google Maps").performClick()
            val uri = shadowOf(controller.get()).nextStartedActivity.data!!
            assertEquals("www.google.com", uri.host)
            assertEquals("8 rue de l’Église & du Parc", uri.getQueryParameter("origin"))
            assertEquals("La Loupe · Gare", uri.getQueryParameter("destination"))
        }
    }

    @Test fun aPendingRequestSurvivesRecreationAndShowsOneReceipt() {
        val saved = Bundle().apply { putString("screen", "REVIEW") }
        Robolectric.buildActivity(MainActivity::class.java).setup(saved).use { controller ->
            val result = TaskCompletionSource<SubmissionReceipt>()
            val submission = ViewModelProvider(controller.get())[ReservationSubmission::class.java]
            val pending = submission.start { result.task }
            assertSame(pending, submission.start { error("Duplicate submission") })
            controller.recreate()
            val window = controller.get().window.decorView
            assertFalse(window.button("Envoi en cours…").isEnabled)
            result.setResult(SubmissionReceipt("NP-DEMO", "PENDING", false))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(window.labels().containsAll(listOf("Demande enregistrée", "NP-DEMO", "◷  2. Transmission en attente")))
            assertNull(submission.pending)
            controller.recreate()
            assertTrue(controller.get().window.decorView.labels().contains("NP-DEMO"))
        }
    }

    @Test fun historyKeepsLastResultsOnErrorAndRefreshesTheirStatus() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val content = activity.column()
            activity.setContentView(content)
            val first = RequestSummary("NP-DEMO", "PENDING", "DEVIS", "Senonches", "La Loupe", "2027-04-16", "10:15")
            var next = Tasks.forResult(listOf(first, RequestSummary("NP-OLD", "CONFIRMED")))
            content.requestHistory("NP-DEMO") { next }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(content.labels().containsAll(listOf("Demande de devis", "16 avril 2027 à 10:15",
                "Départ · Senonches", "NP-OLD", statusLabel("CONFIRMED"))))
            next = Tasks.forException(IllegalStateException("offline"))
            content.button("Actualiser").performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(content.labels().containsAll(listOf(activity.getString(R.string.history_retry), "NP-DEMO")))
            next = Tasks.forResult(listOf(first.copy(status = "SENT")))
            content.button("Actualiser").performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(content.labels().contains(statusLabel("SENT")))
            assertFalse(content.labels().contains(statusLabel("PENDING")))
        }
    }

    private fun View.chooseCommune(label: String, commune: String) {
        field("$label · Commune").editText!!.performClick()
        val dialog = ShadowDialog.getLatestDialog()
        val list = dialog.window!!.decorView.descendants().filterIsInstance<ListView>().single()
        val position = (0 until list.adapter.count).single {
            list.adapter.getItem(it).toString() == commune
        }
        list.performItemClick(null, position, list.adapter.getItemId(position))
    }

    private fun View.field(hint: String) =
        descendants().filterIsInstance<TextInputLayout>().single { it.hint == hint }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }

    private fun View.labels() = descendants().filterIsInstance<TextView>().map { it.text.toString() }.toSet()

    private fun View.button(label: String) =
        descendants().filterIsInstance<MaterialButton>().single { it.text.toString() == label }
}
