package com.github.funkyg.funkytunes

import android.app.Application
import javax.inject.Inject

/**
 * Performs initialization for various libraries and HTTP cache.
 */
class FunkyApplication : Application() {

    @Inject lateinit var component: DaggerComponent

    override fun onCreate() {
        super.onCreate()

        DaggerDaggerComponent.builder()
                .funkyModule(FunkyModule(this))
                .build()
                .inject(this)
    }
}
