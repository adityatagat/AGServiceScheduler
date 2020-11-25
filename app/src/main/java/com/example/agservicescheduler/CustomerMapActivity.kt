package com.example.agservicescheduler

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue

class CustomerMapActivity : AppCompatActivity(), OnMapReadyCallback,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
    com.google.android.gms.location.LocationListener {


    private lateinit var requestService: String
    private lateinit var mMap: GoogleMap
    private lateinit var mGoogleApiClient: GoogleApiClient
    private lateinit var mLastLocation: Location
    private lateinit var mLocationRequest: LocationRequest

    private lateinit var mLogout: Button
    private lateinit var mScheduleService: Button

    private lateinit var pickupLocation: LatLng
    private var radius: Int = 1
    private var driverFound: Boolean = false
    private var driverFoundId: String? = null

    private var mDriverMarker: Marker? = null

    private lateinit var geoQuery: GeoQuery
    private lateinit var driverLocationRef: DatabaseReference
    private var driverLocationRefListener: ValueEventListener? = null

    private var requestBol = false
    private lateinit var mCancel : Button
    private var pickupMarker: Marker? = null

    private lateinit var mDriverInfo: LinearLayout
    private lateinit var mDriverImage: ImageView
    private lateinit var mDriverName: TextView
    private lateinit var mDriverPhone: TextView
    private lateinit var mDriverType: TextView
    private lateinit var mSettings: Button

//    private var destination: String? = null

    private lateinit var mRadioGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_map)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.customer_map) as SupportMapFragment
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
        } else {
            mapFragment.getMapAsync(this)
        }

        mLogout = findViewById(R.id.logout)
        mLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        mScheduleService = findViewById(R.id.schedule_button)
        mScheduleService.setOnClickListener {
                if(requestBol) {
                    requestBol = false
                    mCancel.visibility = Button.VISIBLE
                } else {
                    val selectedRadioButtonId = mRadioGroup.checkedRadioButtonId
                    val radioButton: RadioButton = findViewById(selectedRadioButtonId)
                    requestService = radioButton.text.toString()
                    requestBol = true
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    val ref = FirebaseDatabase.getInstance().getReference("customerRequest")
                    val geoFire = GeoFire(ref)
                    geoFire.setLocation(
                        userId,
                        GeoLocation(mLastLocation.latitude, mLastLocation.longitude)
                    )

                    pickupLocation = LatLng(mLastLocation.latitude, mLastLocation.longitude)
                    pickupMarker = mMap.addMarker(MarkerOptions().position(pickupLocation).title("Deliver here"))
                    mScheduleService.text = "Scheduling order..."

                    getClosestDriver()
                }
        }
        //Cancel button clicked = remove all listeners, reset state and delete DB values
        mCancel = findViewById(R.id.cancel_button)
        mCancel.setOnClickListener {
            geoQuery.removeAllListeners()
            driverLocationRefListener?.let { it1 -> driverLocationRef.removeEventListener(it1) }
            driverFoundId?.let { it1 ->
                FirebaseDatabase.getInstance().reference.child("Users").child("Drivers")
                    .child(it1).child("customerRequest")
            }?.removeValue()
            driverFound = false
            radius = 1
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            val ref = FirebaseDatabase.getInstance().getReference("customerRequest")
            val geoFire = GeoFire(ref)
            geoFire.removeLocation(userId)
            pickupMarker?.remove()
            mScheduleService.text = applicationContext.resources.getString(R.string.schedule_service)
            mCancel.visibility = Button.INVISIBLE

            mDriverInfo.visibility = View.GONE
            mDriverName.text = ""
            mDriverPhone.text = ""
            mDriverType.text = ""
        }

        mSettings = findViewById(R.id.settings)
        mSettings.setOnClickListener {
            val intent = Intent(this, CustomerSettingsActivity::class.java)
            startActivity(intent)
        }
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        /*// Initialize the AutocompleteSupportFragment.
        val autocompleteFragment =
            supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                    as AutocompleteSupportFragment

        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME))

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                //destination = place.name.toString()
                Log.i(TAG, "Place: ${place.name}, ${place.id}")
            }

            override fun onError(status: Status) {
                Log.i(TAG, "An error occurred: $status")
            }
        })*/
        //Driver Information Pane for Customer
        mDriverImage = findViewById(R.id.driverImage)
        mDriverInfo = findViewById(R.id.driverInfo)
        mDriverName = findViewById(R.id.driverName)
        mDriverPhone = findViewById(R.id.driverPhone)
        mDriverType = findViewById(R.id.driverType)

        //Initialize Service Type Radio Group
        mRadioGroup = findViewById(R.id.radioGroup)
        mRadioGroup.check(R.id.bulk)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    data?.let {
                        val place = Autocomplete.getPlaceFromIntent(data)
                        //destination = place.name.toString()
                        Log.i(TAG, "Place: ${place.name}, ${place.id}")
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    // TODO: Handle the error.
                    data?.let {
                        val status = Autocomplete.getStatusFromIntent(data)
                        Log.i(TAG, status.statusMessage.toString())
                    }
                }
                Activity.RESULT_CANCELED -> {
                    // The user canceled the operation.
                    Log.i(TAG, "The user canceled the Places API operation.")
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }


    private fun getClosestDriver() {
        val driverLocation = FirebaseDatabase.getInstance().reference.child("driversAvailable")
        val geoFire = GeoFire(driverLocation)
        geoQuery = geoFire.queryAtLocation(
            GeoLocation(pickupLocation.latitude, pickupLocation.longitude),
            radius.toDouble()
        )
        //Cleanup recursive function
        geoQuery.removeAllListeners()
        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                if (!driverFound && requestBol) {
                    val mCustomerDatabase: DatabaseReference = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(key.toString())
                    mCustomerDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists() && snapshot.childrenCount>0) {
                                val driverMap = snapshot.getValue<Map<String, Any>>()
                                if(driverFound) {
                                    return
                                }
                                if (driverMap != null) {
                                    if (driverMap["serviceType"] == requestService) {
                                        driverFound = true
                                        if (key != null) {
                                            driverFoundId = key
                                        }
                                        val driverRef = driverFoundId?.let {
                                            FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(it).child("customerRequest")
                                        }
                                        val customerId = FirebaseAuth.getInstance().currentUser?.uid
                                        val map = mutableMapOf<String, Any?>()
                                        map["customerRideId"] = customerId
                                        //map["destination"] = destination
                                        driverRef?.updateChildren(map)

                                        getDriverLocation()
                                        mScheduleService.text = "Looking for Driver Location..."
                                        getAssignedDriverInfo()
                                    }
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            TODO("Not yet implemented")
                        }
                    })

                }
            }

            override fun onKeyExited(key: String?) {
                Log.w(TAG,
                    "Driver $key is no longer in the search area",
                )
            }

            override fun onKeyMoved(key: String?, location: GeoLocation?) {
                if (location != null) {
                    Log.w(TAG,
                        "Driver $key has moved to [${location.latitude}, ${location.longitude}] in the search area",
                    )
                }
            }

            override fun onGeoQueryReady() {
                if (!driverFound) {
                    radius++
                    getClosestDriver()
                }
            }

            override fun onGeoQueryError(error: DatabaseError?) {
                if (error != null) {
                    Log.e(TAG,
                        "Error querying Firebase database for driversAvailable : ${error.message}",
                    )
                }
            }
        })


    }

    private fun getDriverLocation() {
        driverLocationRef =
            driverFoundId?.let {
                FirebaseDatabase.getInstance().reference.child("driversWorking").child(
                    it
                ).child("l")
            }!!

        driverLocationRefListener = driverLocationRef.addValueEventListener(object: ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.exists() && requestBol){
                    val data = snapshot.getValue<List<Double>>()
                    //Log.d("CustomerMapActivity", "Value is: $data")

                    mScheduleService.text = "Driver Found!"
                    var locationLat = 0.0
                    var locationLng = 0.0
                    if(data!=null) {
                    locationLat = data[0]
                    locationLng = data[1]
                    }
                    val driverLatLng = LatLng(locationLat, locationLng)
                    //if(mDriverMarker!=null) mDriverMarker.remove()
                    //Get Pickup Location and Driver Location and find distance between them
                    val loc1 = Location("")
                    loc1.latitude = pickupLocation.latitude
                    loc1.longitude = pickupLocation.longitude

                    val loc2 = Location("")
                    loc2.latitude = driverLatLng.latitude
                    loc2.longitude = driverLatLng.longitude

                    val distance:Int = ((loc1.distanceTo(loc2))/1609.344).toInt()
                    if(loc1.distanceTo(loc2)<100) {
                        mScheduleService.text = "Your driver is here!"
                    }else {
                        mScheduleService.text = ("Driver Found at: $distance miles away")
                    }
                    mDriverMarker?.remove()
                    mDriverMarker = mMap.addMarker(MarkerOptions().position(driverLatLng).title("Your Driver").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_truck_foreground)))
                    mCancel.visibility = Button.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException())
            }

        })


    }

    private fun getAssignedDriverInfo() {
        mDriverInfo.visibility= View.VISIBLE
        val mDriverDatabaseReference = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(driverFoundId.toString())
        mDriverDatabaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.exists() && snapshot.childrenCount>0) {
                    val map = snapshot.getValue<Map<String, Any>>()
                    if (map != null) {
                        if(map["name"] !=null) {
                            mDriverName.text = map["name"].toString()
                        }
                        if(map["phone"] !=null) {
                            mDriverPhone.text = map["phone"].toString()
                        }
                        if(map["serviceType"] !=null) {
                            mDriverType.text = map["serviceType"].toString()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read value", error.toException())
            }

        })
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
        if (location != null) {
            mLastLocation = location
            val latLng = LatLng(location.latitude, location.longitude)
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11.0F))
        }

        /*val userId = FirebaseAuth.getInstance().currentUser?.uid
    val ref = FirebaseDatabase.getInstance().getReference("driversAvailable")
    //Save Location Info against the userId to the database using GeoFire
    val geoFire = GeoFire(ref)
    if (location != null) {
        geoFire.setLocation(userId, GeoLocation(location.latitude, location.longitude))
    }*/
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.customer_map) as SupportMapFragment
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

    override fun onStop() {
        super.onStop()
        /*LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val ref = FirebaseDatabase.getInstance().getReference("driversAvailable")
    //Stop saving location Info to DB using GeoFire
    val geoFire = GeoFire(ref)
    geoFire.removeLocation(userId)*/

    }

    companion object {
        private const val AUTOCOMPLETE_REQUEST_CODE = 2
        private const val LOCATION_REQUEST_CODE: Int = 1
        private const val TAG = "CustomerMapsActivity"
    }
}
