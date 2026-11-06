package cider.remote

import kotlinx.serialization.json.Json

val CiderJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
