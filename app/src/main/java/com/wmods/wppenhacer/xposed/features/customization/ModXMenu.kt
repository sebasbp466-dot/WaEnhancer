package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils

class ModXMenu(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private class Entry(val view: View, val label: CharSequence, val icon: Drawable?)

    private var lastX = -1f
    private var lastY = -1f
    private var popup: PopupWindow? = null

    @Throws(Exception::class)
    override fun doHook() {
        val enabled = (xprefs.getBoolean("modx_m3", false) && xprefs.getBoolean("modx_longpress_menu", false))
        Log.i("ModX", "ModXMenu enabled=$enabled")
        if (!enabled) return
        val barId = Utils.getID("action_mode_bar", "id")
        val closeId = Utils.getID("action_mode_close_button", "id")
        if (barId <= 0) {
            Log.w("ModX", "ModXMenu: no se encontró action_mode_bar")
            return
        }

        View::class.java.resolve().firstMethod {
            name = "dispatchTouchEvent"
            superclass()
        }.hook {
            after {
                val ev = args[0] as? MotionEvent ?: return@after
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    lastX = ev.rawX
                    lastY = ev.rawY
                }
            }
        }

        View::class.java.resolve().firstMethod {
            name = "onVisibilityChanged"
            superclass()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (v.id != barId) return@after
                if ((args[0] as? View) !== v) return@after
                val vis = args[1] as Int
                if (vis == View.VISIBLE) {
                    v.postDelayed({ showMenu(v, closeId) }, 150)
                } else {
                    dismiss()
                }
            }
        }
    }

    private fun dismiss() {
        try {
            popup?.dismiss()
        } catch (_: Throwable) {
        }
        popup = null
    }

    private fun collect(g: ViewGroup, closeId: Int, out: MutableList<Entry>) {
        for (i in 0 until g.childCount) {
            val c = g.getChildAt(i)
            if (c.visibility != View.VISIBLE || c.id == closeId) continue
            if (c is ViewGroup) {
                collect(c, closeId, out)
                continue
            }
            if (!c.isClickable) continue
            val desc = c.contentDescription?.takeIf { it.isNotEmpty() }
            val text = (c as? TextView)?.text?.takeIf { it.isNotEmpty() }
            val label = desc ?: text ?: continue
            val icon = when (c) {
                is ImageView -> c.drawable
                is TextView -> c.compoundDrawables[0]
                else -> null
            }
            out.add(Entry(c, label, icon))
        }
    }

    private fun showMenu(bar: View, closeId: Int) {
        if (bar.visibility != View.VISIBLE || !bar.isAttachedToWindow) return
        if (popup?.isShowing == true) return
        val items = ArrayList<Entry>()
        if (bar is ViewGroup) collect(bar, closeId, items)
        Log.i("ModX", "ModXMenu items=${items.size}: " + items.joinToString { it.label })
        if (items.isEmpty()) return

        val ctx = bar.context
        val dm = ctx.resources.displayMetrics
        val d = dm.density
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dyn = xprefs.getBoolean("modx_m3_dynamic", false)
        val bg = ModXColors.surface(ctx, night, dyn)
        val fg = ModXColors.onSurface(ctx, night, dyn)

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
        val shape = GradientDrawable()
        shape.setColor(bg)
        shape.cornerRadius = 20f * d
        card.background = shape

        for (e in items) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = (48 * d).toInt()
            row.setPadding((16 * d).toInt(), 0, (24 * d).toInt(), 0)
            row.background = RippleDrawable(
                ColorStateList.valueOf(0x33808080), null, ColorDrawable(Color.WHITE)
            )
            val icon = e.icon
            if (icon != null) {
                val iv = ImageView(ctx)
                val dr = icon.constantState?.newDrawable()?.mutate() ?: icon
                dr.setTint(fg)
                iv.setImageDrawable(dr)
                val size = (24 * d).toInt()
                val lp = LinearLayout.LayoutParams(size, size)
                lp.marginEnd = (16 * d).toInt()
                row.addView(iv, lp)
            }
            val tv = TextView(ctx)
            tv.text = e.label
            tv.setTextColor(fg)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            row.addView(tv)
            row.setOnClickListener {
                dismiss()
                e.view.post { e.view.performClick() }
            }
            card.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        card.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val w = card.measuredWidth
        val h = card.measuredHeight
        val margin = (8 * d).toInt()
        var x = if (lastX >= 0f) (lastX - w / 2f).toInt() else (dm.widthPixels - w) / 2
        x = x.coerceIn(margin, maxOf(margin, dm.widthPixels - w - margin))
        var y = if (lastY >= 0f) (lastY + 16 * d).toInt() else (dm.heightPixels * 0.55f).toInt()
        if (y + h > dm.heightPixels - (24 * d).toInt()) {
            y = (lastY - h - 16 * d).toInt().coerceAtLeast((48 * d).toInt())
        }

        val pw = PopupWindow(card, w, h, true)
        pw.isOutsideTouchable = true
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pw.elevation = 12f * d
        popup = pw
        try {
            pw.showAtLocation(bar.rootView, Gravity.NO_GRAVITY, x, y)
            val blurBehind = if ((xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_blur", false)))
                xprefs.getInt("modx_blur_radius", 16) else 0
            if (blurBehind > 0 && android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    val root = pw.contentView.rootView
                    val lp = root.layoutParams as android.view.WindowManager.LayoutParams
                    lp.flags = lp.flags or android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND or
                        android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    lp.blurBehindRadius = (blurBehind * d).toInt()
                    lp.dimAmount = 0.2f
                    (ctx.getSystemService(android.content.Context.WINDOW_SERVICE)
                        as android.view.WindowManager).updateViewLayout(root, lp)
                } catch (t: Throwable) {
                    Log.w("ModX", "ModXMenu: no se aplicó el blur de fondo", t)
                }
            }
        } catch (t: Throwable) {
            Log.w("ModX", "ModXMenu: no se pudo mostrar", t)
            popup = null
        }
    }

    override fun getPluginName(): String = "ModX Menu"
}
