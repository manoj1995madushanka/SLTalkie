package com.floodcomms

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

class FloodCommsModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), FloodCommsService.ServiceCallback {

    private val TAG = "FloodCommsModule"
    private var floodCommsService: FloodCommsService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as FloodCommsService.LocalBinder
            floodCommsService = binder.getService()
            floodCommsService?.serviceCallback = this@FloodCommsModule
            isBound = true
            Log.d(TAG, "Service Connected")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            floodCommsService = null
            Log.d(TAG, "Service Disconnected")
        }
    }

    init {
        // Bind to service immediately
        val intent = Intent(reactContext, FloodCommsService::class.java)
        reactContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun getName(): String {
        return "FloodCommsModule"
    }

    @ReactMethod
    fun setBackgroundMode(enabled: Boolean) {
        val intent = Intent(reactApplicationContext, FloodCommsService::class.java)
        if (enabled) {
            intent.action = "START_FOREGROUND"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                reactApplicationContext.startForegroundService(intent)
            } else {
                reactApplicationContext.startService(intent)
            }
        } else {
            intent.action = "STOP_FOREGROUND"
            reactApplicationContext.startService(intent)
        }
    }

    @ReactMethod
    fun startAdvertising(userNickName: String, promise: Promise) {
        if (isBound && floodCommsService != null) {
            floodCommsService?.startAdvertising(userNickName, {
                promise.resolve(null)
            }, { e ->
                promise.reject("ADVERTISE_ERROR", e)
            })
        } else {
            promise.reject("SERVICE_NOT_BOUND", "Service not bound yet")
        }
    }

    @ReactMethod
    fun startDiscovery(promise: Promise) {
        if (isBound && floodCommsService != null) {
            floodCommsService?.startDiscovery({
                promise.resolve(null)
            }, { e ->
                promise.reject("DISCOVERY_ERROR", e)
            })
        } else {
            promise.reject("SERVICE_NOT_BOUND", "Service not bound yet")
        }
    }

    @ReactMethod
    fun stopAdvertising() {
        floodCommsService?.stopAdvertising()
    }

    @ReactMethod
    fun stopDiscovery() {
        floodCommsService?.stopDiscovery()
    }

    @ReactMethod
    fun startRecording() {
        floodCommsService?.startRecording()
    }

    @ReactMethod
    fun stopRecording() {
        floodCommsService?.stopRecording()
    }

    @ReactMethod
    fun playAudioFile(filePath: String, promise: Promise) {
        if (isBound && floodCommsService != null) {
            floodCommsService?.playAudioFile(filePath) {
                promise.resolve(true)
            }
        } else {
            promise.reject("SERVICE_NOT_BOUND", "Service not bound yet")
        }
    }

    // Callback from Service
    override fun onLocationReceived(endpointId: String, lat: Double, lon: Double, filePath: String) {
        val params = Arguments.createMap().apply {
            putString("endpointId", endpointId)
            putDouble("latitude", lat)
            putDouble("longitude", lon)
            putString("filePath", filePath)
        }
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onLocationReceived", params)
    }
}
