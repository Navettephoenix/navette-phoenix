package com.phoenix.navette28

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vérifications hors réseau : aucune adresse n'est inventée ni prise dans une autre commune. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AddressLookupTest {
    private val response = """
      {
        "features": [
          { "properties": { "label": "1 Rue de la Mairie 28250 Senonches", "city": "Senonches" } },
          { "properties": { "label": "9 Rue du Centre 28240 La Loupe", "city": "La Loupe" } },
          { "properties": { "label": "2 Rue de la Mairie 28250 SENONCHES", "city": "SENONCHES" } },
          { "properties": { "label": "", "city": "Senonches" } }
        ]
      }
    """.trimIndent()

    @Test fun filtersOutAddressesInTheWrongCommune() {
        val found = AddressLookup.parseSuggestions(response, "Senonches")
        assertEquals(2, found.size)
        assertTrue(found.all { it.contains("Senonches", ignoreCase = true) })
        assertEquals("1 Rue de la Mairie 28250 Senonches", found.first())
    }

    @Test fun theOtherCommuneOptionAllowsAnyRealResultButNotBlankLabels() {
        assertEquals(3, AddressLookup.parseSuggestions(response, null).size)
    }

    @Test fun aMissingResponseContainsNoFabricatedAddress() {
        assertTrue(AddressLookup.parseSuggestions("""{"features": []}""", "Senonches").isEmpty())
    }
}
