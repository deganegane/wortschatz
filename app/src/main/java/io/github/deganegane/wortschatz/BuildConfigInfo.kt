package io.github.deganegane.wortschatz

import android.content.Context

/** App version from the package manager (BuildConfig is not generated in this project). */
object BuildConfigInfo {
    fun versionName(ctx: Context): String = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}
