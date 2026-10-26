package com.idlix.data.net

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP stack for `:core:data`. No backend URLs, no server-URL prefs
 * keys, no SubDL key storage — those live with their owners
 * ([IdlixDomainResolver] / `SubdlRepository`).
 */
object CoreHttp {

    @Volatile
    var applicationContext: Context? = null
        private set

    @Volatile
    private var userAgentHeader: String = "Idlix-Client/1.0"

    fun init(context: Context, userAgent: String? = null) {
        this.applicationContext = context.applicationContext
        if (!userAgent.isNullOrBlank()) {
            this.userAgentHeader = userAgent
        }
    }

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val headerInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val modifiedRequest = originalRequest.newBuilder()
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", userAgentHeader)
            .build()
        chain.proceed(modifiedRequest)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Never follows redirects: the resolver must observe the `Location` header
     * itself instead of letting OkHttp swallow it.
     */
    val noRedirectClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(headerInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
