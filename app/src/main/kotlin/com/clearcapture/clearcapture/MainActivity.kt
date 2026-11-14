package com.clearcapture.clearcapture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clearcapture.clearcapture.gallery.GalleryScreen
import dagger.hilt.android.AndroidEntryPoint

/** Single activity of the POC shell. For now it opens on the design system's component gallery. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GalleryScreen()
        }
    }
}
