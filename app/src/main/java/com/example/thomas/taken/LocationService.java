package com.example.thomas.taken;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.example.thomas.taken.helper.SQLiteHandler;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

//import com.google.android.gms.location.LocationClient;


public class LocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "LocationService";

    // use the websmithing defaultUploadWebsite for testing and then check your
    // location with your browser here: https://www.websmithing.com/gpstracker/displaymap.php
    private String defaultUploadWebsite;

    private boolean currentlyProcessingLocation = false;
    private LocationRequest mLocationRequest;
    // private LocationClient locationClient;
    private GoogleApiClient locationClient;
    private SQLiteHandler db;
    @Override
    public void onCreate() {
        super.onCreate();

        defaultUploadWebsite = getString(R.string.default_upload_website);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // if we are currently trying to get a location and the alarm manager has called this again,
        // no need to start processing a new location.
        if (!currentlyProcessingLocation) {
            currentlyProcessingLocation = true;
            startTracking();
        }

        return START_NOT_STICKY;
    }

    private void startTracking() {
        Log.d(TAG, "startTracking");

        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
            //locationClient = new LocationClient(this,this,this);
            locationClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            if (!locationClient.isConnected() || !locationClient.isConnecting()) {
                //super.onStart();
                locationClient.connect();
            }
        } else {
            Log.e(TAG, "unable to connect to google play services.");
        }
    }
    public static String getCurrentTimeStamp(){
        try {

            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            dateFormat.setTimeZone(TimeZone.getDefault());
            Date date = new Date(System.currentTimeMillis());
            String dateString = dateFormat.format(date);
            return dateString;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    protected void sendLocationDataToWebsite(Location location) throws IOException {
        // formatted for mysql datetime format
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getDefault());
        // Date date = new Date(location.getTime());
        SharedPreferences sharedPreferences = this.getSharedPreferences("com.example.thomas.taken.prefs", Context.MODE_PRIVATE);


        final RequestParams requestParams = new RequestParams();
        db = new SQLiteHandler(getApplicationContext());
        HashMap<String, String> user = db.getUserDetails();


        String uid = user.get("uid");

        requestParams.put("user_id",uid);
        requestParams.put("latitude", Double.toString(location.getLatitude()));
        requestParams.put("longitude", Double.toString(location.getLongitude()));

//        Double speedInMilesPerHour = location.getSpeed()* 2.2369;
//        requestParams.put("speed",  Integer.toString(speedInMilesPerHour.intValue()));


        requestParams.put("datetime", getCurrentTimeStamp());


       // requestParams.put("locationmethod", location.getProvider());




        Float accuracy = location.getAccuracy();
        requestParams.put("accuracy",  Integer.toString(accuracy.intValue()));


//        requestParams.put("extrainfo",  Integer.toString(altitudeInFeet.intValue()));


        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        List<Address> addresses  = geocoder.getFromLocation(location.getLatitude(),location.getLongitude(), 1);
        String city = addresses.get(0).getLocality();
        //String state = addresses.get(0).getAdminArea();
        //String zip = addresses.get(0).getPostalCode();
        String country = addresses.get(0).getCountryName();
        String featurename = addresses.get(0).getFeatureName();

        //requestParams.put("city",city);
        //requestParams.put("state",state);
        //requestParams.put("zip",zip);
        //requestParams.put("country",country);
        //requestParams.put("featurename",featurename);







        final String uploadWebsite = sharedPreferences.getString("defaultUploadWebsite", defaultUploadWebsite);

        LoopjHttpClient.post(uploadWebsite, requestParams, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
                LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - success", uploadWebsite, requestParams, responseBody, headers, statusCode, null);
                stopSelf();
            }
            @Override
            public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] errorResponse, Throwable e) {
                LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - failure", uploadWebsite, requestParams, errorResponse, headers, statusCode, e);
                stopSelf();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            Log.e(TAG, "position: " + location.getLatitude() + ", " + location.getLongitude() + " accuracy: " + location.getAccuracy());

            // we have our desired accuracy of 500 meters so lets quit this service,
            // onDestroy will be called and stop our location updates
            if (location.getAccuracy() < 100.0) {
                stopLocationUpdates();
                try {
                    sendLocationDataToWebsite(location);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void stopLocationUpdates() {
//        if (locationClient != null && locationClient.isConnected()) {
//            locationClient.removeLocationUpdates(this);
//            locationClient.disconnect();
//        }
        // Disconnecting the client invalidates it.
        locationClient.disconnect();
        // super.onStop();
    }

    /**
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates
     */
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(1000); // milliseconds
        mLocationRequest.setFastestInterval(1000); // the fastest rate in milliseconds at which your app can handle location updates
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                locationClient, mLocationRequest, this);
        //locationClient.requestLocationUpdates(mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection has been suspend");
    }

    /**
     * Called by Location Services if the connection to the
     * location client drops because of an error.
     */

    public void onDisconnected() {
        Log.e(TAG, "onDisconnected");

        stopLocationUpdates();
        stopSelf();
    }

    protected void onStop() {
        // Disconnecting the client invalidates it.
        Log.e(TAG, "onStop");
        locationClient.disconnect();
        // super.onStop();
    }
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed");

        stopLocationUpdates();
        stopSelf();
    }
}
