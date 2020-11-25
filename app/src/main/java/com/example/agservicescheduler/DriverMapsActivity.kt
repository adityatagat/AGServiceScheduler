
package com.example.agservicescheduler

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.directions.route.*
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue
import java.util.*


class DriverMapsActivity : AppCompatActivity(), OnMapReadyCallback,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
    com.google.android.gms.location.LocationListener, RoutingListener {

    private var isLoggingOut = false
    private lateinit var mMap: GoogleMap
    private lateinit var mGoogleApiClient: GoogleApiClient
    private lateinit var mLastLocation: Location
    private lateinit var mLocationRequest: LocationRequest

    private lateinit var mLogout: Button
    private var customerId: String = ""

    private lateinit var customerPickupRef: DatabaseReference
    private var customerPickupRefListener: ValueEventListener? = null
    private var customerPickupMarker: Marker? = null

    private lateinit var mCustomerInfo: LinearLayout
    private lateinit var mCustomerImage: ImageView
    private lateinit var mCustomerName: TextView
    private lateinit var mCustomerPhone: TextView

    private lateinit var mSettings: Button

    private lateinit var mCustomerLocation: TextView
//    private lateinit var destination: String

    private var polylines: List<Polyline>? = null
    private val COLORS = intArrayOf(
        R.color.colorPrimaryDark,
        R.color.colorPrimary,
        R.color.colorAccent,
        R.color.primary_dark_material_light
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.driver_map) as SupportMapFragment
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        } else {
            mapFragment.getMapAsync(this)
        }

        mLogout = findViewById(R.id.logout)
        mLogout.setOnClickListener {
            isLoggingOut = true
            disconnectDriver()
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        getAssignedCustomer()

        //Customer Information Pane for Driver
        mCustomerLocation = findViewById(R.id.customerLocation)
        mCustomerImage = findViewById(R.id.customerImage)
        mCustomerInfo = findViewById(R.id.customerInfo)
        mCustomerName = findViewById(R.id.customerName)
        mCustomerPhone = findViewById(R.id.customerPhone)

        //Navigate to Driver Profile Settings
        mSettings = findViewById(R.id.driverSettings)
        mSettings.setOnClickListener {
            val intent = Intent(this, DriverSettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun getAssignedCustomer() {
        val driverId = FirebaseAuth.getInstance().currentUser?.uid
        val assignedCustomerRef =
            FirebaseDatabase.getInstance().reference.child("Users").child("Drivers")
                .child(driverId!!).child("customerRequest").child("customerRideId")
        assignedCustomerRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    customerId = snapshot.value.toString()
                    getAssignedCustomerPickupLocation()
//                    getAssignedCustomerDestination()
                    getAssignedCustomerInfo()
                } else {
                    customerId = ""
                    customerPickupMarker?.remove()
                    customerPickupRefListener?.let { customerPickupRef.removeEventListener(it) }
                    Toast.makeText(
                        applicationContext,
                        "No active customer requests",
                        Toast.LENGTH_LONG
                    ).show()
                    mCustomerInfo.visibility = View.GONE
                    mCustomerName.text = ""
                    mCustomerPhone.text = ""
                    mCustomerLocation.text = resources.getText(R.string.customer_location)
                    erasePolylines()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read value.", error.toException())
            }

        })
    }
