package com.PineApple.VideoStream.ui.stream

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.PineApple.VideoStream.databinding.FragmentStreamBinding
import com.pedro.common.ConnectChecker
import com.pedro.rtspserver.RtspServerCamera2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.ServerSocket

class StreamFragment : Fragment(), ConnectChecker {

    private val TAG = "StreamFragment"
    private var _binding: FragmentStreamBinding? = null
    private val binding get() = _binding!!

    private lateinit var rtspServerCamera2: RtspServerCamera2
    private var currentPort = 12600
    private var startPortRange = 12600
    private var endPortRange = 12649
    private var currentIp: String? = null
    private val SERVICE_NAME = "PineAppleStream"
    private val SERVICE_TYPE = "_rtsp._tcp"

    private var isSurfaceReady = false
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private lateinit var sharedPreferences: SharedPreferences
    private val PREF_AUDIO_ENABLED = "pref_audio_enabled"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) startInitialStream()
            else Toast.makeText(context, "Permissions required", Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStreamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sharedPreferences = requireContext().getSharedPreferences("VideoStreamPrefs", Context.MODE_PRIVATE)

        setupPortRangeSpinner()

        // Initial setup with default port
        rtspServerCamera2 = RtspServerCamera2(binding.surfaceView, this, currentPort)

        startIpDetection()

        binding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                isSurfaceReady = true
                checkPermissionsAndStart()
            }
            override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                isSurfaceReady = false
                stopStream()
                if (::rtspServerCamera2.isInitialized) {
                    rtspServerCamera2.stopPreview()
                }
            }
        })

        binding.surfaceView.setOnClickListener {
            if (rtspServerCamera2.isStreaming) stopStream() else startStream()
        }
    }

    private fun updateStatus(text: String, colorResId: Int) {
        activity?.runOnUiThread {
            binding.ipAddressText.text = text
            binding.ipAddressText.setTextColor(ContextCompat.getColor(requireContext(), colorResId))
        }
    }

    private fun setupPortRangeSpinner() {
        val ranges = listOf("12600-12649", "12650-12699", "12700-12749", "12750-12799",
                          "12800-12849", "12850-12899", "12900-12949", "12950-12999")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, ranges)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.rangeSpinner.adapter = adapter

        binding.rangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val range = ranges[position].split("-")
                startPortRange = range[0].toInt()
                endPortRange = range[1].toInt()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun findAvailablePort(start: Int, end: Int): Int {
        for (port in start..end) {
            try {
                ServerSocket(port).use { return port }
            } catch (e: Exception) { continue }
        }
        return start
    }

    private fun startIpDetection() {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                for (la in lp.linkAddresses) {
                    val addr = la.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress
                        currentIp = ip
                        activity?.runOnUiThread {
                            if (rtspServerCamera2.isStreaming) {
                                binding.wifiIpText.text = "IP: $ip:$currentPort"
                            } else {
                                binding.wifiIpText.text = "IP: $ip"
                            }
                        }
                    }
                }
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    private fun checkPermissionsAndStart() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (perms.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }) {
            startInitialStream()
        } else {
            updateStatus("Permissions Required", android.R.color.holo_red_light)
            requestPermissionLauncher.launch(perms)
        }
    }

    private fun startInitialStream() {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(150) // Essential delay for SDK 36 permission sync
            if (isSurfaceReady) {
                startStream()
            }
        }
    }

    private fun startStream() {
        updateStatus("Initializing...", android.R.color.holo_orange_light)

        // Stop existing stream cleanly
        if (::rtspServerCamera2.isInitialized) {
            if (rtspServerCamera2.isStreaming) {
                rtspServerCamera2.stopStream()
            }
            if (rtspServerCamera2.isOnPreview) {
                rtspServerCamera2.stopPreview()
            }
        }

        currentPort = findAvailablePort(startPortRange, endPortRange)
        Log.d(TAG, "Attempting to start stream on port: $currentPort")

        // Only create new instance if not initialized or port changed
        if (!::rtspServerCamera2.isInitialized) {
            rtspServerCamera2 = RtspServerCamera2(binding.surfaceView, this, currentPort)
        }

        val isAudioEnabled = sharedPreferences.getBoolean(PREF_AUDIO_ENABLED, true)
        Log.d(TAG, "Audio Enabled Setting: $isAudioEnabled")

        // Prepare with corrected settings
        val videoPrepared = rtspServerCamera2.prepareVideo(1280, 720, 30, 1024 * 1024, 90)
        val audioPrepared = if (isAudioEnabled)
            rtspServerCamera2.prepareAudio(64 * 1024, 32000, true)
        else false

        if (videoPrepared) {
            if (isAudioEnabled && !audioPrepared) {
                Log.e(TAG, "Audio preparation failed! Streaming video only.")
            }

            rtspServerCamera2.startStream("live")
            rtspServerCamera2.startPreview()

            registerService(currentPort)

            val statusText = if (isAudioEnabled && audioPrepared) "Live (Audio+Video)" else "Live (Video Only)"
            updateStatus("$statusText on Port $currentPort", android.R.color.holo_green_light)
            Toast.makeText(requireContext(), statusText, Toast.LENGTH_SHORT).show()

            currentIp?.let { ip ->
                binding.wifiIpText.text = "IP: $ip:$currentPort"
                Log.d(TAG, "Stream started at rtsp://$ip:$currentPort/live")
            }
        } else {
            Log.e(TAG, "Failed to prepare video. Check resolution support.")
            updateStatus("Failed: Video Not Supported", android.R.color.holo_red_light)
        }
    }

    private fun stopStream() {
        if (::rtspServerCamera2.isInitialized && rtspServerCamera2.isStreaming) {
            rtspServerCamera2.stopStream()
            unregisterService()
            updateStatus("Ready", android.R.color.white)
        }
    }

    private fun registerService(port: Int) {
        nsdManager = requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {}
            override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
        }
        nsdManager?.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun unregisterService() {
        registrationListener?.let { nsdManager?.unregisterService(it) }
    }

    // ConnectChecker overrides
    override fun onConnectionSuccess() {
        activity?.runOnUiThread { updateStatus("Watcher Connected", android.R.color.holo_blue_light) }
    }

    override fun onConnectionFailed(r: String) {
        activity?.runOnUiThread { updateStatus("Client Error", android.R.color.holo_orange_light) }
    }

    override fun onDisconnect() {
        activity?.runOnUiThread { updateStatus("Live on Port $currentPort", android.R.color.holo_green_light) }
    }

    override fun onAuthError() {
        activity?.runOnUiThread { updateStatus("Auth Error", android.R.color.holo_red_light) }
    }

    override fun onAuthSuccess() {}

    override fun onConnectionStarted(u: String) {}

    override fun onNewBitrate(b: Long) {}

    override fun onDestroyView() {
        super.onDestroyView()
        stopStream()
        if (::rtspServerCamera2.isInitialized) {
            rtspServerCamera2.stopPreview()
        }
        networkCallback?.let { (requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) }
        _binding = null
    }
}