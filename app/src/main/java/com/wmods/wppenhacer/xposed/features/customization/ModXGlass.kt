package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.Method
import java.util.WeakHashMap

class ModXGlass(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val installed = WeakHashMap<View, Boolean>()

    private class GlassDrawable(
        private val bar: View,
        private val drawChild: Method,
        private val blurPx: Float,
        tint: Int,
        private val corner: Float
    ) : Drawable() {
        private val node = RenderNode("modx_glass")
        private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
        private val path = Path()
        private val rect = RectF()
        private val barLoc = IntArray(2)
        private val pLoc = IntArray(2)
        private var recording = false

        override fun draw(canvas: Canvas) {
            if (recording) return
            val w = bounds.width()
            val h = bounds.height()
            if (w <= 0 || h <= 0) return
            rect.set(0f, 0f, w.toFloat(), h.toFloat())
            path.reset()
            path.addRoundRect(rect, corner, corner, Path.Direction.CW)
            if (canvas.isHardwareAccelerated) {
                try {
                    node.setPosition(0, 0, w, h)
                    node.setRenderEffect(
                        RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    )
                    val rc = node.beginRecording(w, h)
                    try {
                        recording = true
                        bar.getLocationInWindow(barLoc)
                        val now = SystemClock.uptimeMillis()
                        var child: View = bar
                        var p = bar.parent as? ViewGroup
                        while (p != null) {
                            p.getLocationInWindow(pLoc)
                            val save = rc.save()
                            rc.translate(
                                (pLoc[0] - barLoc[0]).toFloat(),
                                (barLoc[1] - pLoc[1]).toFloat()
                            )
                            rc.scale(1f, -1f)
                            for (i in 0 until p.childCount) {
                                val c = p.getChildAt(i)
                                if (c !== child && c.visibility == View.VISIBLE) {
                                    drawChild.invoke(p, rc, c, now)
                                }
                            }
                            rc.restoreToCount(save)
                            child = p
                            p = p.parent as? ViewGroup
                        }
                    } finally {
                        recording = false
                        node.endRecording()
                    }
                    canvas.save()
                    canvas.clipPath(path)
                    canvas.drawRenderNode(node)
                    canvas.restore()
                } catch (t: Throwable) {
                    Log.w("ModX", "ModXGlass: fallo al dibujar el blur", t)
                }
            }
            canvas.drawPath(path, tintPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    @Throws(Exception::class)
    override fun doHook() {
        val enabled = xprefs.getBoolean("modx_glass_bar", false)
        Log.i("ModX", "ModXGlass enabled=$enabled")
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val barId = Utils.getID("bottom_nav", "id")
        if (barId <= 0) return
        val blurDp = xprefs.getInt("modx_glass_blur", 20)
        val tintPct = xprefs.getInt("modx_glass_tint", 55)
        val drawChild = ViewGroup::class.java.getDeclaredMethod(
            "drawChild", Canvas::class.java, View::class.java, Long::class.java
        )
        drawChild.isAccessible = true

        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val view = instance as? View ?: return@after
                if (view.id != barId) return@after
                view.post { install(view, drawChild, blurDp, tintPct) }
            }
        }
    }

    private fun logTree(bar: View) {
        var c: View = bar
        var p = bar.parent as? ViewGroup
        var lvl = 0
        while (p != null && lvl < 8) {
            Log.i("ModX", "ModXGlass nivel $lvl ${p.javaClass.name} hijos=${p.childCount} " +
                "${p.left},${p.top},${p.right},${p.bottom}")
            for (i in 0 until p.childCount) {
                val k = p.getChildAt(i)
                Log.i("ModX", "ModXGlass    $i${if (k === c) "*" else " "} ${k.javaClass.simpleName} " +
                    "${k.left},${k.top},${k.right},${k.bottom} vis=${k.visibility}")
            }
            c = p
            p = p.parent as? ViewGroup
            lvl++
        }
    }

    private fun install(bar: View, drawChild: Method, blurDp: Int, tintPct: Int) {
        if (installed.containsKey(bar)) return
        val d = bar.resources.displayMetrics.density
        val night = (bar.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val base = if (night) 0x1D1B20 else 0xFEF7FF
        val tint = ((tintPct.coerceIn(0, 100) * 255 / 100) shl 24) or base
        val corner = if (xprefs.getBoolean("floating_bottom_bar", false))
            xprefs.getInt("floating_bottom_bar_radius", 24) * d else 0f
        bar.background = GlassDrawable(bar, drawChild, blurDp * d, tint, corner)
        installed[bar] = true
        logTree(bar)

        val vto = bar.viewTreeObserver
        val scrollL = ViewTreeObserver.OnScrollChangedListener { bar.invalidate() }
        val layoutL = ViewTreeObserver.OnGlobalLayoutListener { bar.invalidate() }
        vto.addOnScrollChangedListener(scrollL)
        vto.addOnGlobalLayoutListener(layoutL)
        bar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                bar.removeOnAttachStateChangeListener(this)
                if (vto.isAlive) {
                    vto.removeOnScrollChangedListener(scrollL)
                    vto.removeOnGlobalLayoutListener(layoutL)
                }
                installed.remove(bar)
            }
        })
    }

    override fun getPluginName(): String = "ModX Glass"
}
