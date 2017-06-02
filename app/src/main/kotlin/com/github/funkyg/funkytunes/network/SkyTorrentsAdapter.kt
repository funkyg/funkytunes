package com.github.funkyg.funkytunes.network

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.NetworkResponse
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.frostwire.jlibtorrent.*
import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.FunkyApplication
import com.github.funkyg.funkytunes.R
import com.google.common.io.Files
import java.io.IOException
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
    private val QUERYURL = "/search/all/ss/1/?l=en_us&q=%s"

    @Inject lateinit var volleyQueue: RequestQueue

    init {
        (context.applicationContext as FunkyApplication).component.inject(this)
    }

    fun search(album: Album, resultCollector:SearchResultCollector) {
		search_mirror(0, album, resultCollector)
	}

    fun search_mirror(retry: Int, album: Album, resultCollector: SearchResultCollector) {
		val query = album.getQuery()

		if(retry < DOMAINS.size) {
			val domain = DOMAINS[retry]
			// Build full URL string
			val url = String.format(domain + QUERYURL, URLEncoder.encode(query, "UTF-8"))
			Log.i(Tag, "Trying URL: " + url)

			// Get the results page 
			val request = object : StringRequest(Method.GET, url, Response.Listener<String> { reply ->
				val parsedResults = parseHtml(reply, domain)

				// Fetch torrent files for results
				for (item in parsedResults) {
					Log.i(Tag, "Trying torrent URL: " + item.torrentUrl)
					val torrentRequest = object : Request<TorrentInfo>(Method.GET, item.torrentUrl, Response.ErrorListener { error ->
						Log.i(Tag, "Volley error '" + error.message + "' for URL: " + item.torrentUrl)
						val lowerTitle = item.title.toLowerCase()
						// Add magnet if it has a supported file format in name
						if(lowerTitle.indexOf("mp3") != -1 || lowerTitle.indexOf("ogg") != -1 || lowerTitle.indexOf("m4a") != -1) {
							resultCollector.addResult(item)
						} else if(lowerTitle.indexOf("flac") != -1) {
							item.flacDetected()
							resultCollector.addResult(item)
						} else {
							resultCollector.addFailed()
						}
					}){
						override fun parseNetworkResponse(response: NetworkResponse) : Response<TorrentInfo> {
							if (response.statusCode == 429) {
								resultCollector.notify429()
								return Response.error(VolleyError("Error 429 getting torrent from " + item.torrentUrl))
							} 
							else
							{
								val bytes = response.data
								if (bytes != null) {
									val tmp = createTempFile("funkytunes", ".torrent")
									Files.write(bytes, tmp)
									try {
										val ti = TorrentInfo(tmp)

										return Response.success(ti, HttpHeaderParser.parseCacheHeaders(response))
									} catch (e: IllegalArgumentException) {
										return Response.error(VolleyError("Error " + e.message + " parsing torrent from " + item.torrentUrl))
									} catch (e: IOException) {
										return Response.error(VolleyError("Error " + e.message + " parsing torrent from " + item.torrentUrl))
									} finally {
										tmp.delete()
									}
								} else {
									Log.i(Tag, "Empty bytes returned from URL: " + item.torrentUrl)
									return Response.error(VolleyError("Empty bytes returned from URL: " + item.torrentUrl))
								}
							}
						}
						override fun deliverResponse(response: TorrentInfo) {
							val ti = response
							item.torrentInfo = ti

							val origFiles = ti.origFiles()
							var fileUsable = false
							try {
								Log.i(Tag, "Parsing torrent from URL: " + item.torrentUrl)
								for (fileNum in 0..origFiles.numFiles()-1) {
									val fileName = origFiles.filePath(fileNum)
									if(fileName.endsWith(".mp3") || fileName.endsWith(".flac") || fileName.endsWith(".ogg") || fileName.endsWith(".m4a")) {
										if(!fileUsable) {
											Log.i(Tag, "Torrent usable: " + item.torrentUrl)
											resultCollector.addResult(item)
										}
										if(fileName.endsWith(".flac")) {
											item.flacDetected()
										}
										fileUsable = true
									}
								}
								if(!fileUsable) {
									resultCollector.addFailed()
								}
							}
							catch (e: Exception) {
								Log.w(Tag, "Error " + e.message + " parsing torrent URL: " + item.torrentUrl)
								resultCollector.addFailed()
							}
						}
					}
					torrentRequest.tag = query
					resultCollector.watchRequest(torrentRequest)
					volleyQueue.add(torrentRequest)
				}
			}, Response.ErrorListener { error ->
				Log.i(Tag, error.message ?: "(No message from volley)")
				search_mirror(retry + 1, album, resultCollector)
			}) {
				override fun getHeaders(): MutableMap<String, String> {
					val headers = HashMap<String, String>()
					headers.put("User-agent", "FunkyTunes")
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
        return results.slice(0..Math.min(5, results.size - 1))
    }

    private fun parseHtmlItem(htmlItem: String, prefixDetails: String): SearchResult {
		// TODO: parse XML file instead? 
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
        val prefixYear = (Calendar.getInstance().get(Calendar.YEAR)).toString() + " " // Date.getYear() gives the current year - 1900
        val df1 = SimpleDateFormat("yyyy MM-dd HH:mm", Locale.US)
        val df2 = SimpleDateFormat("MM-dd yyyy", Locale.US)

        val detailsStart = htmlItem.indexOf(DETAILS) + DETAILS.length
        var details = htmlItem.substring(detailsStart, htmlItem.indexOf(DETAILS_END, detailsStart))
        details = prefixDetails + details
        val nameStart = htmlItem.indexOf(NAME, detailsStart) + NAME.length
        val name = htmlItem.substring(nameStart, htmlItem.indexOf(NAME_END, nameStart))

        // Torrent link is first
        val torrentLinkStart = htmlItem.indexOf(TORRENT_LINK, nameStart) + TORRENT_LINK.length
        val torrentLink = prefixDetails + htmlItem.substring(torrentLinkStart, htmlItem.indexOf(TORRENT_LINK_END, torrentLinkStart))

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

        return SearchResult(name, magnetLink, torrentLink, details, 
							size, date, seeders, leechers, 
							null)
    }

}
