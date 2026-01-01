package com.PineApple.VideoStream.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.PineApple.VideoStream.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        settingsViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Audio Switch
        settingsViewModel.isAudioEnabled.observe(viewLifecycleOwner) {
            binding.audioSwitch.isChecked = it
        }
        binding.audioSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsViewModel.setAudioEnabled(isChecked)
        }

        // UDP Switch
        settingsViewModel.useUdp.observe(viewLifecycleOwner) {
            binding.udpSwitch.isChecked = it
        }
        binding.udpSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsViewModel.setUdpEnabled(isChecked)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}