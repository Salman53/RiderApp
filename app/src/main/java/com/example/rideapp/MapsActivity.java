package com.example.rideapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.suke.widget.SwitchButton;

import java.util.Spliterator;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback ,
         GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener,
        LocationListener
{


    private GoogleMap mMap;

    private static final int MY_PERMISSION_REQUEST_CODE = 7000;
    private static final int PLAY_SERVICE_RES_REQUEST = 7001;
    private LocationRequest locationRequest;
    private GoogleApiClient googleApiClient;
    private Location lastlocation;
    private static int UPDATE_INTERVAL = 5000;
    private static int FASTEST_INTERVAL = 3000;
    private static int DISPLACEMENT = 10;
    DatabaseReference ref;
    GeoFire geoFire;
    LatLng latLng;
    LocationManager locationManager;
    int radius = 1; //1km
    boolean isDriverFound = false;
    String driverId = "";
    int distance = 1; //3km
    private static final int LIMIT = 3;

    Button btnPickupRequest;


    Marker mUserMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);



        btnPickupRequest = findViewById(R.id.btnPickupRequest);
        btnPickupRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPickupHere(FirebaseAuth.getInstance().getCurrentUser().getUid());
                findDriver();
            }
        });

        setUpLocation();


    }

    private void findDriver() {
        DatabaseReference drivers = FirebaseDatabase.getInstance().getReference(Common.driver_tbl);
        GeoFire gfDriver = new GeoFire(drivers);

        GeoQuery geoQuery = gfDriver.queryAtLocation(new GeoLocation(lastlocation.getLatitude() , lastlocation.getLongitude()),
                radius);
        geoQuery.removeAllListeners();
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                //If found
                if(!isDriverFound)
                {
                    isDriverFound = true;
                    driverId =key;
                    btnPickupRequest.setText("CALL Driver");
                    Toast.makeText(MapsActivity.this, ""+key, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                //if still not found driver , increase distance
                if(!isDriverFound)
                {
                    radius++;
                    findDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });

    }

    private void requestPickupHere(String uid) {
        DatabaseReference dbRequest = FirebaseDatabase.getInstance().getReference(Common.pickup_request_tbl);
        GeoFire geoFire = new GeoFire(dbRequest);
        geoFire.setLocation(uid , new GeoLocation(lastlocation.getLatitude() , lastlocation.getLongitude()));

        if(mUserMarker.isVisible())
        {
            mUserMarker.remove();
            //Add new marker
          mUserMarker = mMap.addMarker(new MarkerOptions()
                    .title("Pickup Here")
                    .snippet("")
                    .position(new LatLng(lastlocation.getLatitude() , lastlocation.getLongitude()))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
          mUserMarker.showInfoWindow();
          btnPickupRequest.setText("Getting Your Driver...");

        }
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
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);
        mMap.setInfoWindowAdapter(new CustomInfoWindow(this));

    }

    private void startlocationUpdates() {
        if(ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient , locationRequest ,  this);

    }

    private void setUpLocation() {

        if(ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            //Request Runtime permission
            ActivityCompat.requestPermissions(this , new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            } , MY_PERMISSION_REQUEST_CODE);
        }
        else {
            if(checkPlayServices())
            {
                buildGoogleApiClient();
                creatLocationReaquest();
                displayLocation();
                }
            }
        }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode))
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICE_RES_REQUEST).show();
            else {
                Toast.makeText(this, "This is not supported", Toast.LENGTH_SHORT).show();
                finish();
            }
            return false;
        }
        return  true;
    }

    private void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }
    private void stopLoationUpdates() {

        if(ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient ,  this);
    }

    private void creatLocationReaquest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    private void displayLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        lastlocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if (lastlocation != null) {

            final double latitude = lastlocation.getLatitude();
            final double longitude = lastlocation.getLongitude();

            //Add Marker
            if (mUserMarker != null)
                mUserMarker.remove(); //Remove already marker
            mUserMarker = mMap.addMarker(new MarkerOptions()
                    //.title(String.valueOf(BitmapDescriptorFactory.fromResource(R.drawable.car)))
                    .position(new LatLng(latitude, longitude))
                    .title(String.format("Your Location")));

            //Move marker to this position
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 15.0f));

            loadAllAvailableDriver();

        } else {
            Log.d("ERROR", "Cannot get your location");
        }

    }

    private void loadAllAvailableDriver() {
        //load all available driver in distance 3km
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference(Common.driver_tbl);
        GeoFire geoFire = new GeoFire(driverLocation);

        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(lastlocation.getLatitude() , lastlocation.getLongitude()) , distance);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, final GeoLocation location) {

                FirebaseDatabase.getInstance().getReference(Common.user_driver_tbl)
                        .child(key)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                User user = dataSnapshot.getValue(User.class);

                                //Add driver to map
                                mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(location.latitude , location.longitude))
                                .flat(true)
                                .title(user.getName())
                                        .snippet("Phone : "+user.getPhone())
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car)));

                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {

                            }
                        });
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if(distance <= LIMIT)
                {
                    distance++;
                    loadAllAvailableDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode)
        {
            case MY_PERMISSION_REQUEST_CODE:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    if(checkPlayServices())
                    {
                        buildGoogleApiClient();
                        creatLocationReaquest();
                            displayLocation();
                    }

                }
                break;
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
            displayLocation();
            startlocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        googleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        lastlocation = location;
        displayLocation();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}

