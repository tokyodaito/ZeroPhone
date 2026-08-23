package com.numenlabs.zerophone.desktop

import java.awt.Desktop
import java.net.URI

/**
 * Opens a received link in the system browser — only ever as an explicit
 * user action: sync delivers links, it never opens them automatically.
 * Only http/https URLs are browsable (no file://, no arbitrary schemes).
 */
object LinkOpener {

    fun isBrowsable(url: String): Boolean = runCatching {
        val parsed = URI.create(url)
        (parsed.scheme == "http" || parsed.scheme == "https") &&
            !parsed.host.isNullOrBlank()
    }.getOrDefault(false)

    /** Returns true when the URL was handed to the system browser. */
    fun open(url: String): Boolean {
        if (!isBrowsable(url)) return false
        return try {
            if (Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
            ) {
                Desktop.getDesktop().browse(URI.create(url))
                true
            } else {
                false
            }
        } catch (failure: Exception) {
            false
        }
    }
}
