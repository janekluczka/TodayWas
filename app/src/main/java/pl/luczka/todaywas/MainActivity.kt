package pl.luczka.todaywas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.ui.TodayWasApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DsTheme {
                TodayWasApp()
            }
        }
    }
}
