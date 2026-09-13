package com.catch7ng.ostatus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.content.res.Configuration
import android.view.View

/**
 * Shared secondary-page back control.
 * 44dp touch target with a centered 10x18dp, round-ended chevron.
 */
class BackChevronView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Back"
        minimumWidth = dp(44)
        minimumHeight = dp(44)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        paint.color = if (dark) Color.WHITE else Color.BLACK

        val cx = width / 2f
        val cy = height / 2f
        val halfW = 5f * d
        val halfH = 9f * d

        path.reset()
        path.moveTo(cx + halfW, cy - halfH)
        path.lineTo(cx - halfW, cy)
        path.lineTo(cx + halfW, cy + halfH)
        canvas.drawPath(path, paint)
    }

    private fun dp(v: Int) = (v * d + 0.5f).toInt()
}
