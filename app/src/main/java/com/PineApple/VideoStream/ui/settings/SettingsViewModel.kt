package com.PineApple.VideoStream.ui.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("VideoStreamPrefs", Context.MODE_PRIVATE)
    private val PREF_AUDIO_ENABLED = "pref_audio_enabled"
    private val PREF_UDP_ENABLED = "pref_udp_enabled"

    private val _isAudioEnabled = MutableLiveData<Boolean>()
    val isAudioEnabled: LiveData<Boolean> = _isAudioEnabled

    private val _useUdp = MutableLiveData<Boolean>()
    val useUdp: LiveData<Boolean> = _useUdp

    init {
        _isAudioEnabled.value = sharedPreferences.getBoolean(PREF_AUDIO_ENABLED, false)
        _useUdp.value = sharedPreferences.getBoolean(PREF_UDP_ENABLED, true) // Default to UDP
    }

    fun setAudioEnabled(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_AUDIO_ENABLED, isEnabled).apply()
        _isAudioEnabled.value = isEnabled
    }

    fun setUdpEnabled(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_UDP_ENABLED, isEnabled).apply()
        _useUdp.value = isEnabled
    }
}