package com.numenlabs.zerophone

import android.app.admin.DeviceAdminReceiver

/**
 * Device admin component required for Device Owner provisioning. All policy logic
 * (package suspension) lives in [com.numenlabs.zerophone.core.policy.PolicyApplier], which
 * guards every DevicePolicyManager call with isDeviceOwnerApp().
 *
 * NOTE: the package of this class is part of the external adb provisioning contract
 * (`adb shell dpm set-device-owner com.numenlabs.zerophone/.ZeroDeviceAdminReceiver`,
 * see README) and must not change, even though the class physically lives in :core:policy.
 */
class ZeroDeviceAdminReceiver : DeviceAdminReceiver()
