package com.lostf1sh.pixelplayeross.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle

/** Smallest screen width at which Android treats a display as a tablet or unfolded foldable. */
private const val LargeScreenSmallestWidthDp = 600

/**
 * Keeps phones in portrait while tablets and unfolded foldables rotate freely. Android 16+ ignores
 * orientation requests on large screens anyway, so the width check only matters for older tablets.
 */
object PhoneOrientationPolicy : Application.ActivityLifecycleCallbacks {

    @SuppressLint("SourceLockedOrientationActivity")
    fun apply(activity: Activity) {
        val isPhone = activity.resources.configuration.smallestScreenWidthDp < LargeScreenSmallestWidthDp
        val orientation = if (isPhone) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (activity.requestedOrientation != orientation) {
            activity.requestedOrientation = orientation
        }
    }

    // Applied before onCreate so a phone held sideways never lays out in landscape first.
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) = apply(activity)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
