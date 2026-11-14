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
import android.os.SystemClock
import android.util.Log
import android.webkit.WebResourceRequest
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Google's page in the app's language (nothing on it is parsed); YouTube's landing stays English.
private fun signInUrl() =
    "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&hl=${AppLanguage.webTag}" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26hl%3Den%26next%3D%252F"

private fun isYouTubeLandingHost(host: String): Boolean {
    if (host == "accounts.youtube.com" || host == "consent.youtube.com") return false
    return host == "youtube.com" || host.endsWith(".youtube.com")
}

private fun isPendingAuthHandoff(url: String?, allowSigninPath: Boolean): Boolean {
    if (url.isNullOrBlank()) return true
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return true
    val host = parsed.host?.lowercase() ?: return true
    if (!isYouTubeLandingHost(host)) {
        return true
    }
    if (!allowSigninPath && parsed.path.orEmpty().startsWith("/signin")) {
        return true
    }
    return false
}

private fun collectYouTubeCookies(cookies: CookieManager, currentUrl: String? = null): String {
    runCatching { cookies.flush() }
    val merged = linkedMapOf<String, String>()
    val domains = buildList {
        add("https://www.youtube.com")
        add("https://m.youtube.com")
        add("https://youtube.com")
        if (!currentUrl.isNullOrBlank() && !isPendingAuthHandoff(currentUrl, allowSigninPath = true)) {
            add(currentUrl)
        }
    }
    for (domain in domains) {
        val raw = runCatching { cookies.getCookie(domain) }.getOrNull().orEmpty()
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
    return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

/**
 * Google's own sign-in page. ClipFlow never sees the password; once the page lands back on
 * YouTube signed in, the youtube.com cookies are persisted synchronously into [AuthSessionManager]
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
    var currentPageFinished by remember { mutableStateOf(false) }
    var pageCommitted by remember { mutableStateOf(false) }
    var landedOnYouTubeAtMs by remember { mutableStateOf(0L) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun markUrlNavigation(url: String?, committed: Boolean, finished: Boolean = false) {
        currentWebUrl = url
        pageCommitted = committed
        currentPageFinished = finished
        landedOnYouTubeAtMs = if (!isPendingAuthHandoff(url, allowSigninPath = finished)) {
            landedOnYouTubeAtMs.takeIf { it != 0L } ?: SystemClock.elapsedRealtime()
        } else {
            0L
        }
    }

    fun tryCompleteSignIn(candidateUrl: String?, allowSigninPath: Boolean, requireCommit: Boolean = true): Boolean {
        if (finishing) return true
        val activeUrl = candidateUrl ?: webViewRef?.url ?: currentWebUrl
        if (isPendingAuthHandoff(activeUrl, allowSigninPath = allowSigninPath)) return false
        val timedFallback = landedOnYouTubeAtMs != 0L && (SystemClock.elapsedRealtime() - landedOnYouTubeAtMs) >= 1_500L
        if (requireCommit && !pageCommitted && !currentPageFinished && !timedFallback) {
            return false
        }
        val cookieManager = CookieManager.getInstance()
        val header = collectYouTubeCookies(cookieManager, activeUrl)
        if (!Account.looksSignedIn(header)) return false
        finishing = true
        val parsedCount = AuthSessionManager.parseCookieHeader(header).size
        authLog(
            Log.INFO,
            "SignInScreen: captured YouTube session on ${runCatching { Uri.parse(activeUrl).path }.getOrNull()} (cookies=$parsedCount)",
        )
        AuthSessionManager.saveSession(header)
        runCatching {
            webViewRef?.stopLoading()
            webViewRef?.loadUrl("about:blank")
        }
        scope.launch {
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
                    finishing = false
                    error = str(R.string.ui_sign_in_error)
                }
        }
        return true
    }

    // Reliable 500ms cookie-polling observer while the Sign-In WebView is visible
    LaunchedEffect(Unit) {
        while (!finishing && error == null) {
            delay(500L)
            if (!finishing && error == null) {
                tryCompleteSignIn(
                    candidateUrl = webViewRef?.url ?: currentWebUrl,
                    allowSigninPath = currentPageFinished,
                    requireCommit = true,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { CookieManager.getInstance().flush() }
        }
    }

    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pressable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, stringResource(R.string.ui_cancel))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_sign_in_title), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = c.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui_sign_in_line), color = c.onSurfaceVariant, fontSize = 12.sp)
                }
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
                        cookies.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        // Google refuses sign-in from user agents that announce an embedded
                        // WebView ("; wv"); this is the same Chrome engine without the marker.
                        settings.userAgentString = WebSettings.getDefaultUserAgent(ctx).replace("; wv", "")
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                markUrlNavigation(request.url?.toString(), committed = false, finished = false)
                                // Always let Chromium follow redirects so final Set-Cookie headers commit.
                                return false
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                markUrlNavigation(url, committed = false, finished = false)
                                return false
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                markUrlNavigation(url, committed = false, finished = false)
                            }

                            override fun onPageCommitVisible(view: WebView, url: String?) {
                                markUrlNavigation(url, committed = true, finished = currentPageFinished)
                                tryCompleteSignIn(url, allowSigninPath = false, requireCommit = false)
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                markUrlNavigation(url, committed = true, finished = currentPageFinished)
                                tryCompleteSignIn(url, allowSigninPath = false, requireCommit = false)
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                markUrlNavigation(url, committed = true, finished = true)
                                tryCompleteSignIn(url, allowSigninPath = true, requireCommit = false)
                            }
                        }
                        // Start clean when signed out so a half-finished earlier attempt doesn't linger,
                        // and start loading only after the async clear finishes.
                        if (!Account.signedIn) {
                            cookies.removeAllCookies { post { if (!finishing) loadUrl(signInUrl()) } }
                        } else {
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

/** Account button in the headers: the avatar when signed in, a person icon otherwise. */
@Composable
fun AccountAction(onClick: () -> Unit) {
    val profile = Account.profile
    if (profile == null) {
        CircleAction(Icons.Rounded.AccountCircle, stringResource(R.string.ui_sign_in), onClick)
    } else {
        Box(Modifier.size(44.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
            ChannelAvatar(profile.avatar, profile.name, size = 40)
        }
    }
}
