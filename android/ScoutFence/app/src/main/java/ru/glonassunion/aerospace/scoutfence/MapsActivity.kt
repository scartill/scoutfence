package ru.glonassunion.aerospace.scoutfence

import android.app.PendingIntent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

import com.beust.klaxon.Klaxon
import java.lang.Exception

class LorawanQuery(val deveui: String?, var lat: Double?, var lon: Double?)

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, ILorawanJsonHandler {

    private lateinit var mMap: GoogleMap
    private lateinit var wserver: LWIntegrationServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        wserver = LWIntegrationServer(8080, this)
        wserver.start()

        // Add a marker in Sydney and move the camera
        val sydney = LatLng(-34.0, 151.0)
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    override fun HandleLorawanJSON(json: String): Boolean {
        try {
            Log.i("LoraWAN", json)

            val parseResult = Klaxon().parse<LorawanQuery>(json)

            parseResult?.let {
                val newMarker = LatLng(it.lat ?: 0.0, it.lon ?: 0.0)
                mMap.addMarker(MarkerOptions().position(newMarker).title(it.deveui))
            }
        }
        catch (e: Exception)
        {
            val message = e.message ?: "unknown"
            Log.e("LoRaWAN", message)
            return false
        }
        return true
    }
}


