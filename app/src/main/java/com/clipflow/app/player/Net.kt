package com.clipflow.app.player

import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R

import android.util.Log
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

internal fun isYouTubeWebHost(host: String): Boolean =
    host.equals("youtube.com", ignoreCase = true) ||
        host.endsWith(".youtube.com", ignoreCase = true)

internal fun isAuthenticatedBrowseRequest(url: HttpUrl, clientId: String?): Boolean {
    if (!isYouTubeWebHost(url.host)) return false
    if (!url.encodedPath.equals("/youtubei/v1/browse", ignoreCase = true)) return false
    return clientId == null || clientId == "1" || clientId == "2"
}

internal fun prioritizeIpv4(addresses: List<InetAddress>): List<InetAddress> {
    if (addresses.size <= 1) return addresses
    val (ipv4, other) = addresses.partition { it is Inet4Address }
    return if (ipv4.isEmpty() || other.isEmpty()) addresses else ipv4 + other
}

internal val ipv4PreferredDns = Dns { hostname ->
    prioritizeIpv4(Dns.SYSTEM.lookup(hostname))
}

/**
 * Injects persisted YouTube session cookies and SAPISIDHASH authorization headers exclusively
 * onto `youtubei/v1/browse` requests on `*.youtube.com` using WEB/MWEB clients (`Home` and
 * `Subscriptions`), leaving `youtubei/v1/player`, `youtubei.googleapis.com`, and
 * `googlevideo.com` requests unauthenticated.
 */
internal val youtubeAuthInterceptor = Interceptor { chain ->
    val original = chain.request()
    val shouldAuthenticate = isAuthenticatedBrowseRequest(
        original.url,
        original.header("X-YouTube-Client-Name"),
    ) && Account.hasSessionCookies()
    val request = if (shouldAuthenticate) {
        val cookieHeader = Account.cookieHeader()
        val authHeader = Account.authorization(Account.ORIGIN)
        if (cookieHeader.isNotBlank()) {
            original.newBuilder()
                .header("Cookie", cookieHeader)
                .apply {
                    if (authHeader != null) {
                        header("Authorization", authHeader)
                        if (original.method == "POST" && original.header("Origin") == null) {
                            header("Origin", Account.ORIGIN)
                        }
                        header("X-Origin", Account.ORIGIN)
                        header("X-Goog-AuthUser", "0")
                    }
                }
                .build()
        } else {
            original
        }
    } else {
        original
    }

    val response = chain.proceed(request)
    if (shouldAuthenticate) {
        Account.absorbSetCookies(response.headers("Set-Cookie"))
    }
    response
}

/** One HTTP client for the whole app: extractor requests and media playback share it. */
object Net {
    // NewPipe uses a desktop Firefox UA; YouTube serves the web client pages it parses.
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dns(ipv4PreferredDns)
            .retryOnConnectionFailure(true)
            .addInterceptor(youtubeAuthInterceptor)
            // Idle connections to the video servers stay warm between videos.
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
    }

    /** The video-server host YouTube last handed out; usually the same one next time. */
    @Volatile private var mediaHost: String? = null

    fun noteMediaHost(url: String?) {
        url?.toHttpUrlOrNull()?.host?.let { mediaHost = it }
    }

    /** Drops idle sockets and cached CDN edge hints after a VPN / network change so requests retry cleanly. */
    fun onNetworkChanged() {
        mediaHost = null
        runCatching { client.connectionPool.evictAll() }
        authLog(Log.INFO, "Net.onNetworkChanged: evicted connection pool")
    }

    /**
     * Opens (DNS + TLS) a connection to the likely video server while the stream data is
     * still being extracted, so the player's first requests skip ~0.7 s of setup. A cheap
     * request to /generate_204; if YouTube picks another host this time, nothing is lost.
     */
    fun prewarmMediaConnection() {
        val host = mediaHost ?: return
        val request = okhttp3.Request.Builder().url("https://$host/generate_204").head().header("User-Agent", USER_AGENT).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) = response.close()
        })
    }
}

/** Bridges NewPipeExtractor's HTTP abstraction onto OkHttp. */
class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .addHeader("User-Agent", Net.USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            // YouTube answers rate-limited clients with 429 + a captcha page.
            if (response.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            val text = response.body.string()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                if (PlayerFallback.handles(request.url())) PlayerFallback.substitute(request.url(), request.dataToSend(), text) else text,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        val instance: NewPipeDownloader by lazy { NewPipeDownloader(Net.client) }
    }
}
