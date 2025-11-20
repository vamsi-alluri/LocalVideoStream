package com.PineApple.VideoStream.ui.stream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.PineApple.VideoStream.databinding.FragmentStreamBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors

class StreamFragment : Fragment() {

    private var _binding: FragmentStreamBinding? = null
    private val binding get() = _binding!!

    // Network & Camera variables
    private val serverPort = 8080
    private var serverSocket: ServerSocket? = null
    private var currentJpeg: ByteArray? = null // Stores the latest frame to send
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Check Permissions
        if (allPermissionsGranted()) {
            startCamera()
            startServer()
            displayIpAddress()
        } else {
            Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview Use Case (Shows camera on screen)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // Analysis Use Case (Gets frames for streaming)
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        // Convert YUV frame to JPEG Byte Array
                        currentJpeg = imageProxyToJpeg(imageProxy)
                        imageProxy.close()
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("StreamFragment", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(serverPort)
                Log.d("StreamFragment", "Server started on port $serverPort")

                while (true) {
                    val client = serverSocket?.accept()
                    Log.d("StreamFragment", "Client connected: ${client?.inetAddress}")

                    client?.let { socket ->
                        try {
                            // Use DataOutputStream to send primitive types (Int) easily
                            val dataOutputStream = java.io.DataOutputStream(socket.getOutputStream())

                            while (!socket.isClosed) {
                                val jpegData = currentJpeg
                                if (jpegData != null) {
                                    // 1. Send the size of the image first
                                    dataOutputStream.writeInt(jpegData.size)

                                    // 2. Send the actual image data
                                    dataOutputStream.write(jpegData)
                                    dataOutputStream.flush()
                                }
                                Thread.sleep(60) // ~15 FPS (Prevents network flooding)
                            }
                        } catch (e: Exception) {
                            Log.e("StreamFragment", "Client connection error: $e")
                        } finally {
                            socket.close()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StreamFragment", "Server Error: $e")
            }
        }
    }

    private fun displayIpAddress() {
        val wifiManager = requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        binding.ipAddressText.text = "$ipAddress" // e.g., 192.168.1.5
    }

    // Helper: Convert CameraX ImageProxy to JPEG ByteArray
    private fun imageProxyToJpeg(image: ImageProxy): ByteArray? {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y
        yBuffer.get(nv21, 0, ySize)

        // Copy UV (NV21 format expects V then U interwoven)
        val u = ByteArray(uSize)
        val v = ByteArray(vSize)
        uBuffer.get(u)
        vBuffer.get(v)

        // This is a simplified conversion for NV21
        // Standard NV21: YYYYYYYY VU VU VU VU
        // Note: ImageProxy stride handling can be complex; this is a basic implementation
        // suitable for full-frame standard camera buffers.

        // For accurate interleaving of U and V:
        var pixel = 0
        for (i in 0 until uSize) {
            // We are approximating here for simplicity in this snippet.
            // Real NV21 interleaving depends on pixel stride.
            // If pixelStride == 2, the byte buffer already contains interwoven data.
            if(image.planes[1].pixelStride == 2) {
                // If pixel stride is 2, the V buffer effectively covers both
                vBuffer.position(0)
                vBuffer.get(nv21, ySize, vSize)
                break
            }
        }

        // Fallback: Convert using YuvImage
        // To be absolutely safe without complex stride math manually:
        // We can use the Y buffer + V buffer logic if pixelStride is 2 (common on Android)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 70, out)
        return out.toByteArray()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        serverSocket?.close()
        cameraExecutor.shutdown()
        _binding = null
    }
}