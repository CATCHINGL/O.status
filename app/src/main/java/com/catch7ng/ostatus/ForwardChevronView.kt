package com.catch7ng.ostatus

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class ForwardChevronView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        paint.color = if (dark) Color.rgb(142,142,147) else Color.rgb(99,99,102)
        val cx = width / 2f
        val cy = height / 2f
        val halfW = 2.25f * d
        val halfH = 4.25f * d
        path.reset()
        path.moveTo(cx - halfW, cy - halfH)
        path.lineTo(cx + halfW, cy)
        path.lineTo(cx - halfW, cy + halfH)
        canvas.drawPath(path, paint)
    }
}
