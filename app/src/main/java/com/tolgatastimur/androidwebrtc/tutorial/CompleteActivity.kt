package com.tolgatastimur.androidwebrtc.tutorial

import android.Manifest
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log

import com.tolgatastimur.androidwebrtc.R
import com.tolgatastimur.androidwebrtc.databinding.ActivitySamplePeerConnectionBinding

import org.json.JSONException
import org.json.JSONObject
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.VideoCapturer
import org.webrtc.VideoRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

import java.net.URISyntaxException
import java.util.ArrayList

import io.socket.client.IO
import io.socket.client.Socket
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions

import com.tolgatastimur.androidwebrtc.app_rtc_sample.web_rtc.PeerConnectionClient.VIDEO_TRACK_ID
import com.tolgatastimur.androidwebrtc.tutorial.MediaStreamActivity.Companion.FPS
import com.tolgatastimur.androidwebrtc.tutorial.MediaStreamActivity.Companion.VIDEO_RESOLUTION_HEIGHT
import com.tolgatastimur.androidwebrtc.tutorial.MediaStreamActivity.Companion.VIDEO_RESOLUTION_WIDTH
import io.socket.client.Socket.EVENT_CONNECT
import io.socket.client.Socket.EVENT_DISCONNECT
import org.webrtc.SessionDescription.Type.ANSWER
import org.webrtc.SessionDescription.Type.OFFER

class CompleteActivity : AppCompatActivity() {

    private var socket: Socket? = null
    private var isInitiator: Boolean = false
    private var isChannelReady: Boolean = false
    private var isStarted: Boolean = false

