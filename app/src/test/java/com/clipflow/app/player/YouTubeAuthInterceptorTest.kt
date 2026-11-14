package com.clipflow.app.player

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

class YouTubeAuthInterceptorTest {

    @After
    fun tearDown() {
        Account.setSessionCookiesForTest(null)
    }

    @Test
    fun netClientRegistersAuthInterceptorIpv4DnsAndRetryOnFailure() {
        assertTrue(youtubeAuthInterceptor in Net.client.interceptors)
        assertTrue(Net.client.dns === ipv4PreferredDns)
        assertTrue(Net.client.retryOnConnectionFailure)
    }

    @Test
    fun injectsCookieAndSapisidHashHeadersOnYouTubeBrowseRequests() {
        val sapisid = "test_sapisid_secret"
        val p1 = "test_1p_apisid"
        val p3 = "test_3p_apisid"
        Account.setSessionCookiesForTest(
            "SAPISID=$sapisid; SID=sid_val; HSID=hsid_val; SSID=ssid_val; " +
                "__Secure-1PSID=1psid_val; __Secure-3PSID=3psid_val; " +
                "__Secure-1PAPISID=$p1; __Secure-3PAPISID=$p3",
        )
        assertTrue(Account.hasSessionCookies())

        val req = executeThroughInterceptor(
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .post("{\"browseId\":\"FEwhat_to_watch\"}".toRequestBody("application/json".toMediaType()))
                .build(),
            responseHeaders = listOf("Set-Cookie" to "__Secure-1PSIDTS=rotated_token_123; Path=/; Secure"),
        )

        val cookie = req.header("Cookie").orEmpty()
        assertTrue(cookie.contains("SAPISID=$sapisid"))
        assertTrue(cookie.contains("SID=sid_val"))
        assertTrue(cookie.contains("HSID=hsid_val"))
        assertTrue(cookie.contains("SSID=ssid_val"))
        assertTrue(cookie.contains("__Secure-1PSID=1psid_val"))
        assertTrue(cookie.contains("__Secure-3PSID=3psid_val"))

        assertEquals("https://www.youtube.com", req.header("Origin"))
        assertEquals("https://www.youtube.com", req.header("X-Origin"))
        assertEquals("0", req.header("X-Goog-AuthUser"))

        val auth = req.header("Authorization")
        assertNotNull(auth)
        val parts = auth!!.split(' ')
        assertTrue(parts[0] == "SAPISIDHASH")
        val tsAndHash = parts[1].split('_')
        assertEquals(2, tsAndHash.size)
        val ts = tsAndHash[0]
        val expectedSha1 = sha1Hex("$ts $sapisid https://www.youtube.com")
        assertEquals(expectedSha1, tsAndHash[1])
        assertTrue(parts.any { it == "SAPISID1PHASH" })
        assertTrue(parts.any { it == "SAPISID3PHASH" })

        assertTrue(Account.cookieHeader().contains("__Secure-1PSIDTS=rotated_token_123"))
    }

