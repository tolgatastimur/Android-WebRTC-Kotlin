package com.tolgatastimur.androidwebrtc.tutorial

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

import com.tolgatastimur.androidwebrtc.R
import com.tolgatastimur.androidwebrtc.databinding.ActivitySampleCameraRenderBinding

import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoCapturer
import org.webrtc.VideoRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

import com.tolgatastimur.androidwebrtc.app_rtc_sample.web_rtc.PeerConnectionClient.VIDEO_TRACK_ID

/*
* Example of how to render camera with WebRTC SDK without any abstraction classes
* */
class CameraRenderActivity : AppCompatActivity() {

    private var binding: ActivitySampleCameraRenderBinding? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_sample_camera_render)

        // Create video renderers.
        val rootEglBase = EglBase.create()
        binding!!.surfaceView.init(rootEglBase.eglBaseContext, null)
        binding!!.surfaceView.setEnableHardwareScaler(true)
        binding!!.surfaceView.setMirror(true)

        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)
        val factory = PeerConnectionFactory(null)
        factory.setVideoHwAccelerationOptions(rootEglBase.eglBaseContext, rootEglBase.eglBaseContext)

        createVideoTrackFromCameraAndShowIt(factory)
    }

    private fun createVideoTrackFromCameraAndShowIt(factory: PeerConnectionFactory) {
        val videoCapturer = createVideoCapturer()
        val videoSource = factory.createVideoSource(videoCapturer!!)
        videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)

        val localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        localVideoTrack.setEnabled(true)
        localVideoTrack.addRenderer(VideoRenderer(binding!!.surfaceView))
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
        val VIDEO_RESOLUTION_WIDTH = 1280
        val VIDEO_RESOLUTION_HEIGHT = 720
        val FPS = 30
    }

}