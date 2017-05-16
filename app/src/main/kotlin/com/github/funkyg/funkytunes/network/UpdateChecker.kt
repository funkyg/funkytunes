package com.github.funkyg.funkytunes.network

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.github.funkyg.funkytunes.BuildConfig
import com.github.funkyg.funkytunes.FunkyApplication
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.HttpURLConnection
import javax.inject.Inject

class UpdateChecker(val context: Context) {

    private val TAG = "UpdateChecker"
    private val UPDATE_URL = "https://api.github.com/repos/funkyg/funkytunes/releases/latest"

    @Inject lateinit var volleyQueue: RequestQueue

    interface UpdateListener {
        fun updateAvailable()
        fun repoNotFound()
    }

    init {
        (context.applicationContext as FunkyApplication).component.inject(this)
    }

    fun checkUpdate(updateListener: UpdateListener) {
        if (BuildConfig.DEBUG)
            return

        val request = StringRequest(Request.Method.GET, UPDATE_URL, Response.Listener<String> { reply ->
            val available = Gson().fromJson(reply, JsonObject::class.java).get("name").asString
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            if (available != current) {
                updateListener.updateAvailable()
            }
        }, Response.ErrorListener { error ->
            if (error.networkResponse != null &&
                    error.networkResponse.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                updateListener.repoNotFound()
            }
            Log.w(TAG, "Update check failed", error)
        })

        volleyQueue.add(request)
    }
}