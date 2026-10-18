package com.mobile.trackerapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Compact wrap-content zone toggle matching the app's green pill control. */
class ZoneToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)

    var isChecked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var listener: ((ZoneToggleView, Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Toggle zone alerts"
        setOnClickListener {
            isChecked = !isChecked
            listener?.invoke(this, isChecked)
        }
    }

    fun setOnCheckedChangeListener(listener: ((ZoneToggleView, Boolean) -> Unit)?) {
        this.listener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(34), widthMeasureSpec),
            resolveSize(dp(20), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        track.color = if (isChecked) Color.rgb(0, 165, 72) else Color.rgb(201, 210, 206)
        canvas.drawRoundRect(0f, 0f, width, height, height / 2f, height / 2f, track)
        thumb.color = Color.WHITE
        val radius = dp(8).toFloat()
        val centerX = if (isChecked) width - radius - dp(2) else radius + dp(2).toFloat()
        canvas.drawCircle(centerX, height / 2f, radius, thumb)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
