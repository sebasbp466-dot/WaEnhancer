package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class ModXBackdrop(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private class AnimBg(
        private val base: Int,
        c1: Int,
        c2: Int,
        private val speedPct: Int
    ) : Drawable(), Runnable {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val s1 = RadialGradient(0f, 0f, 1f, c1, c1 and 0x00FFFFFF, Shader.TileMode.CLAMP)
        private val s2 = RadialGradient(0f, 0f, 1f, c2, c2 and 0x00FFFFFF, Shader.TileMode.CLAMP)
        private val t0 = SystemClock.uptimeMillis()
        private var running = true

        private fun blob(canvas: Canvas, sh: Shader, x: Float, y: Float, r: Float) {
            paint.shader = sh
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
        }

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f) return
            canvas.drawColor(base)
            val k = 0.05f + speedPct / 100f * 0.35f
            val t = (SystemClock.uptimeMillis() - t0) / 1000f * k
            val big = max(w, h)
            blob(canvas, s1, w * (0.25f + 0.22f * sin(t * 1.1f)), h * (0.20f + 0.15f * cos(t * 0.9f)), big * 0.55f)
            blob(canvas, s2, w * (0.78f + 0.20f * cos(t * 0.8f)), h * (0.55f + 0.18f * sin(t * 1.3f)), big * 0.60f)
            blob(canvas, s1, w * (0.45f + 0.25f * sin(t * 0.6f + 2f)), h * (0.92f + 0.08f * cos(t)), big * 0.50f)
            if (running) scheduleSelf(this, SystemClock.uptimeMillis() + 40)
        }

        override fun run() {
            invalidateSelf()
        }

        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            val changed = super.setVisible(visible, restart)
            running = visible
            if (visible) invalidateSelf() else unscheduleSelf(this)
            return changed
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    private fun idName(v: View): String {
        if (v.id <= 0) return ""
        return try {
            v.resources.getResourceEntryName(v.id)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun findList(v: View): ViewGroup? {
        if (v is ViewGroup && v.javaClass.simpleName == "WDSList") return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val r = findList(v.getChildAt(i))
                if (r != null) return r
            }
        }
        return null
    }

    private fun installBg(root: View, c1: Int, c2: Int, speed: Int) {
        val list = findList(root)
        if (list == null) {
            Log.i("ModX", "ModXBackdrop: no encontré WDSList")
            return
        }
        var content: LinearLayout? = null
        var p = list.parent as? View
        while (p != null) {
            if (p is LinearLayout && idName(p) == "content" && p.background != null) {
                content = p
                break
            }
            p = p.parent as? View
        }
        if (content == null) {
            Log.i("ModX", "ModXBackdrop: no encontré el contenedor content")
            return
        }
        val cur = content.background
        if (cur !is AnimBg) {
            val base = ((cur as? ColorDrawable)?.color ?: (list.background as? ColorDrawable)?.color
                ?: 0x0A1014) or (0xFF shl 24)
            val anim = AnimBg(base, c1, c2, speed)
            ModXState.baseBg = base
            ModXState.bgDrawable = anim
            ModXState.bgView = content
            content.background = anim
            Log.i("ModX", "ModXBackdrop instalado base=#${Integer.toHexString(base)}")
        }
        if (list.background != null) list.background = null
    }

    @Throws(Exception::class)
    override fun doHook() {
        val on = xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_bg_anim", false)
        Log.i("ModX", "ModXBackdrop on=$on")
        if (!on) return
        val barId = Utils.getID("bottom_nav", "id")
        if (barId <= 0) return
        val a = (xprefs.getInt("modx_bg_intensity", 30).coerceIn(5, 60) * 255 / 100) shl 24
        val col1 = xprefs.getInt("modx_bg_color1", 0)
        val col2 = xprefs.getInt("modx_bg_color2", 0)
        val c1 = a or ((if (col1 != 0) col1 else 0x21C063) and 0xFFFFFF)
        val c2 = a or ((if (col2 != 0) col2 else 0x3B82F6) and 0xFFFFFF)
        val speed = xprefs.getInt("modx_bg_speed", 40)

        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (v.id != barId) return@after
                val root = v.rootView
                for (delay in longArrayOf(600L, 2000L, 4500L)) {
                    root.postDelayed({
                        try {
                            installBg(root, c1, c2, speed)
                        } catch (t: Throwable) {
                            Log.w("ModX", "ModXBackdrop falló", t)
                        }
                    }, delay)
                }
            }
        }
    }

    override fun getPluginName(): String = "ModX Backdrop"
}
