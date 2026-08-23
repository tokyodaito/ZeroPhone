package com.numenlabs.zerophone.desktop.state

/** Connection state of the sync client, published into [DesktopAppState]. */
enum class ConnectionState {
    /** No stored device token: the user must enter a pairing shortcode. */
    NEEDS_PAIRING,

    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}
