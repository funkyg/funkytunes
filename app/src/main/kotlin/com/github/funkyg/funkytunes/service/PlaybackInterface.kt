package com.github.funkyg.funkytunes.service

import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.Song

interface PlaybackInterface {

    fun onPlayAlbum(album: Album) {}
    fun onCancelAlbum() {}
    fun onPlaylistLoaded(playlist: List<Song>) {}
    fun onPlaySong(song: Song, index: Int) {}
    fun onPaused() {}
    fun onResumed() {}
    fun onPlayPrevious() {}
    fun onPlayNext() {}
	fun onEnqueueTrack(index: Int) {}
	fun onProgress(index: Int, progress: Int) {}
    fun onStopped() {}
}
