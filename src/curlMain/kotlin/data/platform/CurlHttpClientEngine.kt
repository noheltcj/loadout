package data.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.curl.Curl

internal actual fun platformHttpClientEngine(): HttpClientEngine = Curl.create()
