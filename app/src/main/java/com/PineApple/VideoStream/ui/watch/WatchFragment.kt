package com.PineApple.VideoStream.ui.watch

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.PineApple.VideoStream.databinding.FragmentWatchBinding
import kotlinx.coroutines.*
import java.net.Socket

class WatchFragment : Fragment() {

    // 1. Setup View Binding
    private var _binding: FragmentWatchBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // 2. Setup Coroutines for Networking
    private var streamJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var isStreaming = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate using the Binding class
        _binding = FragmentWatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 3. Access views directly using 'binding' (No findViewById needed!)
        binding.connectButton.setOnClickListener {
            val ip = binding.ipAddressInput.text.toString()

            if (ip.isNotBlank()) {
                stopCurrentStream() // Reset if already running
                isStreaming = true
                streamJob = startViewing(ip)
            } else {
                Toast.makeText(requireContext(), "Please enter an IP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startViewing(ip: String): Job {
        return coroutineScope.launch {
            try {
                Log.d("WatchFragment", "Connecting to $ip...")

                // Connect to the Server (Phone 1) on Port 8080
                val socket = Socket(ip, 8080)
                val inputStream = socket.getInputStream()

                // Basic loop to decode the stream
                while (isStreaming && isActive) {
                    // BitmapFactory.decodeStream is smart enough to find the next JPEG in the stream
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    if (bitmap != null) {
                        // Switch to Main Thread to update UI
                        withContext(Dispatchers.Main) {
                            binding.videoView.setImageBitmap(bitmap)
                        }
                    } else {
                        break // Stream ended
                    }
                }
                socket.close()

            } catch (e: Exception) {
                Log.e("WatchFragment", "Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Connection Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopCurrentStream() {
        isStreaming = false
        streamJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 4. Clean up Binding and Background Jobs to prevent memory leaks
        stopCurrentStream()
        _binding = null
    }
}