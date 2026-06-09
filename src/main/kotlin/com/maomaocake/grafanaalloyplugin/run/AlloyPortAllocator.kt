package com.maomaocake.grafanaalloyplugin.run

import java.net.InetAddress
import java.net.ServerSocket

/**
 * Picks a free TCP port for `alloy run`'s HTTP server. Tries the Alloy default (12345)
 * first; if it's already bound (e.g. another Alloy instance, or anything else), walks
 * upward through the configured range until something opens cleanly.
 *
 * Bound to `127.0.0.1` deliberately — the embedded UI only ever needs loopback access,
 * and binding to `0.0.0.0` here would let the freshly-started Alloy expose its UI on
 * every interface, including company networks.
 */
object AlloyPortAllocator {

    const val DEFAULT_PORT = 12345
    private const val MAX_OFFSET = 50

    /**
     * Returns the first port in `[preferred, preferred+MAX_OFFSET]` that we can bind to,
     * or `null` if every port in the window was busy. Probing closes the socket
     * immediately, so by the time the caller spawns `alloy` there's a tiny race window —
     * acceptable for an interactive run, and the only alternative is to keep a `SO_REUSEADDR`
     * socket open and hand it off, which Alloy can't accept.
     */
    fun allocate(preferred: Int = DEFAULT_PORT): Int? {
        val loopback = InetAddress.getLoopbackAddress()
        for (offset in 0..MAX_OFFSET) {
            val candidate = preferred + offset
            if (candidate > 65535) break
            try {
                ServerSocket(candidate, /* backlog = */ 0, loopback).use { return candidate }
            } catch (_: Throwable) {
                // Port busy or unbindable — try next.
            }
        }
        return null
    }
}
