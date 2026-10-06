package com.zainkhalid.salah.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Compass ring with a needle, for the Qibla tab. Tinted by Icon like the material icons. */
val QiblaIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Qibla", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            // Ring: a 10 radius circle with an 8 radius hole.
            moveTo(12f, 2f)
            arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 22f)
            arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 2f)
            close()
            moveTo(12f, 4f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12f, y1 = 20f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12f, y1 = 4f)
            close()
        }
        .path(fill = SolidColor(Color.Black)) {
            // Needle, pointing up and to the right.
            moveTo(16.6f, 7.4f)
            lineTo(13.5f, 13.5f)
            lineTo(7.4f, 16.6f)
            lineTo(10.5f, 10.5f)
            close()
        }
        .build()
}
