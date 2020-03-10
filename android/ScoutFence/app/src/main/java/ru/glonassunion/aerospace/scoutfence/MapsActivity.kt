package ru.glonassunion.aerospace.scoutfence

import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Bundle
import android.os.Looper;
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.graphics.Color
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
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import java.util.*

class LorawanDevice(val deveui: String, var lat: Double, var lon: Double, var alarm: Boolean)
class Tracker(var device: LorawanDevice, var marker: Marker, var timestamp: Date, var tooFar: Boolean)

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, ILorawanJsonHandler {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var wserver: LWIntegrationServer
    private var trackers: MutableMap<String, Tracker>? = null

    private var location : Location? = null
    private lateinit var locationRequest: LocationRequest     
    private lateinit var locationCallback: LocationCallback

    private var circle_: Circle? = null

    private val CHANNEL_ID = "zone_notification_channel"
    private lateinit var builder: NotificationCompat.Builder
    private val ZONE_NOTIFICATION_ID = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotification()

        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(20 * 1000);

        val context = this
        locationCallback = object: LocationCallback() {            
            override fun onLocationResult(result: LocationResult?)  {
                Log.i("LoRaWAN", "Location acquired ${result}")
                result ?: return

                location = result.getLastLocation()
                updateSelfView()
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        trackers = mutableMapOf()
        map = googleMap
        wserver = LWIntegrationServer(8080, this)
        wserver.start()

        // Add a marker in Sydney and move the camera
        val moscow = LatLng(55.5, 37.5)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(moscow, 15.0f))
        
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (sharedPreferences.getString("radius", "no_value") == "no_value") {
            val editor = sharedPreferences.edit();
            editor.putString("radius", "300.0")
            editor.apply()
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.zone_notification_icon)
            .setContentTitle(getString(R.string.notify_title))
            .setContentText(getString(R.string.notify_content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    private fun updateSelfView() {
        if (location != null) {
            val newCamera = LatLng(
                location?.latitude ?: 0.0,
                location?.longitude ?:0.0
            )
            
            map.moveCamera(CameraUpdateFactory.newLatLng(newCamera))

            val sharedPreferences: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val radius = sharedPreferences.getString("radius", null)?.toDouble() ?: 300.0

            if(circle_ == null) {
                circle_ = map.addCircle(CircleOptions()
                    .center(newCamera)
                    .radius(radius)
                    .strokeColor(Color.RED))
            }
            else {
                circle_?.setCenter(newCamera)
                circle_?.setRadius(radius)
            }
        }

        val trackSnap = trackers
        trackSnap?.let {
            var shouldNotify = false
            for ((_, tracker) in it) {
                if (tracker.device.alarm || tracker.tooFar) {
                    shouldNotify = true
                }
            }

            if(!shouldNotify) {
                Log.d("LoRaWAN", "Relaxing alarms")
                val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(ZONE_NOTIFICATION_ID)
            }
        }
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
        Log.d("LoRaWAN", "Processing JSON ${json}")

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
                    val radius = sharedPreferences.getString("radius", null)?.toDouble() ?: 300.0

                    val selfPos = LatLng(selfLocation.latitude, selfLocation.longitude)
                    val distance = distanceBetween(selfPos, devicePos)

                    Log.d("LoRaWAN", "Device ${device.deveui}, distance $distance, radius $radius")

                    distance > radius
            }

            Log.i("LoRaWAN", "Device alarm state ${device.alarm.toString()}")

            val bitmap = when {
                device.alarm -> fromResource(R.drawable.marker_red)
                tooFar -> fromResource(R.drawable.marker_orange)
                else -> fromResource(R.drawable.marker_green)
            }

            val trackSnap = trackers
            trackSnap?.let {
                if(!it.containsKey(device.deveui)) {
                    val marker = map.addMarker(MarkerOptions()
                        .position(devicePos)
                        .title(device.deveui)
                        .icon(bitmap)
                    )
                    val tracker = Tracker(device, marker, Date(), tooFar)
                    it[device.deveui] = tracker
                    tracker
                }
                else
                {
                    val tracker = it.getValue(device.deveui)
                    tracker.device = device
                    val marker = tracker.marker
                    marker.position = devicePos
                    marker.setIcon(bitmap)
                    tracker.timestamp = Date()
                    tracker.tooFar = tooFar
                }
            }
            
            if (tooFar || device.alarm) {
                Log.d("LoRaWAN", "Setting alarm for tracker ${device.deveui}")
                with(NotificationManagerCompat.from(this)) {
                    notify(ZONE_NOTIFICATION_ID, builder.build())
                }
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

    override fun onResume() {
        super.onResume()
        updateSelfView()
    }
}


