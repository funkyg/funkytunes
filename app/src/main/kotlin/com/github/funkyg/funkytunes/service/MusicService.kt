package com.github.funkyg.funkytunes.service

import android.app.Service
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.github.funkyg.funkytunes.*
import javax.inject.Inject

class MusicService : Service() {

    private val Tag = "MusicService"

    @Inject lateinit var torrentManager: TorrentManager
    private val notificationHandler by lazy { NotificationHandler(this) }
    private var mediaPlayer: MediaPlayer? = null
    private val playbackListeners = ArrayList<PlaybackInterface>()
    private var playedAlbum: Album? = null
    private val playlist = ArrayList<Song>()
    private var currentTrack: Int = 0
    private var currentSongInfo: Song? = null

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
        pause()
        playbackListeners.forEach { l -> l.onPlayAlbum(album) }
        playlist.clear()
        playedAlbum = album
        currentTrack = 0
        torrentManager.setCurrentAlbum(album, { fileList ->
            playlist.addAll(fileList.map { f -> Song(f, album.artist, album.image, null) })
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
    }

    fun resume() {
        playbackListeners.forEach { l -> l.onResumed() }
        mediaPlayer?.start()
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
        torrentManager.requestSong(currentTrack, { file ->
            Log.i(Tag, "Playing track " + file.name)
            mediaPlayer = MediaPlayer.create(this, Uri.fromFile(file))
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener{ songCompleted()}

            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            currentSongInfo = Song(title, artist, playlist!![currentTrack].image,
                                   mediaPlayer?.duration?.div(1000))

            Handler(Looper.getMainLooper()).post({
                playbackListeners.forEach { l ->
                    l.onPlaySong(currentSongInfo!!)
                    l.onResumed()
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
