package com.clipflow.app.player

import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** A pageable list of videos (the account's home, subscriptions, history; a playlist; a channel's tab). */
@Stable
class FeedState(
    private val scope: CoroutineScope,
    /** Warm up the stream data of the first videos once a page lands (Home, Subscriptions). */
    private val prefetchTop: Int = 0,
    /**
     * Home: every refresh should bring different suggestions, as in the YouTube app. The
     * signed-in web feed returns much the same first page each time, so a refresh reads
     * on into the recommendation stream and keeps only videos not yet shown this session.
     */
    private val freshOnRefresh: Boolean = false,
    private val name: String = "Feed",
    private val loader: suspend (next: String?) -> FeedPage,
) {
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Optional status message reported by YouTube when a feed has 0 items (e.g. history disabled nudge). */
    var statusMessage by mutableStateOf<String?>(null)
        private set
    /** Bumped when a refresh lands; lists scroll back to the top on it. */
    var generation by mutableStateOf(0)
        private set
    /** A pull/button refresh is running (the first load shows its own spinner). */
    var refreshing by mutableStateOf(false)
        private set
    /** The category chips YouTube put over the feed's first page (Home); empty when it sent none. */
    var chips by mutableStateOf<List<FeedChip>>(emptyList())
        private set
    /** The chip whose feed is shown; null = the plain feed (the chip without a token, "All"). */
    var chip by mutableStateOf<FeedChip?>(null)
        private set
    private var next: String? = null
    private var loaded = false
    /** When the first page shown last landed (elapsedRealtime); 0 before. */
    private var landedAt = 0L
    /** Ids already put in front of the user this session (fresh-refresh feeds only). */
    private val shown = HashSet<String>()
    private var job: Job? = null
    val hasMore get() = next != null
    /** The next page's token (for a pager that pages on by itself, like Shorts over a channel's list). */
    val nextToken get() = next
    /** Not asked for yet: about to load (a list shows its spinner from the first frame, never nothing). */
    val waiting get() = !loaded
    /** Loaded, and YouTube sent no videos at all (no error either). */
    val loadedEmpty get() = loaded && !loading && error == null && videos.isEmpty()

    fun ensureLoaded() { if (!loaded && !loading) load(fresh = false, showIndicator = false) }

    /** Ensures a feed returning to screen loads if never requested or retries if it failed / came back empty. */
    fun showOrRetry() {
        if (loading) return
        if (!loaded || videos.isEmpty() || error != null) {
            load(fresh = loaded, showIndicator = videos.isNotEmpty())
        }
    }

    /** The first page shown landed more than [ms] ago (false while nothing has). */
    fun olderThan(ms: Long) = landedAt != 0L && monotonicMs() - landedAt > ms

    /** Pull/button refresh: reloads what's shown, the chosen chip's feed included. */
    fun refresh(showIndicator: Boolean = true) = load(fresh = true, showIndicator = showIndicator)

    /**
     * Shows [pick]'s feed from its first page. The plain chip ("All"), or the chip already
     * shown, goes back to the plain feed. The chips row stays as it is meanwhile.
     */
    fun selectChip(pick: FeedChip) {
        chip = if (pick.token == null || pick.title == chip?.title) null else pick
        videos = emptyList(); next = null; statusMessage = null
        load(fresh = false, showIndicator = false)
    }

    /**
     * Loads the first page of what's chosen. The plain feed takes its chips from the answer;
     * [fresh]: a refresh of a fresh-on-refresh feed reads on to unseen videos. A chip's feed is
     * a continuation of the plain one, so its first page is the loader's page for its token.
     */
    private fun load(fresh: Boolean, showIndicator: Boolean = true) {
        loaded = true
        loading = true
        refreshing = showIndicator || videos.isNotEmpty()
        error = null
        statusMessage = null
        job?.cancel()
        val pick = chip
        feedLog(Log.INFO, "$name: loading feed (fresh=$fresh, current=${videos.size}, chip=${pick?.title})")
        job = scope.launch {
            val result = runCatching {
                when {
                    pick?.token != null -> loader(pick.token)
                    fresh && freshOnRefresh && shown.isNotEmpty() -> loadFresh()
                    else -> loader(null)
                }
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (!isActive) return@launch // a newer load (another chip) took over
            result
                .onSuccess {
                    videos = it.videos; next = it.next; generation++
                    statusMessage = it.statusMessage
                    landedAt = monotonicMs()
                    if (pick == null) {
                        chips = it.chips
                        if (freshOnRefresh) it.videos.forEach { v -> shown += v.id }
                    }
                    if (it.videos.isEmpty()) {
                        feedLog(Log.WARN, "$name: loaded 0 videos (fresh=$fresh, chip=${pick?.title}, status=${it.statusMessage})")
                    } else {
                        feedLog(Log.INFO, "$name: loaded ${it.videos.size} videos (fresh=$fresh, hasMore=${it.next != null})")
                    }
                    it.videos.take(prefetchTop).forEach { v -> StreamCache.prefetch(v.id) }
                }
                .onFailure { err ->
                    feedLog(Log.ERROR, "$name: feed load failed (fresh=$fresh, current=${videos.size}, chip=${pick?.title})", err)
                    error = friendly(err)
                }
            loading = false
            refreshing = false
        }
    }

    fun loadMore() {
        val token = next ?: return
        if (loading) return
        loading = true
        val pick = chip
        job = scope.launch {
            val result = runCatching { loader(token) }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (!isActive) return@launch
            result
                .onSuccess { page ->
                    val seen = videos.mapTo(HashSet()) { it.id }
                    val added = page.videos.filter { seen.add(it.id) }
                    videos = videos + added
                    if (freshOnRefresh && pick == null) added.forEach { v -> shown += v.id }
                    next = page.next
                }
                .onFailure { err ->
                    feedLog(Log.ERROR, "$name: loadMore failed", err)
                    error = friendly(err)
                }
            loading = false
        }
    }

    /**
     * Unseen videos only: reads up to [FRESH_PAGES] pages into the feed until there are
     * [FRESH_TARGET]. If YouTube has nothing new at all, falls back to the plain first page.
     */
    private suspend fun loadFresh(): FeedPage {
        val first = loader(null)
        val fresh = LinkedHashMap<String, VideoItem>()
        var token = first.next
        first.videos.filter { it.id !in shown }.forEach { fresh[it.id] = it }
        var pages = 1
        while (fresh.size < FRESH_TARGET && token != null && pages < FRESH_PAGES) {
            val page = runCatching { loader(token) }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    feedLog(Log.WARN, "$name: loadFresh continuation page ${pages + 1} failed", err)
                }
                .getOrNull() ?: break
            page.videos.filter { it.id !in shown }.forEach { fresh.putIfAbsent(it.id, it) }
            token = page.next
            pages++
        }
        if (fresh.isEmpty()) return first
        return FeedPage(fresh.values.toList(), token, first.chips, first.statusMessage)
    }

    fun reset() {
        job?.cancel()
        shown.clear()
        videos = emptyList(); next = null; loaded = false; loading = false; refreshing = false
        error = null; statusMessage = null; chips = emptyList(); chip = null; landedAt = 0L
    }

    private companion object {
        const val FRESH_TARGET = 16
        const val FRESH_PAGES = 5
    }

    private fun monotonicMs(): Long =
        runCatching { android.os.SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }

    private fun friendly(t: Throwable) = when (t) {
        is NotSignedInException -> str(R.string.ui_signed_out_error)
        is YouTubeRefusedException -> t.localizedMessage
        else -> t.message?.takeIf { it.startsWith("YouTube") } ?: YouTube.friendlyError(t)
    }
}

