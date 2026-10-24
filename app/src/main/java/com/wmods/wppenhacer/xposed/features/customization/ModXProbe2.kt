package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils

class ModXProbe2(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

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

    private fun desc(v: View): String {
        val sb = StringBuilder()
        sb.append(v.javaClass.name).append(" id=").append(idName(v))
        sb.append(" ").append(v.width).append("x").append(v.height)
        sb.append(" pos=").append(v.left).append(",").append(v.top)
        sb.append(" bg=").append(bgOf(v))
        sb.append(" z=").append(v.elevation).append("/").append(v.translationZ)
        sb.append(" pad=").append(v.paddingLeft).append(",").append(v.paddingTop)
            .append(",").append(v.paddingRight).append(",").append(v.paddingBottom)
        if (v is ViewGroup) sb.append(" clipC=").append(v.clipChildren).append(" clipP=").append(v.clipToPadding)
        val lp = v.layoutParams
        if (lp != null) {
            sb.append(" lp=").append(lp.javaClass.name).append(" wh=").append(lp.width).append("x").append(lp.height)
            if (lp is ViewGroup.MarginLayoutParams) {
                sb.append(" m=").append(lp.leftMargin).append(",").append(lp.topMargin)
                    .append(",").append(lp.rightMargin).append(",").append(lp.bottomMargin)
            }
            if (lp is RelativeLayout.LayoutParams) sb.append(" rules=").append(lp.rules.joinToString(","))
            try {
                val b = lp.javaClass.getMethod("getBehavior").invoke(lp)
                if (b != null) sb.append(" beh=").append(b.javaClass.name)
            } catch (_: Throwable) {
            }
        }
        return sb.toString()
    }

    private fun logIds(root: View, tag: String, names: List<String>) {
        for (n in names) {
            val id = Utils.getID(n, "id")
            if (id <= 0) {
                Log.i("ModX", "ModXProbe2 $tag $n: sin id")
                continue
            }
            val v = root.findViewById<View>(id)
            if (v == null) {
                Log.i("ModX", "ModXProbe2 $tag $n: no está en pantalla")
                continue
            }
            Log.i("ModX", "ModXProbe2 $tag $n -> ${desc(v)}")
        }
    }

    private fun logChildren(v: View?, tag: String) {
        if (v !is ViewGroup) return
        for (i in 0 until v.childCount) Log.i("ModX", "ModXProbe2 $tag hijo $i ${desc(v.getChildAt(i))}")
    }

    private fun home(root: View) {
        logIds(
            root, "home",
            listOf(
                "toolbar", "toolbar_container", "my_search_bar", "conversations_coordinator_layout",
                "pager", "fab", "bottom_nav", "bottom_nav_divider"
            )
        )
        logChildren(root.findViewById<View>(Utils.getID("conversations_coordinator_layout", "id")), "home.coord")
        val fab = root.findViewById<View>(Utils.getID("fab", "id"))
        val p = fab?.parent as? View
        if (p != null) Log.i("ModX", "ModXProbe2 home fab.padre -> ${desc(p)}")
    }

    private fun chat(root: View) {
        logIds(
            root, "chat",
            listOf(
                "conversation_root_layout", "search_fragment_and_toolbar_holder", "toolbar", "coordinator",
                "conversation_background", "conversation_layout", "footer", "emoji_popup_anchor",
                "edit_layout", "text_entry_layout", "input_layout", "input_layout_content", "entry",
                "buttons", "send_container", "voice_note_btn"
            )
        )
        val list = root.findViewById<View>(android.R.id.list)
        if (list != null) Log.i("ModX", "ModXProbe2 chat lista -> ${desc(list)}")
        logChildren(root.findViewById<View>(Utils.getID("conversation_layout", "id")), "chat.layout")
        logChildren(root.findViewById<View>(Utils.getID("conversation_root_layout", "id")), "chat.root")
    }

    @Throws(Exception::class)
    override fun doHook() {
        if (!xprefs.getBoolean("modx_probe", true)) return
        val barId = Utils.getID("bottom_nav", "id")
        val entryId = Utils.getID("entry", "id")
        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (barId > 0 && v.id == barId) {
                    v.postDelayed({
                        try {
                            home(v.rootView)
                        } catch (t: Throwable) {
                            Log.w("ModX", "ModXProbe2 home falló", t)
                        }
                    }, 5000)
                } else if (entryId > 0 && v.id == entryId) {
                    v.postDelayed({
                        try {
                            chat(v.rootView)
                        } catch (t: Throwable) {
                            Log.w("ModX", "ModXProbe2 chat falló", t)
                        }
                    }, 3500)
                }
            }
        }
    }

    override fun getPluginName(): String = "ModX Probe2"
}
