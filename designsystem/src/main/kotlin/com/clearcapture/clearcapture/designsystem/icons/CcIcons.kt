package com.clearcapture.clearcapture.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The SDK's icons, drawn as vectors (no bitmaps, no icon library). The path data follows the
 * Material Symbols outlines (Apache License 2.0) on a 24 x 24 grid. Icons are tinted where they
 * are drawn, so the fill colour here is irrelevant.
 */
public object CcIcons {

    /** Close: an X. */
    public val Close: ImageVector by lazy {
        icon(
            "Close",
            "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
        )
    }

    /** Back: an arrow pointing left. */
    public val Back: ImageVector by lazy {
        icon("Back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    }

    /** Torch on: a lightning bolt. */
    public val TorchOn: ImageVector by lazy {
        icon("TorchOn", "M7,2v11h3v9l7,-12h-4l4,-8z")
    }

    /** Torch off: a struck-through lightning bolt. */
    public val TorchOff: ImageVector by lazy {
        icon(
            "TorchOff",
            "M3.27,3L2,4.27l5,5V13h3v9l3.58,-6.14L17.73,20 19,18.73 3.27,3zM17,10h-4l4,-8H7v2.18l8.46,8.46L17,10z",
        )
    }

    /** Pass and ready: a check in a circle. */
    public val CheckCircle: ImageVector by lazy {
        icon(
            "CheckCircle",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17" +
                "l7.59,-7.59L19,8l-9,9z",
        )
    }

    /** Warning: a triangle with an exclamation mark. */
    public val Warning: ImageVector by lazy {
        icon("Warning", "M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z")
    }

    /** Fail and blocked: a cross in a circle. */
    public val CrossCircle: ImageVector by lazy {
        icon(
            "CrossCircle",
            "M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2zM17,15.59L15.59,17 12,13.41 8.41,17" +
                " 7,15.59 10.59,12 7,8.41 8.41,7 12,10.59 15.59,7 17,8.41 13.41,12 17,15.59z",
        )
    }

    /** Information: an i in a circle. */
    public val Info: ImageVector by lazy {
        icon(
            "Info",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z",
        )
    }

    /** Location: a map pin. */
    public val Location: ImageVector by lazy {
        icon(
            "Location",
            "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5" +
                "c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z",
        )
    }

    /** Location off: a struck-through map pin. */
    public val LocationOff: ImageVector by lazy {
        icon(
            "LocationOff",
            "M12,6.5c1.38,0 2.5,1.12 2.5,2.5 0,0.74 -0.33,1.39 -0.83,1.85l3.63,3.63c0.98,-1.86 1.7,-3.8 1.7,-5.48" +
                " 0,-3.87 -3.13,-7 -7,-7 -1.98,0 -3.76,0.83 -5.04,2.15l3.19,3.19c0.46,-0.52 1.11,-0.84 1.85,-0.84z" +
                "M16.37,16.1l-4.63,-4.63 -0.11,-0.11L3.27,3 2,4.27l3.18,3.18C5.07,7.95 5,8.47 5,9c0,5.25 7,13 7,13" +
                "s1.67,-1.85 3.38,-4.35L18.73,21 20,19.73l-3.63,-3.63z",
        )
    }

    /** Camera. */
    public val Camera: ImageVector by lazy {
        icon(
            "Camera",
            "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17" +
                "L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,8.8" +
                "c-1.77,0 -3.2,1.43 -3.2,3.2s1.43,3.2 3.2,3.2 3.2,-1.43 3.2,-3.2 -1.43,-3.2 -3.2,-3.2z",
        )
    }

    /** Shipping container: a box with vertical ribs. */
    public val Container: ImageVector by lazy {
        icon(
            "Container",
            "M2,6v12h20V6H2zM20,16H4V8h16v8zM6,9h2v6H6zM10,9h2v6h-2zM14,9h2v6h-2zM18,9h1v6h-1z",
        )
    }

    /** Person: head and shoulders. */
    public val Person: ImageVector by lazy {
        icon(
            "Person",
            "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2" +
                "c0,-2.66 -5.33,-4 -8,-4z",
        )
    }

    /** Vehicle: a car seen from the front. */
    public val Vehicle: ImageVector by lazy {
        icon(
            "Vehicle",
            "M18.92,6.01C18.72,5.42 18.16,5 17.5,5h-11c-0.66,0 -1.21,0.42 -1.42,1.01L3,12v8c0,0.55 0.45,1 1,1h1" +
                "c0.55,0 1,-0.45 1,-1v-1h12v1c0,0.55 0.45,1 1,1h1c0.55,0 1,-0.45 1,-1v-8l-2.08,-5.99zM6.5,16" +
                "c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,13 6.5,13s1.5,0.67 1.5,1.5S7.33,16 6.5,16zM17.5,16" +
                "c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5zM5,11" +
                "l1.5,-4.5h11L19,11H5z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = "Cc.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()
}