    private var binding: ActivitySamplePeerConnectionBinding? = null
    private var peerConnection: PeerConnection? = null
    private var rootEglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var videoTrackFromCamera: VideoTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sample_peer_connection)
        setSupportActionBar(binding!!.toolbar)

        start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onDestroy() {
        if (socket != null) {
            sendMessage("bye")
            socket!!.disconnect()
        }
        super.onDestroy()
    }

    private fun start() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (EasyPermissions.hasPermissions(this, *perms)) {
            connectToSignallingServer()

            initializeSurfaceViews()

            initializePeerConnectionFactory()

            createVideoTrackFromCameraAndShowIt()

            initializePeerConnections()

            startStreamingVideo()
        } else {
            EasyPermissions.requestPermissions(this, "Need some permissions", RC_CALL, *perms)
        }
    }

    private fun connectToSignallingServer() {
        try {
            socket = IO.socket("https://salty-sea-26559.herokuapp.com/")

            socket!!.on(EVENT_CONNECT) { args ->
                Log.d(TAG, "connectToSignallingServer: connect")
                socket!!.emit("create or join", "foo")
            }.on("ipaddr") { args -> Log.d(TAG, "connectToSignallingServer: ipaddr") }.on("created") { args ->
                Log.d(TAG, "connectToSignallingServer: created")
                isInitiator = true
            }.on("full") { args -> Log.d(TAG, "connectToSignallingServer: full") }.on("join") { args ->
                Log.d(TAG, "connectToSignallingServer: join")
                Log.d(TAG, "connectToSignallingServer: Another peer made a request to join room")
                Log.d(TAG, "connectToSignallingServer: This peer is the initiator of room")
                isChannelReady = true
            }.on("joined") { args ->
                Log.d(TAG, "connectToSignallingServer: joined")
                isChannelReady = true
            }.on("log") { args ->
                for (arg in args) {
                    Log.d(TAG, "connectToSignallingServer: $arg")
                }
            }.on("message") { args -> Log.d(TAG, "connectToSignallingServer: got a message") }.on("message") { args ->
                try {
                    if (args[0] is String) {
                        val message = args[0] as String
                        if (message == "got user media") {
                            maybeStart()
                        }
                    } else {
                        val message = args[0] as JSONObject
                        Log.d(TAG, "connectToSignallingServer: got message $message")
                        if (message.getString("type") == "offer") {
                            Log.d(TAG, "connectToSignallingServer: received an offer $isInitiator $isStarted")
                            if (!isInitiator && !isStarted) {
                                maybeStart()
                            }
                            peerConnection!!.setRemoteDescription(SimpleSdpObserver(), SessionDescription(OFFER, message.getString("sdp")))
                            doAnswer()
                        } else if (message.getString("type") == "answer" && isStarted) {
                            peerConnection!!.setRemoteDescription(SimpleSdpObserver(), SessionDescription(ANSWER, message.getString("sdp")))
                        } else if (message.getString("type") == "candidate" && isStarted) {
                            Log.d(TAG, "connectToSignallingServer: receiving candidates")
                            val candidate = IceCandidate(message.getString("id"), message.getInt("label"), message.getString("candidate"))
                            peerConnection!!.addIceCandidate(candidate)
                        }
                        /*else if (message === 'bye' && isStarted) {
                        handleRemoteHangup();
                    }*/
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }.on(EVENT_DISCONNECT) { args -> Log.d(TAG, "connectToSignallingServer: disconnect") }
            socket!!.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }

    }

    private fun doAnswer() {
        peerConnection!!.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "answer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

            }
        }, MediaConstraints())
    }

    private fun maybeStart() {
        Log.d(TAG, "maybeStart: $isStarted $isChannelReady")
        if (!isStarted && isChannelReady) {
            isStarted = true
            if (isInitiator) {
                doCall()
            }
        }
    }

    private fun doCall() {
        val sdpMediaConstraints = MediaConstraints()

        peerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "onCreateSuccess: ")
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "offer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

            }
        }, sdpMediaConstraints)
    }

    private fun sendMessage(message: Any) {
        socket!!.emit("message", message)
    }

    private fun initializeSurfaceViews() {
        rootEglBase = EglBase.create()
        binding!!.surfaceView.init(rootEglBase!!.eglBaseContext, null)
        binding!!.surfaceView.setEnableHardwareScaler(true)
        binding!!.surfaceView.setMirror(true)

        binding!!.surfaceView2.init(rootEglBase!!.eglBaseContext, null)
        binding!!.surfaceView2.setEnableHardwareScaler(true)
        binding!!.surfaceView2.setMirror(true)
    }

    private fun initializePeerConnectionFactory() {
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)
        factory = PeerConnectionFactory(null)
        factory!!.setVideoHwAccelerationOptions(rootEglBase!!.eglBaseContext, rootEglBase!!.eglBaseContext)
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        val videoCapturer = createVideoCapturer()
        val videoSource = factory!!.createVideoSource(videoCapturer!!)
        videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)

        videoTrackFromCamera = factory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        videoTrackFromCamera!!.setEnabled(true)
        videoTrackFromCamera!!.addRenderer(VideoRenderer(binding!!.surfaceView))
    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection(factory!!)
    }

    private fun startStreamingVideo() {
        val mediaStream = factory!!.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera!!)
        peerConnection!!.addStream(mediaStream)

        sendMessage("got user media")
    }

    private fun createPeerConnection(factory: PeerConnectionFactory): PeerConnection {
        val iceServers = ArrayList<PeerConnection.IceServer>()
        iceServers.add(PeerConnection.IceServer("stun:stun.l.google.com:19302"))

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()

        val pcObserver = object : PeerConnection.Observer {
            override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                Log.d(TAG, "onSignalingChange: ")
            }

            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: ")
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.d(TAG, "onIceConnectionReceivingChange: ")
            }

            override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: ")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d(TAG, "onIceCandidate: ")
                val message = JSONObject()

                try {
                    message.put("type", "candidate")
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)

                    Log.d(TAG, "onIceCandidate: sending candidate $message")
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.d(TAG, "onIceCandidatesRemoved: ")
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size)
                val remoteVideoTrack = mediaStream.videoTracks[0]
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addRenderer(VideoRenderer(binding!!.surfaceView2))

            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d(TAG, "onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d(TAG, "onDataChannel: ")
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ")
            }
        }

        return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer: VideoCapturer?
        if (useCamera2()) {
            videoCapturer = createCameraCapturer(Camera2Enumerator(this))
        } else {
            videoCapturer = createCameraCapturer(Camera1Enumerator(true))
        }
        return videoCapturer
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        return null
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }

    companion object {
        private val TAG = "CompleteActivity"
        private val RC_CALL = 111
    }

}
