package com.floodcomms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import android.os.PowerManager

class FloodCommsService : Service() {

    private val binder = LocalBinder()
    private val TAG = "FloodCommsService"
    private val SERVICE_ID = "com.floodcomms"
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val CHANNEL_ID = "FloodCommsChannel"
    private val NOTIFICATION_ID = 123
    private val MSG_NOTIFICATION_ID = 456

    private val connectedEndpoints = HashSet<String>()
    private val audioHelper = AudioHelper()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var wakeLock: PowerManager.WakeLock? = null

    // Callback to communicate back to Module
    var serviceCallback: ServiceCallback? = null

    interface ServiceCallback {
        fun onLocationReceived(endpointId: String, lat: Double, lon: Double, filePath: String)
    }

    inner class LocalBinder : Binder() {
        fun getService(): FloodCommsService = this@FloodCommsService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FloodComms::BackgroundService")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_FOREGROUND") {
            startForegroundService()
        } else if (intent?.action == "STOP_FOREGROUND") {
            stopForegroundService()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SLTalkie is running")
            .setContentText("Listening for nearby messages...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SLTalkie Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    // --- Nearby Connections Logic ---

    fun startAdvertising(userNickName: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this)
            .startAdvertising(
                userNickName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun startDiscovery(onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this)
            .startDiscovery(
                SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun stopAdvertising() {
        Nearby.getConnectionsClient(this).stopAdvertising()
    }

    fun stopDiscovery() {
        Nearby.getConnectionsClient(this).stopDiscovery()
    }

    fun startRecording() {
        if (connectedEndpoints.isEmpty()) return

        getLocation { lat, lon ->
            val startMarker = "START".toByteArray(Charset.forName("UTF-8"))
            val buffer = ByteBuffer.allocate(startMarker.size + 16)
            buffer.put(startMarker)
            buffer.putDouble(lat)
            buffer.putDouble(lon)
            
            val startPayload = Payload.fromBytes(buffer.array())
            Nearby.getConnectionsClient(this)
                .sendPayload(connectedEndpoints.toList(), startPayload)

            audioHelper.startRecording { audioData ->
                val payload = Payload.fromBytes(audioData)
                Nearby.getConnectionsClient(this)
                    .sendPayload(connectedEndpoints.toList(), payload)
            }
        }
    }

    fun stopRecording() {
        audioHelper.stopRecording()
        val endMarker = "END".toByteArray(Charset.forName("UTF-8"))
        val endPayload = Payload.fromBytes(endMarker)
        if (connectedEndpoints.isNotEmpty()) {
            Nearby.getConnectionsClient(this)
                .sendPayload(connectedEndpoints.toList(), endPayload)
        }
    }

    fun playAudioFile(filePath: String, onComplete: () -> Unit) {
        audioHelper.playFile(filePath, onComplete)
    }

    private fun getLocation(callback: (Double, Double) -> Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    callback(location.latitude, location.longitude)
                } else {
                    callback(0.0, 0.0)
                }
            }.addOnFailureListener {
                callback(0.0, 0.0)
            }
        } catch (e: SecurityException) {
            callback(0.0, 0.0)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(this@FloodCommsService).acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to $endpointId")
                    connectedEndpoints.add(endpointId)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Log.w(TAG, "Connection rejected by $endpointId")
                ConnectionsStatusCodes.STATUS_ERROR -> Log.e(TAG, "Connection error with $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Nearby.getConnectionsClient(this@FloodCommsService)
                .requestConnection("User", endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                
                if (bytes.size >= 5) {
                    val marker = String(bytes, 0, 5, Charset.forName("UTF-8"))
                    if (marker == "START") {
                        if (bytes.size >= 21) {
                            val buffer = ByteBuffer.wrap(bytes)
                            buffer.position(5)
                            val lat = buffer.getDouble()
                            val lon = buffer.getDouble()
                            
                            val fileName = "msg_${System.currentTimeMillis()}_${endpointId}.pcm"
                            val filePath = File(filesDir, fileName).absolutePath
                            
                            audioHelper.startSaving(filePath)
                            
                            // Show notification
                            showIncomingMessageNotification()
                            
                            serviceCallback?.onLocationReceived(endpointId, lat, lon, filePath)
                        }
                        return
                    }
                }
                
                if (bytes.size == 3) {
                     val marker = String(bytes, 0, 3, Charset.forName("UTF-8"))
                     if (marker == "END") {
                         audioHelper.stopSaving()
                         return
                     }
                }

                audioHelper.saveChunk(bytes)
                audioHelper.playAudio(bytes)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun showIncomingMessageNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Incoming Voice Message")
            .setContentText("You are receiving a voice message.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(MSG_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
        stopDiscovery()
        audioHelper.release()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}
