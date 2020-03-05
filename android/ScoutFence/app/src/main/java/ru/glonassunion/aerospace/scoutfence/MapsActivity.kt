package ru.glonassunion.aerospace.scoutfence

import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Bundle
import android.os.Looper;
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.beust.klaxon.Klaxon
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.util.*

class LorawanDevice(val deveui: String, var lat: Double, var lon: Double, var alarm: Boolean)
class Tracker(var device: LorawanDevice, var marker: Marker, var timestamp: Date)

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, ILorawanJsonHandler {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var wserver: LWIntegrationServer
    private lateinit var trackers: MutableMap<String, Tracker>

    private var location : Location? = null
    private lateinit var locationRequest: LocationRequest     
    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val task = fusedLocationClient.getLastLocation().addOnSuccessListener { location : Location? ->
            Log.i("LoRaWAN", "Initial location acquired ${location}")
        }
        task.addOnFailureListener { e: Exception ->
            Log.e("LoRaWAN", "getLastLocation() failed with $e")
        }

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(20 * 1000);

        locationCallback = object: LocationCallback() {            
            override fun onLocationResult(result: LocationResult?)  {
                Log.i("LoRaWAN", "Location acquired ${result}")
                result ?: return

                location = result.getLastLocation()

                if (location != null) {
                    val newCamera = LatLng(
                        location?.latitude ?: 0.0,
                        location?.longitude ?:0.0
                    )
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(newCamera))
                }
            }
        }
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
        
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val editor = sharedPreferences.edit();
        editor.putInt("radius", 300)
        editor.apply()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }


    private fun distanceBetween(a: LatLng, b: LatLng) : Float {
        var results : FloatArray = FloatArray(1)
        Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            results)
        return results[0]
    }

    override fun HandleLorawanJSON(json: String): Boolean {
        try {
            val device = Klaxon().parse<LorawanDevice>(json)
                ?: throw IllegalArgumentException("Unable to parse JSON: $json")

            val devicePos = LatLng(device.lat, device.lon)

            val selfLocation = this.location
            val tooFar = if (selfLocation == null) {
                    Log.w("LoRaWAN", "Unable to acquire self location")
                    false
                }
                else {
                    val sharedPreferences: SharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(applicationContext)
                    val radius = sharedPreferences.getInt("radius", 0)

                    val selfPos = LatLng(selfLocation.latitude, selfLocation.longitude)
                    val distance = distanceBetween(selfPos, devicePos)

                    Log.d("LoRaWAN", "Device ${device.deveui}, distance $distance, radius $radius")

                    distance > radius
            }

            Log.i("LoRaWAN", "Device alerm state ${device.alarm.toString()}")

            val bitmap = when {
                device.alarm -> fromResource(R.drawable.marker_red)
                tooFar -> fromResource(R.drawable.marker_orange)
                else -> fromResource(R.drawable.marker_green)
            }

            if(!trackers.containsKey(device.deveui)) {
                val marker = mMap.addMarker(MarkerOptions()
                    .position(devicePos)
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
                marker.position = devicePos
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        return true
    }
}


