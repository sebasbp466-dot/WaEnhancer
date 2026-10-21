package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.WeakHashMap

class ModXRows(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val done = WeakHashMap<View, Boolean>()
    private var aName = 90
    private var aMsg = 65
    private var aDate = 75
    private var glass = false
    private var radius = 20
    private var fill = 6
    private var border = 8
    private var nameId = 0
    private var msgId = 0
    private var fromId = 0
    private var dateId = 0

    @Throws(Exception::class)
    override fun doHook() {
        val on = xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_rows", false)
        Log.i("ModX", "ModXRows on=$on")
        if (!on) return
        aName = xprefs.getInt("modx_row_a_name", 90)
        aMsg = xprefs.getInt("modx_row_a_msg", 65)
        aDate = xprefs.getInt("modx_row_a_date", 75)
        glass = xprefs.getBoolean("modx_row_glass", false)
        radius = xprefs.getInt("modx_row_radius", 20)
        fill = xprefs.getInt("modx_row_fill", 6)
        border = xprefs.getInt("modx_row_border", 8)
        val rowId = Utils.getID("contact_row_container", "id")
        if (rowId <= 0) return
        nameId = Utils.getID("conversations_row_contact_name", "id")
        msgId = Utils.getID("single_msg_tv", "id")
        fromId = Utils.getID("msg_from_tv", "id")
        dateId = Utils.getID("conversations_row_date", "id")

        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (v.id != rowId) return@after
                v.post { style(v) }
            }
        }
    }

    private fun style(row: View) {
        if (nameId > 0) row.findViewById<View>(nameId)?.alpha = aName / 100f
        if (msgId > 0) row.findViewById<View>(msgId)?.alpha = aMsg / 100f
        if (fromId > 0) row.findViewById<View>(fromId)?.alpha = aMsg / 100f
        if (dateId > 0) row.findViewById<View>(dateId)?.alpha = aDate / 100f
        if (glass && !done.containsKey(row)) {
            val d = row.resources.displayMetrics.density
            val card = GradientDrawable()
            card.cornerRadius = radius * d
            card.setColor(((fill.coerceIn(0, 100) * 255 / 100) shl 24) or 0xFFFFFF)
            card.setStroke(maxOf(1, d.toInt()), ((border.coerceIn(0, 100) * 255 / 100) shl 24) or 0xFFFFFF)
            val inset = InsetDrawable(card, (8 * d).toInt(), (3 * d).toInt(), (8 * d).toInt(), (3 * d).toInt())
            val orig: Drawable? = row.background
            row.background = if (orig != null) LayerDrawable(arrayOf(inset, orig)) else inset
            done[row] = true
        }
    }

    override fun getPluginName(): String = "ModX Rows"
}
