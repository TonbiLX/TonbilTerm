package com.tonbil.termostat.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tonbil.termostat.data.api.TonbilApi
import com.tonbil.termostat.data.api.WebSocketClient
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.screen.DashboardViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tonbil_prefs")

// Bağlantı noktası — her zaman domain üzerinden
val SERVER_URLS = listOf(
    "http://temp.tonbilx.com",     // Domain (LAN + dış erişim)
)

const val DEFAULT_BASE_URL = "http://temp.tonbilx.com"

fun baseUrlToWsUrl(baseUrl: String): String {
    return baseUrl
        .replace("https://", "wss://")
        .replace("http://", "ws://") + "/ws"
}

val appModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = false
        }
    }

    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(get<Json>())
            }
            install(Logging) {
                level = LogLevel.NONE
            }
            install(WebSockets)
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 15_000
            }
            engine {
                config {
                    connectTimeout(8, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                }
            }
        }
    }

    single { get<Context>().dataStore }

    single { TonbilApi(get(), DEFAULT_BASE_URL) }

    single { WebSocketClient(get(), baseUrlToWsUrl(DEFAULT_BASE_URL), get()) }

    single { TonbilRepository(get(), get(), get(), get()) }

    viewModel { DashboardViewModel(get()) }
}
