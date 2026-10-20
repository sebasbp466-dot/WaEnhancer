package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.WeakHashMap

class ModXMinimal(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val roots = WeakHashMap<View, Long>()

    @Throws(Exception::class)
    override fun doHook() {
        val on = xprefs.getBoolean("modx_min", false)
        val icons = on && xprefs.getBoolean("modx_min_icons", false)
        val title = on && xprefs.getBoolean("modx_min_title", false)
        val search = on && xprefs.getBoolean("modx_min_search", false)
        val topbar = on && xprefs.getBoolean("modx_min_topbar", false)
        val labels = on && xprefs.getBoolean("modx_min_labels", false)
        Log.i(
            "ModX",
            "ModXMinimal on=$on icons=$icons title=$title search=$search topbar=$topbar labels=$labels"
        )
        if (!(icons || title || search || topbar || labels)) return
        val barId = Utils.getID("bottom_nav", "id")
        val toolbarId = Utils.getID("toolbar", "id")
        val searchId = Utils.getID("my_search_bar", "id")
        if (barId <= 0) return

        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (v.id != barId) return@after
                val root = v.rootView
                if (roots.containsKey(root)) return@after
                roots[root] = 0L
                root.viewTreeObserver.addOnGlobalLayoutListener(
                    ViewTreeObserver.OnGlobalLayoutListener {
                        val now = SystemClock.uptimeMillis()
                        if (now - (roots[root] ?: 0L) < 250) return@OnGlobalLayoutListener
                        roots[root] = now
                        applyAll(root, barId, toolbarId, searchId, icons, title, search, topbar, labels)
                    }
                )
                root.post {
                    applyAll(root, barId, toolbarId, searchId, icons, title, search, topbar, labels)
                }
            }
        }
    }

    private fun gone(v: View?) {
        if (v != null && v.visibility != View.GONE) v.visibility = View.GONE
    }

    private fun hideLabels(v: View) {
        if (v.javaClass.simpleName == "BaselineLayout") {
            gone(v)
            return
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) hideLabels(v.getChildAt(i))
    }

    private fun applyAll(
        root: View, barId: Int, toolbarId: Int, searchId: Int,
        icons: Boolean, title: Boolean, search: Boolean, topbar: Boolean, labels: Boolean
    ) {
        val toolbar = if (toolbarId > 0) root.findViewById<View>(toolbarId) else null
        if (topbar) {
            gone(toolbar)
        } else if (toolbar is ViewGroup) {
            if (title && toolbar.childCount > 0) gone(toolbar.getChildAt(0))
            if (icons) {
                for (i in 0 until toolbar.childCount) {
                    val m = toolbar.getChildAt(i)
                    if (m is ViewGroup && m.javaClass.simpleName == "ActionMenuView") {
                        for (j in 0 until m.childCount - 1) gone(m.getChildAt(j))
                    }
                }
            }
        }
        if (search && searchId > 0) gone(root.findViewById<View>(searchId))
        if (labels) {
            val bar = root.findViewById<View>(barId)
            if (bar != null) hideLabels(bar)
        }
    }

    override fun getPluginName(): String = "ModX Minimal"
}
