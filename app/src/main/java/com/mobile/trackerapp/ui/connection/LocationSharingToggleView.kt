package com.location.and.phone.tracker.locationnphonetracker.ui.connection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.location.and.phone.tracker.locationnphonetracker.R

/** Green pill toggle used for the Connection screen's location sharing state. */
class LocationSharingToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var isChecked: Boolean = false
        set(value) {
            field = value
            refreshDrawableState()
            invalidate()
        }

    private var listener: ((LocationSharingToggleView, Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.connection_sharing_on)
        setOnClickListener {
            isChecked = !isChecked
            listener?.invoke(this, isChecked)
        }
    }

    fun setOnCheckedChangeListener(listener: ((LocationSharingToggleView, Boolean) -> Unit)?) {
        this.listener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(36), widthMeasureSpec),
            resolveSize(dp(24), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val thumbRadius = dp(8).toFloat()
        val thumbInset = dp(3).toFloat()

        trackPaint.color = if (isChecked) Color.rgb(0, 165, 72) else Color.rgb(201, 210, 206)
        canvas.drawRoundRect(0f, 0f, width, height, height / 2f, height / 2f, trackPaint)

        thumbPaint.color = Color.WHITE
        val thumbCenterX = if (isChecked) {
            width - thumbInset - thumbRadius
        } else {
            thumbInset + thumbRadius
        }
        canvas.drawCircle(thumbCenterX, height / 2f, thumbRadius, thumbPaint)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
