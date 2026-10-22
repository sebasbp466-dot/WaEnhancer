package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.Method
import java.util.ArrayDeque

class ModXChat(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val hooked = HashSet<Class<*>>()
    private val stackTL = ThreadLocal<ArrayDeque<Int>>()
    private var mainId = 0
    private var a255 = 217

    private fun findDraw(cls: Class<*>): Method? {
        var k: Class<*>? = cls
        while (k != null && k != Drawable::class.java && k != Any::class.java) {
            try {
                return k.getDeclaredMethod("draw", Canvas::class.java)
            } catch (_: NoSuchMethodException) {
            }
            k = k.superclass
        }
        return null
    }

    private fun hookBubble(v: View) {
        if (v.id != mainId) return
        val d = v.background ?: return
        val cls = d.javaClass
        if (!hooked.add(cls)) return
        val parentBg = (v.parent as? View)?.background?.javaClass?.name
        Log.i("ModX", "ModXChat burbuja clase=${cls.name} padre=$parentBg")
        val m = findDraw(cls)
        if (m == null) {
            Log.w("ModX", "ModXChat: la clase no declara draw")
            return
        }
        try {
            m.hook {
                before {
                    val st = stackTL.get() ?: ArrayDeque<Int>().also { stackTL.set(it) }
                    val dr = instance as? Drawable
                    val c = args[0] as? Canvas
                    val owner = dr?.callback as? View
                    if (dr != null && c != null && owner != null && owner.id == mainId) {
                        val r = dr.bounds
                        val pad = 24 * owner.resources.displayMetrics.density
                        st.push(
                            c.saveLayerAlpha(
                                r.left - pad, r.top - pad, r.right + pad, r.bottom + pad, a255
                            )
                        )
                    } else {
                        st.push(-1)
                    }
                }
                after {
                    val st = stackTL.get() ?: return@after
                    if (st.isEmpty()) return@after
                    val n = st.pop()
                    val c = args[0] as? Canvas ?: return@after
                    if (n >= 0) c.restoreToCount(n)
                }
            }
        } catch (t: Throwable) {
            Log.w("ModX", "ModXChat: no se pudo enganchar draw", t)
        }
    }

    @Throws(Exception::class)
    override fun doHook() {
        val on = xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_chat", false)
        val wall = xprefs.getInt("modx_chat_wall_blur", 12)
        val bubble = xprefs.getInt("modx_chat_bubble_alpha", 85)
        Log.i("ModX", "ModXChat on=$on wall=$wall bubble=$bubble")
        if (!on) return
        mainId = Utils.getID("main_layout", "id")
        a255 = bubble.coerceIn(20, 100) * 255 / 100
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
                    hookBubble(v)
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
                    if (v.id == mainId) hookBubble(v)
                }
            }
        }
    }

    override fun getPluginName(): String = "ModX Chat"
}