/*    Function to display customer entered destination using Google Places API.
* */
/*    private fun getAssignedCustomerDestination() {
        val driverId = FirebaseAuth.getInstance().currentUser?.uid
        val assignedCustomerRef =
            FirebaseDatabase.getInstance().reference.child("Users").child("Drivers")
                .child(driverId!!).child("customerRequest").child("destination")
        assignedCustomerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    destination = snapshot.value.toString()
                    mCustomerLocation.text = "Destination: $destination"
                } else {
                    mCustomerLocation.text = "Destination: "
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read value.", error.toException())
            }

        })

    }*/

    private fun getAssignedCustomerInfo() {
        mCustomerInfo.visibility= View.VISIBLE
        val mCustomerDatabaseReference = FirebaseDatabase.getInstance().reference.child("Users").child(
            "Customers"
        ).child((customerId))
        mCustomerDatabaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.hasChildren()) {
                    val map = snapshot.getValue<Map<String, Any>>()
                    if (map != null) {
                        if (map["name"] != null) {
                            mCustomerName.text = map["name"].toString()
                        }
                        if (map["phone"] != null) {
                            mCustomerPhone.text = map["phone"].toString()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read value", error.toException())
            }

        })
    }

    private fun getAssignedCustomerPickupLocation() {

        customerPickupRef =
            FirebaseDatabase.getInstance().reference.child("customerRequest").child(customerId)
                .child("l")
        customerPickupRefListener =
            customerPickupRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && customerId == "") {
                        val data = snapshot.getValue<List<Double>>()
                        var locationLat = 0.0
                        var locationLng = 0.0

                        if (data != null) {
                            locationLat = data[0].toString().toDouble()
                            locationLng = data[1].toString().toDouble()
                        }
                        val pickupLatLng = LatLng(locationLat, locationLng)
                        customerPickupMarker = mMap.addMarker(MarkerOptions().position(pickupLatLng).title("Customer Location"))
                        getRouteToMarker(pickupLatLng)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Failed to read value.", error.toException())
                }
            })
    }

    private fun getRouteToMarker(pickupLatLng: LatLng) {
        val routing: Routing = Routing.Builder()
            .key(R.string.google_maps_key.toString())
            .travelMode(AbstractRouting.TravelMode.DRIVING)
            .withListener(this)
            .alternativeRoutes(false)
            .waypoints(LatLng(mLastLocation.latitude, mLastLocation.longitude), pickupLatLng)
            .build()
        routing.execute()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
            //return
        }
        buildGoogleApiClient()
        mMap.isMyLocationEnabled = true
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    private fun buildGoogleApiClient() {
        mGoogleApiClient = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build()
        mGoogleApiClient.connect()
    }

    override fun onConnected(p0: Bundle?) {
        // Set interval to 1000 ms
        mLocationRequest = LocationRequest()
        mLocationRequest.apply {
            interval = 1000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
            //return
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
            mGoogleApiClient,
            mLocationRequest,
            this
        )
    }

    override fun onConnectionSuspended(p0: Int) {
        TODO("Not yet implemented")
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        TODO("Not yet implemented")
    }

    override fun onLocationChanged(location: Location?) {
        if (applicationContext != null) {
            if (location != null) {
                mLastLocation = location

            }
            val latLng = LatLng(mLastLocation.latitude, mLastLocation.longitude)
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11.0F))

            val userId = FirebaseAuth.getInstance().currentUser?.uid
            val driversAvailableRef =
                FirebaseDatabase.getInstance().getReference("driversAvailable")
            val driversWorkingRef = FirebaseDatabase.getInstance().getReference("driversWorking")
            //Save Location Info against the userId to the database using GeoFire
            val geoFireAvailable = GeoFire(driversAvailableRef)
            val geoFireWorking = GeoFire(driversWorkingRef)
            if (customerId == "") {
                geoFireWorking.removeLocation(userId)
                geoFireAvailable.setLocation(
                    userId,
                    GeoLocation(mLastLocation.latitude, mLastLocation.longitude)
                )
            } else {
                geoFireAvailable.removeLocation(userId)
                geoFireWorking.setLocation(
                    userId,
                    GeoLocation(mLastLocation.latitude, mLastLocation.longitude)
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.driver_map) as SupportMapFragment
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mapFragment.getMapAsync(this)
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Please provide location permissions for this app!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }
    }
    private fun disconnectDriver() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val ref = FirebaseDatabase.getInstance().getReference("driversAvailable")
        //Stop saving location Info to DB using GeoFire
        val geoFire = GeoFire(ref)
        geoFire.removeLocation(userId)
    }

    override fun onRoutingFailure(p0: RouteException?) {
        // The Routing request failed

        if(p0 != null) {
            Toast.makeText(this, "Error: " + p0.message, Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }

    override fun onRoutingStart() {
        TODO("Not yet implemented")
    }

    override fun onRoutingSuccess(route: ArrayList<Route>?, shortestRouteIndex: Int) {
        val center: CameraUpdate = CameraUpdateFactory.newLatLng(
            LatLng(
                mLastLocation.latitude,
                mLastLocation.longitude
            )
        )
        val zoom: CameraUpdate = CameraUpdateFactory.zoomTo(16f)

        mMap.moveCamera(center)

        erasePolylines()

        polylines = ArrayList()
        //add route(s) to the map.

        if (route != null) {
            for (i in 0 until route.size) {

                //In case of more than 5 alternative routes
                val colorIndex: Int = i % COLORS.size
                val polyOptions = PolylineOptions()
                polyOptions.color(resources.getColor(COLORS[colorIndex]))
                polyOptions.width((10 + i * 3).toFloat())
                polyOptions.addAll(route[i].points)
                val polyline: Polyline = mMap.addPolyline(polyOptions)
                (polylines as ArrayList<Polyline>).add(polyline)
                Toast.makeText(
                    applicationContext,
                    "Route " + (i + 1) + ": distance - " + route[i]
                        .distanceValue + ": duration - " + route[i].durationValue,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onRoutingCancelled() {
        TODO("Not yet implemented")
    }

    private fun erasePolylines() {
        if(polylines!=null) {
            for (poly in polylines!!) {
                poly.remove()
            }
        }
    }


    override fun onStop() {
        super.onStop()
        if(!isLoggingOut) {
            disconnectDriver()
        }
    }

    companion object {
        private const val LOCATION_REQUEST_CODE: Int = 1
        private const val TAG = "DriverMapsActivity"
    }


}