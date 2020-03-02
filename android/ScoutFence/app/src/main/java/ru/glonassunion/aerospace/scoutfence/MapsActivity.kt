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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory.*
import com.google.android.gms.maps.model.Marker
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.*

class LorawanDevice(val deveui: String, var lat: Double, var lon: Double, var alarm: Boolean)
class Tracker(var device: LorawanDevice, var marker: Marker, var timestamp: Date)

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, ILorawanJsonHandler {

    private lateinit var mMap: GoogleMap
    private lateinit var wserver: LWIntegrationServer

    private lateinit var trackers: MutableMap<String, Tracker>

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
        trackers = mutableMapOf()
        mMap = googleMap
        wserver = LWIntegrationServer(8080, this)
        wserver.start()

        // Add a marker in Sydney and move the camera
        val moscow = LatLng(55.5, 37.5)
        mMap.moveCamera(CameraUpdateFactory.newLatLng(moscow))
    }

    override fun HandleLorawanJSON(json: String): Boolean {
        try {
            val device = Klaxon().parse<LorawanDevice>(json)
                ?: throw IllegalArgumentException("Unable to parse JSON: $json")

            val position = LatLng(device.lat, device.lon)
            val tooFar = false
            Log.i("Alarm", device.alarm.toString())
            val bitmap = if (device.alarm)
                    fromResource(R.drawable.marker_red)
                else if (tooFar)
                    fromResource(R.drawable.marker_orange)
                else
                    fromResource(R.drawable.marker_green)

            if(!trackers.containsKey(device.deveui)) {
                val marker = mMap.addMarker(MarkerOptions()
                    .position(position)
                    .title(device.deveui)
                    .icon(bitmap)
                )
                val tracker = Tracker(device, marker, Date())
                trackers[device.deveui] = tracker
            }
            else
            {
                val tracker = trackers.getValue(device.deveui)
                val marker = tracker.marker
                marker.position = position
                marker.setIcon(bitmap)
                tracker.timestamp = Date()
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


