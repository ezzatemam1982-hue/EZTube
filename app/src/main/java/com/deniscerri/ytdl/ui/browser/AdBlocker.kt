package com.deniscerri.ytdl.ui.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Strict host-only ad blocker for [InAppBrowserActivity].
 *
 * Only exact host or subdomain matches against dedicated ad/popup networks return an empty
 * [WebResourceResponse]. All other requests return `null` from `shouldInterceptRequest` so
 * pages, countdown timers, captchas, redirects, and video streams load natively.
 */
object AdBlocker {

    private val AD_HOSTS = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adnxs.com",
        "taboola.com",
        "outbrain.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerclick.com",
        "exoclick.com",
        "exosrv.com",
        "juicyads.com",
        "adsterra.com",
        "adsterratools.com",
        "adcash.com",
        "hilltopads.net",
        "clickadu.com",
        "admaven.com",
        "monetag.com",
        "pemsrv.com",
        "a-ads.com",
        "criteo.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "scorecardresearch.com",
        "quantserve.com",
        "histats.com",
        "yandexadexchange.net",
        "zedo.com",
        "trafficjunky.net",
        "trafficfactory.biz",
        "ero-advertising.com",
        "bidswitch.net",
        "smartadserver.com",
        "amazon-adsystem.com",
        "moatads.com",
        "adform.net",
        "realsrv.com",
        "adtng.com",
        "onclasrv.com",
        "highperformanceformat.com",
        "effectivegatecpm.com",
        "profitabledisplaynetwork.com",
        "plugrush.com",
        "adxpansion.com",
        "mgid.com",
        "revcontent.com",
        "richpush.co",
        "ads-twitter.com",
        "adsystem.com",
    )

    fun isAdHost(host: String?): Boolean {
        val normalized = host
            ?.trim()
            ?.lowercase(Locale.US)
            ?.trimEnd('.')
            ?.takeIf { it.isNotEmpty() }
            ?: return false

        return AD_HOSTS.any { domain ->
            normalized == domain || normalized.endsWith(".$domain")
        }
    }

    fun isAdUrl(uri: Uri?): Boolean = isAdHost(uri?.host)

    fun isAdUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { Uri.parse(url).host }.getOrNull()
            ?: url.substringAfter("://", "")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('@')
                .substringBefore(':')
        return isAdHost(host)
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
