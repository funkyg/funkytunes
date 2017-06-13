package com.github.funkyg.funkytunes.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.github.funkyg.funkytunes.activities.PlayingQueueActivity
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import android.widget.RemoteViews
import com.github.funkyg.funkytunes.*


class NotificationHandler(private val service: MusicService) : BroadcastReceiver(), PlaybackInterface {

    private val NotificationId = 412
    private val RequestCode = 100
    private val NotificationTimeout = TimeUnit.MINUTES.toMillis(15)
    private val ActionPause = "com.github.funkyg.funkytunes.pause"
    private val ActionResume = "com.github.funkyg.funkytunes.play"
    private val ActionPrev  = "com.github.funkyg.funkytunes.prev"
    private val ActionNext  = "com.github.funkyg.funkytunes.next"
    private val ActionStop  = "com.github.funkyg.funkytunes.destroy"

    @Inject lateinit var torrentManager: TorrentManager
    @Inject lateinit var notificationManager: NotificationManagerCompat
    private var handler = Handler()
    private var currentSong: Song? = null

    init {
        (service.applicationContext as FunkyApplication).component.inject(this)
        val filter = IntentFilter()
        filter.addAction(ActionNext)
        filter.addAction(ActionPause)
        filter.addAction(ActionResume)
        filter.addAction(ActionPrev)
        filter.addAction(ActionStop)
        service.registerReceiver(this, filter)

        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        notificationManager.cancelAll()
    }

    fun stop() {
        service.unregisterReceiver(this)
    }

    private val StopForegroundRunnable = Runnable {
        service.stopForeground(true)
    }

    private val UpdateLoadingNotificationRunnable = Runnable {
        updateLoadingNotification()
    }

    /**
     * Immediately destroy foreground and allow clearing notification. After {@link #NotificationTimeout},
     * automatically remove the notification.
     */
    private fun stopNotification() {
        service.stopForeground(false)
        handler.postDelayed(StopForegroundRunnable, NotificationTimeout)
    }

    override fun onPlayAlbum(album: Album) = updateLoadingNotification()

    private fun updateLoadingNotification() {
        val contentIntent = PendingIntent.getActivity(service, RequestCode,
                Intent(service, PlayingQueueActivity::class.java), PendingIntent.FLAG_CANCEL_CURRENT)
        val notificationBuilder = NotificationCompat.Builder(service)
                .setContentTitle(service.getString(R.string.notification_loading_title))
                .setContentText(torrentManager.getDownloadSpeed())
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress(0, 0, true)
        service.startForeground(NotificationId, notificationBuilder.build())
        handler.postDelayed(UpdateLoadingNotificationRunnable, 1000)
    }

    override fun onCancelAlbum() {
        service.stopForeground(true)
        handler.removeCallbacks(UpdateLoadingNotificationRunnable)
    }

    override fun onPlaySong(song: Song, index: Int) {
        currentSong = song
        updateNotification()
    }

    override fun onPaused() {
        updateNotification()
        stopNotification()
    }

    override fun onResumed() = updateNotification()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ActionPause  -> service.pause()
            ActionResume -> service.resume()
            ActionPrev   -> service.playPrevious()
            ActionNext   -> service.playNext()
            ActionStop   -> {
                service.stop()
                service.stopForeground(true)
                return
            }
        }
    }

    private fun updateNotification() {
        handler.removeCallbacks(UpdateLoadingNotificationRunnable)
        if (service.isPlaying()) {
            handler.removeCallbacks(StopForegroundRunnable)
        }

		if (currentSong == null) {
			return
		}

        Thread(Runnable {
            val remoteView = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification)
            remoteView.setOnClickPendingIntent(R.id.notificationStop, createPendingIntent(ActionStop))
            remoteView.setTextViewText(R.id.notificationArtist, currentSong!!.artist)
            remoteView.setTextViewText(R.id.notificationSongName, currentSong!!.name)
            val notificationBuilder = NotificationCompat.Builder(service)

            notificationBuilder.addAction(R.drawable.ic_skip_previous_white_24dp,
                    service.getString(R.string.previous), createPendingIntent(ActionPrev))

            addPlayPauseAction(notificationBuilder)

            notificationBuilder.addAction(R.drawable.ic_skip_next_white_24dp,
                    service.getString(R.string.next), createPendingIntent(ActionNext))

            val contentIntent = PendingIntent.getActivity(service, RequestCode,
                    Intent(service, PlayingQueueActivity::class.java), PendingIntent.FLAG_CANCEL_CURRENT)

            val icon = Glide.with(service)
                    .load(currentSong!!.image.url)
                    .asBitmap()
                    .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                    .get()
            notificationBuilder
                    .setStyle(NotificationCompat.DecoratedMediaCustomViewStyle())
                    .setColor(ContextCompat.getColor(service, R.color.colorPrimary))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(icon)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(contentIntent)
                    .setShowWhen(false)
                    .setContentTitle(currentSong!!.name)
                    .setContentText(currentSong!!.artist)
                    .setCustomBigContentView(remoteView)
                    .setCustomContentView(remoteView)

            service.startForeground(NotificationId, notificationBuilder.build())
        }).start()
    }

    private fun addPlayPauseAction(builder: NotificationCompat.Builder) {
        if (service.isPlaying()) {
            builder.addAction(R.drawable.ic_pause_white_24dp, service.getString(R.string.pause),
                    createPendingIntent(ActionPause))
        } else {
            builder.addAction(R.drawable.ic_play_arrow_white_24dp, service.getString(R.string.play),
                    createPendingIntent(ActionResume))
        }
    }

    private fun createPendingIntent(action: String) =
            PendingIntent.getBroadcast(service, RequestCode,
                    Intent(action).setPackage(service.packageName), PendingIntent.FLAG_CANCEL_CURRENT)
}
