package com.github.funkyg.funkytunes

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.NetworkInterface

/**
 * https://stackoverflow.com/q/28386553
 */
fun isVpnConnection(context: Context): Boolean {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    } else {
        return NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { n -> n.isUp }
                .map { n -> n.name }
                .any { nn -> nn.startsWith("tun") || nn.startsWith("pptp")}
    }
}