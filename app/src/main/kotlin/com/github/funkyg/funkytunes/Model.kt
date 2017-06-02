package com.github.funkyg.funkytunes

data class Album(val title: String, val artist: String, val image: Image) {
	fun getQuery(): String {
        // Exclude additions like "(Original Motion Picture Soundtrack)" or "(Deluxe Edition)" from
        // the query.
        val name = title.split('(', '[', limit = 2)[0]
        val query = artist + " " + name
		return query
	}
}

data class Song(val name: String, val artist: String?, val image: Image, val duration: Int?)

data class Image(val url: String)
