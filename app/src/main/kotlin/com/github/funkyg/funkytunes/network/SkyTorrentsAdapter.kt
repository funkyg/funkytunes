package com.github.funkyg.funkytunes.network

import android.content.Context
import android.util.Log
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.FunkyApplication
import com.github.funkyg.funkytunes.R
import java.net.URLEncoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

/**
 * Parses SkyTorrents search results from HTML.
 */
class SkyTorrentsAdapter(context: Context) {

    private val Tag = "SkyTorrentsAdapter"
    private val DOMAINS = arrayOf("https://www.skytorrents.in")
    private val QUERYURL = "/search/all/ed/1/?l=en_us&q=%s"

    @Inject lateinit var volleyQueue: RequestQueue

    init {
        (context.applicationContext as FunkyApplication).component.inject(this)
    }

    class SearchResult(val title: String, val torrentUrl: String, val detailsUrl: String,
                       val size: String, val added: Date?, val seeds: Int, val leechers: Int)

    fun search(album: Album, listener: (String) -> Unit, errorListener: (Int) -> Unit) {
		search_mirror(0, album, listener, errorListener)
	}

    fun search_mirror(retry: Int, album: Album, listener: (String) -> Unit, errorListener: (Int) -> Unit) {
        // Exclude additions like "(Original Motion Picture Soundtrack)" or "(Deluxe Edition)" from
        // the query.
        val name = album.title.split('(', '[', limit = 2)[0]
        val query = album.artist + " " + name
		if(retry < DOMAINS.size) {
			val domain = DOMAINS[retry]
			// Build full URL string
			val url = String.format(domain + QUERYURL, URLEncoder.encode(query, "UTF-8"))
			Log.i(Tag, "Trying URL: " + url)

			val request = object : StringRequest(Method.GET, url, Response.Listener<String> { reply ->
				val parsedResults = parseHtml(reply, domain)
				when (parsedResults.isEmpty()) {
					false -> listener(parsedResults.first().torrentUrl)
					true  -> errorListener(R.string.error_no_torrent_found)
				}
			}, Response.ErrorListener { error ->
				search_mirror(retry + 1, album, listener, errorListener)
			}) {
				override fun getHeaders(): MutableMap<String, String> {
					val headers = HashMap<String, String>()
					// Spoof Firefox user agent to force a result from The Pirate Bay
					headers.put("User-agent", "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2")
					return headers
				}
			}

			volleyQueue.add(request)
		}
    }

    private fun parseHtml(html: String, prefixDetails: String): List<SearchResult> {
        // Texts to find subsequently
        val RESULTS = "<tbody>"
        val TORRENT = "<td style=\"word-wrap: break-word;\">"

        // Parse the search results from HTML by looking for the identifying texts
        val results = ArrayList<SearchResult>()
        val resultsStart = html.indexOf(RESULTS) + RESULTS.length

        var torStart = html.indexOf(TORRENT, resultsStart)
        while (torStart >= 0) {
            val nextTorrentIndex = html.indexOf(TORRENT, torStart + TORRENT.length)
            if (nextTorrentIndex >= 0) {
                results.add(parseHtmlItem(html.substring(torStart + TORRENT.length, nextTorrentIndex), prefixDetails))
            } else {
                results.add(parseHtmlItem(html.substring(torStart + TORRENT.length), prefixDetails))
            }
            torStart = nextTorrentIndex
        }
        return results
    }

