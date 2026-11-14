package com.clearcapture.clearcapture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.clearcapture.clearcapture.gallery.GalleryScreen
import com.clearcapture.clearcapture.home.HomeScreen
import dagger.hilt.android.AndroidEntryPoint

/** Single activity of the POC shell: the home screen launches the SDK; the gallery shows the design system. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var gallery by rememberSaveable { mutableStateOf(false) }
            MaterialTheme {
                Surface {
                    if (gallery) {
                        BackHandler { gallery = false }
                        GalleryScreen()
                    } else {
                        HomeScreen(onOpenGallery = { gallery = true })
                    }
                }
            }
        }
    }
}
