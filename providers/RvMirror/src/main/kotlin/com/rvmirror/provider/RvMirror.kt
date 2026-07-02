package com.rvmirror.provider

import android.content.Context
import androidx.annotation.Keep
import com.flixclusive.provider.FlixclusiveProvider
import com.flixclusive.provider.Provider
import com.flixclusive.provider.ProviderApi
import okhttp3.OkHttpClient

@Keep
@FlixclusiveProvider
class RvMirror : Provider() {
    override fun getApi(
        context: Context,
        client: OkHttpClient,
    ): ProviderApi = RvMirrorApi(
        context = context,
        client = client,
        provider = this,
    )
}