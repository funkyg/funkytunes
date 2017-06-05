package com.github.funkyg.funkytunes.service

import android.content.Context
import android.os.Handler
import android.util.Log
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.FileCompletedAlert
import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.FunkyApplication
import com.github.funkyg.funkytunes.R
import com.github.funkyg.funkytunes.network.PirateBayAdapter
import com.github.funkyg.funkytunes.network.SearchResultCollector
import com.github.funkyg.funkytunes.network.SkyTorrentsAdapter
import com.google.common.io.Files
import java.io.File
import javax.inject.Inject

class TorrentManager(private val context: Context) : AlertListener {

    private data class FileInfo(val filename: String, val indexInTorrent: Int, val completed: Boolean, val path: File)

    private val Tag = "TorrentManager"
    private val MAGNET_TIMEOUT_SECONDS = 60

    @Inject lateinit var pirateBayAdapter: PirateBayAdapter
    @Inject lateinit var skyTorrentsAdapter: SkyTorrentsAdapter
    private val sessionManager = SessionManager()
    private var files: List<FileInfo>? = null
    private var currentHash: Sha1Hash? = null
    private var onTorrentAddedListener: ((List<String>) -> Unit)? = null
    private var onFileCompletedListener: Pair<Int, (File) -> Unit>? = null
    private var errorListener: ((Int) -> Unit)? = null
	private lateinit var torrentTimeoutHandler: Handler
	private lateinit var torrentTimeoutRunnable: Runnable

    init {
        (context.applicationContext as FunkyApplication).component.inject(this)
        sessionManager.start()
        sessionManager.addListener(this)

        // Disable all seeding. We should handle this more intelligently, eg seed only on wifi,
        // and/or add a setting.
        sessionManager.maxActiveSeeds(0)
    }

    fun stop() {
        sessionManager.pause()
    }

    fun destroy() {
        sessionManager.stop()
    }

    override fun types() = intArrayOf(AlertType.ADD_TORRENT.swig(), AlertType.FILE_COMPLETED.swig())

    override fun alert(alert: Alert<*>) {
		try {
			val type = alert.type()

			when (type) {
				AlertType.ADD_TORRENT -> {
					val handle = (alert as AddTorrentAlert).handle()
					handle.resume()
					handle.prioritizeFiles(getTorrentFiles(handle).map{ Priority.IGNORE }.toTypedArray())
					files = getTorrentFiles(handle)
							.withIndex()
							.filter { p -> (p.value.endsWith(".mp3") || p.value.endsWith(".flac") || p.value.endsWith(".ogg") || p.value.endsWith(".m4a")) }
							.sortedBy { f -> f.value }
							.map { f -> FileInfo(f.value, f.index, false, File(handle.savePath(), f.value)) }
					if (files!!.isEmpty()) {
						errorListener!!.invoke(R.string.error_torrent_invalid_files)
						sessionManager.remove(handle)
					}
					else {
						currentHash = handle.infoHash()
						onTorrentAddedListener?.invoke(files!!.map { f -> convertToFriendlySongName(f.filename) })
						onTorrentAddedListener = null
					}
				}
				AlertType.FILE_COMPLETED -> {
					val position = (alert as FileCompletedAlert).index()
					val currentFile = files!!.find { f -> f.indexInTorrent == position }

					// For some reason we are getting this event for m3u and other file types which we
					// don't even download.
					currentFile ?: return

					Log.i(Tag, "Finished downloading " + currentFile.path.name)
					files = files!!.map { f ->
						if (f.indexInTorrent == position)
							f.copy(completed = true)
						else
							f
					}

					if (onFileCompletedListener != null && onFileCompletedListener?.first == position) {
						onFileCompletedListener?.second?.invoke(currentFile.path)
						onFileCompletedListener = null

						preloadNextTrack(position, alert.handle())
					}
				}
			}
		} catch (e: Exception) {
			Log.e(Tag, "This error prevented the torrent file from downloading:")
			Log.e(Tag, Log.getStackTraceString(e))
			throw e
		}
    }

