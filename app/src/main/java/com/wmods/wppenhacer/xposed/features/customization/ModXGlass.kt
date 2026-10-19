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
import android.widget.LinearLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.Method
import java.util.WeakHashMap

class ModXGlass(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val installed = WeakHashMap<View, Boolean>()
    private val padded = WeakHashMap<View, Boolean>()
    private val fabDone = WeakHashMap<View, Boolean>()
    private var lastScan = 0L

    private class GlassDrawable(
        private val bar: View,
        private val drawChild: Method,
        private val blurPx: Float,
        tint: Int,
        private val corner: Float,
        private val mirror: Boolean,
        private val extend: Boolean
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
            val barH = bounds.height()
            if (w <= 0 || barH <= 0) return
            bar.getLocationInWindow(barLoc)
            var extra = 0
            if (extend) {
                val ins = bar.rootWindowInsets
                val nav = if (ins != null) {
                    ins.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars()).bottom
                } else 0
                val gap = bar.rootView.height - (barLoc[1] + barH)
                extra = minOf(nav, gap).coerceAtLeast(0)
            }
            val h = barH + extra
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
                            if (mirror) {
                                rc.translate(
                                    (pLoc[0] - barLoc[0]).toFloat(),
                                    (barLoc[1] - pLoc[1]).toFloat()
                                )
                                rc.scale(1f, -1f)
                            } else {
                                rc.translate(
                                    (pLoc[0] - barLoc[0]).toFloat(),
                                    (pLoc[1] - barLoc[1]).toFloat()
                                )
                            }
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
        val under = xprefs.getBoolean("modx_glass_under", false) &&
            !xprefs.getBoolean("floating_bottom_bar", false)
        Log.i("ModX", "ModXGlass enabled=$enabled under=$under")
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val barId = Utils.getID("bottom_nav", "id")
        if (barId <= 0) return
        val blurDp = xprefs.getInt("modx_glass_blur", 20)
        val tintPct = xprefs.getInt("modx_glass_tint", 55)
        val fabIds = arrayOf("fab", "fab_second", "extended_mini_fab")
            .mapNotNull { n -> Utils.getID(n, "id").takeIf { id -> id > 0 } }
            .toSet()
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
                view.post { install(view, drawChild, blurDp, tintPct, under, fabIds) }
            }
        }
    }

    private fun isScroller(v: View): Boolean {
        var k: Class<*>? = v.javaClass
        while (k != null) {
            when (k.name) {
                "androidx.recyclerview.widget.RecyclerView",
                "android.widget.AbsListView",
                "android.widget.ScrollView",
                "androidx.core.widget.NestedScrollView" -> return true
            }
            k = k.superclass
        }
        return false
    }

    private fun scan(v: View, barH: Int, fabIds: Set<Int>, minH: Int) {
        if (v is ViewGroup && isScroller(v)) {
            val horizontal = v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)
            if (!horizontal) {
                if (!padded.containsKey(v) && v.height >= minH) {
                    v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom + barH)
                    v.clipToPadding = false
                    padded[v] = true
                }
                return
            }
        }
        if (v.id in fabIds && !fabDone.containsKey(v)) {
            val lp = v.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                lp.bottomMargin += barH
                v.layoutParams = lp
                fabDone[v] = true
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) scan(v.getChildAt(i), barH, fabIds, minH)
        }
    }

    private fun applyUnder(bar: View, fabIds: Set<Int>) {
        var anc: View = bar
        var content: View? = null
        var depth = 0
        while (depth < 8 && content == null) {
            val p = anc.parent as? ViewGroup ?: break
            if (p is LinearLayout && p.orientation == LinearLayout.VERTICAL) {
                val idx = p.indexOfChild(anc)
                var best: View? = null
                for (i in 0 until idx) {
                    val c = p.getChildAt(i)
                    if (c.visibility == View.VISIBLE && c.height > anc.height &&
                        (best == null || c.height > best.height)
                    ) best = c
                }
                if (best != null) {
                    content = best
                    break
                }
            }
            anc = p
            depth++
        }
        val c = content ?: run {
            Log.w("ModX", "ModXGlass: no encontré el contenido bajo la barra")
            return
        }
        val barH = anc.height
        if (barH <= 0) return
        val lp = anc.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.topMargin != -barH) {
            lp.topMargin = -barH
            anc.layoutParams = lp
            Log.i("ModX", "ModXGlass bajo-barra: contenido=${c.javaClass.simpleName} alto=${c.height} barra=$barH")
        }
        scan(c, barH, fabIds, barH * 3)
    }

    private fun freeClip(bar: View) {
        var p = bar.parent as? ViewGroup
        var n = 0
        while (p != null && n < 12) {
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent as? ViewGroup
            n++
        }
    }

    private fun install(
        bar: View, drawChild: Method, blurDp: Int, tintPct: Int,
        under: Boolean, fabIds: Set<Int>
    ) {
        if (installed.containsKey(bar)) return
        val d = bar.resources.displayMetrics.density
        val night = (bar.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val base = if (night) 0x1D1B20 else 0xFEF7FF
        val tint = ((tintPct.coerceIn(0, 100) * 255 / 100) shl 24) or base
        val corner = if (xprefs.getBoolean("floating_bottom_bar", false))
            xprefs.getInt("floating_bottom_bar_radius", 24) * d else 0f
        val extend = xprefs.getBoolean("modx_glass_extend", false) &&
            !xprefs.getBoolean("floating_bottom_bar", false)
        bar.background = GlassDrawable(bar, drawChild, blurDp * d, tint, corner, !under, extend)
        installed[bar] = true
        if (under) applyUnder(bar, fabIds)
        if (extend) {
            freeClip(bar)
            bar.postDelayed({
                val ins = bar.rootWindowInsets
                val nav = ins?.getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.navigationBars()
                )?.bottom ?: -1
                val loc = IntArray(2)
                bar.getLocationInWindow(loc)
                Log.i(
                    "ModX",
                    "ModXGlass extend: root=${bar.rootView.height} barTop=${loc[1]} barH=${bar.height} nav=$nav"
                )
            }, 1500)
        }

        val vto = bar.viewTreeObserver
        val scrollL = ViewTreeObserver.OnScrollChangedListener { bar.invalidate() }
        val layoutL = ViewTreeObserver.OnGlobalLayoutListener {
            bar.invalidate()
            if (under) {
                val now = SystemClock.uptimeMillis()
                if (now - lastScan > 400) {
                    lastScan = now
                    applyUnder(bar, fabIds)
                }
            }
        }
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
