package com.tolgatastimur.androidwebrtc

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View

import com.tolgatastimur.androidwebrtc.app_rtc_sample.main.AppRTCMainActivity
import com.tolgatastimur.androidwebrtc.tutorial.CameraRenderActivity
import com.tolgatastimur.androidwebrtc.tutorial.CompleteActivity
import com.tolgatastimur.androidwebrtc.tutorial.DataChannelActivity
import com.tolgatastimur.androidwebrtc.tutorial.MediaStreamActivity

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    fun openAppRTCActivity(view: View) {
        startActivity(Intent(this, AppRTCMainActivity::class.java))
    }

    fun openSampleActivity(view: View) {
        startActivity(Intent(this, CameraRenderActivity::class.java))
    }

    fun openSamplePeerConnectionActivity(view: View) {
        startActivity(Intent(this, MediaStreamActivity::class.java))
    }

    fun openSampleDataChannelActivity(view: View) {
        startActivity(Intent(this, DataChannelActivity::class.java))
    }

    fun openSampleSocketActivity(view: View) {
        startActivity(Intent(this, CompleteActivity::class.java))

    }
}
