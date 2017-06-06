package com.github.funkyg.funkytunes.service

import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.Song

interface PlaybackInterface {

    fun onPlayAlbum(album: Album) {}
    fun onCancelAlbum() {}
    fun onPlaylistLoaded(playlist: List<Song>) {}
    fun onPlaySong(song: Song) {}
    fun onPaused() {}
    fun onResumed() {}
    fun onPlayPrevious() {}
    fun onPlayNext() {}
	fun onPlayTrack(index: Int) {}
	fun onEnqueueTrack(index: Int) {}
}
