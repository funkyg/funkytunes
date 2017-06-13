package com.github.funkyg.funkytunes.activities

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.View
import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.BR
import com.github.funkyg.funkytunes.R
import com.github.funkyg.funkytunes.Song
import com.github.funkyg.funkytunes.databinding.ActivityPlayingQueueBinding
import com.github.funkyg.funkytunes.databinding.ItemPlaylistBinding
import com.github.funkyg.funkytunes.service.PlaybackInterface
import com.github.lzyzsd.circleprogress.DonutProgress
import com.github.nitrico.lastadapter.Holder
import com.github.nitrico.lastadapter.ItemType
import com.github.nitrico.lastadapter.LastAdapter


class PlayingQueueActivity : BaseActivity(), PlaybackInterface {

	private val Tag = "PlayingQueueActivity"

    private lateinit var binding: ActivityPlayingQueueBinding

	private var adapter: LastAdapter? = null
	private var currentPlaylist: List<Song>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_playing_queue)
        binding.recycler.layoutManager = LinearLayoutManager(this)
    }

    override fun onServiceConnected() {
        service!!.addPlaybackInterface(this)
        binding.bottomControl.container.setMusicService(service!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        service!!.removePlaybackInterface(this)
        service?.removePlaybackInterface(binding.bottomControl.root as PlaybackInterface)
    }

    override fun onPlayAlbum(album: Album) {
        runOnUiThread {
            binding.recycler.visibility = View.GONE
            binding.progress.visibility = View.VISIBLE
            binding.empty.visibility = View.GONE
        }
    }

    override fun onCancelAlbum() {
        finish()
    }

	override fun onProgress(index: Int, progress: Int) {
		val playlist: List<Song>? = currentPlaylist
		if (playlist == null) {
			Log.v(Tag, "Progress received when playlist is null")
			return
		}
		val song = playlist[index]
		if (!song.isPlaying) {
			song.isPlaying = false
			song.progress = progress.toInt()

			//Log.v(Tag, "Progress[$index] = $progress song=$song")
			runOnUiThread {
				binding.recycler.getAdapter().notifyDataSetChanged()
			}
		} else {
			Log.v(Tag, "Progress received when song is playing: $song")
		}
	}

	override fun onPlaySong(song: Song, index: Int) {
		val playlist: List<Song>? = currentPlaylist
		if (playlist != null) {
			for(s in playlist) {
				s.isPlaying = false
			}
		} else {
			Log.v(Tag, "onPlaySong received when playlist is empty")
		}
		song.isPlaying = true
		song.isQueued = false

		runOnUiThread {
			binding.recycler.getAdapter().notifyDataSetChanged()
		}
		Log.i(Tag, "Playing track $index, adapter: $adapter")
	}

	override fun onEnqueueTrack(index: Int) {
		val playlist: List<Song>? = currentPlaylist
		if (playlist != null) {
			for(s in playlist) {
				s.isQueued = false
			}
		} else {
			Log.v(Tag, "onEnqueueTrack received when playlist is empty")
			return
		}
		val song = playlist[index]
		song.isPlaying = false
		song.isQueued = true

		runOnUiThread {
			binding.recycler.getAdapter().notifyDataSetChanged()
		}
		Log.i(Tag, "Enqueued track $index")
	}

    override fun onPlaylistLoaded(playlist: List<Song>) {
        runOnUiThread {
			currentPlaylist = playlist

            val itemBinder = object : ItemType<ItemPlaylistBinding>(R.layout.item_playlist) {
                override fun onBind(holder: Holder<ItemPlaylistBinding>) {
                    holder.binding.root.setOnClickListener {
                        service!!.playTrack(holder.adapterPosition)
                    }
                }
            }

            adapter = LastAdapter(playlist, BR.song)
                    .map(Song::class.java, itemBinder)
                    .into(binding.recycler)
            binding.recycler.visibility = View.VISIBLE
            binding.progress.visibility = View.GONE
            binding.empty.visibility = View.GONE
        }
    }

    override fun onStopped() {
        binding.recycler.visibility = View.GONE
        binding.progress.visibility = View.GONE
        binding.empty.visibility = View.VISIBLE
    }
}
