package com.github.funkyg.funkytunes

data class Album(val title: String, val artist: String, val year: Int?, val image: Image) {
	fun getQuery(): String {
        // Exclude additions like "(Original Motion Picture Soundtrack)" or "(Deluxe Edition)" from
        // the query.
        val name = title.split('(', '[', limit = 2)[0]
        val yearFormat = year?.toString() ?: ""
        return "$artist $name $yearFormat"
	}

	val titleyear: String
		get() { 
			val yearFormat = if (year != null) "("+year.toString()+")" else ""
			return "$title $yearFormat"
		}
}

data class Song(val name: String, val artist: String?, val image: Image, val duration: Int?)

data class Image(val url: String)
