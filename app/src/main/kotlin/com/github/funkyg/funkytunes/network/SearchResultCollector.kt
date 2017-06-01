package com.github.funkyg.funkytunes.network

import android.util.Log
import com.android.volley.Request
import com.frostwire.jlibtorrent.*
import com.github.funkyg.funkytunes.R
import java.util.*

/* Torrent search result */
class SearchResult(val title: String, val magnetLink: String, val torrentUrl: String?, val detailsUrl: String,
					val size: String, val added: Date?, val seeds: Int, val leechers: Int,
					var torrentInfo: TorrentInfo?) {
					}

/*
 * Collects responses from torrent requests
 * Calls a callback when all results are complete
 */

class SearchResultCollector(val torrentListener: (TorrentInfo)->Unit, val magnetListener: (String)->Unit, val errorListener: (Int) -> Unit) {
	private val Tag = "ResultCollector"
	private val watchedRequests = arrayListOf<Request<TorrentInfo>>()
	private var failedRequests = 0
	private val searchResults = arrayListOf<SearchResult>()

	fun watchRequest(req: Request<TorrentInfo>) {
		watchedRequests.add(req)
	}

	private fun getBestResult(): SearchResult {
		assert(searchResults.size > 0)
		val eligibleTorrents = searchResults
		                       .sortedByDescending {r -> r.seeds + r.leechers}
		Log.i(Tag, "Selecting torrent: ${eligibleTorrents[0].detailsUrl}")
		return eligibleTorrents[0]
	}

	private fun checkComplete() {
		if(watchedRequests.size <= failedRequests + searchResults.size) {
			if(searchResults.size > 0) {
				/* Success - got one or more good results */
				val bestResult = getBestResult()
				var sent = false
				bestResult.torrentInfo?.let { ti ->
					Log.i(Tag, "Starting torrent download")
					torrentListener(ti)
					sent = true
				}
				if(!sent) {
					Log.i(Tag, "Starting manget download")
					magnetListener(bestResult.magnetLink)
				}
			} else {
				Log.w(Tag, "All requests failed")
				errorListener(R.string.error_no_torrent_found)
			}
		} else {
			val totalCompleted = failedRequests + searchResults.size
			Log.i(Tag, "${totalCompleted} / ${watchedRequests.size} - ${searchResults.size} usable")
			for(request in watchedRequests) {
				Log.i(Tag, "INQUEUE: " + request.getUrl())
			}
		}
	}

	fun addFailed() {
		failedRequests = failedRequests + 1
		checkComplete()
	}

	fun addResult(result: SearchResult) {
		searchResults.add(result)
		checkComplete()
	}
}
