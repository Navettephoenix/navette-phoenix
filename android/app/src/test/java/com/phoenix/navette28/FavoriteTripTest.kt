package com.phoenix.navette28

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class FavoriteTripTest {
    @Test fun favoriteRequiresExplicitSaveAndCanBeDeleted() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val prefs = controller.get().getSharedPreferences("favorite-test", Context.MODE_PRIVATE)
            FavoriteTrip.clear(prefs)
            assertNull(FavoriteTrip.load(prefs))

            val route = mapOf(
                "departureCommune" to "Senonches",
                "departureDetail" to "Mairie",
                "departure" to "Senonches · Mairie",
                "arrivalCommune" to "La Loupe",
                "arrivalDetail" to "Gare",
                "arrival" to "La Loupe · Gare",
                "name" to "Nom privé",
                "email" to "client@example.test"
            )
            FavoriteTrip.save(prefs, route)
            val saved = FavoriteTrip.load(prefs)!!
            assertEquals("Senonches · Mairie", saved["departure"])
            assertEquals("La Loupe · Gare", saved["arrival"])
            assertFalse(saved.containsKey("name"))
            assertFalse(saved.containsKey("email"))
            assertEquals(6, saved.size)
            FavoriteTrip.clear(prefs)
            assertNull(FavoriteTrip.load(prefs))
        }
    }

    @Test fun invalidRouteCannotBeStored() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val prefs = controller.get().getSharedPreferences("favorite-invalid-test", Context.MODE_PRIVATE)
            FavoriteTrip.clear(prefs)
            var rejected = false
            try {
                FavoriteTrip.save(prefs, mapOf("departureCommune" to "Senonches"))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertNull(FavoriteTrip.load(prefs))
        }
    }
}
