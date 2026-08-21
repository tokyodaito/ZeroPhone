package com.numenlabs.zerophone

import android.app.admin.DeviceAdminReceiver

/**
 * Device admin component required for Device Owner provisioning. All policy logic
 * (package suspension) lives in [com.numenlabs.zerophone.policy.PolicyApplier], which
 * guards every DevicePolicyManager call with isDeviceOwnerApp().
 */
class ZeroDeviceAdminReceiver : DeviceAdminReceiver()
