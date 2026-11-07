package pl.luczka.todaywas

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import pl.luczka.todaywas.data.sync.util.StartupSync

@HiltAndroidApp
class TodayWasApplication :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var startupSync: StartupSync

    // Lives as long as the process, for app-start work that isn't any screen's state.
    private val applicationScope = MainScope()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { startupSync.run() }
    }
}
