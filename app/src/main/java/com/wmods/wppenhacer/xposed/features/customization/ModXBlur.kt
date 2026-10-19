package com.wmods.wppenhacer.xposed.features.customization

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Build
import android.view.WindowManager
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature

class ModXBlur(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    @Throws(Exception::class)
    override fun doHook() {
        if (!(xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_blur", false))) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val radiusDp = xprefs.getInt("modx_blur_radius", 16)

        Dialog::class.java.resolve().firstMethod {
            name = "show"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val dialog = instance as? Dialog ?: return@after
                val window = dialog.window ?: return@after
                val px = (radiusDp * dialog.context.resources.displayMetrics.density).toInt()
                val attrs = window.attributes
                attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                attrs.blurBehindRadius = px
                window.attributes = attrs
            }
        }
    }

    override fun getPluginName(): String = "ModX Blur"
}
