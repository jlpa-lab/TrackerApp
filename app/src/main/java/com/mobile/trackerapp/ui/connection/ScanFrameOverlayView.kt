package com.mobile.trackerapp.ui.connection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Reference-style QR scan brackets and center guide line. */
class ScanFrameOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA8E8C0.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 4f * density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 46f * density
        val right = width - left
        val top = 86f * density
        val bottom = height - top
        val length = 32f * density
        val radius = 14f * density

        canvas.drawLine(left + radius, top, left + length, top, paint)
        canvas.drawArc(left, top, left + radius * 2, top + radius * 2, 180f, 90f, false, paint)
        canvas.drawLine(left, top + radius, left, top + length, paint)

        canvas.drawLine(right - length, top, right - radius, top, paint)
        canvas.drawArc(right - radius * 2, top, right, top + radius * 2, 270f, 90f, false, paint)
        canvas.drawLine(right, top + radius, right, top + length, paint)

        canvas.drawLine(left + radius, bottom, left + length, bottom, paint)
        canvas.drawArc(left, bottom - radius * 2, left + radius * 2, bottom, 90f, 90f, false, paint)
        canvas.drawLine(left, bottom - radius, left, bottom - length, paint)

        canvas.drawLine(right - length, bottom, right - radius, bottom, paint)
        canvas.drawArc(right - radius * 2, bottom - radius * 2, right, bottom, 0f, 90f, false, paint)
        canvas.drawLine(right, bottom - radius, right, bottom - length, paint)

        canvas.drawLine(left + 14f * density, height / 2f, right - 14f * density, height / 2f, paint)
    }
}
