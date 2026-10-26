package com.idlix.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.idlix.data.net.CoreHttp
import com.idlix.data.net.IdlixDomainResolver
import kotlinx.coroutines.Dispatchers

class IdlixMobileApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        CoreHttp.init(this, "IdlixMobile-Client/1.0 (Android)")
        IdlixDomainResolver.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(CoreHttp.okHttpClient)
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .decoderDispatcher(Dispatchers.IO)
            .fetcherDispatcher(Dispatchers.IO)
            .transformationDispatcher(Dispatchers.IO)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

}
