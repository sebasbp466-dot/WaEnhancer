package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils

class ModXProbe(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    @Throws(Exception::class)
    override fun doHook() {
        if (!xprefs.getBoolean("modx_probe", true)) return
        val barId = Utils.getID("bottom_nav", "id")
        if (barId <= 0) return
        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (v.id != barId) return@after
                v.postDelayed({
                    try {
                        dump(v)
                    } catch (t: Throwable) {
                        Log.w("ModX", "ModXProbe falló", t)
                    }
                }, 4000)
            }
        }
    }

    private fun idName(v: View): String {
        if (v.id <= 0) return "-"
        return try {
            v.resources.getResourceEntryName(v.id)
        } catch (_: Throwable) {
            v.id.toString()
        }
    }

    private fun bgOf(v: View): String {
        val b = v.background ?: return "sin-fondo"
        val c = (b as? ColorDrawable)?.color
        return b.javaClass.simpleName + (if (c != null) " #" + Integer.toHexString(c) else "")
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

    private fun findScroller(v: View): View? {
        if (v is ViewGroup && isScroller(v) &&
            v.height > v.resources.displayMetrics.heightPixels * 0.4
        ) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val r = findScroller(v.getChildAt(i))
                if (r != null) return r
            }
        }
        return null
    }

    private fun dumpTree(v: View, depth: Int, max: Int) {
        val extra = if (v is TextView) {
            " texto='${v.text.toString().take(18)}' tam=${v.textSize} alfa=${Color.alpha(v.currentTextColor)} color=#${Integer.toHexString(v.currentTextColor)}"
        } else ""
        Log.i(
            "ModX",
            "ModXProbe fila ${"  ".repeat(depth)}${v.javaClass.simpleName} id=${idName(v)} ${v.width}x${v.height} fondo=${bgOf(v)}$extra"
        )
        if (depth < max && v is ViewGroup) {
            for (i in 0 until v.childCount) dumpTree(v.getChildAt(i), depth + 1, max)
        }
    }

    private fun dump(bar: View) {
        val root = bar.rootView
        Log.i("ModX", "ModXProbe raiz ${root.javaClass.simpleName} ${root.width}x${root.height} fondo=${bgOf(root)}")
        val list = findScroller(root)
        if (list == null) {
            Log.i("ModX", "ModXProbe: no encontré la lista")
            return
        }
        Log.i("ModX", "ModXProbe lista ${list.javaClass.name} ${list.width}x${list.height} fondo=${bgOf(list)}")
        var p = list.parent as? View
        var n = 0
        while (p != null && n < 8) {
            Log.i("ModX", "ModXProbe ancestro $n ${p.javaClass.simpleName} id=${idName(p)} ${p.width}x${p.height} fondo=${bgOf(p)}")
            p = p.parent as? View
            n++
        }
        val g = list as ViewGroup
        for (i in 0 until minOf(2, g.childCount)) dumpTree(g.getChildAt(i), 0, 4)
    }

    override fun getPluginName(): String = "ModX Probe"
}
