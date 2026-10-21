package com.unishuttle.app.presentation.ui.screen.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

fun bitmapDescriptorFromVector(
    context: Context,
    @DrawableRes drawableResId: Int,
): BitmapDescriptor {
    val vectorDrawable = requireNotNull(ContextCompat.getDrawable(context, drawableResId)) {
        "Drawable resource $drawableResId was not found."
    }
    val width = vectorDrawable.intrinsicWidth.coerceAtLeast(1)
    val height = vectorDrawable.intrinsicHeight.coerceAtLeast(1)
    vectorDrawable.setBounds(0, 0, width, height)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    vectorDrawable.draw(Canvas(bitmap))

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** Convenience object so screens outside `map` package can access the helper. */
object MapMarkerIcons {
    fun shuttleIcon(context: Context, @DrawableRes drawableResId: Int): BitmapDescriptor =
        bitmapDescriptorFromVector(context, drawableResId)
}