    private fun parseHtmlItem(htmlItem: String, prefixDetails: String): SearchResult {
        // Texts to find subsequently
        val DETAILS = "<a href=\""
        val DETAILS_END = "\" title=\""
        val NAME = "\">"
        val NAME_END = "</a>"
        val TORRENT_LINK = "<a href=\""
        val TORRENT_LINK_END = "\" rel=\"nofollow\""
        val MAGNET_LINK = "<a href=\""
        val MAGNET_LINK_END = "\" rel=\"nofollow\""
        val SIZE = "<td class=\"is-hidden-touch\" >"
        val SIZE_END = "</td>"
		val NUMFILES = "<td class=\"is-hidden-touch\" style=\"text-align: center;\" >"
		val NUMFILES_END = "</td>"

        val DATE = "<td class=\"is-hidden-touch\" style=\"text-align: center;\">"
        val DATE_END = "</td>"
        val SEEDERS = "<td style=\"text-align: center;\">"
        val SEEDERS_END = "</td>"
        val LEECHERS = "<td style=\"text-align: center;\">"
        val LEECHERS_END = "</td>"
        val prefixYear = (Date().year + 1900).toString() + " " // Date.getYear() gives the current year - 1900
        val df1 = SimpleDateFormat("yyyy MM-dd HH:mm", Locale.US)
        val df2 = SimpleDateFormat("MM-dd yyyy", Locale.US)

        val detailsStart = htmlItem.indexOf(DETAILS) + DETAILS.length
        var details = htmlItem.substring(detailsStart, htmlItem.indexOf(DETAILS_END, detailsStart))
        details = prefixDetails + details
        val nameStart = htmlItem.indexOf(NAME, detailsStart) + NAME.length
        val name = htmlItem.substring(nameStart, htmlItem.indexOf(NAME_END, nameStart))

        // Torrent link is first
        val torrentLinkStart = htmlItem.indexOf(TORRENT_LINK, nameStart) + TORRENT_LINK.length
        val torrentLink = htmlItem.substring(torrentLinkStart, htmlItem.indexOf(TORRENT_LINK_END, torrentLinkStart))

        // Magnet link is second
        val magnetLinkStart = htmlItem.indexOf(MAGNET_LINK, torrentLinkStart) + MAGNET_LINK.length
        val magnetLink = htmlItem.substring(magnetLinkStart, htmlItem.indexOf(MAGNET_LINK_END, magnetLinkStart))

        val sizeStart = htmlItem.indexOf(SIZE, magnetLinkStart) + SIZE.length
        var size = htmlItem.substring(sizeStart, htmlItem.indexOf(SIZE_END, sizeStart))
        size = size.replace("&nbsp;", " ")

        val numFilesStart = htmlItem.indexOf(NUMFILES, sizeStart) + NUMFILES.length
        var numFiles = htmlItem.substring(numFilesStart, htmlItem.indexOf(NUMFILES_END, numFilesStart))
        numFiles = numFiles.replace("&nbsp;", " ")

        val dateStart = htmlItem.indexOf(DATE, numFilesStart) + DATE.length
        var dateText = htmlItem.substring(dateStart, htmlItem.indexOf(DATE_END, dateStart))
        dateText = dateText.replace("&nbsp;", " ")
        var date: Date? = null
        if (dateText.startsWith("Today")) {
            date = Date()
        } else if (dateText.startsWith("Y-day")) {
            date = Date(Date().time - 86400000L)
        } else {
            try {
                date = df1.parse(prefixYear + dateText)
            } catch (e: ParseException) {
                try {
                    date = df2.parse(dateText)
                } catch (e1: ParseException) {
                    // Not parsable at all; just leave it at null
                }

            }
        }

        val seedersStart = htmlItem.indexOf(SEEDERS, dateStart) + SEEDERS.length
        val seedersText = htmlItem.substring(seedersStart, htmlItem.indexOf(SEEDERS_END, seedersStart))
        val seeders = Integer.parseInt(seedersText)

        val leechersStart = htmlItem.indexOf(LEECHERS, seedersStart) + LEECHERS.length
        val leechersText = htmlItem.substring(leechersStart, htmlItem.indexOf(LEECHERS_END, leechersStart))
        val leechers = Integer.parseInt(leechersText)

        return SearchResult(name, magnetLink, details, size, date, seeders, leechers)
    }

}
