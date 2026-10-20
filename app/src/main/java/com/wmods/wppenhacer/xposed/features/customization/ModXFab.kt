package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.WeakHashMap

class ModXFab(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val roots = WeakHashMap<View, Long>()

    @Throws(Exception::class)
    override fun doHook() {
        val on = xprefs.getBoolean("modx_x", false)
        val hide = on && xprefs.getBoolean("modx_fab_hide", false)
        val color = if (on) xprefs.getInt("modx_fab_color", 0) else 0
        Log.i("ModX", "ModXFab on=$on hide=$hide color=$color")
        if (!hide && color == 0) return
        val ids = arrayOf("fab", "fab_second", "extended_mini_fab")
            .mapNotNull { n -> Utils.getID(n, "id").takeIf { id -> id > 0 } }
            .toSet()
        if (ids.isEmpty()) return

        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (v.id !in ids) return@after
                val root = v.rootView
                if (roots.containsKey(root)) {
                    root.post { applyAll(root, ids, hide, color) }
                    return@after
                }
                roots[root] = 0L
                root.viewTreeObserver.addOnGlobalLayoutListener(
                    ViewTreeObserver.OnGlobalLayoutListener {
                        val now = SystemClock.uptimeMillis()
                        if (now - (roots[root] ?: 0L) < 300) return@OnGlobalLayoutListener
                        roots[root] = now
                        applyAll(root, ids, hide, color)
                    }
                )
                root.post { applyAll(root, ids, hide, color) }
            }
        }
    }

    private fun applyAll(root: View, ids: Set<Int>, hide: Boolean, color: Int) {
        for (id in ids) {
            val f = root.findViewById<View>(id) ?: continue
            if (hide) {
                if (f.visibility != View.GONE) f.visibility = View.GONE
                continue
            }
            if (color != 0) {
                val cur = f.backgroundTintList?.defaultColor
                if (cur != color) f.backgroundTintList = ColorStateList.valueOf(color)
            }
        }
    }

    override fun getPluginName(): String = "ModX Fab"
}
