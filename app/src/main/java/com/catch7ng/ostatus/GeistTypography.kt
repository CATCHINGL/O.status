package com.catch7ng.ostatus

import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/** App UI typography only. Does not affect the status-bar indicator drawing. */
object GeistTypography {
    private fun font(view: View, weight: Int): Typeface {
        val base = ResourcesCompat.getFont(view.context, R.font.geist_variable)
            ?: Typeface.create("sans-serif", Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(base, weight.coerceIn(1, 1000), false)
        } else {
            Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    fun apply(root: View) {
        applyRecursive(root)
    }

    private fun applyRecursive(view: View) {
        if (view is TextView) {
            val sp = view.textSize / view.resources.displayMetrics.scaledDensity
            val weight = when {
                sp >= 30f -> 700          // page titles
                sp <= 13f && view.text.toString().any { it.isLetter() } -> 600 // section labels
                sp >= 16f -> 500          // setting labels / controls
                else -> 400               // values / footer
            }
            view.typeface = font(view, weight)
            view.includeFontPadding = false
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyRecursive(view.getChildAt(i))
        }
    }
}
