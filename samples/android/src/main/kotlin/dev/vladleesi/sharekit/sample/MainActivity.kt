package dev.vladleesi.sharekit.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.vladleesi.kmpsharekit.ShareKit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ShareSample() }
    }

    override fun onResume() {
        super.onResume()
        ShareKit.setActivity(this)
    }
}
