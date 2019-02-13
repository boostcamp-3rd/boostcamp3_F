package com.boostcamp.travery.mapservice

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import androidx.core.app.NotificationCompat

import android.location.LocationManager
import android.util.Log
import android.content.Context
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.boostcamp.travery.R
import java.util.*
import android.os.*
import androidx.core.app.TaskStackBuilder
import com.boostcamp.travery.data.model.Suggestion
import com.boostcamp.travery.data.model.TimeCode
import kotlin.collections.ArrayList


@SuppressLint("Registered")
class MapTrackingService : Service() {

    private val mFusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(
                this
        )
    }

    private val locationRequest: LocationRequest by lazy {
        LocationRequest()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL_MS)
                .setFastestInterval(FASTEST_UPDATE_INTERVAL_MS)
    }
    private val TAG = "MyLocationService"
    private val UPDATE_INTERVAL_MS: Long = 2500  // 1초
    private val FASTEST_UPDATE_INTERVAL_MS: Long = 1500 //

    private val userActionPositionList: ArrayList<LatLng> = ArrayList()
    private var lostLocationCnt = 0
    //private val timeCodeList: ArrayList<TimeCode> = ArrayList()
    var isRunning = false
    private var canSuggest = true
    private val suggestList: ArrayList<Suggestion> = ArrayList()
    private var startTime: Long? = null
    private var second: Int = 0

    private var exLocation: Location? = null
    private lateinit var standardLocation: Location

    private val mapTrackingRepository = MapTrackingRepository.getInstance()


    private var mCallback: ICallback? = null
    private val mLocationManager: LocationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val notification: NotificationCompat.Builder by lazy {
        val notificationIntent = Intent(this, TrackingActivity::class.java)

        val pendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            // Add the intent, which inflates the back stack
            addNextIntentWithParentStack(notificationIntent)
            // Get the PendingIntent containing the entire back stack
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setContentTitle(getString(R.string.service_title))
                .setContentText(getString(R.string.service_message))
                .setSmallIcon(R.drawable.ic_play_circle_filled_black_60dp)
                .setContentIntent(pendingIntent)
    }
    private val mBinder = LocalBinder()

    lateinit var secondTimer: Timer

    internal inner class LocalBinder : Binder() {
        val service: MapTrackingService
            get() = this@MapTrackingService
    }

    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val nowLocationList = locationResult.locations
            if (nowLocationList.size > 0) {
                val location = nowLocationList.last()
                Log.d(TAG, "onLocationResult : " + location.accuracy)

                if (exLocation != null) {
                    //이동거리가 1m 이상 10m 이하이고 오차범위가 10m 미만일 때
                    //실내에서는 12m~30m정도의 오차 발생
                    //야외에서는 3m~11m정도의 오차 발생
                    if (location.distanceTo(exLocation) >= 2 && location.accuracy < 9.5) {

                        val locate = LatLng(location.latitude, location.longitude)
                        if (isRunning) {
                            mapTrackingRepository.addTotalDistance(location.distanceTo(exLocation))
                            //timeCodeList.add(TimeCode(locate, location.time))
                        }
                        mapTrackingRepository.addTimeCode(TimeCode(locate, location.time))
                        //mCallback?.sendLocation(locate, location.accuracy)

                        exLocation = location

                        //이동 거리가 11m이하에서 움직일 때에는 lostLocationCnt 상승
                        if (location.distanceTo(standardLocation) < 11) {
                            if (isRunning) lostLocationCnt++
                        } else {//1.5초에서 2.5초에 한번 씩 데이터가 들어옴
                            //200번 쌓이면 5분
                            if (lostLocationCnt > 1 && canSuggest) {
                                Log.d("lolott","제안 추가")
                                mapTrackingRepository.addSuggest(Suggestion(
                                        LatLng(
                                                standardLocation.latitude,
                                                standardLocation.longitude
                                        ), standardLocation.time, location.time
                                ))
                                //mCallback?.sendSuggestList(suggestList)
                            }
                            standardLocation = location
                            canSuggest = true
                            lostLocationCnt = 0
                        }
                    } else {
                        if (isRunning)
                            lostLocationCnt++
                    }

                } else {
                    exLocation = location
                    standardLocation = location
                    val locate = LatLng(location.latitude, location.longitude)
                    //mCallback?.sendLocation(locate, location.accuracy)
                    if (isRunning) {
                        //timeCodeList.add(TimeCode(locate, location.time))
                        mapTrackingRepository.addTimeCode(TimeCode(locate, location.time))
                    }
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    override fun onCreate() {

        Log.e(TAG, "onCreate")

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(locationRequest)

        secondTimer = Timer()

        try {
            mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

        } catch (ex: java.lang.SecurityException) {
            Log.i(TAG, "fail to request location update, ignore", ex)
        } catch (ex: IllegalArgumentException) {
            Log.d(TAG, "network provider does not exist, " + ex.message)
        }
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        startForeground(1, notification.build())

        isRunning = true

        secondTimer.schedule(SecondTimer(), 1000, 1000)

        startTime = System.currentTimeMillis()
        mapTrackingRepository.setStartTime(startTime ?: 0L)
        mCallback?.saveInitCourse(startTime ?: System.currentTimeMillis())

        return Service.START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val providers = mLocationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val mLocation = mLocationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || mLocation.accuracy < bestLocation.accuracy) {
                bestLocation = mLocation
            }
        }
        if (isRunning && bestLocation != null) {
            //timeCodeList.add(TimeCode(LatLng(bestLocation.latitude, bestLocation.longitude), bestLocation.time))
        }
        return bestLocation
    }

    override fun onDestroy() {
        Log.e(TAG, "onDestroy")
        super.onDestroy()
        isRunning = false
        secondTimer.cancel()
        mFusedLocationClient.removeLocationUpdates(locationCallback)
        mapTrackingRepository.clearData()
    }

    inner class SecondTimer : TimerTask() {
        override fun run() {
            second++
            mapTrackingRepository.setSecond(second)
        }
    }

    interface ICallback {
        fun saveInitCourse(startTime: Long)
    }

    fun registerCallback(cb: ICallback) {
        mCallback = cb
    }

    fun getLastLocation(): Location? {
        return getLastKnownLocation()
    }

    fun setCanSuggestFalse() {
        canSuggest = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }
}