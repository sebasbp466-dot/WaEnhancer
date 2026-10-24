package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.ArrayDeque

class ModXChat(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val probed = HashSet<Class<*>>()
    private val hookedMethods = HashSet<Method>()
    private val stackTL = ThreadLocal<ArrayDeque<Int>>()
    private var a255 = 77

    private fun idName(v: View): String {
        if (v.id <= 0) return "-"
        return try {
            v.resources.getResourceEntryName(v.id)
        } catch (_: Throwable) {
            v.id.toString()
        }
    }

    private fun probe(main: View) {
        val row = main.parent as? ViewGroup ?: return
        val cls = row.javaClass
        if (!probed.add(cls)) return
        Log.i(
            "ModX",
            "ModXChat probe main=${main.javaClass.name} ${main.width}x${main.height} bg=${main.background?.javaClass?.name} fg=${main.foreground?.javaClass?.name}"
        )
        Log.i(
            "ModX",
            "ModXChat probe fila=${cls.name} id=${idName(row)} bg=${row.background?.javaClass?.name} willNotDraw=${row.willNotDraw()}"
        )
        val mg = main as? ViewGroup
        if (mg != null) {
            for (i in 0 until mg.childCount) {
                val c = mg.getChildAt(i)
                Log.i("ModX", "ModXChat probe hijo $i ${c.javaClass.name} id=${idName(c)} bg=${c.background?.javaClass?.name}")
            }
        }
        var k: Class<*>? = cls
        var n = 0
        while (k != null && k != ViewGroup::class.java && k != View::class.java && n < 8) {
            for (m in k.declaredMethods) {
                if (m.parameterTypes.any { it == Canvas::class.java }) {
                    Log.i("ModX", "ModXChat probe metodo ${k.name}.${m.name}")
                }
            }
            for (f in k.declaredFields) {
                if (Modifier.isStatic(f.modifiers)) continue
                if (Drawable::class.java.isAssignableFrom(f.type)) {
                    var d: Drawable? = null
                    try {
                        f.isAccessible = true
                        d = f.get(row) as? Drawable
                    } catch (_: Throwable) {
                    }
                    Log.i(
                        "ModX",
                        "ModXChat probe campo ${k.simpleName}.${f.name} ${f.type.simpleName} valor=${d?.javaClass?.simpleName} ${d?.bounds?.width()}x${d?.bounds?.height()}"
                    )
                }
            }
            k = k.superclass
            n++
        }

        var kk: Class<*>? = cls
        var target: Method? = null
        while (kk != null && kk != ViewGroup::class.java && kk != View::class.java && target == null) {
            try {
                target = kk.getDeclaredMethod("onDraw", Canvas::class.java)
            } catch (_: NoSuchMethodException) {
            }
            kk = kk.superclass
        }
        if (target == null) {
            Log.i("ModX", "ModXChat probe: la fila no declara onDraw")
            return
        }
        if (!hookedMethods.add(target)) return
        Log.i("ModX", "ModXChat probe onDraw en ${target.declaringClass.name}")
        try {
            target.hook {
                before {
                    val st = stackTL.get() ?: ArrayDeque<Int>().also { stackTL.set(it) }
                    val v = instance as? View
                    val c = args[0] as? Canvas
                    if (v != null && c != null) {
                        st.push(c.saveLayerAlpha(0f, 0f, v.width.toFloat(), v.height.toFloat(), a255))
                    } else {
                        st.push(-1)
                    }
                }
                after {
                    val st = stackTL.get() ?: return@after
                    if (st.isEmpty()) return@after
                    val saved = st.pop()
                    val c = args[0] as? Canvas ?: return@after
                    if (saved >= 0) c.restoreToCount(saved)
                }
            }
        } catch (t: Throwable) {
            Log.w("ModX", "ModXChat: no se pudo enganchar onDraw", t)
        }
    }

    @Throws(Exception::class)
    override fun doHook() {
        val on = xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_chat", false)
        val wall = xprefs.getInt("modx_chat_wall_blur", 12)
        val bubble = xprefs.getInt("modx_chat_bubble_alpha", 85)
        Log.i("ModX", "ModXChat on=$on wall=$wall bubble=$bubble")
        if (!on) return
        a255 = bubble.coerceIn(20, 100) * 255 / 100
        val mainId = Utils.getID("main_layout", "id")
        val wallId = Utils.getID("conversation_background", "id")

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
                    v.post {
                        try {
                            probe(v)
                        } catch (t: Throwable) {
                            Log.w("ModX", "ModXChat probe falló", t)
                        }
                    }
                }
            }
        }
    }

    override fun getPluginName(): String = "ModX Chat"
}
