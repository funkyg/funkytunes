package com.github.funkyg.funkytunes

import android.view.View

data class Album(val title: String, val artist: String, val year: Int?, val image: Image) {
	fun getQuery(): String {
        // Exclude additions like "(Original Motion Picture Soundtrack)" or "(Deluxe Edition)" from
        // the query.
        val name = title.split('(', '[', limit = 2)[0]
        val yearFormat = year?.toString() ?: ""
        return "$artist $name $yearFormat"
	}
}

data class Song(var name: String, var artist: String?, val image: Image, var duration: Int?, var isPlaying: Boolean = false, var isQueued: Boolean = false, var progress: Float = 0.0f) {
	val songPlayingVisible: Int
		get() = if (isPlaying && !isQueued) View.VISIBLE else View.GONE
	val songProgressVisible: Int
		get() = if (isQueued && !isPlaying && progress > 0.1) View.VISIBLE else View.GONE
	val songLoadingVisible: Int
		get() = if (isQueued && !isPlaying && progress <= 0.1) View.VISIBLE else View.GONE
}

data class Image(val url: String)
