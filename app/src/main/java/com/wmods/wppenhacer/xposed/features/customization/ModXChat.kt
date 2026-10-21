package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils

class ModXChat(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    @Throws(Exception::class)
    override fun doHook() {
        val on = xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_chat", false)
        val wall = xprefs.getInt("modx_chat_wall_blur", 12)
        val bubble = xprefs.getInt("modx_chat_bubble_alpha", 85)
        Log.i("ModX", "ModXChat on=$on wall=$wall bubble=$bubble")
        if (!on) return
        val wallId = Utils.getID("conversation_background", "id")
        val mainId = Utils.getID("main_layout", "id")
        val a255 = bubble.coerceIn(20, 100) * 255 / 100

        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (wallId > 0 && v.id == wallId && wall > 0 && Build.VERSION.SDK_INT >= 31) {
                    val r = wall * v.resources.displayMetrics.density
                    v.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
                } else if (mainId > 0 && v.id == mainId) {
                    v.background?.mutate()?.alpha = a255
                }
            }
        }

        if (mainId > 0) {
            View::class.java.resolve().firstMethod {
                name = "setBackgroundDrawable"
                superclass()
            }.hook {
                after {
                    val v = instance as? View ?: return@after
                    if (v.id == mainId) v.background?.mutate()?.alpha = a255
                }
            }
        }
    }

    override fun getPluginName(): String = "ModX Chat"
}
