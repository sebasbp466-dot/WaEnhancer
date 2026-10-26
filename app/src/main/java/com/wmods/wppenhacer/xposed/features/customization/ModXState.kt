package com.wmods.wppenhacer.xposed.features.customization

import android.graphics.drawable.Drawable
import android.view.View

object ModXState {
    @Volatile
    var baseBg: Int = 0

    @Volatile
    var bgDrawable: Drawable? = null

    @Volatile
    var bgView: View? = null
}