/**
 * Everything the signed-in tabs show, kept above the tab switcher so it survives tab changes.
 * Subscriptions and its channel row are read ahead in the background once Home is in
 * ([warmUp]), and the row is kept on disk ([CHANNELS_PREFS]) so it's there from the first frame
 * on the next start: both used to wait for the tab, the row a few seconds after the videos.
 */
@Stable
class LibraryData(scope: CoroutineScope, context: Context) {
    val home = FeedState(scope, prefetchTop = 2, freshOnRefresh = true, name = "Home") { InnerTube.home(it) }
    val subscriptions = FeedState(scope, prefetchTop = 2, name = "Subscriptions") { InnerTube.subscriptionsFeed(it) }
    val history = FeedState(scope, name = "History") { InnerTube.history(it) }
    private val prefs = context.getSharedPreferences(CHANNELS_PREFS, Context.MODE_PRIVATE)
    var channels by mutableStateOf(readChannels())
        private set
    var playlists by mutableStateOf<List<PlaylistRef>>(emptyList())
        private set
    private val scope = scope
    private var channelsJob: Job? = null
    private var playlistsJob: Job? = null
    var refreshingChannels by mutableStateOf(false)
        private set
    var refreshingPlaylists by mutableStateOf(false)
        private set
    private var homeShown = false
    /** Subscriptions has been on screen since it was last (re)loaded. */
    private var subscriptionsShown = false

