package com.tolgatastimur.androidwebrtc.tutorial

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log

import com.tolgatastimur.androidwebrtc.R
import com.tolgatastimur.androidwebrtc.databinding.ActivitySamplePeerConnectionBinding

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

import java.util.ArrayList

import com.tolgatastimur.androidwebrtc.app_rtc_sample.web_rtc.PeerConnectionClient.VIDEO_TRACK_ID

/*
* Shows how to use PeerConnection to connect clients and stream video using MediaStream
* without any networking
* */
class MediaStreamActivity : AppCompatActivity() {

    private var binding: ActivitySamplePeerConnectionBinding? = null
    private var rootEglBase: EglBase? = null
    private var videoTrackFromCamera: VideoTrack? = null
    private var factory: PeerConnectionFactory? = null
    private var localPeerConnection: PeerConnection? = null
    private var remotePeerConnection: PeerConnection? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sample_peer_connection)

        initializeSurfaceViews()

        initializePeerConnectionFactory()

        createVideoTrackFromCameraAndShowIt()

        initializePeerConnections()

        startStreamingVideo()
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
        localPeerConnection = createPeerConnection(factory!!, true)
        remotePeerConnection = createPeerConnection(factory!!, false)
    }

    private fun startStreamingVideo() {
        val mediaStream = factory!!.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera!!)
        localPeerConnection!!.addStream(mediaStream)

        val sdpMediaConstraints = MediaConstraints()

        localPeerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "onCreateSuccess: ")
                localPeerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                remotePeerConnection!!.setRemoteDescription(SimpleSdpObserver(), sessionDescription)

                remotePeerConnection!!.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        localPeerConnection!!.setRemoteDescription(SimpleSdpObserver(), sessionDescription)
                        remotePeerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                    }
                }, sdpMediaConstraints)
            }
        }, sdpMediaConstraints)
    }

    private fun createPeerConnection(factory: PeerConnectionFactory, isLocal: Boolean): PeerConnection {
        val rtcConfig = PeerConnection.RTCConfiguration(ArrayList<PeerConnection.IceServer>())
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
                Log.d(TAG, "onIceCandidate: $isLocal")
                if (isLocal) {
                    remotePeerConnection!!.addIceCandidate(iceCandidate)
                } else {
                    localPeerConnection!!.addIceCandidate(iceCandidate)
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

        // First, try to find front facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
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

    /*
    * Read more about Camera2 here
    * https://developer.android.com/reference/android/hardware/camera2/package-summary.html
    * */
    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }

    companion object {
        private val TAG = "SamplePeerConnectionAct"
        val VIDEO_RESOLUTION_WIDTH = 1280
        val VIDEO_RESOLUTION_HEIGHT = 720
        val FPS = 30
        private val DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement"
    }

}