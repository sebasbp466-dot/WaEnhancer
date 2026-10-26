package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RelativeLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.WeakHashMap

class ModXChatGlass(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val done = WeakHashMap<View, Boolean>()

    private fun guard(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w("ModX", "ModXChatGlass falló", t)
        }
    }

    private fun refresh(v: View) {
        val vto = v.viewTreeObserver
        val l1 = ViewTreeObserver.OnScrollChangedListener { v.invalidate() }
        val l2 = ViewTreeObserver.OnGlobalLayoutListener { v.invalidate() }
        vto.addOnScrollChangedListener(l1)
        vto.addOnGlobalLayoutListener(l2)
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(x: View) {}
            override fun onViewDetachedFromWindow(x: View) {
                x.removeOnAttachStateChangeListener(this)
                if (vto.isAlive) {
                    vto.removeOnScrollChangedListener(l1)
                    vto.removeOnGlobalLayoutListener(l2)
                }
            }
        })
    }

    private fun setupHeader(holder: View, coordId: Int, toolbarId: Int, tries: Int) {
        if (done.containsKey(holder)) return
        val h = holder.height
        val root = holder.parent as? ViewGroup
        if (h <= 0 || root == null) {
            if (tries < 10) {
                holder.postDelayed({ guard { setupHeader(holder, coordId, toolbarId, tries + 1) } }, 250)
            }
            return
        }
        val coordinator = root.findViewById<View>(coordId) ?: return
        val glass = ModXGlass.instance?.makeGlass(holder, 0, 0, 0, false) ?: return
        holder.findViewById<View>(toolbarId)?.background = null
        holder.background = glass
        holder.elevation = 24f
        val lp = coordinator.layoutParams as? ViewGroup.MarginLayoutParams
        if (lp != null && lp.topMargin != -h) {
            lp.topMargin = -h
            coordinator.layoutParams = lp
        }
        val list = coordinator.findViewById<View>(android.R.id.list)
        if (list != null) {
            list.setPadding(list.paddingLeft, list.paddingTop + h, list.paddingRight, list.paddingBottom)
            (list as? ViewGroup)?.clipToPadding = false
        }
        done[holder] = true
        refresh(holder)
        Log.i("ModX", "ModXChatGlass encabezado listo h=$h")
    }

    private fun setupInput(input: View, footerId: Int, tries: Int) {
        if (done.containsKey(input)) return
        val root = input.rootView
        val footer = root.findViewById<View>(footerId)
        val list = root.findViewById<View>(android.R.id.list)
        if (footer == null || list == null || footer.height <= 0) {
            if (tries < 10) {
                input.postDelayed({ guard { setupInput(input, footerId, tries + 1) } }, 250)
            }
            return
        }
        val glass = ModXGlass.instance?.makeGlass(input, 0, 0, 100, true) ?: return
        input.background = glass
        val box = list.parent as? View
        val lp = box?.layoutParams as? RelativeLayout.LayoutParams
        if (box != null && lp != null) {
            lp.removeRule(RelativeLayout.ABOVE)
            box.layoutParams = lp
            val base = list.paddingBottom
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, base + footer.height)
            (list as? ViewGroup)?.clipToPadding = false
            footer.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, base + (bottom - top))
            }
        }
        done[input] = true
        refresh(input)
        Log.i("ModX", "ModXChatGlass barra de escribir lista")
    }

    @Throws(Exception::class)
    override fun doHook() {
        val chat = xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_chat", false)
        val header = chat && xprefs.getBoolean("modx_chat_header", false)
        val input = chat && xprefs.getBoolean("modx_chat_input", false)
        Log.i("ModX", "ModXChatGlass header=$header input=$input")
        if (!header && !input) return
        val holderId = Utils.getID("search_fragment_and_toolbar_holder", "id")
        val coordId = Utils.getID("coordinator", "id")
        val inputId = Utils.getID("input_layout", "id")
        val toolbarId = Utils.getID("toolbar", "id")
        val footerId = Utils.getID("footer", "id")

        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (header && holderId > 0 && v.id == holderId) {
                    v.postDelayed({ guard { setupHeader(v, coordId, toolbarId, 0) } }, 300)
                } else if (input && inputId > 0 && v.id == inputId) {
                    v.postDelayed({ guard { setupInput(v, footerId, 0) } }, 300)
                }
            }
        }
    }

    override fun getPluginName(): String = "ModX Chat Glass"
}
