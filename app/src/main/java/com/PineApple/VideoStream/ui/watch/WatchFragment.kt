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
                val socket = Socket(ip, 8080)

                // Use DataInputStream to read the size integer
                val dataInputStream = java.io.DataInputStream(socket.getInputStream())

                while (isStreaming && isActive) {
                    try {
                        // 1. Read the size of the incoming image
                        val imageSize = dataInputStream.readInt()

                        // 2. Create a buffer to hold exactly that many bytes
                        val imageBytes = ByteArray(imageSize)

                        // 3. Read the full image data into the buffer
                        dataInputStream.readFully(imageBytes)

                        // 4. Decode the bytes into a Bitmap
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageSize)

                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                binding.videoView.setImageBitmap(bitmap)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("WatchFragment", "Stream lost: ${e.message}")
                        break
                    }
                }
                socket.close()

            } catch (e: Exception) {
                Log.e("WatchFragment", "Connection Error: ${e.message}")
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