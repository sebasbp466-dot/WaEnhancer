package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.WeakHashMap
import kotlin.math.abs

class ModXMotion(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val lastOffset = WeakHashMap<View, Int>()
    private val resetters = WeakHashMap<View, Runnable>()

    @Throws(Exception::class)
    override fun doHook() {
        val scrollBlur = (xprefs.getBoolean("modx_lg", false) && xprefs.getBoolean("modx_scroll_blur", false))
        val sendAnim = (xprefs.getBoolean("modx_m3", false) && xprefs.getBoolean("modx_send_anim", false))
        Log.i("ModX", "ModXMotion scrollBlur=$scrollBlur sendAnim=$sendAnim")
        if (scrollBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) hookScroll()
        if (sendAnim) hookSend()
    }

    private fun hookScroll() {
        val strengthDp = xprefs.getInt("modx_scroll_blur_strength", 6)
        val offsetMethod = View::class.java.getDeclaredMethod("computeVerticalScrollOffset")
        offsetMethod.isAccessible = true
        View::class.java.resolve().firstMethod {
            name = "onScrollChanged"
            superclass()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (v !is ViewGroup) return@after
                val dm = v.resources.displayMetrics
                if (v.height < dm.heightPixels * 0.35f) return@after
                val off = try {
                    offsetMethod.invoke(v) as Int
                } catch (_: Throwable) {
                    return@after
                }
                val prev = lastOffset.put(v, off) ?: off
                val delta = abs(off - prev)
                val r = (delta / 60f).coerceAtMost(1f) * strengthDp * dm.density
                try {
                    if (r >= 1f) {
                        v.setRenderEffect(
                            RenderEffect.createBlurEffect(0f, r, Shader.TileMode.CLAMP)
                        )
                    } else {
                        v.setRenderEffect(null)
                    }
                } catch (_: Throwable) {
                }
                val reset = resetters.getOrPut(v) { Runnable { v.setRenderEffect(null) } }
                v.removeCallbacks(reset)
                v.postDelayed(reset, 120)
            }
        }
    }

    private fun hookSend() {
        val sendId = Utils.getID("send", "id")
        if (sendId <= 0) {
            Log.w("ModX", "ModXMotion: no se encontró el id send")
            return
        }
        View::class.java.resolve().firstMethod {
            name = "performClick"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val v = instance as? View ?: return@after
                if (v.id != sendId) return@after
                v.animate().cancel()
                v.animate().scaleX(0.7f).scaleY(0.7f).rotation(-25f).setDuration(80)
                    .withEndAction {
                        v.animate().scaleX(1.3f).scaleY(1.3f).rotation(15f).setDuration(110)
                            .withEndAction {
                                v.animate().scaleX(1f).scaleY(1f).rotation(0f)
                                    .setDuration(140).start()
                            }.start()
                    }.start()
            }
        }
    }

    override fun getPluginName(): String = "ModX Motion"
}
