package com.impulsive.app.backend.service.protection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

class ImpulsiveBubbleCardDrawable(
    private val density: Float,
) : Drawable() {
    private val cornerRadius = 28f * density
    private val clipPath = Path()
    private val cardBounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drawableAlpha = 255

    override fun draw(canvas: Canvas) {
        cardBounds.set(bounds)
        clipPath.reset()
        clipPath.addRoundRect(cardBounds, cornerRadius, cornerRadius, Path.Direction.CW)

        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = withDrawableAlpha(SurfaceColor)
        canvas.drawRect(cardBounds, paint)

        val width = cardBounds.width()
        val height = cardBounds.height()
        val scale = min(width, height)
        drawBubble(canvas, cardBounds.left - width * 0.04f, cardBounds.top + height * 0.08f, scale * 0.72f, Lavender)
        drawBubble(canvas, cardBounds.right + width * 0.03f, cardBounds.top + height * 0.12f, scale * 0.78f, BodyBlue)
        drawBubble(canvas, cardBounds.left - width * 0.08f, cardBounds.top + height * 0.48f, scale * 0.62f, SoulYellow)
        drawBubble(canvas, cardBounds.right + width * 0.07f, cardBounds.top + height * 0.68f, scale * 0.70f, FocusCoral)
        drawBubble(canvas, cardBounds.left + width * 0.03f, cardBounds.bottom + height * 0.02f, scale * 0.62f, NexusGreen)
        drawBubble(canvas, cardBounds.right - width * 0.16f, cardBounds.bottom + height * 0.08f, scale * 0.68f, Lavender)
        canvas.restoreToCount(checkpoint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = withDrawableAlpha(BorderColor)
        canvas.drawRoundRect(cardBounds, cornerRadius, cornerRadius, paint)
    }

    private fun drawBubble(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        if (radius <= 0f) return
        paint.shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(withDrawableAlpha(withAlpha(color, 92)), withDrawableAlpha(withAlpha(color, 0))),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius, paint)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun getAlpha(): Int = drawableAlpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android Drawable API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun withDrawableAlpha(color: Int): Int {
        val sourceAlpha = Color.alpha(color)
        return withAlpha(color, sourceAlpha * drawableAlpha / 255)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private companion object {
        val SurfaceColor = Color.parseColor("#FFFEFC")
        val Lavender = Color.parseColor("#D0C3F1")
        val BodyBlue = Color.parseColor("#BDE0FE")
        val SoulYellow = Color.parseColor("#FEF1AB")
        val FocusCoral = Color.parseColor("#F5A7A6")
        val NexusGreen = Color.parseColor("#93E9BE")
        val BorderColor = Color.parseColor("#38D0C3F1")
    }
}