    @Test
    fun injectsAuthHeadersExclusivelyOnYouTubeBrowseWebAndMwebRequests() {
        val sapisid = "s1_browse_secret"
        Account.setSessionCookiesForTest("SAPISID=$sapisid; SID=sid1; HSID=hsid1; SSID=ssid1")

        val webBrowseReq = executeThroughInterceptor(
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("X-YouTube-Client-Name", "1")
                .post("{\"browseId\":\"FEsubscriptions\"}".toRequestBody("application/json".toMediaType()))
                .build(),
        )
        assertTrue(webBrowseReq.header("Cookie").orEmpty().contains("SAPISID=$sapisid"))
        assertTrue(webBrowseReq.header("Authorization").orEmpty().startsWith("SAPISIDHASH "))
        assertEquals("https://www.youtube.com", webBrowseReq.header("Origin"))
        assertEquals("https://www.youtube.com", webBrowseReq.header("X-Origin"))
        assertEquals("0", webBrowseReq.header("X-Goog-AuthUser"))

        val mwebBrowseReq = executeThroughInterceptor(
            Request.Builder()
                .url("https://m.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("X-YouTube-Client-Name", "2")
                .post("{\"browseId\":\"FEwhat_to_watch\"}".toRequestBody("application/json".toMediaType()))
                .build(),
        )
        assertNotNull(mwebBrowseReq.header("Cookie"))
        assertNotNull(mwebBrowseReq.header("Authorization"))
        assertEquals("https://www.youtube.com", mwebBrowseReq.header("Origin"))
        assertEquals("https://www.youtube.com", mwebBrowseReq.header("X-Origin"))
        assertEquals("0", mwebBrowseReq.header("X-Goog-AuthUser"))

        val androidBrowseReq = executeThroughInterceptor(
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("X-YouTube-Client-Name", "3")
                .post("{\"browseId\":\"FEwhat_to_watch\"}".toRequestBody("application/json".toMediaType()))
                .build(),
        )
        assertNull(androidBrowseReq.header("Cookie"))
        assertNull(androidBrowseReq.header("Authorization"))
        assertNull(androidBrowseReq.header("Origin"))
        assertNull(androidBrowseReq.header("X-Origin"))
        assertNull(androidBrowseReq.header("X-Goog-AuthUser"))
    }

    @Test
    fun excludesAuthHeadersFromPlayerGoogleVideoWatchAndThirdPartyRequests() {
        Account.setSessionCookiesForTest("SAPISID=s1; SID=sid1; HSID=hsid1; SSID=ssid1")

        val excludedPosts = listOf(
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("X-YouTube-Client-Name", "1")
                .post("{\"videoId\":\"dQw4w9WgXcQ\"}".toRequestBody("application/json".toMediaType()))
                .build(),
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("X-YouTube-Client-Name", "101")
                .post("{\"videoId\":\"dQw4w9WgXcQ\"}".toRequestBody("application/json".toMediaType()))
                .build(),
            Request.Builder()
                .url("https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false&id=dQw4w9WgXcQ")
                .post("{\"videoId\":\"dQw4w9WgXcQ\"}".toRequestBody("application/json".toMediaType()))
                .build(),
            Request.Builder()
                .url("https://youtubei.googleapis.com/youtubei/v1/reel/reel_item_watch?prettyPrint=false&id=dQw4w9WgXcQ")
                .header("X-YouTube-Client-Name", "3")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build(),
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build(),
        )
        val excludedGets = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://r1---sn-4g5e6nsz.googlevideo.com/videoplayback?expire=1800000000",
            "https://googlevideo.com/generate_204",
            "https://example.com/api",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        ).map { Request.Builder().url(it).get().build() }

        for (candidate in excludedPosts + excludedGets) {
            val forwarded = executeThroughInterceptor(
                candidate,
                responseHeaders = listOf("Set-Cookie" to "IGNORED_NON_BROWSE=val; Path=/"),
            )
            assertNull("Expected no Cookie for ${candidate.url}", forwarded.header("Cookie"))
            assertNull("Expected no Authorization for ${candidate.url}", forwarded.header("Authorization"))
            assertNull("Expected no Origin for ${candidate.url}", forwarded.header("Origin"))
            assertNull("Expected no X-Origin for ${candidate.url}", forwarded.header("X-Origin"))
            assertNull("Expected no X-Goog-AuthUser for ${candidate.url}", forwarded.header("X-Goog-AuthUser"))
        }
        assertFalse(Account.cookieHeader().contains("IGNORED_NON_BROWSE"))
    }

