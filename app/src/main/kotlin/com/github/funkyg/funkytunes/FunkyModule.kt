package com.github.funkyg.funkytunes

import android.support.v4.app.NotificationManagerCompat
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.github.funkyg.funkytunes.network.*
import com.github.funkyg.funkytunes.service.NotificationHandler
import com.github.funkyg.funkytunes.service.TorrentManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class FunkyModule(private val app: FunkyApplication) {

    @Provides
    @Singleton
    fun getVolleyQueue() = Volley.newRequestQueue(app)

    @Provides
    @Singleton
    fun getTorrentManager() = TorrentManager(app)

    @Provides
    @Singleton
    fun getNotificationManager() =  NotificationManagerCompat.from(app)

    @Provides
    fun getChartsFetcher() = ChartsFetcher(app)

    @Provides
    fun getUpdateChecker() = UpdateChecker(app)

    @Provides
    fun getSearchHandler() = SearchHandler(app)

    @Provides
    fun getPirateBayAdapter() = PirateBayAdapter(app)

    @Provides
    fun getSkyTorrentsAdapter() = SkyTorrentsAdapter(app)
}
