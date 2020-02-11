package com.tolgatastimur.androidwebrtc.tutorial

import android.content.Intent
import android.databinding.DataBindingUtil
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View

import com.tolgatastimur.androidwebrtc.R
import com.tolgatastimur.androidwebrtc.databinding.ActivitySampleDataChannelBinding
import com.myhexaville.smartimagepicker.ImagePicker

import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.ArrayList

/*
* Shows how to use PeerConnection to connect clients and send text messages and images using DataChannel
* without any networking
* */
class DataChannelActivity : AppCompatActivity() {

    private var binding: ActivitySampleDataChannelBinding? = null
    private var factory: PeerConnectionFactory? = null
    private var localPeerConnection: PeerConnection? = null
    private var remotePeerConnection: PeerConnection? = null
    private var localDataChannel: DataChannel? = null
    private var imagePicker: ImagePicker? = null

    internal var incomingFileSize: Int = 0
    internal var currentIndexPointer: Int = 0
    internal var imageFileBytes: ByteArray? = null
    internal var receivingFile: Boolean = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sample_data_channel)

        initializePeerConnectionFactory()

        initializePeerConnections()

        connectToOtherPeer()

        imagePicker = ImagePicker(this, null
        ) { imageUri ->
            val imageFile = imagePicker!!.imageFile
            val size = imageFile.length().toInt()
            val bytes = readPickedFileAsBytes(imageFile, size)
            sendImage(size, bytes)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        imagePicker!!.handleActivityResult(resultCode, requestCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        imagePicker!!.handlePermission(requestCode, grantResults)
    }

    private fun initializePeerConnectionFactory() {
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)
        factory = PeerConnectionFactory(null)
    }

    private fun initializePeerConnections() {
        localPeerConnection = createPeerConnection(factory!!, true)
        remotePeerConnection = createPeerConnection(factory!!, false)

        localDataChannel = localPeerConnection!!.createDataChannel("sendDataChannel", DataChannel.Init())
        localDataChannel!!.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(l: Long) {

            }

            override fun onStateChange() {
                Log.d(TAG, "onStateChange: " + localDataChannel!!.state().toString())
                runOnUiThread {
                    if (localDataChannel!!.state() == DataChannel.State.OPEN) {
                        binding!!.sendButton.isEnabled = true
                    } else {
                        binding!!.sendButton.isEnabled = false
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {

            }
        })
    }

    private fun connectToOtherPeer() {
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
                //                Log.d(TAG, "onIceConnectionReceivingChange: ");
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
                Log.d(TAG, "onAddStream: ")
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d(TAG, "onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d(TAG, "onDataChannel: is local: " + isLocal + " , state: " + dataChannel.state())
                dataChannel.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(l: Long) {

                    }

                    override fun onStateChange() {
                        Log.d(TAG, "onStateChange: remote data channel state: " + dataChannel.state().toString())
                    }

                    override fun onMessage(buffer: DataChannel.Buffer) {
                        Log.d(TAG, "onMessage: got message")
                        readIncomingMessage(buffer.data)
                    }
                })
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ")
            }
        }

        return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    fun sendMessage(view: View) {
        val message = binding!!.textInput.text.toString()
        if (message.isEmpty()) {
            return
        }

        binding!!.textInput.setText("")

        val data = stringToByteBuffer("-s$message", Charset.defaultCharset())
        localDataChannel!!.send(DataChannel.Buffer(data, false))
    }

    private fun readPickedFileAsBytes(imageFile: File, size: Int): ByteArray {
        val bytes = ByteArray(size)
        try {
            val buf = BufferedInputStream(FileInputStream(imageFile))
            buf.read(bytes, 0, bytes.size)
            buf.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return bytes
    }

    private fun sendImage(size: Int, bytes: ByteArray) {
        val numberOfChunks = size / CHUNK_SIZE

        val meta = stringToByteBuffer("-i$size", Charset.defaultCharset())
        localDataChannel!!.send(DataChannel.Buffer(meta, false))

        for (i in 0 until numberOfChunks) {
            val wrap = ByteBuffer.wrap(bytes, i * CHUNK_SIZE, CHUNK_SIZE)
            localDataChannel!!.send(DataChannel.Buffer(wrap, false))
        }
        val remainder = size % CHUNK_SIZE
        if (remainder > 0) {
            val wrap = ByteBuffer.wrap(bytes, numberOfChunks * CHUNK_SIZE, remainder)
            localDataChannel!!.send(DataChannel.Buffer(wrap, false))
        }
    }

    private fun readIncomingMessage(buffer: ByteBuffer) {
        val bytes: ByteArray
        if (buffer.hasArray()) {
            bytes = buffer.array()
        } else {
            bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
        }
        if (!receivingFile) {
            val firstMessage = String(bytes, Charset.defaultCharset())
            val type = firstMessage.substring(0, 2)

            if (type == "-i") {
                incomingFileSize = Integer.parseInt(firstMessage.substring(2, firstMessage.length))
                imageFileBytes = ByteArray(incomingFileSize)
                Log.d(TAG, "readIncomingMessage: incoming file size $incomingFileSize")
                receivingFile = true
            } else if (type == "-s") {
                runOnUiThread { binding!!.remoteText.text = firstMessage.substring(2, firstMessage.length) }
            }
        } else {
            for (b in bytes) {
                this!!.imageFileBytes?.set(currentIndexPointer++, b)
            }
            if (currentIndexPointer == incomingFileSize) {
                Log.d(TAG, "readIncomingMessage: received all bytes")
                val bmp = imageFileBytes?.size?.let { BitmapFactory.decodeByteArray(imageFileBytes, 0, it) }
                receivingFile = false
                currentIndexPointer = 0
                runOnUiThread { binding!!.image.setImageBitmap(bmp) }
            }
        }
    }

    fun pickImage(view: View) {
        imagePicker!!.openCamera()
    }

    companion object {
        private val TAG = "SampleDataChannelAct"
        val CHUNK_SIZE = 64000

        private fun stringToByteBuffer(msg: String, charset: Charset): ByteBuffer {
            return ByteBuffer.wrap(msg.toByteArray(charset))
        }
    }

}