package com.saucedplussytv.androidtv.client

import com.android.volley.AuthFailureError
import com.android.volley.Header
import com.android.volley.Request
import com.android.volley.toolbox.BaseHttpStack
import com.android.volley.toolbox.HttpResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Volley HTTP transport backed by OkHttp 5.x.
 *
 * Java's HttpURLConnection (Volley's default HurlStack) has a distinctive TLS
 * fingerprint that Cloudflare's bot detection can identify and block. OkHttp's
 * fingerprint is much closer to a real browser client and passes CF checks.
 *
 * Redirects are disabled at the OkHttp layer: Volley's BasicNetwork expects to
 * handle 3xx itself, and a redirect followed silently inside OkHttp (e.g. a
 * Cloudflare challenge) would surface to Volley as a 200 of HTML, breaking the
 * JSON contract with a cryptic Gson error instead of a clean failure.
 */
internal class OkHttpStack(
    private val client: OkHttpClient = DEFAULT_CLIENT
) : BaseHttpStack() {

    @Throws(IOException::class, AuthFailureError::class)
    override fun executeRequest(
        request: Request<*>,
        additionalHeaders: Map<String, String>
    ): HttpResponse {
        val builder = okhttp3.Request.Builder().url(request.url)

        // request.headers may throw AuthFailureError; let it propagate so Volley
        // treats it as an auth failure rather than sending an unauthenticated request.
        val merged = buildMap<String, String> {
            putAll(request.headers)
            putAll(additionalHeaders)
        }
        merged.forEach { (k, v) -> builder.header(k, v) }

        // request.body / bodyContentType may also throw AuthFailureError (propagates).
        fun body(): RequestBody? {
            val raw = request.body ?: return null
            val ct = (request.bodyContentType ?: "application/octet-stream").toMediaType()
            return raw.toRequestBody(ct)
        }
        when (request.method) {
            Request.Method.GET     -> builder.get()
            Request.Method.HEAD    -> builder.head()
            Request.Method.DELETE  -> builder.delete(body())
            Request.Method.POST    -> builder.post(body() ?: EMPTY_BODY)
            Request.Method.PUT     -> builder.put(body() ?: EMPTY_BODY)
            Request.Method.PATCH   -> builder.patch(body() ?: EMPTY_BODY)
            Request.Method.OPTIONS -> builder.method("OPTIONS", body())
            Request.Method.TRACE   -> builder.method("TRACE", null)
            else -> throw IllegalArgumentException("Unsupported method: ${request.method}")
        }

        val response = client.newCall(builder.build()).execute()
        return try {
            val headers = response.headers.map { Header(it.first, it.second) }
            // OkHttp 5 guarantees a non-null body (empty for HEAD/204), unlike 4.x.
            // contentLength() is -1 for unknown/chunked length, which Volley accepts.
            val responseBody = response.body
            HttpResponse(
                response.code, headers,
                responseBody.contentLength().toInt(),
                responseBody.byteStream()
            )
        } catch (e: Throwable) {
            // We hand Volley the still-open body stream on success; if we fail before
            // that hand-off, close the response so its connection isn't leaked.
            response.close()
            throw e
        }
    }

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody()

        private val DEFAULT_CLIENT = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
