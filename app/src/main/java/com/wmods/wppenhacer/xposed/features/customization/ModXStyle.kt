package com.wmods.wppenhacer.xposed.features.customization

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature

class ModXStyle(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private fun playEnter(
        view: View, density: Float, slideDp: Float, startScale: Float,
        durationMs: Long, blurDp: Float
    ) {
        view.alpha = 0f
        view.translationY = slideDp * density
        view.scaleX = startScale
        view.scaleY = startScale
        view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(durationMs).setInterpolator(DecelerateInterpolator(1.6f)).start()
        if (blurDp > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val va = ValueAnimator.ofFloat(blurDp * density, 0f)
            va.duration = durationMs
            va.interpolator = DecelerateInterpolator(1.6f)
            va.addUpdateListener {
                val r = it.animatedValue as Float
                if (r < 0.5f) view.setRenderEffect(null)
                else view.setRenderEffect(
                    RenderEffect.createBlurEffect(0f, r, Shader.TileMode.CLAMP)
                )
            }
            va.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.setRenderEffect(null)
                }
            })
            va.start()
        }
    }

    @Throws(Exception::class)
    override fun doHook() {
        val m3 = xprefs.getBoolean("modx_m3_dialogs", false)
        val anim = xprefs.getBoolean("modx_animations", false)
        val motionBlur = anim && xprefs.getBoolean("modx_motion_blur", false)
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
                    playEnter(decor, density, 0f, 0.88f, 280, if (motionBlur) 10f else 0f)
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
                    if (view.javaClass.simpleName != "DecorView") return@after
                    if (view.context !is Activity) return@after
                    val density = view.resources.displayMetrics.density
                    playEnter(view, density, 40f, 0.94f, 320, if (motionBlur) 24f else 0f)
                }
            }
        }
    }

    override fun getPluginName(): String = "ModX Style"
}