    @Test
    fun prioritizesIpv4AddressesWhilePreservingIpv6Fallback() {
        val v6First = InetAddress.getByAddress(byteArrayOf(0x20, 0x01, 0x48, 0x60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x88.toByte(), 0x88.toByte()))
        val v4First = InetAddress.getByAddress(byteArrayOf(142.toByte(), 250.toByte(), 185.toByte(), 78))
        val v6Second = InetAddress.getByAddress(byteArrayOf(0x26, 0x07, 0xf8.toByte(), 0xb0.toByte(), 0x40, 0x05, 0x08, 0x0b, 0, 0, 0, 0, 0, 0, 0x20, 0x0e))
        val v4Second = InetAddress.getByAddress(byteArrayOf(172.toByte(), 217.toByte(), 14, 206.toByte()))

        val sorted = prioritizeIpv4(listOf(v6First, v4First, v6Second, v4Second))
        assertEquals(listOf(v4First, v4Second, v6First, v6Second), sorted)
        assertTrue(sorted[0] is Inet4Address && sorted[1] is Inet4Address)
        assertTrue(sorted[2] is Inet6Address && sorted[3] is Inet6Address)

        assertEquals(listOf(v6First, v6Second), prioritizeIpv4(listOf(v6First, v6Second)))
        assertEquals(listOf(v4First, v4Second), prioritizeIpv4(listOf(v4First, v4Second)))
        assertTrue(prioritizeIpv4(emptyList()).isEmpty())
    }

    @Test
    fun authSessionManagerParsesValidatesAndBuildsCookieRestoreEntries() {
        val parsed = AuthSessionManager.parseCookieHeader(
            "SAPISID=sapi_123; SID=sid_456; __Host-GAPS=gaps_val; __Secure-3PAPISID=p3_789; empty= ",
        )
        assertEquals(4, parsed.size)
        assertEquals("sapi_123", parsed["SAPISID"])
        assertFalse(parsed.containsKey("empty"))
        assertTrue(AuthSessionManager.hasAuthCookiePair(parsed))

        assertFalse(AuthSessionManager.hasAuthCookiePair(mapOf("PREF" to "tz=UTC", "YSC" to "abc")))
        assertFalse(AuthSessionManager.hasAuthCookiePair(mapOf("SAPISID" to "only_api_sid")))
        assertTrue(AuthSessionManager.hasAuthCookiePair(mapOf("__Secure-3PAPISID" to "p3", "__Secure-3PSID" to "psid3")))

        val entries = AuthSessionManager.buildCookieRestoreEntries(
            linkedMapOf(
                "__Host-GAPS" to "gaps_val",
                "SAPISID" to "sapi_123",
            ),
        )
        assertEquals(4, entries.size)
        val hostEntry = entries.first { it.second.startsWith("__Host-GAPS=") }
        assertFalse(hostEntry.second.contains("Domain="))
        assertTrue(hostEntry.second.contains("Path=/; Secure"))

        val domainEntry = entries.first { it.second.startsWith("SAPISID=") }
        assertTrue(domainEntry.second.contains("Domain=.youtube.com; Path=/; Secure"))
    }

    @Test
    fun skipsAuthHeadersWhenSignedOut() {
        Account.setSessionCookiesForTest(null)
        assertFalse(Account.hasSessionCookies())

        for (url in listOf(
            "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false&id=abc",
            "https://r1---sn-4g5e6nsz.googlevideo.com/videoplayback?expire=1800",
        )) {
            val builder = Request.Builder().url(url)
            val req = executeThroughInterceptor(
                if (url.contains("googlevideo")) builder.get().build()
                else builder.post("{\"browseId\":\"FEsubscriptions\"}".toRequestBody("application/json".toMediaType())).build(),
            )
            assertNull(req.header("Cookie"))
            assertNull(req.header("Authorization"))
            assertNull(req.header("Origin"))
            assertNull(req.header("X-Origin"))
            assertNull(req.header("X-Goog-AuthUser"))
        }
    }

    private fun executeThroughInterceptor(
        input: Request,
        responseHeaders: List<Pair<String, String>> = emptyList(),
    ): Request {
        val observed = AtomicReference<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor(youtubeAuthInterceptor)
            .addInterceptor { chain ->
                val forwarded = chain.request()
                observed.set(forwarded)
                Response.Builder()
                    .request(forwarded)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .apply { responseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                    .build()
            }
            .build()
        client.newCall(input).execute().close()
        return observed.get()
    }

    private fun sha1Hex(input: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
