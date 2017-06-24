package com.github.funkyg.funkytunes.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.FunkyApplication
import com.github.funkyg.funkytunes.Song
import javax.inject.Inject

class MusicService : Service() {

    private val Tag = "MusicService"
    // The volume we set the media player to when we lose audio focus, but are allowed to reduce
    // the volume instead of stopping playback.
    private val DUCK_VOLUME = 0.1f

    @Inject lateinit var torrentManager: TorrentManager
    private val notificationHandler by lazy { NotificationHandler(this) }
    private val audioManager by lazy {getSystemService(Context.AUDIO_SERVICE) as AudioManager}

    private var mediaPlayer: MediaPlayer? = null
    private val playbackListeners = ArrayList<PlaybackInterface>()
    private var playedAlbum: Album? = null
    private val playlist = ArrayList<Song>()
    private var currentTrack: Int = 0
    private var currentSongInfo: Song? = null
	private var noisyReceiverRegistered = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(Tag, "Received ACTION_AUDIO_BECOMING_NOISY intent")
            pause()
        }
    }
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(Tag, "AUDIOFOCUS_GAIN")
                mediaPlayer?.setVolume(1f, 1f)
                resume()
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.i(Tag, "AUDIOFOCUS_LOSS, AUDIOFOCUS_LOSS_TRANSIENT")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(Tag, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                mediaPlayer?.setVolume(DUCK_VOLUME, DUCK_VOLUME)
            }
        }
    }

    class MusicBinder(val service: MusicService) : Binder()

    override fun onBind(intent: Intent?) = MusicBinder(this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =  START_STICKY

    override fun onCreate() {
        super.onCreate()
        (applicationContext as FunkyApplication).component.inject(this)
        addPlaybackInterface(notificationHandler)

        // Delete all files, so we don't run out of space.
        // This should be replaced by proper cache handling, to discard files only if a certain
        // amount of storage is used, with a strategy like Least Recently Used.
        filesDir.deleteRecursively()
    }

    override fun onDestroy() {
        super.onDestroy()
        torrentManager.destroy()
        notificationHandler.stop()
        removePlaybackInterface(notificationHandler)
    }

    fun play(album: Album) {
        if (isPlaying()) {
            pause()
        }
        playbackListeners.forEach { l -> l.onPlayAlbum(album) }
        playlist.clear()
        playedAlbum = album
        currentTrack = 0
        torrentManager.setCurrentAlbum(album, { fileList ->
            playlist.addAll(fileList.mapIndexed { idx, f -> Song(f, album.artist, album.image, null, idx) })
            playbackListeners.forEach { l -> l.onPlaylistLoaded(playlist) }
            playTrack()
        }, { messageRes ->
            Handler(Looper.getMainLooper()).post({
                Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
                playbackListeners.forEach { l -> l.onCancelAlbum() }
            })
        })
    }

    fun pause() {
        playbackListeners.forEach { l -> l.onPaused() }
        mediaPlayer?.pause()
        playbackStopped()
    }

    fun resume() {
        playbackStarted {
            playbackListeners.forEach { l -> l.onResumed() }
            mediaPlayer?.start()
        }
    }

    private fun playbackStopped() {
		if (noisyReceiverRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (t: Throwable) {
            }
		}
        audioManager.abandonAudioFocus(afChangeListener)
    }

    private fun playbackStarted(callback: () -> Unit) {
        val result = audioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
			noisyReceiverRegistered = true
            callback()
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    fun playPrevious() {
        if (currentTrack == 0)
            return

        playbackListeners.forEach { l -> l.onPlayPrevious() }
        pause()
        currentTrack--
        playTrack()
    }

    fun playNext() {
        if (currentTrack == playlist.size)
            return

        playbackListeners.forEach { l -> l.onPlayNext() }
        pause()
        currentTrack++
        playTrack()
    }

    fun stop() {
        mediaPlayer?.stop()
        torrentManager.stop()
        playlist.clear()
        currentSongInfo = null
        playbackListeners.forEach { l -> l.onStopped() }
        playbackStopped()
    }

    fun playTrack(index: Int) {
        assert(index >= 0 && index <= playlist.size)
        pause()
        currentTrack = index
        playTrack()
        startService(Intent(this, MusicService::class.java))
    }

    /**
     * Requests {@link #currentTrack} as torrent, and starts playing it on FILE_COMPLETED.
     */
    private fun playTrack() {
        Handler(Looper.getMainLooper()).post({
            playbackListeners.forEach { l ->
                l.onEnqueueTrack(currentTrack)
            }
        })
        torrentManager.requestSong(currentTrack, { file ->
            playbackStarted {
                Log.i(Tag, "Playing track " + file.name)
                mediaPlayer = MediaPlayer.create(this, Uri.fromFile(file))
                mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
                mediaPlayer?.start()
                mediaPlayer?.setOnCompletionListener { songCompleted() }

                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(file.absolutePath)
                val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                val song = playlist!![currentTrack]
                song.name = title
                song.artist = artist
                song.duration = mediaPlayer?.duration?.div(1000)
                currentSongInfo = song

                Handler(Looper.getMainLooper()).post({
                    playbackListeners.forEach { l ->
                        l.onPlaySong(currentSongInfo!!, currentTrack)
                        l.onResumed()
                    }
                })
            }
        }, {index: Int, progress: Int -> 
            Handler(Looper.getMainLooper()).post({
                playbackListeners.forEach { l ->
                    l.onProgress(index, progress)
                }
            })
        })
    }

    /**
     * Callback when the current song is over, starts the next track.
     */
    private fun songCompleted() {
        if (currentTrack + 1 >= playlist.size) {
            playbackListeners.forEach { l -> l.onPaused() }
            playbackStopped()
            stopSelf()
            return
        }

        currentTrack++
        playTrack()
    }

    fun getCurrentSong(): Song? {
        return currentSongInfo
    }

    fun getSongProgress(): Int? {
        return mediaPlayer?.currentPosition?.div(1000)
    }

    /**
     * Sets callback for playlist changes, and calls it immediately with current playlist.
     */
    fun addPlaybackInterface(callback: PlaybackInterface) {
        playbackListeners += callback
        playedAlbum?.let { a -> callback.onPlayAlbum(a) }
        if (!playlist.isEmpty()) {
            callback.onPlaylistLoaded(playlist)
        }
    }

    fun removePlaybackInterface(callback: PlaybackInterface) {
        playbackListeners -= callback
    }
}
