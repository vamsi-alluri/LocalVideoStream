package com.PineApple.VideoStream.ui.stream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.PineApple.VideoStream.databinding.FragmentStreamBinding
import com.pedro.common.ConnectCheckerRtsp
import com.pedro.library.rtsp.RtspServerCamera2
import java.net.Inet4Address

class StreamFragment : Fragment(), ConnectCheckerRtsp {

    private var _binding: FragmentStreamBinding? = null
    private val binding get() = _binding!!

    private lateinit var rtspServerCamera2: RtspServerCamera2

    // NSD (Network Service Discovery) variables
    private lateinit var nsdManager: NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private val SERVICE_NAME = "PineAppleStream"
    private val SERVICE_TYPE = "_rtsp._tcp" // Standard RTSP service type
    private val PORT = 1935

    // Permission Launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startPreview()
            } else {
                Toast.makeText(context, "Permissions required to stream", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Keep screen on while streaming
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize the Server Camera
        rtspServerCamera2 = RtspServerCamera2(binding.surfaceView, this, PORT)

        // Setup UI
        setupSurfaceView()
        displayIpAddress()

        // We will add a floating button or use the existing layout to trigger stream/switch
        // Since your XML only showed the IP text, I'll assume you might want to tap the screen or add buttons dynamically
        // checking if you have buttons in XML not shown in snippet, if not, adding click listeners to the view itself
        binding.surfaceView.setOnClickListener {
            if (rtspServerCamera2.isStreaming) {
                stopStream()
            } else {
                startStream()
            }
        }

        // Add a long click to switch cameras
        binding.surfaceView.setOnLongClickListener {
            try {
                rtspServerCamera2.switchCamera()
                Toast.makeText(context, "Switched Camera", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot switch camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            true
        }

        checkPermissions()
    }

    private fun setupSurfaceView() {
        binding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // Determine layout logic if needed
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                rtspServerCamera2.startPreview()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (rtspServerCamera2.isStreaming) {
                    rtspServerCamera2.stopStream()
                }
                rtspServerCamera2.stopPreview()
            }
        })
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE
        )

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startPreview()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun startPreview() {
        if (!rtspServerCamera2.isOnPreview) {
            // Configure default video settings (Width, Height, FPS, Bitrate, Rotation)
            // 1280x720 is a safe default for most phones
            rtspServerCamera2.prepareVideo(1280, 720, 30, 1200 * 1024, 90)
            rtspServerCamera2.prepareAudio()
            rtspServerCamera2.startPreview()
        }
    }

    private fun startStream() {
        if (!rtspServerCamera2.isStreaming) {
            if (rtspServerCamera2.prepareAudio() && rtspServerCamera2.prepareVideo()) {
                rtspServerCamera2.startStream()
                registerService(PORT)
                binding.ipAddressText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
                Toast.makeText(context, "Server Started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error preparing stream, this device can't do it", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopStream() {
        if (rtspServerCamera2.isStreaming) {
            rtspServerCamera2.stopStream()
            unregisterService()
            binding.ipAddressText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
            Toast.makeText(context, "Server Stopped", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Networking & IP Display ---

    private fun displayIpAddress() {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = cm.activeNetwork
        val caps: NetworkCapabilities? = cm.getNetworkCapabilities(activeNetwork)
        val linkProperties: LinkProperties? = cm.getLinkProperties(activeNetwork)

        var ip = "No IP Found"

        if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
            linkProperties?.linkAddresses?.forEach { linkAddress ->
                val address = linkAddress.address
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    ip = address.hostAddress ?: ip
                }
            }
        }

        binding.ipAddressText.text = ip
    }

    // --- NSD Registration (So WatchFragment can find us) ---

    private fun registerService(port: Int) {
        nsdManager = requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                val mServiceName = NsdServiceInfo.serviceName
                activity?.runOnUiThread {
                    Toast.makeText(context, "Service Registered: $mServiceName", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Registration failed
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- ConnectCheckerRtsp Callbacks ---

    override fun onConnectionSuccessRtsp() {
        activity?.runOnUiThread {
            Toast.makeText(context, "Client Connected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailedRtsp(reason: String) {
        activity?.runOnUiThread {
            stopStream()
            Toast.makeText(context, "Connection Failed: $reason", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewBitrateRtsp(bitrate: Long) {
        // Optional: Update UI with bitrate
    }

    override fun onDisconnectRtsp() {
        activity?.runOnUiThread {
            Toast.makeText(context, "Client Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAuthErrorRtsp() {
        activity?.runOnUiThread {
            Toast.makeText(context, "Auth Error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAuthSuccessRtsp() {
        activity?.runOnUiThread {
            Toast.makeText(context, "Auth Success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (rtspServerCamera2.isStreaming) {
            rtspServerCamera2.stopStream()
            unregisterService()
        }
        rtspServerCamera2.stopPreview()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        _binding = null
    }
}