    /** Once per session (the kept row shows meanwhile); again only after [reset]. */
    fun ensureChannels() {
        if (channelsJob != null) return
        fetchChannels(showIndicator = false)
    }

    /** Pull-to-refresh on Subscriptions: reloads subscribed channels so newly subscribed ones appear. */
    fun refreshChannels(): Job = fetchChannels(showIndicator = true)

    private fun fetchChannels(showIndicator: Boolean): Job {
        channelsJob?.cancel()
        if (showIndicator) refreshingChannels = true
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = runCatching { InnerTube.channels() }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (!isActive) return@launch
            result
                .onSuccess { channels = it; writeChannels(it) }
                .onFailure { err ->
                    feedLog(Log.ERROR, "Channels: fetch failed", err)
                    if (!showIndicator) channelsJob = null // the next look at the tab tries again
                }
            refreshingChannels = false
        }
        channelsJob = job
        job.start()
        return job
    }

    fun loadPlaylists(showIndicator: Boolean = false): Job {
        playlistsJob?.cancel()
        if (showIndicator) refreshingPlaylists = true
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = runCatching { InnerTube.playlists() }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (!isActive) return@launch
            result
                .onSuccess { playlists = it }
                .onFailure { err -> feedLog(Log.ERROR, "Playlists: fetch failed", err) }
            refreshingPlaylists = false
        }
        playlistsJob = job
        job.start()
        return job
    }

    /**
     * Signed in: reads Subscriptions and its channel row ahead, once Home's first page is in (so
     * it never competes with what's on screen), or after [WARM_UP_WAIT_MS] if Home isn't asked for.
     */
    suspend fun warmUp() {
        withTimeoutOrNull(WARM_UP_WAIT_MS) { snapshotFlow { home.waiting || home.loading }.first { !it } }
        if (!Account.signedIn) return
        subscriptions.ensureLoaded()
        ensureChannels()
    }

    /** Ensures Home loads on screen open and recovers if an earlier background fetch failed or returned empty. */
    fun showHome() {
        val failedOrEmpty = !home.loading && (home.error != null || home.videos.isEmpty())
        if (!homeShown && (failedOrEmpty || home.olderThan(STALE_AHEAD_MS))) {
            home.refresh(showIndicator = home.videos.isNotEmpty())
        } else {
            home.showOrRetry()
        }
        homeShown = true
    }

    /**
     * The Subscriptions tab is on screen. A page read ahead long ago is reloaded before it's seen,
     * and so is one whose read-ahead failed or returned empty.
     */
    fun showSubscriptions() {
        val failedOrEmpty = !subscriptions.loading && (subscriptions.error != null || subscriptions.videos.isEmpty())
        if (!subscriptionsShown && (failedOrEmpty || subscriptions.olderThan(STALE_AHEAD_MS))) {
            subscriptions.refresh(showIndicator = subscriptions.videos.isNotEmpty())
        } else {
            subscriptions.showOrRetry()
        }
        subscriptionsShown = true
        ensureChannels()
    }

    fun playlistFeed(id: String) = FeedState(scope, name = "Playlist($id)") { InnerTube.playlist(id, it) }

    fun reset() {
        home.reset(); subscriptions.reset(); history.reset()
        channelsJob?.cancel(); channelsJob = null; homeShown = false; subscriptionsShown = false
        playlistsJob?.cancel(); playlistsJob = null
        refreshingChannels = false; refreshingPlaylists = false
        channels = emptyList(); playlists = emptyList()
        prefs.edit().remove(KEY_CHANNELS).apply()
    }

    /** Invalidates stale cached state after sign-in and immediately loads all authenticated feeds. */
    fun reloadSignedIn() {
        reset()
        if (!Account.signedIn) return
        home.refresh(showIndicator = false)
        subscriptions.refresh(showIndicator = false)
        refreshChannels()
        history.refresh(showIndicator = false)
        loadPlaylists(showIndicator = false)
    }

    private fun readChannels(): List<ChannelRef> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_CHANNELS, null) ?: return emptyList())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ChannelRef(o.getString("id"), o.optString("name"), o.optString("avatar").ifBlank { null })
        }
    }.getOrElse { prefs.edit().remove(KEY_CHANNELS).apply(); emptyList() }

    private fun writeChannels(list: List<ChannelRef>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar ?: "")) }
        prefs.edit().putString(KEY_CHANNELS, arr.toString()).apply()
    }

    private companion object {
        const val CHANNELS_PREFS = "library"
        const val KEY_CHANNELS = "channels"
        const val WARM_UP_WAIT_MS = 6_000L
        /** A Subscriptions page read ahead this long before the tab is first opened is read again. */
        const val STALE_AHEAD_MS = 10 * 60_000L
    }
}

