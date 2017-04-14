package com.github.funkyg.funkytunes.activities

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.BR
import com.github.funkyg.funkytunes.R
import com.github.funkyg.funkytunes.Song
import com.github.funkyg.funkytunes.databinding.ActivityPlayingQueueBinding
import com.github.funkyg.funkytunes.databinding.ItemPlaylistBinding
import com.github.funkyg.funkytunes.service.PlaybackInterface
import com.github.nitrico.lastadapter.Holder
import com.github.nitrico.lastadapter.ItemType
import com.github.nitrico.lastadapter.LastAdapter

class PlayingQueueActivity : BaseActivity(), PlaybackInterface {

    private lateinit var binding: ActivityPlayingQueueBinding

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

    override fun onPlaylistLoaded(playlist: List<Song>) {
        runOnUiThread {
            val itemBinder = object : ItemType<ItemPlaylistBinding>(R.layout.item_playlist) {
                override fun onBind(holder: Holder<ItemPlaylistBinding>) {
                    holder.binding.root.setOnClickListener {
                        service!!.playTrack(holder.adapterPosition)
                    }
                }
            }

            LastAdapter(playlist, BR.song)
                    .map(Song::class.java, itemBinder)
                    .into(binding.recycler)
            binding.recycler.visibility = View.VISIBLE
            binding.progress.visibility = View.GONE
            binding.empty.visibility = View.GONE
        }
    }
}
