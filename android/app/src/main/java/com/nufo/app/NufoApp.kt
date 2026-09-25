package com.nufo.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.nufo.app.data.DishTable
import com.nufo.app.data.FoodApi
import com.nufo.app.data.FoodRepository
import com.nufo.app.data.NufoDatabase
import com.nufo.app.data.SettingsStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Manual DI: one repository and one settings store for the whole process. */
class NufoApp : Application(), SingletonImageLoader.Factory {
    private val db by lazy { NufoDatabase.create(this) }
    val repository by lazy { FoodRepository(FoodApi(), db.history(), db.cache(), DishTable(this)) }
    val settings by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        // Open the database now, in parallel with activity creation, so history is ready by the end of the
        // opening beat instead of when the first screen first asks for it.
        thread(name = "db-warmup") { db.openHelper.writableDatabase }
    }

    // The Open Food Facts image CDN can take well over 10 s per photo, so allow more time than
    // Coil's default and rely on its disk cache so each photo is downloaded only once.
    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = {
                OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("User-Agent", FoodApi.USER_AGENT).build())
                    }
                    .build()
            }))
        }
        .build()
}