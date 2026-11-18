package com.clipflow.app.player

import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Google's page in the app's language (nothing on it is parsed); YouTube's landing stays English.
private fun signInUrl() =
    "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&hl=${AppLanguage.webTag}" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26hl%3Den%26next%3D%252F"

private fun isYouTubeRedirectUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val scheme = parsed.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false
    val host = parsed.host?.lowercase()?.trimEnd('.') ?: return false
    val isYoutubeHost = (host == "youtube.com" || host.endsWith(".youtube.com")) &&
        host != "accounts.google.com" &&
        host != "accounts.youtube.com" &&
        host != "consent.youtube.com"
    if (!isYoutubeHost) return false
    // Do NOT intercept mid-authentication during Google's multi-step redirect chain (CheckCookie, ServiceLogin)
    if (url.contains("CheckCookie", ignoreCase = true) || url.contains("ServiceLogin", ignoreCase = true)) {
        return false
    }
    return true
}

private fun mergeCookiesFromUrls(
    cookies: CookieManager,
    urls: List<String>,
): Pair<String, LinkedHashMap<String, String>> {
    runCatching { cookies.flush() }
    val merged = linkedMapOf<String, String>()
    for (url in urls) {
        if (url.isBlank()) continue
        val raw = runCatching { cookies.getCookie(url) }.getOrNull().orEmpty()
        if (raw.isBlank()) continue
        raw.split(';').forEach { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@forEach
            val name = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                merged[name] = value
            }
        }
    }
    val header = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    return header to merged
}

private fun hasAuthCookieToken(cookieMap: Map<String, String>): Boolean =
    cookieMap.containsKey("SID") ||
        cookieMap.containsKey("SAPISID") ||
        cookieMap.containsKey("LOGIN_INFO") ||
        cookieMap.containsKey("__Secure-1PSID") ||
        cookieMap.containsKey("__Secure-3PSID") ||
        cookieMap.containsKey("__Secure-1PAPISID") ||
        cookieMap.containsKey("__Secure-3PAPISID")

