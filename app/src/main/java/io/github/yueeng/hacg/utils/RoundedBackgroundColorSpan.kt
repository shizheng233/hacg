package io.github.yueeng.hacg.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt

class RoundedBackgroundColorSpan(private val backgroundColor: Int) : ReplacementSpan() {
    private val linePadding = 2f // play around with these as needed
    private val sidePadding = 5f // play around with these as needed
    private fun measureText(paint: Paint, text: CharSequence, start: Int, end: Int): Float =
        paint.measureText(text, start, end)

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        p4: Paint.FontMetricsInt?
    ): Int =
        (measureText(paint, text, start, end) + (2 * sidePadding)).roundToInt()

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val rect = RectF(
            x, y + paint.fontMetrics.top - linePadding,
            x + getSize(paint, text, start, end, paint.fontMetricsInt),
            y + paint.fontMetrics.bottom + linePadding
        )
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, 5F, 5F, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawText(text, start, end, x + sidePadding, y * 1F, paint)
    }

}