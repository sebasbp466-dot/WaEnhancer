package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.WeakHashMap

class ModXChat(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val fieldCache = HashMap<Class<*>, List<Field>>()
    private val logged = HashSet<Class<*>>()
    private val chatLists = WeakHashMap<View, Boolean>()

    private fun fieldsOf(c: Class<*>): List<Field> =
        fieldCache.getOrPut(c) {
            val out = ArrayList<Field>()
            var k: Class<*>? = c
            while (k != null && k != View::class.java && k != ViewGroup::class.java && k != Any::class.java) {
                for (f in k.declaredFields) {
                    if (Modifier.isStatic(f.modifiers)) continue
                    if (!Drawable::class.java.isAssignableFrom(f.type)) continue
                    try {
                        f.isAccessible = true
                        out.add(f)
                    } catch (_: Throwable) {
                    }
                }
                k = k.superclass
            }
            out
        }

    private fun bubbleAlpha(row: View, a255: Int) {
        val first = logged.add(row.javaClass)
        for (f in fieldsOf(row.javaClass)) {
            try {
                val d = f.get(row) as? Drawable ?: continue
                val b = d.bounds
                if (first) {
                    Log.i(
                        "ModX",
                        "ModXChat campo ${f.declaringClass.simpleName}.${f.name} ${d.javaClass.simpleName} ${b.width()}x${b.height()} alfa=${d.alpha}"
                    )
                }
                if (b.width() > 150 && b.height() > 80 && d.alpha != a255) d.mutate().alpha = a255
            } catch (_: Throwable) {
            }
        }
    }

    @Throws(Exception::class)
    override fun doHook() {
        val on = xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_chat", false)
        val wall = xprefs.getInt("modx_chat_wall_blur", 12)
        val bubble = xprefs.getInt("modx_chat_bubble_alpha", 85)
        Log.i("ModX", "ModXChat on=$on wall=$wall bubble=$bubble")
        if (!on) return
        val wallId = Utils.getID("conversation_background", "id")
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
                }
            }
        }

        try {
            ViewGroup::class.java.resolve().firstMethod {
                name = "dispatchDraw"
                superclass()
            }.hook {
                after {
                    val v = instance as? ViewGroup ?: return@after
                    val p = v.parent as? ListView ?: return@after
                    val isChat = chatLists.getOrPut(p) {
                        wallId > 0 && p.rootView.findViewById<View>(wallId) != null
                    }
                    if (isChat) bubbleAlpha(v, a255)
                }
            }
        } catch (t: Throwable) {
            Log.w("ModX", "ModXChat: no se pudo enganchar dispatchDraw", t)
        }
    }

    override fun getPluginName(): String = "ModX Chat"
}