/**
 * Google's own sign-in page. ClipFlow never sees the password; once the page lands back on
 * YouTube signed in, cookies are persisted synchronously into [AuthSessionManager]
 * and kept synced with Android's [CookieManager] across app restarts.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentWebUrl by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun saveSessionAndClose(
        header: String,
        sourceUrl: String?,
        unconditional: Boolean,
    ) {
        if (finishing) return
        error = null
        finishing = true
        runCatching { CookieManager.getInstance().flush() }
        val parsedCount = AuthSessionManager.parseCookieHeader(header).size
        authLog(
            Log.INFO,
            "SignInScreen: saving session on ${runCatching { Uri.parse(sourceUrl).host }.getOrNull()} (cookies=$parsedCount, unconditional=$unconditional)",
        )
        AuthSessionManager.saveSession(header)
        runCatching {
            webViewRef?.stopLoading()
            webViewRef?.loadUrl("about:blank")
        }
        scope.launch {
            if (header.isBlank()) {
                if (unconditional) onDone() else onCancel()
                return@launch
            }
            runCatching { Account.completeSignIn(header) }
                .onSuccess {
                    // Keep cookies persisted and synced in CookieManager for cold restarts
                    AuthSessionManager.restoreCookiesToCookieManager()
                    Toast.makeText(
                        context,
                        context.getString(R.string.ui_signed_in_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDone()
                }
                .onFailure { err ->
                    authLog(Log.ERROR, "SignInScreen: completeSignIn failed", err)
                    if (unconditional) {
                        AuthSessionManager.saveSession(header)
                        AuthSessionManager.restoreCookiesToCookieManager()
                        onDone()
                    } else {
                        finishing = false
                        error = str(R.string.ui_sign_in_error)
                    }
                }
        }
    }

    fun tryAutoCompleteSignIn(candidateUrl: String?): Boolean {
        if (finishing) return true
        val activeUrl = candidateUrl?.takeIf { it.isNotBlank() } ?: webViewRef?.url ?: currentWebUrl
        if (!isYouTubeRedirectUrl(activeUrl)) return false
        val cookieManager = CookieManager.getInstance()
        runCatching { cookieManager.flush() }
        val (header, map) = mergeCookiesFromUrls(
            cookieManager,
            listOf(
                "https://accounts.google.com",
                "https://m.youtube.com",
                "https://www.youtube.com",
            ),
        )
        if (header.isBlank() || !hasAuthCookieToken(map)) return false
        saveSessionAndClose(header = header, sourceUrl = activeUrl, unconditional = false)
        return true
    }

    fun completeUnconditionally() {
        if (finishing) return
        val activeUrl = webViewRef?.url?.takeIf { it.isNotBlank() } ?: currentWebUrl
        val cookieManager = CookieManager.getInstance()
        val urls = buildList {
            add("https://accounts.google.com")
            add("https://www.google.com")
            add("https://google.com")
            add("https://youtube.com")
            add("https://m.youtube.com")
            add("https://www.youtube.com")
            if (!activeUrl.isNullOrBlank()) add(activeUrl)
        }
        val (header, _) = mergeCookiesFromUrls(cookieManager, urls)
        saveSessionAndClose(header = header, sourceUrl = activeUrl, unconditional = true)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { CookieManager.getInstance().flush() }
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
            }
            webViewRef = null
        }
    }

    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).pressable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, stringResource(R.string.ui_cancel))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ui_sign_in_title),
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = c.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.ui_sign_in_line),
                        color = c.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .pressable(enabled = !finishing, onClick = { completeUnconditionally() })
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (finishing) c.primary.copy(alpha = 0.5f) else c.primary)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Done",
                    color = c.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    WebView(ctx).apply {
                        webViewRef = this
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        // Clean, modern mobile Chrome user-agent so Google accepts credentials cleanly on first attempt
                        val defaultUa = WebSettings.getDefaultUserAgent(ctx)
                        settings.userAgentString = if (defaultUa.contains("Chrome/")) {
                            defaultUa.replace("; wv", "")
                        } else {
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (request.isForMainFrame) {
                                    currentWebUrl = request.url?.toString()
                                }
                                // Always let Chromium follow redirects so final Set-Cookie headers commit.
                                return false
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                currentWebUrl = url
                                return false
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                val activeUrl = url?.takeIf { it.isNotBlank() } ?: view.url
                                if (!activeUrl.isNullOrBlank()) currentWebUrl = activeUrl
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                val activeUrl = url?.takeIf { it.isNotBlank() } ?: view.url
                                if (!activeUrl.isNullOrBlank()) currentWebUrl = activeUrl
                                // Do not intercept during redirect chain: wait until onPageFinished reaches final youtube.com
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                val activeUrl = url.takeIf { it.isNotBlank() } ?: view.url
                                if (!activeUrl.isNullOrBlank()) currentWebUrl = activeUrl
                                if (isYouTubeRedirectUrl(activeUrl)) {
                                    runCatching { CookieManager.getInstance().flush() }
                                    tryAutoCompleteSignIn(activeUrl)
                                }
                            }
                        }
                        // Clear stale session cookies cleanly before fresh login to avoid race conditions
                        cookies.removeSessionCookies {
                            cookies.flush()
                            loadUrl(signInUrl())
                        }
                    }
                },
            )
            if (finishing || error != null) {
                Column(
                    Modifier.fillMaxSize().background(c.surface).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    if (error == null) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.ui_finishing_sign_in), fontWeight = FontWeight.Bold)
                    } else {
                        Text(error.orEmpty(), textAlign = TextAlign.Center, color = c.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        PillButton(stringResource(R.string.ui_close), onCancel)
                    }
                }
            }
        }
    }
}

/** Opens ClipFlow's Downloader Hub (downloads, queue, history, yt-dlp settings). */
@Composable
fun DownloadsAction() {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressable { InAppDownloadBridge.openDownloaderHub(context) }
            .clip(CircleShape)
            .background(c.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.FolderOpen,
            contentDescription = stringResource(R.string.ui_downloads),
            tint = c.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Account button in the headers: custom avatar / Google photo / initial when signed in, else account icon. */
@Composable
fun AccountAction(onClick: () -> Unit) {
    val profile = Account.profile
    val hasLocalOverride = LocalAvatarStore.mode != LocalAvatarStore.Mode.Default
    if (profile == null && !hasLocalOverride) {
        CircleAction(Icons.Rounded.AccountCircle, stringResource(R.string.ui_sign_in), onClick)
    } else {
        Box(Modifier.size(44.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
            AccountAvatar(profile, size = 40)
        }
    }
}
