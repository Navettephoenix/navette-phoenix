package com.phoenix.navette28

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.NumberFormat
import java.util.Locale

class RouteActivity : FragmentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val from = intent.getStringExtra("from") ?: return finish()
        val to = intent.getStringExtra("to") ?: return finish()
        val content = column().apply { setBackgroundColor(Palette.paper) }
        actionBar(content, from, to)
        setContentView(content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            content.setPadding(dp(16) + bars.left, dp(12) + bars.top,
                dp(16) + bars.right, dp(12) + bars.bottom)
            insets
        }
        val status = content.label("Calcul de l’itinéraire…", 15f, Palette.muted)
        content.space(9)
        val gateway = FirebaseGateway(applicationContext)
        if (!gateway.available || !BuildConfig.HAS_MAPS) {
            status.text = "Utilisez Google Maps pour afficher le trajet, la distance et la durée."
            return
        }
        val holder = FrameLayout(this).apply { id = MAP_CONTAINER }
        content.addView(holder, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        val fragment = supportFragmentManager.findFragmentByTag("maps") as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction().replace(MAP_CONTAINER, it, "maps").commit()
            }
        gateway.call("computeRoute", mapOf("from" to from, "to" to to)).addOnCompleteListener(this) { task ->
            val route = if (task.isSuccessful) task.result as? Map<*, *> else null
            val polyline = route?.get("polyline") as? String
            val metres = route?.get("distanceMeters") as? Number
            val seconds = route?.get("durationSeconds") as? Number
            val points = polyline?.let { runCatching { decode(it) }.getOrNull() }.orEmpty()
            if (points.size < 2 || metres == null || seconds == null) {
                status.text = "Le calcul est indisponible. Vous pouvez continuer dans Google Maps."
                return@addOnCompleteListener
            }
            val km = NumberFormat.getNumberInstance(Locale.FRANCE).apply { maximumFractionDigits = 1 }
                .format(metres.toDouble() / 1000)
            val minutes = (seconds.toInt() + 59) / 60
            status.text = "$km km · environ $minutes min"
            fragment.getMapAsync { map ->
                map.addPolyline(PolylineOptions().addAll(points).color(Palette.sun).width(dp(5).toFloat()))
                map.addMarker(MarkerOptions().position(points.first()).title("Départ"))
                map.addMarker(MarkerOptions().position(points.last()).title("Arrivée"))
                val bounds = LatLngBounds.builder().also { builder -> points.forEach { builder.include(it) } }.build()
                map.setOnMapLoadedCallback { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(35))) }
            }
        }
    }

    private fun actionBar(content: android.widget.LinearLayout, from: String, to: String) {
        content.action("‹  Retour") { finish() }
        content.space(12)
        content.label("Votre itinéraire", 27f, bold = true)
        content.label("$from → $to", 15f)
        content.space(8)
        content.action("Ouvrir dans Google Maps") {
            val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                .appendQueryParameter("api", "1")
                .appendQueryParameter("origin", from)
                .appendQueryParameter("destination", to)
                .appendQueryParameter("travelmode", "driving").build()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                android.app.AlertDialog.Builder(this)
                    .setMessage("Aucune application ne peut afficher l’itinéraire.")
                    .setPositiveButton("OK", null).show()
            }
        }
        content.space(10)
    }

    private fun decode(encoded: String): List<LatLng> {
        require(encoded.length <= 250_000)
        var index = 0
        var latitude = 0
        var longitude = 0
        fun coordinate(): Int {
            var result = 0
            var shift = 0
            do {
                require(index < encoded.length && shift <= 25)
                val next = encoded[index++].code - 63
                require(next in 0..63)
                result = result or ((next and 31) shl shift)
                shift += 5
            } while (next >= 32)
            return if ((result and 1) == 1) (result shr 1).inv() else result shr 1
        }
        return buildList {
            while (index < encoded.length) {
                latitude += coordinate()
                longitude += coordinate()
                add(LatLng(latitude / 100000.0, longitude / 100000.0))
            }
        }
    }

    private companion object {
        val MAP_CONTAINER = View.generateViewId()
    }
}
