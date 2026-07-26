package pl.luczka.todaywas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import pl.luczka.todaywas.ui.main.MainScreen
import pl.luczka.todaywas.ui.theme.TodayWasTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TodayWasTheme {
                MainScreen()
            }
        }
    }
}
