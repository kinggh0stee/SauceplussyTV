package com.saucedplussytv.androidtv.client

import com.android.volley.AuthFailureError
import com.android.volley.Header
import com.android.volley.Request
import com.android.volley.toolbox.BaseHttpStack
import com.android.volley.toolbox.HttpResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Volley HTTP transport backed by OkHttp 4.x.
 *
 * Java's HttpURLConnection (Volley's default HurlStack) has a distinctive TLS
 * fingerprint that Cloudflare's bot detection can identify and block. OkHttp's
 * fingerprint is much closer to a real browser client and passes CF checks.
 */
internal class OkHttpStack(
    private val client: OkHttpClient = DEFAULT_CLIENT
) : BaseHttpStack() {

    override fun executeRequest(
        request: Request<*>,
        additionalHeaders: Map<String, String>
    ): HttpResponse {
        val builder = okhttp3.Request.Builder().url(request.url)

        val merged = buildMap<String, String> {
            try { putAll(request.headers) } catch (_: AuthFailureError) {}
            putAll(additionalHeaders)
        }
        merged.forEach { (k, v) -> builder.header(k, v) }

        when (request.method) {
            Request.Method.GET -> builder.get()
            Request.Method.HEAD -> builder.head()
            Request.Method.DELETE -> builder.delete()
            else -> {
                val ct = request.bodyContentType ?: "application/octet-stream"
                val body = (request.body ?: ByteArray(0)).toRequestBody(ct.toMediaType())
                when (request.method) {
                    Request.Method.POST  -> builder.post(body)
                    Request.Method.PUT   -> builder.put(body)
                    Request.Method.PATCH -> builder.patch(body)
                    else -> throw IllegalArgumentException("Unsupported method: ${request.method}")
                }
            }
        }

        val response = client.newCall(builder.build()).execute()
        val headers = response.headers.map { Header(it.first, it.second) }
        return HttpResponse(
            response.code, headers,
            response.body?.contentLength()?.toInt() ?: -1,
            response.body?.byteStream()
        )
    }

    companion object {
        private val DEFAULT_CLIENT = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
