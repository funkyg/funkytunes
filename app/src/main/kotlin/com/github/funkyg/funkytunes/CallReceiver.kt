package com.github.funkyg.funkytunes

import android.content.BroadcastReceiver
import android.content.Context
import android.telephony.TelephonyManager
import android.content.Intent
import com.github.funkyg.funkytunes.service.MusicService

class CallReceiver(private val musicService: MusicService) : BroadcastReceiver() {

    private var pausedByCall = false

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING, TelephonyManager.EXTRA_STATE_OFFHOOK ->
                if (musicService.isPlaying()) {
                    musicService.pause()
                    pausedByCall = true
                }
            TelephonyManager.EXTRA_STATE_IDLE ->
                if (pausedByCall) {
                    musicService.resume()
                    pausedByCall = false
                }
        }
    }
}
