package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
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

private val LIQUID_AGSL = """
uniform shader contents;
uniform float2 size;
uniform float radius;
uniform float lensW;
uniform float lensAmt;
uniform float disp;
uniform float rimGain;
uniform float sat;
uniform float frost;
uniform float2 light;

float sdRound(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

float rnd(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 fc) {
    float2 hb = size * 0.5;
    float2 p = fc - hb;
    float d = sdRound(p, hb, radius);
    if (d > 1.0) {
        return half4(0.0);
    }
    float e = 1.0;
    float2 g = float2(
        sdRound(p + float2(e, 0.0), hb, radius) - sdRound(p - float2(e, 0.0), hb, radius),
        sdRound(p + float2(0.0, e), hb, radius) - sdRound(p - float2(0.0, e), hb, radius));
    float gl = length(g);
    float2 n = g / max(gl, 0.0001);

    float rim = 1.0 - clamp(-d / lensW, 0.0, 1.0);
    float axisFade = smoothstep(0.6, 1.6, gl);
    float edgeGuard = smoothstep(0.0, 5.0, -d);
    float bend = rim * rim * axisFade * edgeGuard;
    float2 off = n * bend * lensAmt;
    float2 ca = n * bend * lensAmt * (0.015 + 0.07 * disp);
    float2 lo = float2(0.5);
    float2 hi = size - float2(0.5);
    float2 uv = fc - off;
    half4 cr = contents.eval(clamp(uv + ca, lo, hi));
    half4 cg = contents.eval(clamp(uv, lo, hi));
    half4 cb = contents.eval(clamp(uv - ca, lo, hi));

    half a = max(cr.a, max(cg.a, cb.a));
    half3 src = half3(cr.r, cg.g, cb.b);
    half l = dot(src, half3(0.2126, 0.7152, 0.0722));
    half3 col = half3(l) + (src - half3(l)) * half(sat);
    float lum = float(l) / max(float(a), 0.001);
    float adapt = clamp((1.0 - lum) * frost, 0.0, 0.5);
    col = mix(col, half3(0.82) * a, half(adapt));

    float2 L = normalize(light);
    float lit = clamp(dot(n, L), 0.0, 1.0);
    float back = clamp(dot(n, -L), 0.0, 1.0);
    float rimLine = 1.0 - smoothstep(0.0, 2.2, -d);
    float rimBand = 1.0 - smoothstep(0.0, 8.0, -d);
    float inner = (1.0 - smoothstep(2.0, 18.0, -d)) * back;
    col = col * half(1.0 - 0.20 * inner);
    float t = clamp(fc.x / size.x * 0.55 + fc.y / size.y * 0.45, 0.0, 1.0);
    float s = (rimLine * (0.30 + 1.0 * lit) + rimBand * (0.10 * lit + 0.14 * back) + 0.05 * (1.0 - t)) * rimGain;
    s = clamp(s, 0.0, 0.9);
    half3 outc = col * half(1.0 - s) + half3(half(s));
    half outa = a + half(s) * (half(1.0) - a);
    outc += half3(half((rnd(fc) - 0.5) / 255.0));
    return half4(outc, outa);
}
"""

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
        private val cornerFixed: Float,
        private val mirror: Boolean,
        private val extend: Boolean,
        private val liquid: Int,
        private val dispersion: Int,
        private val rimGain: Int,
        private val sat: Int
    ) : Drawable() {
        private val node = RenderNode("modx_glass")
        private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val path = Path()
        private val rect = RectF()
        private val barLoc = IntArray(2)
        private val pLoc = IntArray(2)
        private var recording = false
        private var shader: RuntimeShader? = null
        private var shaderFailed = false
        private var strokeKey = 0L

        private fun buildEffect(gw: Int, gh: Int, corner: Float): RenderEffect {
            val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            if (liquid <= 0 || shaderFailed || Build.VERSION.SDK_INT < 33) return blur
            return try {
                val sh = shader ?: RuntimeShader(LIQUID_AGSL).also { shader = it }
                sh.setFloatUniform("size", gw.toFloat(), gh.toFloat())
                sh.setFloatUniform("radius", corner)
                sh.setFloatUniform("lensW", gh * 0.30f)
                sh.setFloatUniform("lensAmt", liquid / 100f * 0.40f * gh)
                sh.setFloatUniform("disp", dispersion / 100f)
                sh.setFloatUniform("rimGain", rimGain / 100f)
                sh.setFloatUniform("sat", sat / 100f)
                sh.setFloatUniform("frost", 0.12f)
                sh.setFloatUniform("light", -0.5f, -0.85f)
                RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(sh, "contents"), blur
                )
            } catch (t: Throwable) {
                shaderFailed = true
                Log.w("ModX", "ModXGlass: el shader liquid falló", t)
                blur
            }
        }

        override fun draw(canvas: Canvas) {
            if (recording) return
            val w = bounds.width()
            val barH = bounds.height()
            if (w <= 0 || barH <= 0) return
            bar.getLocationInWindow(barLoc)
            val d = bar.resources.displayMetrics.density
            val pill = liquid > 0
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            val corner: Float
            if (pill) {
                left = (12 * d).toInt()
                right = w - left
                top = (6 * d).toInt()
                bottom = barH - (6 * d).toInt()
                corner = (bottom - top) / 2f
            } else {
                var extra = 0
                if (extend) {
                    val ins = bar.rootWindowInsets
                    val nav = if (ins != null) {
                        ins.getInsetsIgnoringVisibility(
                            android.view.WindowInsets.Type.navigationBars()
                        ).bottom
                    } else 0
                    val gap = bar.rootView.height - (barLoc[1] + barH)
                    extra = minOf(nav, gap).coerceAtLeast(0)
                }
                left = 0
                right = w
                top = 0
                bottom = barH + extra
                corner = cornerFixed
            }
            val gw = right - left
            val gh = bottom - top
            if (gw <= 0 || gh <= 0) return
            rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            path.reset()
            path.addRoundRect(rect, corner, corner, Path.Direction.CW)
            if (canvas.isHardwareAccelerated) {
                try {
                    node.setPosition(left, top, right, bottom)
                    node.setRenderEffect(buildEffect(gw, gh, corner))
                    val rc = node.beginRecording(gw, gh)
                    try {
                        recording = true
                        val gx = barLoc[0] + left
                        val gy = barLoc[1] + top
                        val now = SystemClock.uptimeMillis()
                        var child: View = bar
                        var p = bar.parent as? ViewGroup
                        while (p != null) {
                            p.getLocationInWindow(pLoc)
                            val save = rc.save()
                            if (mirror) {
                                rc.translate((pLoc[0] - gx).toFloat(), (gy - pLoc[1]).toFloat())
                                rc.scale(1f, -1f)
                            } else {
                                rc.translate((pLoc[0] - gx).toFloat(), (pLoc[1] - gy).toFloat())
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
            if (pill) {
                val key = gw.toLong() * 100000L + gh
                if (key != strokeKey) {
                    strokeKey = key
                    strokePaint.shader = LinearGradient(
                        left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(),
                        intArrayOf(0x66FFFFFF, 0x0AFFFFFF, 0x40FFFFFF),
                        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                    )
                }
                strokePaint.strokeWidth = 1.0f * d
                canvas.drawPath(path, strokePaint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    @Throws(Exception::class)
    override fun doHook() {
        val enabled = xprefs.getBoolean("modx_lg", false) &&
            xprefs.getBoolean("modx_glass_bar", false)
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

    private fun styleTabs(bar: View) {
        val d = bar.resources.displayMetrics.density
        val g = bar as? ViewGroup ?: return
        val row = g.getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until row.childCount) {
            val tab = row.getChildAt(i) as? ViewGroup ?: continue
            val fl = tab.getChildAt(0) as? ViewGroup ?: continue
            val ind = fl.getChildAt(0) ?: continue
            if (ind.javaClass.simpleName != "View") continue
            if (ind.tag == "modx_tab") continue
            val gd = android.graphics.drawable.GradientDrawable()
            gd.setColor(0x38FFFFFF)
            gd.cornerRadius = 1000f
            gd.setStroke(maxOf(1, (1 * d).toInt()), 0x59FFFFFF)
            ind.background = gd
            ind.tag = "modx_tab"
        }
    }

    private fun hideDivider(bar: View) {
        val id = Utils.getID("bottom_nav_divider", "id")
        if (id > 0) bar.rootView.findViewById<View>(id)?.alpha = 0f
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
        val liquid = if (xprefs.getBoolean("modx_liquid", false) && Build.VERSION.SDK_INT >= 33)
            xprefs.getInt("modx_liquid_strength", 60) else 0
        val iosTab = liquid > 0 && xprefs.getBoolean("modx_ios_tab", false)
        val effBlur = if (liquid > 0) xprefs.getInt("modx_liquid_blur", 4).toFloat() else blurDp.toFloat()
        val effTint = if (liquid > 0) xprefs.getInt("modx_liquid_tint", 8).toFloat() else tintPct.toFloat()
        val night = (bar.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val base = if (liquid > 0) 0xFFFFFF else if (night) 0x1D1B20 else 0xFEF7FF
        val tint = ((effTint.coerceIn(0f, 100f) * 255f / 100f).toInt() shl 24) or base
        val corner = if (xprefs.getBoolean("floating_bottom_bar", false))
            xprefs.getInt("floating_bottom_bar_radius", 24) * d else 0f
        val extend = xprefs.getBoolean("modx_glass_extend", false) &&
            !xprefs.getBoolean("floating_bottom_bar", false) && liquid == 0
        bar.background = GlassDrawable(
            bar, drawChild, effBlur * d, tint, corner, !under, extend, liquid,
            xprefs.getInt("modx_liquid_disp", 25),
            xprefs.getInt("modx_liquid_rim", 70),
            xprefs.getInt("modx_liquid_sat", 125)
        )
        installed[bar] = true
        if (under) applyUnder(bar, fabIds)
        if (liquid > 0) hideDivider(bar)
        if (iosTab) styleTabs(bar)
        bar.postDelayed({
            if (liquid > 0) hideDivider(bar)
            if (iosTab) styleTabs(bar)
        }, 2500)
        if (extend) freeClip(bar)

        val vto = bar.viewTreeObserver
        val scrollL = ViewTreeObserver.OnScrollChangedListener { bar.invalidate() }
        val layoutL = ViewTreeObserver.OnGlobalLayoutListener {
            bar.invalidate()
            if (iosTab) styleTabs(bar)
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
