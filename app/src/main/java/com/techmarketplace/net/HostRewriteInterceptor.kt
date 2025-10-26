// app/src/main/java/com/techmarketplace/net/HostRewriteInterceptor.kt
package com.techmarketplace.net

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl

class HostRewriteInterceptor(
    private val targetIp: String = "10.0.2.2",
    private val targetPort: Int = 9000
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val url = req.url

        val isLocalSigned =
            (url.host == "localhost" || url.host == "127.0.0.1") && (url.port == 9000 || url.port == -1)

        if (!isLocalSigned) return chain.proceed(req)

        val newUrl: HttpUrl = url.newBuilder()
            .host(targetIp)
            .port(targetPort)
            .build()

        val hostHeader = if (url.port == -1 || url.port == 80) "localhost" else "localhost:${url.port}"

        val newReq = req.newBuilder()
            .url(newUrl)
            .header("Host", hostHeader) // mantiene la firma válida
            .build()

        return chain.proceed(newReq)
    }
}
