package com.floodcomms

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.io.File
import android.util.Log
import android.annotation.SuppressLint

class FloodCommsModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val SERVICE_ID = "com.floodcomms"
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val TAG = "FloodCommsModule"
    private val connectedEndpoints = HashSet<String>()
    private val audioHelper = AudioHelper()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    init {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(reactApplicationContext)
    }

    override fun getName(): String {
        return "FloodCommsModule"
    }

    @ReactMethod
    fun startAdvertising(userNickName: String, promise: Promise) {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        
        Nearby.getConnectionsClient(reactApplicationContext)
            .startAdvertising(
                userNickName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener {
                Log.d(TAG, "Advertising started")
                promise.resolve(null)
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "Advertising failed", e)
                promise.reject("ADVERTISE_ERROR", e)
            }
    }

    @ReactMethod
    fun startDiscovery(promise: Promise) {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        
        Nearby.getConnectionsClient(reactApplicationContext)
            .startDiscovery(
                SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
            )
            .addOnSuccessListener {
                Log.d(TAG, "Discovery started")
                promise.resolve(null)
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "Discovery failed", e)
                promise.reject("DISCOVERY_ERROR", e)
            }
    }

    @ReactMethod
    fun stopAdvertising() {
        Nearby.getConnectionsClient(reactApplicationContext).stopAdvertising()
    }

    @ReactMethod
    fun stopDiscovery() {
        Nearby.getConnectionsClient(reactApplicationContext).stopDiscovery()
    }

    @ReactMethod
    fun startRecording() {
        if (connectedEndpoints.isEmpty()) return

        getLocation { lat, lon ->
            // 1. Send START payload with Location
            val startMarker = "START".toByteArray(Charset.forName("UTF-8"))
            val buffer = ByteBuffer.allocate(startMarker.size + 16)
            buffer.put(startMarker)
            buffer.putDouble(lat)
            buffer.putDouble(lon)
            
            val startPayload = Payload.fromBytes(buffer.array())
            Nearby.getConnectionsClient(reactApplicationContext)
                .sendPayload(connectedEndpoints.toList(), startPayload)

            // 2. Start Recording and streaming BODY payloads
            audioHelper.startRecording { audioData ->
                val payload = Payload.fromBytes(audioData)
                Nearby.getConnectionsClient(reactApplicationContext)
                    .sendPayload(connectedEndpoints.toList(), payload)
            }
        }
    }



    @ReactMethod
    fun playAudioFile(filePath: String, promise: Promise) {
        audioHelper.playFile(filePath) {
            promise.resolve(true)
        }
    }private fun getLocation(callback: (Double, Double) -> Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                callback(location.latitude, location.longitude)
            } else {
                callback(0.0, 0.0)
            }
        }.addOnFailureListener {
            callback(0.0, 0.0)
        }
    }

    @ReactMethod
    fun stopRecording() {
        audioHelper.stopRecording()
        
        // 3. Send END payload
        val endMarker = "END".toByteArray(Charset.forName("UTF-8"))
        val endPayload = Payload.fromBytes(endMarker)
        if (connectedEndpoints.isNotEmpty()) {
            Nearby.getConnectionsClient(reactApplicationContext)
                .sendPayload(connectedEndpoints.toList(), endPayload)
        }
    }

    // Callbacks (Placeholders for now)
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Automatically accept connection for now
            Nearby.getConnectionsClient(reactApplicationContext).acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to $endpointId")
                    connectedEndpoints.add(endpointId)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected by $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error with $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Found endpoint, request connection
             Nearby.getConnectionsClient(reactApplicationContext)
                .requestConnection(getName(), endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            // Lost endpoint
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                
                // Check for START/END markers
                if (bytes.size >= 5) {
                    val marker = String(bytes, 0, 5, Charset.forName("UTF-8"))
                    if (marker == "START") {
                        // Handle START: Extract location and start saving
                        if (bytes.size >= 21) { // 5 (START) + 8 (Lat) + 8 (Lon)
                            val buffer = ByteBuffer.wrap(bytes)
                            buffer.position(5)
                            val lat = buffer.getDouble()
                            val lon = buffer.getDouble()
                            
                            val fileName = "msg_${System.currentTimeMillis()}_${endpointId}.pcm"
                            val filePath = File(reactApplicationContext.filesDir, fileName).absolutePath
                            
                            audioHelper.startSaving(filePath)
                            
                            // Emit location and file path to JS
                            sendLocationEvent(endpointId, lat, lon, filePath)
                        }
                        return
                    }
                }
                
                if (bytes.size == 3) {
                     val marker = String(bytes, 0, 3, Charset.forName("UTF-8"))
                     if (marker == "END") {
                         // Handle END: Stop saving
                         audioHelper.stopSaving()
                         return
                     }
                }

                // Handle BODY: Audio Data
                audioHelper.saveChunk(bytes)
                audioHelper.playAudio(bytes)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Update
        }
    }

    private fun sendLocationEvent(endpointId: String, lat: Double, lon: Double, filePath: String) {
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
