package com.unishuttle.app.presentation.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.unishuttle.app.presentation.ui.theme.UniColors
import androidx.compose.ui.graphics.toArgb

/**
 * Renders the Campus Ink design-system markers to bitmaps for MapLibre
 * symbol layers. Stop markers keep the 20dp Sun circle with a 2dp Ink ring
 * (dashed outer ring when selected); shuttle markers are Brick pills with a
 * heading arrow and label, rotated on the map via the layer's icon-rotate.
 */
object CampusMarkerFactory {

    fun stopIcon(context: Context, selected: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (48 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = sizePx / 2f
        val cy = sizePx / 2f

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UniColors.Ink.toArgb() }

        if (selected) {
            val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = UniColors.Ink.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = 2 * density
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            canvas.drawCircle(cx, cy, 16 * density, dashed)
        }

        canvas.drawCircle(cx, cy, 10 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UniColors.Sun.toArgb() })

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UniColors.Ink.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 2 * density
        }
        canvas.drawCircle(cx, cy, 10 * density, ring)

        // Keep the painter referenced so R8 does not strip the color setup.
        outline.alpha = 255
        return bitmap
    }

    fun shuttleIcon(context: Context, label: String): Bitmap {
        val density = context.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UniColors.Canvas.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13 * density
        }
        val textWidth = textPaint.measureText(label)
        val iconSize = 12 * density
        val horizontalPadding = 12 * density
        val gap = 6 * density

        val pillHeight = 28 * density
        val pillWidth = (horizontalPadding * 2) + iconSize + gap + textWidth
        // A little extra room so the hard shadow is not clipped.
        val shadowMargin = 3 * density
        val bitmap = Bitmap.createBitmap(
            (pillWidth + shadowMargin * 2).toInt(),
            (pillHeight + shadowMargin * 2).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        val pill = RectF(shadowMargin, shadowMargin, shadowMargin + pillWidth, shadowMargin + pillHeight)
        val radius = pillHeight / 2f

        // Hard offset shadow (Campus Ink signature)
        val shadow = RectF(pill).apply { offset(shadowMargin, shadowMargin) }
        canvas.drawRoundRect(
            shadow,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UniColors.Ink.toArgb() },
        )

        canvas.drawRoundRect(pill, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UniColors.Brick.toArgb() })

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UniColors.Ink.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 2 * density
        }
        canvas.drawRoundRect(pill, radius, radius, border)

        // Heading arrow, drawn pointing up (0deg); the symbol layer rotates it.
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UniColors.Canvas.toArgb() }
        val arrowCx = pill.left + horizontalPadding + iconSize / 2f
        val arrowCy = pill.centerY()
        val half = iconSize / 2f
        val arrow = android.graphics.Path().apply {
            moveTo(arrowCx, arrowCy - half)
            lineTo(arrowCx + half * 0.85f, arrowCy + half * 0.7f)
            lineTo(arrowCx, arrowCy + half * 0.25f)
            lineTo(arrowCx - half * 0.85f, arrowCy + half * 0.7f)
            close()
        }
        canvas.drawPath(arrow, arrowPaint)

        val textX = pill.left + horizontalPadding + iconSize + gap
        val textY = arrowCy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, textX, textY, textPaint)

        return bitmap
    }
}
