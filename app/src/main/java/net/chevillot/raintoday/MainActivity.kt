package net.chevillot.raintoday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import net.chevillot.raintoday.ui.RainTodayApp
import net.chevillot.raintoday.ui.RainTodayTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            RainTodayTheme {
                RainTodayApp(viewModel)
            }
        }
    }
}