    private fun convertToFriendlySongName(name: String): String {
        return File(name)
                .nameWithoutExtension
                .replace('_', ' ')
                .replace("-", " - ")
                .split(" ").map { n ->
                val stringArray = n.trim().toCharArray()
				if(stringArray.size > 0) {
					stringArray[0] = Character.toUpperCase(stringArray[0])
				}
                String(stringArray)
            }
            .joinToString(" ")
    }

    private fun getTorrentFiles(handle: TorrentHandle): List<String> {
        val origFiles = handle.torrentFile().origFiles()
        return (0..origFiles.numFiles()-1).map { i -> origFiles.filePath(i) }
    }

	fun startTorrent(torrentInfo: TorrentInfo) {
		Thread({ ->
			if (torrentInfo != null) {
				sessionManager.download(torrentInfo, context.filesDir)
			}
		}).start()
	}

	fun startMagnet(magnet: String) { 
		Thread({ ->
			val torrentBytes = sessionManager.fetchMagnet(magnet, MAGNET_TIMEOUT_SECONDS)
			if (torrentBytes != null) {
				val tmp = createTempFile("funkytunes", ".torrent")
				Files.write(torrentBytes, tmp)
				val ti = TorrentInfo(tmp)
				sessionManager.download(ti, context.filesDir)
				tmp.delete()
			}
			else {
				Log.w(Tag, "Fetching torrent from magnet timed out")
				errorListener!!.invoke(R.string.magnet_download_timeout)
			}
		}).start()
	}

    fun setCurrentAlbum(album: Album, listener: (List<String>) -> Unit, errorListener: (Int) -> Unit) {
        assert(onTorrentAddedListener == null)
        onTorrentAddedListener = listener
        this.errorListener = errorListener
        if (currentHash != null) {
            sessionManager.remove(sessionManager.find(currentHash))
            currentHash = null
        }

		val resultCollector = SearchResultCollector(
				2, // number of adapters
				this::startTorrent,
				this::startMagnet,
				errorListener)

        skyTorrentsAdapter.search(album, resultCollector)
        pirateBayAdapter.search(album, resultCollector)

		// Force harvesting results after 10s even if some requests have not finished
		torrentTimeoutHandler = Handler()
		torrentTimeoutRunnable = Runnable {
			Log.i(Tag, "Forcing go() after 10 seconds")
			resultCollector.forceGo()
		}
		torrentTimeoutHandler.postDelayed(torrentTimeoutRunnable, 10000)
    }

    fun requestSong(position: Int, listener: (File) -> Unit) {
        val fileInfo = files?.get(position)
        val handle = sessionManager.find(currentHash)
        if (fileInfo!!.completed) {
            listener(fileInfo.path)
            preloadNextTrack(position, handle)
            return
        }

        onFileCompletedListener = Pair(fileInfo.indexInTorrent, listener)

        handle.setFilePriority(fileInfo.indexInTorrent, Priority.NORMAL)
        Log.i(Tag, "Starting to download " + fileInfo.path.name)
    }

    /**
     * Downloads the file after requestedPosition (unless it is the last file). It might download
     * files that are not audio.
     */
    private fun preloadNextTrack(requestedPosition: Int, handle: TorrentHandle) {
        if (requestedPosition < getTorrentFiles(handle).size) {
            Log.i(Tag, "Preloading next file")
            handle.setFilePriority(requestedPosition + 1, Priority.NORMAL)
        }
    }

    /**
     * http://stackoverflow.com/a/5599842
     */
    fun getDownloadSpeed(): String {
        val rate =  sessionManager.downloadRate() / 8
        val units = arrayOf(R.string.units_bps, R.string.units_kbps, R.string.units_mbps, R.string.units_gbps)

        if (rate <= 0) {
            return context.getString(units.first(), 0)
        }

        val digitGroups = (Math.log10(rate.toDouble())/Math.log10(1024.toDouble())).toInt()
        val amount = rate/Math.pow(1024.toDouble(), digitGroups.toDouble())
        return context.getString(units[digitGroups], amount.toInt())
    }
}
