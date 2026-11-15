package com.deniscerri.ytdl.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class InAppBrowserActivity : BaseActivity() {

    private lateinit var rootView: View
    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var urlEditText: EditText
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var adsBlockedBadge: TextView
    private lateinit var statusHostText: TextView
    private lateinit var bookmarkBtn: MaterialButton
    private lateinit var backBtn: MaterialButton
    private lateinit var forwardBtn: MaterialButton
    private lateinit var refreshBtn: MaterialButton
    private lateinit var closeBtn: MaterialButton
    private lateinit var mediaFab: ExtendedFloatingActionButton

    private lateinit var downloadViewModel: DownloadViewModel

    private val mainHandler = Handler(Looper.getMainLooper())
    private val streamRegistry = BrowserDownloadInterceptor.StreamRegistry()
    private val activePopupResolvers = mutableSetOf<WebView>()

    @Volatile
    private var currentPageUrl: String = DEFAULT_HOME_URL

    @Volatile
    private var currentPageTitle: String = ""

    @Volatile
    private var currentUserAgent: String = ""

    private var blockedAdsCount: Int = 0
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isDestroyedFlag = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_app_browser)

        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]

        rootView = findViewById(R.id.browser_root)
        webView = findViewById(R.id.browser_webview)
        fullscreenContainer = findViewById(R.id.browser_fullscreen_container)
        urlEditText = findViewById(R.id.et_browser_url)
        progressIndicator = findViewById(R.id.browser_progress)
        adsBlockedBadge = findViewById(R.id.tv_ads_blocked_badge)
        statusHostText = findViewById(R.id.tv_browser_status_host)
        bookmarkBtn = findViewById(R.id.btn_browser_bookmark)
        backBtn = findViewById(R.id.btn_browser_back)
        forwardBtn = findViewById(R.id.btn_browser_forward)
        refreshBtn = findViewById(R.id.btn_browser_refresh)
        closeBtn = findViewById(R.id.btn_browser_close)
        mediaFab = findViewById(R.id.fab_detected_media)

        val initialInput = intent.getStringExtra(EXTRA_URL)
        currentPageUrl = if (!initialInput.isNullOrBlank()) {
            BrowserSitesStore.normalizeInputToUrl(initialInput)
        } else {
            DEFAULT_HOME_URL
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            askPermissions(includeOptional = false)
        }

        setupWebView()
        setupControls()
        observeDuplicateDownloads()

        urlEditText.setText(currentPageUrl)
        updateNavigationButtons()
        updateBookmarkIcon(currentPageUrl)
        updateHostStatus(currentPageUrl)
        updateMediaFabBadge()

        webView.loadUrl(currentPageUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            currentUserAgent = userAgentString
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUri = request?.url ?: return null
                if (AdBlocker.isAdUrl(reqUri)) {
                    mainHandler.post { incrementAdsBlocked() }
                    return AdBlocker.emptyResponse()
                }

                val reqUrl = reqUri.toString()
                if (BrowserDownloadInterceptor.isPassiveStreamUrl(reqUrl)) {
                    val reqReferer = request.requestHeaders?.entries
                        ?.firstOrNull { it.key.equals("Referer", ignoreCase = true) }
                        ?.value
                        ?.takeIf { it.isNotBlank() }
                        ?: currentPageUrl

                    val added = streamRegistry.record(
                        url = reqUrl,
                        referer = reqReferer,
                        pageTitle = currentPageTitle,
                    )
                    if (added) {
                        mainHandler.post { updateMediaFabBadge() }
                    }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val reqUri = request?.url ?: return false
                return handleNavigationUri(reqUri)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank()) {
                    currentPageUrl = url
                    if (!urlEditText.hasFocus()) {
                        urlEditText.setText(url)
                    }
                    updateBookmarkIcon(url)
                    updateHostStatus(url)
                }
                updateNavigationButtons()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (!url.isNullOrBlank()) {
                    currentPageUrl = url
                    if (!urlEditText.hasFocus()) {
                        urlEditText.setText(url)
                    }
                    updateBookmarkIcon(url)
                    updateHostStatus(url)
                }
                updateNavigationButtons()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val resolvedUrl = url ?: view?.url
                val resolvedTitle = view?.title?.trim().orEmpty()
                if (!resolvedUrl.isNullOrBlank()) {
                    currentPageUrl = resolvedUrl
                    currentPageTitle = resolvedTitle
                    if (!urlEditText.hasFocus()) {
                        urlEditText.setText(resolvedUrl)
                    }
                    BrowserSitesStore.recordRecent(this@InAppBrowserActivity, resolvedUrl, resolvedTitle)
                    updateBookmarkIcon(resolvedUrl)
                    updateHostStatus(resolvedUrl)
                }
                progressIndicator.isVisible = false
                updateNavigationButtons()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99) {
                    progressIndicator.isVisible = true
                    progressIndicator.progress = newProgress
                } else {
                    progressIndicator.isVisible = false
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                val clean = title?.trim().orEmpty()
                if (clean.isNotEmpty()) {
                    currentPageTitle = clean
                    val pageUrl = view?.url ?: currentPageUrl
                    BrowserSitesStore.recordRecent(this@InAppBrowserActivity, pageUrl, clean)
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val hitResult = view?.hitTestResult
                val hitExtra = hitResult?.extra
                if (hitExtra != null && (
                        hitResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                            hitResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                        )
                ) {
                    val uri = runCatching { Uri.parse(hitExtra) }.getOrNull()
                    if (uri != null) {
                        if (AdBlocker.isAdUrl(uri)) {
                            incrementAdsBlocked()
                            return false
                        }
                        if (BrowserDownloadInterceptor.isDirectDownloadUrl(hitExtra)) {
                            handleDirectDownload(
                                url = hitExtra,
                                userAgent = currentUserAgent,
                                referer = currentPageUrl,
                                contentDisposition = null,
                                mimeType = null,
                            )
                            return false
                        }
                        if (BrowserDownloadInterceptor.isHttpUrl(hitExtra)) {
                            webView.loadUrl(hitExtra)
                            return false
                        }
                    }
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val resolverWebView = createPopupResolverWebView()
                transport.webView = resolverWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(view)
                fullscreenContainer.isVisible = true
            }

            override fun onHideCustomView() {
                exitFullscreenCustomView()
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            handleDirectDownload(
                url = url,
                userAgent = userAgent?.takeIf { it.isNotBlank() } ?: currentUserAgent,
                referer = currentPageUrl,
                contentDisposition = contentDisposition,
                mimeType = mimetype,
            )
        }

        webView.setOnLongClickListener {
            val hitResult = webView.hitTestResult
            val type = hitResult.type
            if (type != WebView.HitTestResult.SRC_ANCHOR_TYPE &&
                type != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            ) {
                return@setOnLongClickListener false
            }

            val fallbackUrl = hitResult.extra
            if (type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                val msg = Handler(Looper.getMainLooper()) { m ->
                    val anchorHref = m.data.getString("url")?.takeIf { it.isNotBlank() }
                        ?: fallbackUrl
                    if (!anchorHref.isNullOrBlank()) {
                        showLongPressLinkDialog(anchorHref)
                    }
                    true
                }.obtainMessage()
                webView.requestFocusNodeHref(msg)
                true
            } else if (!fallbackUrl.isNullOrBlank()) {
                showLongPressLinkDialog(fallbackUrl)
                true
            } else {
                false
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createPopupResolverWebView(): WebView {
        val resolver = WebView(this)
        activePopupResolvers.add(resolver)
        var handled = false

        fun disposeResolver() {
            if (resolver in activePopupResolvers) {
                activePopupResolvers.remove(resolver)
                resolver.stopLoading()
                resolver.destroy()
            }
        }

        resolver.settings.javaScriptEnabled = true
        resolver.settings.userAgentString = currentUserAgent
        resolver.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (AdBlocker.isAdUrl(request?.url)) {
                    mainHandler.post { incrementAdsBlocked() }
                    return AdBlocker.emptyResponse()
                }
                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUri = request?.url ?: return true
                if (!handled) {
                    handled = true
                    routePopupTarget(targetUri)
                    mainHandler.post { disposeResolver() }
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!handled && !url.isNullOrBlank() && url != "about:blank") {
                    handled = true
                    val targetUri = runCatching { Uri.parse(url) }.getOrNull()
                    if (targetUri != null) {
                        routePopupTarget(targetUri)
                    }
                    mainHandler.post { disposeResolver() }
                }
            }
        }

        resolver.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (!handled) {
                handled = true
                handleDirectDownload(
                    url = url,
                    userAgent = userAgent?.takeIf { it.isNotBlank() } ?: currentUserAgent,
                    referer = currentPageUrl,
                    contentDisposition = contentDisposition,
                    mimeType = mimetype,
                )
                mainHandler.post { disposeResolver() }
            }
        }

        mainHandler.postDelayed({ disposeResolver() }, POPUP_RESOLVER_TIMEOUT_MS)
        return resolver
    }

    private fun routePopupTarget(targetUri: Uri) {
        val targetUrl = targetUri.toString()
        if (AdBlocker.isAdUrl(targetUri)) {
            incrementAdsBlocked()
            return
        }
        if (!BrowserDownloadInterceptor.isHttpUrl(targetUrl)) {
            return
        }
        if (BrowserDownloadInterceptor.isDirectDownloadUrl(targetUrl)) {
            handleDirectDownload(
                url = targetUrl,
                userAgent = currentUserAgent,
                referer = currentPageUrl,
                contentDisposition = null,
                mimeType = null,
            )
            return
        }
        webView.loadUrl(targetUrl)
    }

    private fun handleNavigationUri(uri: Uri): Boolean {
        val urlStr = uri.toString()
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            return urlStr != "about:blank"
        }
        if (AdBlocker.isAdUrl(uri)) {
            incrementAdsBlocked()
            return true
        }
        if (BrowserDownloadInterceptor.isDirectDownloadUrl(urlStr)) {
            handleDirectDownload(
                url = urlStr,
                userAgent = currentUserAgent,
                referer = currentPageUrl,
                contentDisposition = null,
                mimeType = null,
            )
            return true
        }
        return false
    }

    private fun setupControls() {
        closeBtn.setOnClickListener { finish() }

        bookmarkBtn.setOnClickListener {
            val activeUrl = webView.url?.takeIf { it.isNotBlank() } ?: currentPageUrl
            val activeTitle = webView.title?.takeIf { it.isNotBlank() } ?: currentPageTitle
            val nowSaved = BrowserSitesStore.toggleSaved(this, activeUrl, activeTitle)
            updateBookmarkIcon(activeUrl)
            showSnackbar(
                getString(
                    if (nowSaved) R.string.browser_bookmarked_added
                    else R.string.browser_bookmarked_removed
                )
            )
        }

        backBtn.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }

        forwardBtn.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }

        refreshBtn.setOnClickListener {
            webView.reload()
        }

        urlEditText.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event != null &&
                event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                isEnter
            ) {
                val target = BrowserSitesStore.normalizeInputToUrl(urlEditText.text.toString())
                urlEditText.clearFocus()
                hideKeyboard(urlEditText)
                webView.loadUrl(target)
                true
            } else {
                false
            }
        }

        mediaFab.setOnClickListener {
            showCapturedMediaSheet()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (customView != null) {
                        exitFullscreenCustomView()
                    } else if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun observeDuplicateDownloads() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                downloadViewModel.alreadyExistsUiState.collectLatest { duplicates ->
                    if (duplicates.isNotEmpty()) {
                        downloadViewModel.alreadyExistsUiState.value = mutableListOf()
                        showSnackbar(getString(R.string.browser_already_in_queue))
                    }
                }
            }
        }
    }

    private fun handleDirectDownload(
        url: String,
        userAgent: String?,
        referer: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val result = BrowserDownloadInterceptor.enqueueDirectDownload(
            context = this,
            url = url,
            userAgent = userAgent,
            referer = referer,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
        )
        when (result) {
            is BrowserDownloadInterceptor.DirectDownloadResult.Started -> {
                val isArchive = BrowserDownloadInterceptor.isArchiveOrBinaryUrl(url)
                val snackbar = Snackbar.make(
                    rootView,
                    getString(R.string.browser_downloading_file, result.fileName),
                    Snackbar.LENGTH_LONG
                )
                if (!isArchive && (BrowserDownloadInterceptor.isDirectDownloadUrl(url) ||
                        BrowserDownloadInterceptor.isMediaMime(mimeType))
                ) {
                    snackbar.setAction(R.string.browser_open_eztube_downloader) {
                        BrowserDownloadInterceptor.openInEzTubeDownloader(
                            activity = this,
                            url = url,
                            title = result.fileName,
                            userAgent = userAgent,
                            referer = referer,
                            isDirectOrCapturedStream = true,
                        )
                    }
                }
                snackbar.show()
            }
            BrowserDownloadInterceptor.DirectDownloadResult.UnsupportedScheme -> {
                showSnackbar(getString(R.string.browser_blob_unsupported))
            }
            is BrowserDownloadInterceptor.DirectDownloadResult.Failed -> {
                showSnackbar(result.reason)
            }
            BrowserDownloadInterceptor.DirectDownloadResult.DuplicateSkipped -> Unit
        }
    }

    private fun showLongPressLinkDialog(linkUrl: String) {
        if (isFinishing || isDestroyedFlag) return

        val isArchive = BrowserDownloadInterceptor.isArchiveOrBinaryUrl(linkUrl)
        val isHttp = BrowserDownloadInterceptor.isHttpUrl(linkUrl)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.browser_download_link_title)
            .setMessage(linkUrl)
            .setNegativeButton(R.string.cancel, null)

        if (isArchive) {
            dialog.setPositiveButton(R.string.browser_download_direct) { _, _ ->
                handleDirectDownload(
                    url = linkUrl,
                    userAgent = currentUserAgent,
                    referer = currentPageUrl,
                    contentDisposition = null,
                    mimeType = null,
                )
            }
        } else {
            dialog.setPositiveButton(R.string.browser_open_eztube_downloader) { _, _ ->
                if (!isHttp) {
                    showSnackbar(getString(R.string.browser_blob_unsupported))
                } else {
                    BrowserDownloadInterceptor.openInEzTubeDownloader(
                        activity = this,
                        url = linkUrl,
                        title = currentPageTitle.takeIf { it.isNotBlank() },
                        userAgent = currentUserAgent,
                        referer = currentPageUrl,
                        isDirectOrCapturedStream = BrowserDownloadInterceptor.isDirectDownloadUrl(linkUrl),
                    )
                }
            }
            if (isHttp) {
                dialog.setNeutralButton(R.string.browser_download_direct) { _, _ ->
                    handleDirectDownload(
                        url = linkUrl,
                        userAgent = currentUserAgent,
                        referer = currentPageUrl,
                        contentDisposition = null,
                        mimeType = null,
                    )
                }
            }
        }
        dialog.show()
    }

    private fun showCapturedMediaSheet() {
        val sheet = BottomSheetDialog(this)
        val content = LayoutInflater.from(this).inflate(
            R.layout.bottom_sheet_captured_media,
            null,
            false
        )

        val sendPageBtn = content.findViewById<MaterialButton>(R.id.btn_send_page_ytdlp)
        val countLabel = content.findViewById<TextView>(R.id.tv_captured_count_label)
        val clearBtn = content.findViewById<MaterialButton>(R.id.btn_clear_captured_media)
        val emptyText = content.findViewById<TextView>(R.id.tv_no_media_detected)
        val recycler = content.findViewById<RecyclerView>(R.id.rv_captured_media)

        lateinit var adapter: CapturedMediaAdapter
        fun refreshSheetList() {
            val captured = streamRegistry.snapshot()
            countLabel.text = "${captured.size} ${getString(R.string.items)}"
            clearBtn.isVisible = captured.isNotEmpty()
            emptyText.isVisible = captured.isEmpty()
            recycler.isVisible = captured.isNotEmpty()
            adapter.submitList(captured)
        }

        adapter = CapturedMediaAdapter(
            onDirectDownload = { media ->
                sheet.dismiss()
                handleDirectDownload(
                    url = media.url,
                    userAgent = currentUserAgent,
                    referer = media.referer.ifBlank { currentPageUrl },
                    contentDisposition = null,
                    mimeType = null,
                )
            },
            onOpenInEzTube = { media ->
                sheet.dismiss()
                BrowserDownloadInterceptor.openInEzTubeDownloader(
                    activity = this,
                    url = media.url,
                    title = media.pageTitle.takeIf { it.isNotBlank() },
                    userAgent = currentUserAgent,
                    referer = media.referer.ifBlank { currentPageUrl },
                    isDirectOrCapturedStream = true,
                )
            },
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        refreshSheetList()

        sendPageBtn.setOnClickListener {
            sheet.dismiss()
            val pageUrl = webView.url?.takeIf { it.isNotBlank() } ?: currentPageUrl
            val pageTitle = webView.title?.takeIf { it.isNotBlank() } ?: currentPageTitle
            BrowserDownloadInterceptor.openInEzTubeDownloader(
                activity = this,
                url = pageUrl,
                title = pageTitle.takeIf { it.isNotBlank() },
                userAgent = currentUserAgent,
                referer = pageUrl,
                isDirectOrCapturedStream = false,
            )
        }

        clearBtn.setOnClickListener {
            streamRegistry.clear()
            updateMediaFabBadge()
            refreshSheetList()
        }

        sheet.setContentView(content)
        sheet.show()
    }

    private fun incrementAdsBlocked() {
        if (isDestroyedFlag) return
        blockedAdsCount++
        adsBlockedBadge.isVisible = true
        adsBlockedBadge.text = "-$blockedAdsCount"
        updateHostStatus(currentPageUrl)
    }

    private fun updateMediaFabBadge() {
        if (isDestroyedFlag) return
        val count = streamRegistry.count()
        mediaFab.text = if (count > 0) count.toString() else getString(R.string.download)
    }

    private fun updateBookmarkIcon(url: String?) {
        val saved = BrowserSitesStore.isSaved(this, url)
        bookmarkBtn.setIconResource(
            if (saved) R.drawable.ic_browser_bookmark_filled
            else R.drawable.ic_browser_bookmark_border
        )
    }

    private fun updateNavigationButtons() {
        val canBack = webView.canGoBack()
        val canForward = webView.canGoForward()
        backBtn.isEnabled = canBack
        backBtn.alpha = if (canBack) 1f else 0.35f
        forwardBtn.isEnabled = canForward
        forwardBtn.alpha = if (canForward) 1f else 0.35f
    }

    private fun updateHostStatus(url: String?) {
        val host = url?.let { runCatching { Uri.parse(it).host?.removePrefix("www.") }.getOrNull() }
        statusHostText.text = if (blockedAdsCount > 0 && !host.isNullOrBlank()) {
            "$host • ${getString(R.string.browser_ads_blocked, blockedAdsCount)}"
        } else {
            host ?: ""
        }
    }

    private fun exitFullscreenCustomView() {
        val active = customView ?: return
        fullscreenContainer.removeView(active)
        fullscreenContainer.isVisible = false
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun showSnackbar(message: String) {
        if (!isFinishing && !isDestroyedFlag) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        isDestroyedFlag = true
        mainHandler.removeCallbacksAndMessages(null)
        exitFullscreenCustomView()
        activePopupResolvers.toList().forEach { popup ->
            runCatching {
                popup.stopLoading()
                popup.destroy()
            }
        }
        activePopupResolvers.clear()

        (webView.parent as? ViewGroup)?.removeView(webView)
        runCatching {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val DEFAULT_HOME_URL = "https://www.google.com"
        private const val POPUP_RESOLVER_TIMEOUT_MS = 8000L
    }
}