/**
 * A list state that animates back to the top whenever [signal] changes (tapping the
 * already-selected tab). The value it was created with is ignored, so merely showing a
 * tab never scrolls it.
 */
@Composable
fun rememberScrollToTop(signal: Int): LazyListState {
    val state = rememberLazyListState()
    val initial = remember { signal }
    LaunchedEffect(signal) { if (signal != initial) state.animateScrollToItem(0) }
    return state
}

/** The narrowest a card gets in a grid: one a row on a phone, two on an open Fold, four on a landscape tablet. */
private val GridCardMin = 280.dp

/**
 * Cards a row on this window (Rail 3 grids): the page's width (right of the rail) over [GridCardMin], at most four.
 * A wide window always gets two: an upright 600–643 dp tablet (Galaxy Tab A, Lenovo Tab M8) has under 560 dp
 * right of the rail and showed one phone card stretched across it (a Threads report, 2026-11-04).
 */
@Composable
fun gridColumns(): Int {
    val window = LocalWindowLayout.current
    val width = LocalConfiguration.current.screenWidthDp.dp - if (window.wide) RailWidth else 0.dp
    // A TV is seen from across the room at 960 dp: four a row, as TV apps do (three at 280 looked like a tablet's).
    return (width / if (window.tv) TvCardMin else GridCardMin).toInt().coerceIn(if (window.wide) 2 else 1, 4)
}

/** A TV's narrowest card (Android TV, Tv.kt). */
private val TvCardMin = 200.dp

/**
 * Video cards, [columns] a row: a grid kept inside the page's LazyColumn, so headers and the
 * trailing loader stay ordinary items. One column is exactly the phone's list.
 */
