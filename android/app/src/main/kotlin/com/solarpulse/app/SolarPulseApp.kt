package com.solarpulse.app

import android.app.Application
import android.content.Context
import com.solarpulse.app.alerts.AlertEngine
import com.solarpulse.app.alerts.AlertWorker
import com.solarpulse.app.alerts.Notifier
import com.solarpulse.app.data.AuthRepository
import com.solarpulse.app.data.Backend
import com.solarpulse.app.data.DataRepository
import com.solarpulse.app.data.LocalBackend
import com.solarpulse.app.data.Prefs
import com.solarpulse.app.data.SupabaseBackend
import com.solarpulse.app.data.WeatherRepository
import com.solarpulse.core.model.Database
import com.solarpulse.core.seed.Seed
import com.solarpulse.core.sim.Sim
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.osmdroid.config.Configuration
import java.io.File

class SolarPulseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // OpenStreetMap tile policy: identify the app; cache tiles in app-private storage.
        Configuration.getInstance().apply {
            load(this@SolarPulseApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        container.notifier.ensureChannel()
        if (container.isDemo) AlertWorker.schedule(this)
    }
}

/** Manual dependency injection: one instance of everything for the process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sim = Sim()
    val prefs = Prefs(appContext)

    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabase: SupabaseClient? =
        if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
                install(Auth)
                install(Postgrest)
            }
        } else {
            null
        }

    /** Demo mode: no Supabase credentials in local.properties. */
    val isDemo: Boolean get() = supabase == null

    private val seedFactory: () -> Database = { Seed(sim).build() }

    private val backend: Backend = supabase?.let { SupabaseBackend(it, seedFactory) }
        ?: LocalBackend(File(appContext.filesDir, "solarpulse-db-v1.json"), seedFactory) { Seed(sim).buildAlertRules() }

    val repository = DataRepository(backend, sim, seedFactory)
    val auth = AuthRepository(supabase, prefs)

    val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 8_000
        }
    }
    val weather = WeatherRepository(http, sim)
    val notifier = Notifier(appContext)
    val alerts = AlertEngine(repository, sim, notifier, appContext)

    /** Live clock (every 5 s while someone listens): drives current power, energy flow, weather. */
    val ticker: StateFlow<Long> = flow {
        while (true) {
            emit(sim.now())
            delay(5_000)
        }
    }.stateIn(appScope, SharingStarted.WhileSubscribed(5_000), sim.now())

    /** `POST {SUPABASE_URL}/functions/v1/ingest` (shown for webhook integrations). */
    val ingestUrl: String =
        "${supabaseUrl.ifBlank { "https://<project-ref>.supabase.co" }}/functions/v1/ingest"
}

val Context.container: AppContainer get() = (applicationContext as SolarPulseApp).container
