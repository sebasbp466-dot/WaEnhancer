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
        val m3 = (xprefs.getBoolean("modx_m3", false) && xprefs.getBoolean("modx_m3_dialogs", false))
        val anim = (xprefs.getBoolean("modx_m3", false) && xprefs.getBoolean("modx_animations", false))
        val motionBlur = anim && xprefs.getBoolean("modx_motion_blur", false)
        android.util.Log.i("ModX", "ModXStyle m3=$m3 anim=$anim motionBlur=$motionBlur")
        val m3blur = xprefs.getBoolean("modx_m3", false) && xprefs.getBoolean("modx_m3_blur", false)
        val m3blurDp = xprefs.getInt("modx_m3_blur_radius", 16)
        if (!m3 && !anim && !m3blur) return

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
                if (m3blur && android.os.Build.VERSION.SDK_INT >= 31) {
                    val ba = window.attributes
                    ba.flags = ba.flags or android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    ba.blurBehindRadius = (m3blurDp * dialog.context.resources.displayMetrics.density).toInt()
                    window.attributes = ba
                }
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
                    bg.setColor(ModXColors.surface(dialog.context, night, xprefs.getBoolean("modx_m3_dynamic", false)))
                    bg.cornerRadius = 28f * density
                    window.setBackgroundDrawable(bg)
                }
                if (anim) {
                    playEnter(decor, density, 0f, 0.80f, 380, if (motionBlur) 20f else 0f)
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
                    playEnter(view, density, 90f, 0.86f, 520, if (motionBlur) 40f else 0f)
                }
            }
        }
    }

    override fun getPluginName(): String = "ModX Style"
}
