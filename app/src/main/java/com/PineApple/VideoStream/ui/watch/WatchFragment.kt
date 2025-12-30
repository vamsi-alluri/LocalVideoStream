package com.PineApple.VideoStream.ui.watch

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.PineApple.VideoStream.databinding.FragmentWatchBinding

class WatchFragment : Fragment() {

    private var _binding: FragmentWatchBinding? = null
    private val binding get() = _binding!!

    // Player
    private var player: ExoPlayer? = null

    // Discovery
    private lateinit var nsdManager: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val availableSenders = mutableListOf<String>() // List of IPs/Names
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    // Preferences for remembering last sender
    private lateinit var prefs: SharedPreferences
    private val PREF_LAST_IP = "last_connected_ip"

    // Constants matching StreamFragment
    private val SERVICE_TYPE = "_rtsp._tcp." // Must match the Sender's type

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireActivity().getSharedPreferences("VideoStreamPrefs", Context.MODE_PRIVATE)

        setupSpinner()
        startDiscovery()

        binding.connectButton.setOnClickListener {
            val selectedIp = binding.senderSpinner.selectedItem as? String

            // Allow manual entry if needed, or fallback to spinner selection
            val manualIp = binding.ipAddressInput.text.toString()
            val targetIp = if (manualIp.isNotBlank()) manualIp else selectedIp

            if (!targetIp.isNullOrBlank()) {
                saveLastUsedIp(targetIp)
                initializePlayer(targetIp)
            } else {
                Toast.makeText(requireContext(), "Select a sender or enter IP", Toast.LENGTH_SHORT).show()
            }
        }

        // Pre-fill input with last used IP
        val lastIp = prefs.getString(PREF_LAST_IP, "")
        if (!lastIp.isNullOrEmpty()) {
            binding.ipAddressInput.setText(lastIp)
        }
    }

    // --- RTSP Player Logic ---

    @OptIn(UnstableApi::class)
    private fun initializePlayer(ip: String) {
        releasePlayer() // Clean up old player if exists

        player = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = player

        // Construct RTSP URL.
        // RootEncoder defaults to valid RTSP endpoints at port 1935 (as defined in StreamFragment)
        val rtspUrl = "rtsp://$ip:1935"

        // Create MediaItem
        val mediaItem = MediaItem.fromUri(rtspUrl)

        // While MediaItem.fromUri often works, explicitly using RtspMediaSource
        // ensures ExoPlayer handles the protocol correctly if auto-detection fails.
        // If you just use setMediaItem(mediaItem), ExoPlayer usually auto-detects RTSP based on the scheme.
        player?.setMediaItem(mediaItem)

        player?.prepare()
        player?.play()

        Toast.makeText(requireContext(), "Connecting to $rtspUrl", Toast.LENGTH_SHORT).show()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    // --- Service Discovery Logic (NSD) ---

    private fun setupSpinner() {
        spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableSenders)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.senderSpinner.adapter = spinnerAdapter
    }

    private fun startDiscovery() {
        nsdManager = requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                // Ensure this is our service type
                if (service.serviceType.contains("_rtsp")) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val hostIp = serviceInfo.host.hostAddress ?: return

                            requireActivity().runOnUiThread {
                                if (!availableSenders.contains(hostIp)) {
                                    availableSenders.add(hostIp)
                                    spinnerAdapter.notifyDataSetChanged()

                                    // Auto-select if it matches last used
                                    val lastIp = prefs.getString(PREF_LAST_IP, "")
                                    if (hostIp == lastIp) {
                                        binding.senderSpinner.setSelection(availableSenders.indexOf(hostIp))
                                    }
                                }
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                // Optional: Remove from list if lost
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        // Look for RTSP services
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLastUsedIp(ip: String) {
        prefs.edit().putString(PREF_LAST_IP, ip).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _binding = null
    }
}
