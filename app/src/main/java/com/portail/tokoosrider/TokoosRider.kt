package com.portail.tokoosrider

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.pusher.client.channel.SubscriptionEventListener
import com.pusher.pushnotifications.PushNotifications.subscribe
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.gson.Gson
import com.portail.tokoosrider.models.PubnubLocation
import com.portail.tokoosrider.models.PubnubMessage
import com.portail.tokoosrider.models.Ride
import com.portail.tokoosrider.models.Rider
import com.portail.tokoosrider.providers.LocalData
import com.portail.tokoosrider.providers.RiderAuth
import com.portail.tokoosrider.utils.UtilData
import com.pubnub.api.PNConfiguration
import com.pubnub.api.PubNub
import com.pubnub.api.callbacks.PNCallback
import com.pubnub.api.callbacks.SubscribeCallback
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult
import com.pusher.client.channel.PusherEvent
import kotlinx.android.synthetic.main.fragment_home_bottom_sheet.*
import kotlinx.android.synthetic.main.ride_request_layout.*
import kotlinx.android.synthetic.main.ride_request_layout.view.*
import kotlinx.android.synthetic.main.welcome_layout.view.*
import java.util.*


class TokoosRider : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var polyline: Polyline

    private val REQUEST_FINE_LOCATION = 1

    private lateinit var geocoder: Geocoder
    private var mLocationRequest: LocationRequest? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val UPDATE_INTERVAL = (10 * 1000).toLong()
    private val FASTEST_INTERVAL = (5 * 1000).toLong()
    private val MAP_ZOOM = 17.0f

    private var currentAddress: String  = ""
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    private lateinit var riderAuth: RiderAuth
    private lateinit var rider: Rider

    private lateinit var pubNub: PubNub

    private lateinit var localData: LocalData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        localData = LocalData(this@TokoosRider)

        riderAuth = RiderAuth(this@TokoosRider)
        rider = riderAuth.getRider()!!

        var greetingText = "Bonjour"

        welcome_layout.rider_name.text = rider.fullName
        welcome_layout.rider_rating.rating = rider.rate.toFloat()

        Timer().scheduleAtFixedRate(object: TimerTask() {
            override fun run() {
                val now = Calendar.getInstance()
                val amPm = now.get(Calendar.AM_PM)
                val hours = now.get(Calendar.HOUR)
                greetingText = if(amPm == Calendar.AM) { "Bonjour" } else {
                    if(hours <= 3) { "Bon Après-Midi" } else {
                        if(hours <= 9) { "Bonsoir" } else { "Bonne Nuit" }
                    }
                }
                welcome_layout.greeting.text = greetingText
            }
        }, 0, (20 * 60 * 1000))

        geocoder = Geocoder(this@TokoosRider, Locale.getDefault())

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = FusedLocationProviderClient(this@TokoosRider)

        val options = PusherOptions()
        options.setCluster("mt1")
        val pusher = Pusher("b13b7c90eb776ee89b77", options)

        val channel = pusher.subscribe(rider.channel)
        channel.bind("my-event") {  }
        pusher.connect()

        val pubnubConfig = PNConfiguration()
        pubnubConfig.subscribeKey = UtilData.PUBNUB_SUBSCRIBE_KEY
        pubnubConfig.publishKey = UtilData.PUBNUB_PUBLISH_KEY
        pubnubConfig.isSecure = true

        pubNub = PubNub(pubnubConfig)
        pubNub.subscribe().channels(listOf(rider.channel)).execute()

        pubNub.addListener(object : SubscribeCallback() {
            override fun status(pubnub: PubNub?, status: PNStatus?) {}
            override fun presence(pubnub: PubNub?, presence: PNPresenceEventResult?) {}
            override fun message(pubnub: PubNub?, message: PNMessageResult?) {
                try {
                    val data = Gson().fromJson(message?.message!!.asString, PubnubMessage::class.java)

                    when(data.type) {

                        UtilData.TYPE_RIDER_REQUEST -> {

                            val riderData = mutableMapOf<String, Any>()
                            riderData["rider_id"] = rider.id

                            runOnUiThread {
                                if(localData.hasActiveRide()) {
                                    fusedLocationClient.lastLocation.addOnSuccessListener {
                                        if (it != null) {
                                            try {
                                                runOnUiThread {
                                                    val riderLocation =
                                                        PubnubLocation(it.latitude, it.longitude)
                                                    val response = PubnubMessage(
                                                        UtilData.TYPE_RIDER_REQUEST,
                                                        "Je suis disponible",
                                                        rider.channel, riderLocation, riderData
                                                    )
                                                    pubNub.publish()
                                                        .message(Gson().toJson(response))
                                                        .channel(data.channel).async(object :
                                                        PNCallback<PNPublishResult>() {
                                                        override fun onResponse(
                                                            result: PNPublishResult?,
                                                            status: PNStatus?
                                                        ) {
                                                        }
                                                    })
                                                }
                                            } catch (e: Exception) {
                                                runOnUiThread {
                                                    val riderLocation = PubnubLocation(
                                                        currentLatitude,
                                                        currentLongitude
                                                    )
                                                    val response = PubnubMessage(
                                                        UtilData.TYPE_RIDER_REQUEST,
                                                        "Je suis disponible",
                                                        rider.channel, riderLocation, riderData
                                                    )
                                                    pubNub.publish()
                                                        .message(Gson().toJson(response))
                                                        .channel(data.channel).async(object :
                                                        PNCallback<PNPublishResult>() {
                                                        override fun onResponse(
                                                            result: PNPublishResult?,
                                                            status: PNStatus?
                                                        ) {
                                                        }
                                                    })
                                                }
                                            }
                                        }
                                    }.addOnFailureListener {
                                        runOnUiThread {
                                            val riderLocation =
                                                PubnubLocation(currentLatitude, currentLongitude)
                                            val response = PubnubMessage(
                                                UtilData.TYPE_RIDER_REQUEST,
                                                "Je suis disponible",
                                                rider.channel, riderLocation, riderData
                                            )
                                            pubNub.publish().message(Gson().toJson(response))
                                                .channel(data.channel)
                                                .async(object : PNCallback<PNPublishResult>() {
                                                    override fun onResponse(
                                                        result: PNPublishResult?,
                                                        status: PNStatus?
                                                    ) {
                                                    }
                                                })
                                        }
                                    }
                                }
                            }
                        }

                        UtilData.TYPE_RIDER_LOCATION -> {
                            try {
                                runOnUiThread {
                                    val ride = Gson().fromJson(data.data?.get("ride").toString(), Ride::class.java)
                                    localData.saveCurrentRide(Gson().toJson(ride))
                                    startLocationUpdates(data.channel)
                                    welcome_layout.visibility = View.GONE
                                    ride_request_layout.visibility = View.VISIBLE

                                    ride_request_layout.user_name.text = ride.user.name
                                    ride_request_layout.origin_text.text = ride.origin.address
                                    ride_request_layout.destination_text.text = ride.destination.address

                                    ride_request_layout.cancel_ride.setOnClickListener {

                                    }
                                }
                            } catch (e: java.lang.Exception) {}
                        }
                    }
                } catch (e: java.lang.Exception) {  }
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap

        if(checkPermissions()) {
            mMap.isMyLocationEnabled = true
            mMap.setOnMyLocationButtonClickListener { false }
        } else { requestPermissions() }

        mMap.setOnMyLocationClickListener {
            val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
            mMap.addMarker(MarkerOptions().position(LatLng(it.latitude, it.longitude)).title(addresses[0].getAddressLine(0)))
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), MAP_ZOOM))
            this.setLocationVariable(addresses[0].getAddressLine(0), it.latitude, it.longitude)
        }

        if(checkPermissions()){
            fusedLocationClient.lastLocation.addOnSuccessListener {
                if(it != null){
                    try {
                        startLocationUpdates(rider.channel)
                        val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), MAP_ZOOM))
                        this.setLocationVariable(addresses[0].getAddressLine(0), it.latitude, it.longitude)
                    } catch (e: Exception){}
                }
            }.addOnFailureListener {
                runOnUiThread {
                    Toast.makeText(this@TokoosRider, it.message, Toast.LENGTH_LONG).show()
                }
            }
        } else { this.requestPermissions() }
    }

    private fun setLocationVariable(address: String, latitude: Double, longitude: Double){
        runOnUiThread {
            this.currentAddress = address
            this.currentLatitude = latitude
            this.currentLongitude = longitude
        }
    }

    override fun onBackPressed() {  }

    private fun startLocationUpdates(channel: String?) {

        mLocationRequest = LocationRequest()
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest!!.interval = UPDATE_INTERVAL
        mLocationRequest!!.fastestInterval = FASTEST_INTERVAL

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest!!)
        val locationSettingsRequest = builder.build()

        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(locationSettingsRequest)

        LocationServices.getFusedLocationProviderClient(this@TokoosRider).requestLocationUpdates(
            mLocationRequest, object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult?) {
                    if(locationResult != null) {
                        onLocationChanged(locationResult.lastLocation, channel)
                    }
                }
            },
            Looper.myLooper()
        )
    }

    private fun onLocationChanged(location: Location?, channel: String?) {
        if (location != null) {
            try {
                runOnUiThread {
                    val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), MAP_ZOOM))
                    setLocationVariable(address[0].getAddressLine(0), location.latitude, location.longitude)

                    if(channel != null) {
                        val riderLocation =
                            PubnubLocation(currentLatitude, currentLongitude)
                        val response = PubnubMessage(
                            UtilData.TYPE_RIDER_LOCATION,
                            "Ma location",
                            rider.channel, riderLocation, null
                        )

                        pubNub.publish().message(Gson().toJson(response))
                            .channel(channel)
                            .async(object : PNCallback<PNPublishResult>() {
                                override fun onResponse(
                                    result: PNPublishResult?,
                                    status: PNStatus?
                                ) {
                                }
                            })
                    }
                }
            } catch (e: java.lang.Exception){  }
        }
    }

    private fun checkPermissions(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) { true } else { requestPermissions(); false }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_FINE_LOCATION
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            REQUEST_FINE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startActivity(Intent(this@TokoosRider, TokoosRider::class.java))
                }
            }
        }
    }
}
