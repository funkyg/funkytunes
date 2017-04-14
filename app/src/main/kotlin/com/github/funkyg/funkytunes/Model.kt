package com.github.funkyg.funkytunes

data class Album(val title: String, val artist: String, val image: Image)

data class Song(val name: String, val artist: String?, val image: Image, val duration: Int?)

data class Image(val url: String)