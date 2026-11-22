package com.phoenix.navette28

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

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

    @Test fun bookingWithEmptyAddressesShowsAnError() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val window = controller.get().window.decorView
            window.button("Réserver une navette").performClick()
            window.button("Continuer").performClick()
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog.window!!.decorView.labels().contains(
                "Indiquez une adresse de départ et une adresse d’arrivée."
            ))
        }
    }

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
