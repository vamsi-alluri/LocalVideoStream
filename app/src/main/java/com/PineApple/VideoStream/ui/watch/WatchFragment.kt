package com.PineApple.VideoStream.ui.watch

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.PineApple.VideoStream.databinding.FragmentWatchBinding

class WatchFragment : Fragment() {

    private val TAG = "WatchFragment"
    private var _binding: FragmentWatchBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private lateinit var nsdManager: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val availableSenders = mutableListOf<String>() // Format "IP:Port"
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private val SERVICE_TYPE = "_rtsp._tcp"

    private lateinit var sharedPreferences: SharedPreferences
    private var useUdp = true
    private var isFallbackAttempt = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = requireContext().getSharedPreferences("VideoStreamPrefs", Context.MODE_PRIVATE)
        useUdp = sharedPreferences.getBoolean("pref_udp_enabled", true)

        spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableSenders)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.senderSpinner.adapter = spinnerAdapter

        binding.senderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (binding.ipAddressInput.text.toString().trim().isEmpty()) {
                    val selected = availableSenders[position]
                    Log.d(TAG, "Auto-connecting to selected stream: $selected")
                    isFallbackAttempt = false // Reset fallback on new selection
                    initializePlayer(selected)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        startDiscovery()

        binding.connectButton.setOnClickListener {
            val selection = binding.senderSpinner.selectedItem as? String
            val manualIp = binding.ipAddressInput.text.toString()
            val manualPort = binding.portInput.text.toString()

            val target = if (manualIp.isNotBlank()) {
                if (manualIp.contains(":")) manualIp else "$manualIp:${manualPort.ifBlank { "12600" }}"
            } else selection

            if (!target.isNullOrBlank()) {
                Log.d(TAG, "Connect button clicked. Target: $target")
                isFallbackAttempt = false // Reset fallback on manual connect
                initializePlayer(target)
            } else {
                 Toast.makeText(context, "Please select a stream or enter IP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer(ipPort: String) {
        val transport = if (useUdp && !isFallbackAttempt) "UDP" else "TCP"
        Log.d(TAG, "Initializing player for: $ipPort (Transport: $transport)")
        Toast.makeText(context, "Connecting via $transport...", Toast.LENGTH_SHORT).show()
        
        player?.release()
        player = ExoPlayer.Builder(requireContext()).build().apply {
            binding.playerView.player = this

            val cleanIpPort = ipPort.replace("rtsp://", "").replace("/live", "")
            val rtspUrl = "rtsp://$cleanIpPort/live"
            
            Log.d(TAG, "Playing RTSP URI: $rtspUrl")

            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(transport == "TCP")
                .setTimeoutMs(10000)
                .setDebugLoggingEnabled(true)
                .createMediaSource(MediaItem.fromUri(rtspUrl))

            setMediaSource(mediaSource)
            
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player Error: ${error.errorCodeName}", error)
                    
                    // If UDP fails with a network error, try falling back to TCP
                    if (useUdp && !isFallbackAttempt && error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                        Log.w(TAG, "UDP connection failed, falling back to TCP.")
                        isFallbackAttempt = true
                        initializePlayer(ipPort)
                    } else {
                        val cause = error.cause?.message ?: error.message
                        Toast.makeText(context, "Connection Failed: $cause", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> Log.d(TAG, "Player State: Buffering")
                        Player.STATE_READY -> Log.d(TAG, "Player State: Ready - Playing")
                        Player.STATE_ENDED -> Log.d(TAG, "Player State: Ended")
                        Player.STATE_IDLE -> Log.d(TAG, "Player State: Idle")
                    }
                }
            })

            prepare()
            playWhenReady = true
        }
    }

    private fun startDiscovery() {
        Log.d(TAG, "Starting NSD Discovery")
        nsdManager = requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_rtsp._tcp")) {
                     nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, err: Int) {}
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            val entry = "${si.host.hostAddress}:${si.port}"
                            activity?.runOnUiThread {
                                if (!availableSenders.contains(entry)) {
                                    availableSenders.add(entry)
                                    spinnerAdapter.notifyDataSetChanged()
                                }
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onDiscoveryStopped(s: String) {}
            override fun onStartDiscoveryFailed(s: String, e: Int) {}
            override fun onStopDiscoveryFailed(s: String, e: Int) {}
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        _binding = null
    }
}