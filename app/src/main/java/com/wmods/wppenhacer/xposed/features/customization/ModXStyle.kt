package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.app.Dialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature

class ModXStyle(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    @Throws(Exception::class)
    override fun doHook() {
        val m3 = xprefs.getBoolean("modx_m3_dialogs", false)
        val anim = xprefs.getBoolean("modx_animations", false)
        if (!m3 && !anim) return

        Dialog::class.java.resolve().firstMethod {
            name = "show"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val dialog = instance as? Dialog ?: return@after
                val window = dialog.window ?: return@after
                val decor = window.decorView
                val attrs = window.attributes
                val floating = attrs.width == ViewGroup.LayoutParams.WRAP_CONTENT &&
                    attrs.height == ViewGroup.LayoutParams.WRAP_CONTENT
                if (!floating) return@after
                val density = dialog.context.resources.displayMetrics.density
                val orig = decor.background
                val hasBg = orig != null && !(orig is ColorDrawable && orig.color == 0)
                if (m3 && hasBg) {
                    val ui = dialog.context.resources.configuration.uiMode
                    val night = (ui and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    val bg = GradientDrawable()
                    bg.setColor(if (night) 0xFF2B2930.toInt() else 0xFFF3EDF7.toInt())
                    bg.cornerRadius = 28f * density
                    window.setBackgroundDrawable(bg)
                }
                if (anim) {
                    decor.alpha = 0f
                    decor.scaleX = 0.92f
                    decor.scaleY = 0.92f
                    decor.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }

        if (anim) {
            View::class.java.resolve().firstMethod {
                name = "onAttachedToWindow"
                superclass()
                emptyParameters()
            }.hook {
                after {
                    val view = instance as? View ?: return@after
                    if (view.javaClass.name != "com.android.internal.policy.DecorView") return@after
                    if (view.context !is Activity) return@after
                    val density = view.resources.displayMetrics.density
                    view.alpha = 0f
                    view.translationY = 24f * density
                    view.animate().alpha(1f).translationY(0f)
                        .setDuration(240).setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }
    }

    override fun getPluginName(): String = "ModX Style"
}
