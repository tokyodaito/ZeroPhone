package com.numenlabs.zerophone.sync.server.auth

import io.ktor.server.auth.Principal

/**
 * Authenticated device identity attached to a call by the Bearer device
 * token provider. Carries only the stable [deviceId] — never the raw
 * token or its hash.
 */
data class DevicePrincipal(val deviceId: String) : Principal

/** Name of the Ktor authentication provider guarding all protected routes. */
const val DEVICE_AUTH: String = "device"
