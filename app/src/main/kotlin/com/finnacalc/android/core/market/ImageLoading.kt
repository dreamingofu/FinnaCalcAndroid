//
// ImageLoading.kt
//
// The app's Coil image loader.
//
// It exists for one reason: Wikimedia's User-Agent policy. Requests carrying a
// generic library UA — which is what OkHttp sends by default — are answered
// with 403, so every Trade Tracker portrait silently fell back to a monogram.
// A descriptive UA identifying the app is what their policy asks for, and is
// the honest way to use images the About page already credits.
//
// https://foundation.wikimedia.org/wiki/Policy:User-Agent_policy
//
// Everything else (Brandfetch, Logo.dev, news thumbnails, YouTube stills) is
// unaffected by the header, so this is one loader for the whole app rather
// than a special case bolted onto the tracker.
//

package com.finnacalc.android.core.market

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

object FinnaImageLoader : SingletonImageLoader.Factory {

    /**
     * Identifies the app, per Wikimedia's policy. A contact point is part of
     * what they ask for, so this is the app's own address rather than a
     * browser string copied to look like one.
     */
    const val USER_AGENT = "FinnaCalc/1.0 (Android; +https://finnacalc.com)"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
            }
            .build()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()

    /** Installed once from FinnaApp.onCreate. */
    fun install(context: Context) {
        SingletonImageLoader.setSafe { newImageLoader(context) }
    }
}
