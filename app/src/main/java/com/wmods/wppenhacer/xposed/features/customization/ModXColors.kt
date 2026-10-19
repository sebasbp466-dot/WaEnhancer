package com.wmods.wppenhacer.xposed.features.customization

import android.content.Context
import android.os.Build

object ModXColors {
    fun surface(ctx: Context, night: Boolean, dynamic: Boolean): Int {
        if (dynamic && Build.VERSION.SDK_INT >= 31) {
            try {
                return ctx.getColor(
                    if (night) android.R.color.system_neutral1_800
                    else android.R.color.system_neutral1_50
                )
            } catch (_: Throwable) {
            }
        }
        return if (night) 0xFF2B2930.toInt() else 0xFFF3EDF7.toInt()
    }

    fun onSurface(ctx: Context, night: Boolean, dynamic: Boolean): Int {
        if (dynamic && Build.VERSION.SDK_INT >= 31) {
            try {
                return ctx.getColor(
                    if (night) android.R.color.system_neutral1_100
                    else android.R.color.system_neutral1_900
                )
            } catch (_: Throwable) {
            }
        }
        return if (night) 0xFFE6E0E9.toInt() else 0xFF1D1B20.toInt()
    }
}
