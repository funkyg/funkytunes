package com.github.funkyg.funkytunes.network

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.FunkyApplication
import com.github.funkyg.funkytunes.Image
import com.github.funkyg.funkytunes.R
import com.github.salomonbrys.kotson.*
import com.google.common.io.CharStreams
import com.google.common.io.Files
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import javax.inject.Inject

class SearchHandler(val context: Context) {

    private val Tag = "SearchHandler"
    private val SEARCH_URL = Uri.parse("https://api.discogs.com/database/search")
    private val DISCOGS_API_KEY = "VDgrHCFJOTSTdUMyvCAe"
    private val DISCOGS_API_SECRET = "HXHPkmfJJnrcfYmYYfBXrdlRDLAVmmuA"

    @Inject lateinit var volleyQueue: RequestQueue

    private val Gson = GsonBuilder()
            .registerTypeAdapter<Album> {
                deserialize {
                    // HACK: Apparently Discogs does not return artist and title seperately, so we
                    //       have to extract them this way.
                    Album(it.json["title"].string.split(" - ")[0],
                            it.json["title"].string.split(" - ")[1],
                            Image(it.json["thumb"].string))
                }
            }
            .create()

    init {
        (context.applicationContext as FunkyApplication).component.inject(this)
    }

    fun search(query: String, listener: (List<Album>) -> Unit) {
        val uri = SEARCH_URL.buildUpon()
                .appendQueryParameter("q", query)
                .appendQueryParameter("type", "release")
                .appendQueryParameter("key", DISCOGS_API_KEY)
                .appendQueryParameter("secret", DISCOGS_API_SECRET)
                .build()

        val request = object : StringRequest(Method.GET, uri.toString(), Response.Listener<String> { reply ->
            listener(parseFeed(reply))
        }, Response.ErrorListener { error ->
            Log.w(Tag, error)
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers.put("User-agent", "funkytunes")
                return headers
            }
        }

        volleyQueue.add(request)
    }

    private fun parseFeed(data: String): List<Album> {
        val json = JsonParser().parse(data)["results"].array
        val items = Gson.fromJson<List<Album>>(json)
        // HACK: Discogs returns many duplicate releases, so we try to remove duplicates by filtering
        //       for unique artist and title.
        return items.distinctBy { x -> x.artist + x.title }
    }
}
