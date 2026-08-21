package com.numenlabs.zerophone

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * ZeroPhone application entry point: generates the Hilt singleton component
 * shared by the launcher activity, the boot receiver and the re-lock alarm.
 */
@HiltAndroidApp
class ZeroPhoneApplication : Application()