fun LazyListScope.videoCards(
    videos: List<VideoItem>,
    columns: Int,
    onOpen: (VideoItem) -> Unit,
    /** A card of its own in the grid's second spot (Home's Shorts card); grids only. */
    extra: (@Composable (Modifier) -> Unit)? = null,
    /** Given to the first card, so a TV remote can be put there (fresh search results). */
    firstFocus: FocusRequester? = null,
) {
    val videos = videos.filterNot(Dismissed::hides)
    fun Modifier.first(video: VideoItem) = if (firstFocus != null && video === videos.firstOrNull()) focusRequester(firstFocus) else this
    if (columns <= 1) {
        items(videos, key = { it.id }) { video -> VideoCard(video, onClick = { onOpen(video) }, modifier = Modifier.first(video)) }
        return
    }
    val cells: List<Any> = if (extra != null && videos.isNotEmpty()) videos.take(1) + ExtraCell + videos.drop(1) else videos
    // A row keeps its first video's key (always a video), so rows already shown stay put as more load.
    items(cells.chunked(columns), key = { row -> "row-" + (row.first() as VideoItem).id }) { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp)) {
            row.forEach { cell ->
                if (cell is VideoItem) VideoCard(cell, onClick = { onOpen(cell) }, modifier = Modifier.weight(1f).first(cell))
                else extra?.invoke(Modifier.weight(1f))
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Where [videoCards]' extra card goes among the videos. */
private object ExtraCell

/**
 * Cards taken out with "Not interested" / "Don't recommend channel" (#16), gone from every feed at once.
 * Kept for this run of the app only: the account remembers, so the next load leaves them out itself.
 */
object Dismissed {
    private val videos = mutableStateMapOf<String, Unit>()
    private val channels = mutableStateMapOf<String, Unit>()

    fun hides(video: VideoItem) = video.id in videos || (video.channelUrl?.let { it in channels } == true)

    fun hide(video: VideoItem, channel: Boolean) {
        if (channel) video.channelUrl?.let { channels[it] = Unit } else videos[video.id] = Unit
    }

    fun restore(video: VideoItem, channel: Boolean) {
        if (channel) video.channelUrl?.let(channels::remove) else videos.remove(video.id)
    }
}

/** A feed as a column of video cards with infinite scroll and designed loading/error states. */
@Composable
fun FeedList(
    feed: FeedState,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    header: LazyListScope.() -> Unit = {},
    scrollToTop: Int = 0,
    pullToRefresh: Boolean = true,
    refreshing: Boolean = feed.refreshing,
    onRefresh: () -> Unit = { feed.refresh() },
    /** Collapsing header height: the refresh spinner appears just below it. */
    indicatorTop: Dp = 0.dp,
    /** A card of its own in a grid's second spot (wide windows only). */
    gridExtra: (@Composable (Modifier) -> Unit)? = null,
    /** Set false when the caller renders its own custom empty state (e.g. an empty Home category chip). */
    showEmptyState: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    val list = rememberScrollToTop(scrollToTop)
    // Fresh content always starts at the top (first load and every refresh).
    LaunchedEffect(feed.generation) { list.scrollToItem(0) }
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, feed.videos.size) { if (nearEnd && feed.videos.isNotEmpty()) feed.loadMore() }
    val columns = gridColumns()
    PullToRefresh(enabled = pullToRefresh, refreshing = refreshing, onRefresh = onRefresh, topPadding = indicatorTop) {
    LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        header()
        if (feed.error != null && feed.videos.isNotEmpty()) {
            item(key = "refresh-error") {
                FeedRefreshErrorBanner(message = feed.error.orEmpty(), onRetry = onRefresh)
            }
        }
        videoCards(feed.videos, columns, onOpen, extra = gridExtra)
        when {
            // No keys on these trailing items: a keyed loader at index 0 makes LazyColumn
            // hold it in view when videos arrive above it, landing at the bottom of the page.
            feed.error != null && feed.videos.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.ui_feed_error_title),
                    body = feed.error.orEmpty(),
                    action = { PillButton(stringResource(R.string.ui_try_again), onRefresh) },
                )
            }
            (feed.loading && !refreshing) || feed.hasMore -> item {
                Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                }
            }
            showEmptyState && feed.loadedEmpty -> item {
                EmptyState(
                    icon = Icons.Rounded.VideoLibrary,
                    title = stringResource(R.string.ui_feed_empty_title),
                    body = feed.statusMessage ?: stringResource(R.string.ui_feed_empty_body),
                    action = { PillButton(stringResource(R.string.ui_try_again), onRefresh) },
                )
            }
        }
    }
    }
}

@Composable
internal fun FeedRefreshErrorBanner(message: String, onRetry: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.errorContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = c.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.ui_feed_error_title),
                color = c.onErrorContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Text(
                text = message,
                color = c.onErrorContainer.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PillButton(stringResource(R.string.ui_try_again), onRetry)
    }
}

/**
 * Swipe down at the top to reload (replaces the old refresh button). The indicator uses
 * the theme's colors and sits just under the screen header.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(enabled: Boolean, refreshing: Boolean, onRefresh: () -> Unit, topPadding: Dp = 0.dp, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val c = MaterialTheme.colorScheme
    val pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pull,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = pull, isRefreshing = refreshing,
                containerColor = c.primaryContainer, color = c.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding),
            )
        },
    ) { content() }
}
