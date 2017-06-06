package com.github.funkyg.funkytunes.view

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.*
import com.github.funkyg.funkytunes.R
import com.github.funkyg.funkytunes.Song
import com.github.funkyg.funkytunes.Util
import com.github.funkyg.funkytunes.activities.PlayingQueueActivity
import com.github.funkyg.funkytunes.service.MusicService
import com.github.funkyg.funkytunes.service.PlaybackInterface

class BottomControlView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : RelativeLayout(context, attrs, defStyleAttr), PlaybackInterface {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private lateinit var musicService: MusicService
    private val logo: ImageView by lazy { findViewById(R.id.logo) as ImageView }
    private val textHolder: View by lazy { findViewById(R.id.text_holder) }
    private val progressBar: ProgressBar by lazy { findViewById(R.id.progress_bar) as ProgressBar }
    private val title: TextView by lazy { findViewById(R.id.title) as TextView }
    private val artist: TextView by lazy { findViewById(R.id.artist) as TextView }
    private val playPause: ImageButton by lazy { findViewById(R.id.play_pause) as ImageButton }

    fun setMusicService(musicService: MusicService) {
        this.musicService = musicService
        musicService.addPlaybackInterface(this)
        val listener = View.OnClickListener{
            val intent = Intent(context, PlayingQueueActivity::class.java)
            context.startActivity(intent)
        }
        logo.setOnClickListener(listener)
        textHolder.setOnClickListener(listener)

        musicService.getCurrentSong()?.let { s -> onPlaySong(s, -1) }
        if (musicService.isPlaying())
            onResumed()
        else
            onPaused()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        musicService.removePlaybackInterface(this)
    }

    override fun onPlaySong(song: Song, index: Int) {
        visibility = View.VISIBLE
        progressBar.progress = musicService.getSongProgress()!!
        progressBar.max = song.duration!!
        Util.setUrl(logo, song.image.url)
        title.text = song.name
        artist.text = song.artist
        if (musicService.isPlaying())
            incrementProgress()
    }

    private fun incrementProgress() {
        handler?.postDelayed(incrementProgressRunnable, 1000)
    }

    private val incrementProgressRunnable: Runnable = Runnable {
        incrementProgress()
        progressBar.progress = musicService.getSongProgress()!!
    }

    override fun onResumed() {
        playPause.setImageResource(R.drawable.ic_pause_accent_24dp)
        playPause.setOnClickListener { musicService.pause() }
    }

    override fun onPaused() {
        playPause.setImageResource(R.drawable.ic_play_arrow_accent_24dp)
        playPause.setOnClickListener { musicService.resume() }
        handler?.removeCallbacks(incrementProgressRunnable)
    }
}
