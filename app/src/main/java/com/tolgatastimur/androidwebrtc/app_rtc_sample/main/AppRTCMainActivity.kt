package com.tolgatastimur.androidwebrtc.app_rtc_sample.main

import android.Manifest
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity

import com.tolgatastimur.androidwebrtc.R
import com.tolgatastimur.androidwebrtc.app_rtc_sample.call.CallActivity

import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions

import com.tolgatastimur.androidwebrtc.app_rtc_sample.util.Constants.EXTRA_ROOMID
import com.tolgatastimur.androidwebrtc.databinding.ActivityMainBinding

/**
 * Handles the initial setup where the user selects which room to join.
 */
class AppRTCMainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding!!.connectButton.setOnClickListener { v -> connect() }
        binding!!.roomEdittext.requestFocus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    @AfterPermissionGranted(RC_CALL)
    private fun connect() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (EasyPermissions.hasPermissions(this, *perms)) {
            connectToRoom(binding!!.roomEdittext.text.toString())
        } else {
            EasyPermissions.requestPermissions(this, "Need some permissions", RC_CALL, *perms)
        }
    }

    private fun connectToRoom(roomId: String) {
        val intent = Intent(this, CallActivity::class.java)
        intent.putExtra(EXTRA_ROOMID, roomId)
        startActivityForResult(intent, CONNECTION_REQUEST)
    }

    companion object {
        private val LOG_TAG = "AppRTCMainActivity"
        private val CONNECTION_REQUEST = 1
        private const val RC_CALL = 111
    }
}
