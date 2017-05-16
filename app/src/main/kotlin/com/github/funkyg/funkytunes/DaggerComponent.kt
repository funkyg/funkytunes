package com.github.funkyg.funkytunes

import com.github.funkyg.funkytunes.activities.MainActivity
import com.github.funkyg.funkytunes.network.ChartsFetcher
import com.github.funkyg.funkytunes.network.UpdateChecker
import com.github.funkyg.funkytunes.network.SearchHandler
import com.github.funkyg.funkytunes.network.PirateBayAdapter
import com.github.funkyg.funkytunes.service.MusicService
import com.github.funkyg.funkytunes.service.NotificationHandler
import com.github.funkyg.funkytunes.service.TorrentManager
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(FunkyModule::class))
interface DaggerComponent {

    fun inject(app: FunkyApplication)
    fun inject(chartsFetcher: ChartsFetcher)
    fun inject(searchHandler: SearchHandler)
    fun inject(pirateBayAdapter: PirateBayAdapter)
    fun inject(updateChecker: UpdateChecker)
    fun inject(musicService: MusicService)
    fun inject(notificationHandler: NotificationHandler)
    fun inject(mainActivity: MainActivity)
    fun inject(torrentManager: TorrentManager)

}