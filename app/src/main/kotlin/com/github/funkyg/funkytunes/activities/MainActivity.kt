package com.github.funkyg.funkytunes.activities

import android.app.AlertDialog
import android.content.Intent
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.github.funkyg.funkytunes.Album
import com.github.funkyg.funkytunes.BR
import com.github.funkyg.funkytunes.FunkyApplication
import com.github.funkyg.funkytunes.R
import com.github.funkyg.funkytunes.databinding.ActivityMainBinding
import com.github.funkyg.funkytunes.databinding.ItemMainBinding
import com.github.funkyg.funkytunes.network.ChartsFetcher
import com.github.funkyg.funkytunes.network.UpdateChecker
import com.github.funkyg.funkytunes.network.SearchHandler
import com.github.nitrico.lastadapter.Holder
import com.github.nitrico.lastadapter.ItemType
import com.github.nitrico.lastadapter.LastAdapter
import javax.inject.Inject

class MainActivity : BaseActivity(), SearchView.OnQueryTextListener {

    @Inject lateinit var chartsFetcher: ChartsFetcher
    @Inject lateinit var updateChecker: UpdateChecker
    @Inject lateinit var searchHandler: SearchHandler
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (applicationContext as FunkyApplication).component.inject(this)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.recycler.layoutManager = LinearLayoutManager(this)

        chartsFetcher.fetchAppleAlbumFeed { f -> showAlbums(f) }
        checkUpdates()
    }

    override fun onServiceConnected() {
        binding.bottomControl.container.setMusicService(service!!)
    }

    private fun showAlbums(albums: List<Album>) {
        val itemBinding = object : ItemType<ItemMainBinding>(R.layout.item_main) {
            override fun onBind(holder: Holder<ItemMainBinding>) {
                holder.itemView.setOnClickListener {
                    service?.play(holder.binding.album)
                    startActivity(Intent(this@MainActivity, PlayingQueueActivity::class.java))
                }
            }
        }
        LastAdapter(albums, BR.album)
                .map<Album>(itemBinding)
                .into(binding.recycler)
        binding.recycler.visibility = View.VISIBLE
        binding.progress.visibility = View.GONE

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_main, menu)
        val searchItem = menu!!.findItem(R.id.search)
        val sv = MenuItemCompat.getActionView(searchItem) as SearchView
        sv.queryHint = getString(R.string.search_title)
        sv.setOnQueryTextListener(this)
        MenuItemCompat.setOnActionExpandListener(searchItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                ChartsFetcher(this@MainActivity).fetchAppleAlbumFeed { f -> showAlbums(f) }
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.play_queue -> {
                startActivity(Intent(this, PlayingQueueActivity::class.java))
                return true
            }
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onQueryTextSubmit(query: String) = true

    override fun onQueryTextChange(newText: String): Boolean {
        if (!newText.isEmpty()) {
            SearchHandler(this).search(newText, { a -> showAlbums(a)})
        }
        return true
    }

    private fun checkUpdates() {
        updateChecker.checkUpdate(object : UpdateChecker.UpdateListener {
            override fun updateAvailable() {
                AlertDialog.Builder(this@MainActivity)
                        .setMessage(R.string.update_available)
                        .setPositiveButton(R.string.download, { _, _ ->
                            val uri = Uri.parse(getString(R.string.github_update_url))
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
            }

            override fun repoNotFound() {
                AlertDialog.Builder(this@MainActivity)
                        .setMessage(R.string.github_repository_missing)
                        .setPositiveButton(R.string.gitlab_mirror_summary, { _, _ ->
                            val uri = Uri.parse(getString(R.string.gitlab_mirror_url))
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
            }
        })
    }
}
