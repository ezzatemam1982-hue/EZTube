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
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

class YouTubeAuthInterceptorTest {

    @After
    fun tearDown() {
        Account.setSessionCookiesForTest(null)
    }

    @Test
    fun netClientRegistersYouTubeAuthInterceptor() {
        assertTrue(youtubeAuthInterceptor in Net.client.interceptors)
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
    fun injectsHeadersOnMobileYouTubeHostAndSkipsNonYouTubeOrNonWebClients() {
        Account.setSessionCookiesForTest("SAPISID=s1; SID=sid1; HSID=hsid1; SSID=ssid1")

        val mReq = executeThroughInterceptor(
            Request.Builder().url("https://m.youtube.com/watch?v=dQw4w9WgXcQ").get().build(),
        )
        assertNotNull(mReq.header("Cookie"))
        assertNotNull(mReq.header("Authorization"))
        assertEquals("https://www.youtube.com", mReq.header("X-Origin"))
        assertEquals("0", mReq.header("X-Goog-AuthUser"))

        val nonYtReq = executeThroughInterceptor(
            Request.Builder().url("https://example.com/api").get().build(),
        )
        assertNull(nonYtReq.header("Cookie"))
        assertNull(nonYtReq.header("Authorization"))
        assertNull(nonYtReq.header("X-Origin"))
        assertNull(nonYtReq.header("X-Goog-AuthUser"))

        val mwebReq = executeThroughInterceptor(
            Request.Builder()
                .url("https://m.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("X-YouTube-Client-Name", "2")
                .post("{\"browseId\":\"FEwhat_to_watch\"}".toRequestBody("application/json".toMediaType()))
                .build(),
        )
        assertNotNull(mwebReq.header("Cookie"))
        assertNotNull(mwebReq.header("Authorization"))

        val visionOsReq = executeThroughInterceptor(
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("X-YouTube-Client-Name", "101")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build(),
        )
        assertNull(visionOsReq.header("Cookie"))
        assertNull(visionOsReq.header("Authorization"))

        val androidReq = executeThroughInterceptor(
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("X-YouTube-Client-Name", "3")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build(),
        )
        assertNull(androidReq.header("Cookie"))
        assertNull(androidReq.header("Authorization"))
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

        val req = executeThroughInterceptor(
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .post("{\"browseId\":\"FEsubscriptions\"}".toRequestBody("application/json".toMediaType()))
                .build(),
        )
        assertNull(req.header("Cookie"))
        assertNull(req.header("Authorization"))
        assertNull(req.header("X-Origin"))
        assertNull(req.header("X-Goog-AuthUser"))
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
