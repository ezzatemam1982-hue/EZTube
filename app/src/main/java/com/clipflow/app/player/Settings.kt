package com.clipflow.app.player

import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import java.io.File
import java.text.Normalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Settings (Derek, 2026-11-02: example 4 "Big title that folds" in docs/design/settings-merged.html, Ledgii's
 * settings page merged with Android's own). A page of its own over the app, opened from the header avatar.
 * The large title and the search field fold into a small bar as the page scrolls (search becomes an icon
 * there). Up front: the account card and four quick toggles; then every category as a tile with a summary
 * of what's set, tiles in a group joined into one block. Search finds any setting in any category; a
 * result opens its category scrolled to that setting, which lights up. A category is one level deep and
 * slides in Ledgii's way (shared axis X).
 */
enum class SettingsPage(@StringRes val titleRes: Int, val icon: ImageVector) {
    Playback(R.string.settings_playback, Icons.Rounded.PlayCircle),
    Captions(R.string.captions_title, Icons.Rounded.ClosedCaption),
    SponsorBlock(R.string.settings_sponsorblock, Icons.Rounded.FastForward),
    Appearance(R.string.ui_appearance, Icons.Rounded.Palette),
    Language(R.string.language_title, Icons.Rounded.Translate),
    Notifications(R.string.settings_notifications, Icons.Rounded.Notifications),
    About(R.string.settings_about, Icons.Rounded.Info),
    Account(R.string.settings_account, Icons.Rounded.Person),
}

private val BarHeight = 64.dp
/** Scrolled this far, the big title has folded into the bar. */
private val FoldDistance = 70.dp
private val OuterCorner = 21.dp
private val InnerCorner = 7.dp
private val JoinGap = 3.dp

/**
 * The whole page. [active] is false while it slides away (its Back stops catching). Back goes up one
 * level: a category → the front page, a search → cleared, the front page → closed.
 *
 * [wide] (a window 600 dp or wider; large screens, "Rail 3" in docs/design/large-screens-rail.html): list-detail.
 * The front page stays on the left with the open category's tile lit; the category is a panel on the right
 * with its own title and scroll. Both are always in view, so Back only clears a search, then closes. All
 * state lives above the switch, so a window that narrows (rotation, a fold) carries on as the phone page on
 * the same category, and going wide again keeps it.
 */
@Composable
fun SettingsScreen(
    player: PlayerState,
    active: Boolean,
    onClose: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    wide: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    // Saveable: picking a language rebuilds the window, which reopens here on Language.
    // Opened wide, the panel's first category counts as open, so a window that narrows stays on it.
    var page by rememberSaveable { mutableStateOf(if (wide) SettingsPage.Playback else null) }
    /** The category opened last this session: what the wide page's panel shows while [page] is null (back on the phone's front page). */
    var last by rememberSaveable { mutableStateOf(SettingsPage.Playback) }
    var query by rememberSaveable { mutableStateOf("") }
    /** The setting a search result points at, lit once its category opens. */
    var target by remember { mutableStateOf<String?>(null) }
    /** Counts search picks, so a result in the category already open (wide) still scrolls to its row. */
    var findSeq by remember { mutableIntStateOf(0) }
    val indexScroll = rememberScrollState()
    // Moodii's entrance: flips true a frame after Settings opens; the front page rises in once per opening.
    val entered by rememberEntered()
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val panelShift = with(LocalDensity.current) { PanelShift.roundToPx() }

    fun open(to: SettingsPage, key: String?) {
        target = key
        page = to
        last = to
        if (key != null) findSeq++
    }

    val close = { focusManager.clearFocus(force = true); onClose() }

    BackHandler(enabled = active) {
        when {
            !wide && page != null -> { page = null; target = null }
            query.isNotEmpty() -> query = ""
            else -> close()
        }
    }

    @Composable
    fun Front(selected: SettingsPage?) = FrontPane(
        player = player,
        query = query,
        onQuery = { query = it },
        scroll = indexScroll,
        entered = entered,
        searchFocus = searchFocus,
        selected = selected,
        onOpen = { open(it, null) },
        onPick = { hit ->
            focusManager.clearFocus(force = true)
            open(hit.page, hit.key)
        },
        onSignedOut = onSignedOut,
        onClose = close,
    )

    @Composable
    fun Category(shown: SettingsPage, inPanel: Boolean) {
        val scroll = rememberScrollState()
        val find = remember(shown) { Find(target) }
        // A plain field the reach effect reads: the row the latest search pick points at (null after a tile tap).
        find.key = target
        FoldingPage(
            title = stringResource(shown.titleRes),
            summary = if (shown == SettingsPage.About) null else summaryOf(shown),
            scroll = scroll,
            find = find,
            findSeq = findSeq,
            inPanel = inPanel,
            onBack = { page = null; target = null },
        ) {
            CompositionLocalProvider(LocalFind provides find) {
                when (shown) {
                    SettingsPage.Playback -> PlaybackPage(player)
                    SettingsPage.Captions -> CaptionsPage()
                    SettingsPage.SponsorBlock -> SponsorBlockPage()
                    SettingsPage.Appearance -> AppearancePage()
                    SettingsPage.Language -> LanguagePage()
                    SettingsPage.Notifications -> NotificationsPage(player)
                    SettingsPage.About -> AboutPage(player)
                    SettingsPage.Account -> AccountPage(player, onSignIn, onSignedOut)
                }
            }
        }
    }

    if (wide) {
        val detail = page ?: last
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(c.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            val listWidth = (maxWidth * 0.45f).coerceIn(300.dp, 400.dp)
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight()) { Front(selected = detail) }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = statusTop + 8.dp, end = 12.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(PanelCorner))
                        .background(c.surfaceContainerLow),
                ) {
                    AnimatedContent(
                        targetState = detail,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            // Shared axis Y inside the panel: the next category comes in from its tile's side.
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
                            else (slideInVertically(tween(440, easing = SettingsEase)) { dir * panelShift } + fadeIn(tween(350, delayMillis = 60))) togetherWith
                                (slideOutVertically(tween(260)) { -dir * panelShift * 3 / 4 } + fadeOut(tween(160)))
                        },
                        label = "settingsPanel",
                    ) { shown -> Category(shown, inPanel = true) }
                }
            }
        }
        return
    }

    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize().background(c.surface),
        transitionSpec = {
            // Ledgii's shared axis X: in from 1/8 of the width while fading, out a little the other way.
            val dir = if (targetState != null) 1 else -1
            if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
            else (slideInHorizontally(tween(440, easing = SettingsEase)) { dir * it / 8 } + fadeIn(tween(350))) togetherWith
                (slideOutHorizontally(tween(260)) { -dir * it / 10 } + fadeOut(tween(220)))
        },
        label = "settingsPage",
    ) { shown ->
        if (shown == null) Front(selected = null) else Category(shown, inPanel = false)
    }
}

/** The front page: the big title, search, then the account card, quick toggles and tiles (or the search results). */
@Composable
private fun FrontPane(
    player: PlayerState,
    query: String,
    onQuery: (String) -> Unit,
    scroll: ScrollState,
    entered: Boolean,
    searchFocus: FocusRequester,
    selected: SettingsPage?,
    onOpen: (SettingsPage) -> Unit,
    onPick: (Hit) -> Unit,
    onSignedOut: () -> Unit,
    onClose: () -> Unit,
) {
    FoldingPage(
        title = stringResource(R.string.settings_title),
        summary = null,
        scroll = scroll,
        find = null,
        titleModifier = Modifier.riseIn(entered, 0),
        onBack = onClose,
        onSearch = {
            scroll.animateScrollTo(0)
            searchFocus.requestFocus()
        },
    ) {
        Box(Modifier.riseIn(entered, 1)) { SearchField(query, onQuery, searchFocus) }
        if (query.isBlank()) FrontPage(player, entered, selected, onOpen, onSignedOut) else Results(query, onPick)
    }
}

/** The wide page's category panel: its corners and how far a category travels as it comes in. */
private val PanelCorner = 28.dp
private val PanelShift = 40.dp

/** Ledgii's settings curve (fast out, long soft landing). */
private val SettingsEase = CubicBezierEasing(0.2f, 0.85f, 0.22f, 1f)

// ── The page frame: big title that folds into the bar ─────────────────────────

@Composable
private fun FoldingPage(
    title: String,
    summary: String?,
    scroll: ScrollState,
    find: Find?,
    onBack: () -> Unit,
    titleModifier: Modifier = Modifier,
    onSearch: (suspend () -> Unit)? = null,
    findSeq: Int = 0,
    /** The wide page's category panel: already clear of the status bar, no back arrow, the panel's tone. */
    inPanel: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val foldPx = with(density) { FoldDistance.toPx() }
    // Read in layer lambdas only, so scrolling never recomposes the page.
    val fold = { (scroll.value / foldPx).coerceIn(0f, 1f) }
    val folded by remember { derivedStateOf { fold() > 0.6f } }
    val statusTop = if (inPanel) 0.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val surface = if (inPanel) c.surfaceContainerLow else c.surface
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (find != null) {
        find.scroll = scroll
        find.topPx = with(density) { (statusTop + BarHeight + 48.dp).toPx() }
        LaunchedEffect(find, findSeq) { find.reach() }
    }
    Box(Modifier.fillMaxSize().background(surface)) {
        Column(
            Modifier
                .fillMaxSize()
                // Before the scroll, so a focused field scrolls clear of the keyboard.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .verticalScroll(scroll)
                .onGloballyPositioned { find?.content = it }
                .padding(horizontal = if (inPanel) 24.dp else 20.dp),
        ) {
            // In the panel the title sits near its top; the bar only appears (fades in) as the title folds into it.
            Spacer(Modifier.height(if (inPanel) 16.dp else statusTop + BarHeight))
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(
                modifier = titleModifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 6.dp)
                    .graphicsLayer {
                        val f = fold()
                        alpha = 1f - f
                        val s = 1f - 0.12f * f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(if (isRtl) 1f else 0f, 1f)
                    },
            ) {
                val baseStyle = LocalTextStyle.current.merge(
                    TextStyle(
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.9).sp,
                        textAlign = TextAlign.Start,
                    ),
                )
                val maxWidthPx = constraints.maxWidth
                val fittedSize = remember(title, maxWidthPx, baseStyle) {
                    var sp = 31f
                    while (sp > 22f) {
                        val w = measurer.measure(
                            text = title,
                            style = baseStyle.copy(fontSize = sp.sp),
                            maxLines = 1,
                            softWrap = false,
                        ).size.width
                        if (w <= maxWidthPx) break
                        sp -= 1f
                    }
                    sp.sp
                }
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = baseStyle.copy(fontSize = fittedSize, lineHeight = fittedSize * 1.15f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (summary != null) {
                Text(
                    summary,
                    color = c.onSurfaceVariant,
                    fontSize = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
                )
            }
            content()
            Spacer(Modifier.height(36.dp))
        }
        // Under the folded bar the page fades out instead of cutting off (the tabs' top vignette).
        val vignette = remember(surface) { vignetteBrush(surface, opaqueAtTop = true) }
        Box(
            Modifier
                .padding(top = statusTop + BarHeight)
                .fillMaxWidth()
                .height(28.dp)
                .graphicsLayer { alpha = fold() }
                .drawBehind { drawRect(vignette) },
        )
        // The bar the title folds into (opaque: the page scrolls under it).
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(surface, alpha = if (inPanel) fold() else 1f) }
                .padding(top = statusTop)
                .height(BarHeight)
                .padding(start = if (inPanel) 22.dp else 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!inPanel) {
                Box(Modifier.size(48.dp).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.ui_back), tint = c.onSurface)
                }
            }
            Text(
                title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (inPanel) 0.dp else 6.dp)
                    .graphicsLayer {
                        val f = fold()
                        alpha = f
                        translationY = (1f - f) * 10.dp.toPx()
                    },
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onSearch != null) {
                Box(
                    Modifier
                        .size(48.dp)
                        .graphicsLayer { alpha = fold() }
                        .pressable(enabled = folded) { scope.launch { onSearch() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Search, stringResource(R.string.settings_search), tint = c.onSurface)
                }
            }
        }
    }
}

// ── Front page ────────────────────────────────────────────────────────────────

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, focus: FocusRequester) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    // Search dismisses the keyboard (rule 4; the window-level hide comes ~120 ms later or the IME undoes it).
    fun done() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        view.postDelayed({ view.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0) }, 120)
    }
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(c.surfaceContainerHighest)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.settings_search), color = c.onSurfaceVariant, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(TextStyle(color = c.onSurface, fontSize = 16.sp)),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { done() }, onDone = { done() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(40.dp).pressable { onQuery("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, stringResource(R.string.ui_clear_search), tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun FrontPage(
    player: PlayerState,
    entered: Boolean,
    selected: SettingsPage?,
    onOpen: (SettingsPage) -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    Box(Modifier.riseIn(entered, 2)) {
        AccountCard(
            onClick = { onOpen(SettingsPage.Account) },
            onAvatarClick = { showAvatarPicker = true },
            onSignedOut = onSignedOut,
        )
    }
    Box(Modifier.riseIn(entered, 3)) { QuickToggles(player) }
    Column(Modifier.riseIn(entered, 4)) {
        GroupLabel(stringResource(R.string.settings_group_downloads))
        Tile(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.settings_downloader_title),
            line = stringResource(R.string.settings_downloader_line),
            shape = RoundedCornerShape(OuterCorner),
        ) { InAppDownloadBridge.openDownloaderSettings(context) }
    }
    Column(Modifier.riseIn(entered, 5)) {
        GroupLabel(stringResource(R.string.settings_group_watching))
        CategoryTiles(listOf(SettingsPage.Playback, SettingsPage.Captions, SettingsPage.SponsorBlock), selected, onOpen)
    }
    Column(Modifier.riseIn(entered, 6)) {
        GroupLabel(stringResource(R.string.settings_group_look))
        CategoryTiles(listOf(SettingsPage.Appearance, SettingsPage.Language), selected, onOpen)
    }
    Column(Modifier.padding(top = 24.dp).riseIn(entered, 7)) {
        CategoryTiles(listOf(SettingsPage.Notifications, SettingsPage.About), selected, onOpen)
    }
    Box(Modifier.padding(top = 24.dp).riseIn(entered, 8)) {
        Tile(
            icon = Icons.Rounded.RestartAlt,
            title = stringResource(R.string.settings_reset_title),
            line = stringResource(R.string.settings_reset_line),
            shape = RoundedCornerShape(OuterCorner),
        ) { showResetConfirm = true }
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_reset_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        resetStreamingDefaults(player, context)
                    },
                ) {
                    Text(stringResource(R.string.settings_reset_confirm_action), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
    }
    if (showAvatarPicker) {
        AvatarPickerSheet(
            profile = Account.profile,
            onDismiss = { showAvatarPicker = false },
            onMessage = { player.actionMessage = it },
        )
    }
}

private fun resetStreamingDefaults(player: PlayerState, context: android.content.Context) {
    player.setDataSaver(false)
    QualityPreference.setDefault(VideoQualityMode.Auto)
    PlayerControls.set(true)
    SeekStep.set(10)
    ChaptersStayOpen.set(false)
    AutoPip.set(true)
    player.setZoomFillSetting(true)
    FullscreenSwipes.turnBrightness(true)
    FullscreenSwipes.turnVolume(true)
    if (AudioOnly.enabled) player.setAudioOnly(false)
    if (PlaybackSpeed.remember) player.toggleRememberSpeed()
    PlaybackSpeed.save(1f)
    LiveControls.set(false)
    ShortsSetting.set(context, true)
    CaptionSettings.resetToDefaults()
    SponsorBlock.resetToDefaults()
    ThemeSettings.resetToDefaults()
    player.actionMessage = context.getString(R.string.settings_reset_done)
}

/** Ledgii's account card: primary container, tappable avatar, who's signed in, quick sign-out or chevron into Account. */
@Composable
private fun AccountCard(onClick: () -> Unit, onAvatarClick: () -> Unit, onSignedOut: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val profile = Account.profile
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(OuterCorner))
            .background(c.primaryContainer)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(56.dp).pressable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            AccountAvatar(profile, size = 56)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(c.surface)
                    .border(1.5.dp, c.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.avatar_picker_cd),
                    tint = c.onSurface,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile?.name ?: stringResource(R.string.ui_sign_in),
                color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (profile != null) {
                val identity = listOfNotNull(profile.email, profile.handle?.takeIf { it != profile.email }).joinToString(" · ")
                if (identity.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        identity,
                        color = c.onPrimaryContainer.copy(alpha = 0.88f), fontSize = 13.sp,
                        lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (profile != null) stringResource(R.string.settings_full_sync) else stringResource(R.string.ui_account_pitch_title),
                color = c.onPrimaryContainer.copy(alpha = 0.78f), fontSize = 12.5.sp,
                lineHeight = 16.5.sp, maxLines = 2,
            )
        }
        if (profile != null) {
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.surface)
                    .pressable(onClick = { Account.signOut(); onSignedOut() })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = stringResource(R.string.ui_sign_out),
                    tint = c.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.ui_sign_out),
                    color = c.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Icon(Icons.Rounded.ChevronRight, null, tint = c.onPrimaryContainer.copy(alpha = 0.7f))
        }
    }
}

/** The most used switches, one tap from the front page (like Quick Settings). */
@Composable
private fun QuickToggles(player: PlayerState) {
    val context = LocalContext.current
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickToggle(Icons.Rounded.Headphones, stringResource(R.string.bar_audio_only), AudioOnly.enabled, Modifier.weight(1f)) {
                player.setAudioOnly(!AudioOnly.enabled)
            }
            QuickToggle(Icons.Rounded.FastForward, stringResource(R.string.settings_sponsorblock), SponsorBlock.enabled, Modifier.weight(1f)) {
                SponsorBlock.updateEnabled(!SponsorBlock.enabled)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickToggle(Icons.Rounded.SlowMotionVideo, stringResource(R.string.ui_shorts), ShortsSetting.enabled, Modifier.weight(1f)) {
                ShortsSetting.set(context, !ShortsSetting.enabled)
            }
            QuickToggle(Icons.Rounded.Speed, stringResource(R.string.settings_speed_keep), PlaybackSpeed.remember, Modifier.weight(1f)) {
                player.toggleRememberSpeed()
            }
        }
    }
}

@Composable
private fun QuickToggle(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val background by animateColorAsState(if (on) c.primary else c.surfaceContainerHigh, tween(260), label = "quickBg")
    val content by animateColorAsState(if (on) c.onPrimary else c.onSurface, tween(260), label = "quickFg")
    Row(
        modifier
            .pressable(pressedScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .drawBehind { drawRect(background) }
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = content, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (on) R.string.settings_on else R.string.settings_off),
                color = content.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1,
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 9.dp),
    )
}

/** Rows that touch: 3 dp apart, small inner corners, the block's outer corners round. */
@Composable
private fun Joined(count: Int, item: @Composable (index: Int, shape: Shape) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(JoinGap)) {
        for (i in 0 until count) item(i, joinedShape(i, count))
    }
}

private fun joinedShape(index: Int, count: Int): Shape {
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun CategoryTiles(pages: List<SettingsPage>, selected: SettingsPage?, onOpen: (SettingsPage) -> Unit) {
    Joined(pages.size) { i, shape ->
        val page = pages[i]
        Tile(page.icon, stringResource(page.titleRes), summaryOf(page), shape, selected = page == selected) { onOpen(page) }
    }
}

/**
 * Ledgii's tile: an icon square, the title and summary, a chevron. [selected] (the wide page's open
 * category) turns it primary container, both tones opaque.
 */
@Composable
private fun Tile(
    icon: ImageVector,
    title: AnnotatedString,
    line: AnnotatedString?,
    shape: Shape,
    crumb: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    // A 0…1 blend rather than animated colours, so a theme change still lands at once.
    val lit by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "tileLit")
    val background = lerp(c.surfaceContainer, c.primaryContainer, lit)
    val content = lerp(LocalContentColor.current, c.onPrimaryContainer, lit)
    val muted = lerp(c.onSurfaceVariant, c.onPrimaryContainer.copy(alpha = 0.8f), lit)
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .clip(shape)
            .drawBehind { drawRect(background) }
            .heightIn(min = 78.dp)
            .padding(horizontal = 17.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSquare(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (crumb != null) {
                Text(crumb, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
            }
            Text(title, color = content, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (line != null) {
                Spacer(Modifier.height(4.dp))
                Text(line, color = muted, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, line: String?, shape: Shape, selected: Boolean = false, onClick: () -> Unit) =
    Tile(icon, AnnotatedString(title), line?.let(::AnnotatedString), shape, null, selected, onClick)

@Composable
private fun IconSquare(icon: ImageVector) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(22.dp))
    }
}

/**
 * Moodii's entrance trigger: flips true one frame after first composition. With animations off
 * it starts true, so nothing rises.
 */
@Composable
private fun rememberEntered(): androidx.compose.runtime.State<Boolean> {
    val reduced = rememberReducedMotion()
    val entered = remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { entered.value = true }
    return entered
}

/** Moodii's riseIn: fades in and rises 26 dp into place, [order] × 70 ms after the page appears. */
private fun Modifier.riseIn(entered: Boolean, order: Int): Modifier = composed {
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(620, delayMillis = 70 * order, easing = MoodiiDecelerate),
        label = "riseIn",
    )
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 26.dp.toPx()
    }
}

/** Moodii's EmphasizedDecelerate. */
private val MoodiiDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Opens [intent]; with no app on the phone to take it, says so instead. */
private fun android.content.Context.openOrSay(intent: Intent, onMessage: (String) -> Unit) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: android.content.ActivityNotFoundException) {
        onMessage(str(R.string.settings_no_app))
    }
}

// ── Summaries (each tile's line, and under a category's title) ────────────────

@Composable
private fun summaryOf(page: SettingsPage): String = when (page) {
    SettingsPage.Playback -> listOfNotNull(
        if (QualityPreference.defaultMode != VideoQualityMode.Auto) defaultQualityLabel(QualityPreference.defaultMode) else null,
        if (DataSaver.enabled) stringResource(R.string.settings_data_saver_summary) else null,
        // A TV has neither (TvPlayer): its controls are the remote's.
        if (LocalWindowLayout.current.tv) null
        else stringResource(if (PlayerControls.inBar) R.string.ui_player_controls_pill else R.string.ui_player_controls_on_video),
        if (PlaybackSpeed.remember) stringResource(R.string.settings_speed_kept_summary, PlaybackSpeed.label(PlaybackSpeed.saved))
        else stringResource(R.string.settings_speed_per_video),
        if (SeekStep.seconds != 10) stringResource(R.string.settings_seek_step_summary, SeekStep.seconds.toString()) else null,
        if (FullscreenZoom.fill && !LocalWindowLayout.current.tv) stringResource(R.string.settings_zoom_fill_summary) else null,
        if (AudioOnly.enabled) stringResource(R.string.bar_audio_only) else null,
    ).joinDots()
    SettingsPage.Captions -> listOf(captionShowLabel(CaptionSettings.show), stringResource(CaptionSettings.look.labelRes)).joinDots()
    SettingsPage.SponsorBlock ->
        if (!SponsorBlock.enabled) stringResource(R.string.settings_off)
        else stringResource(
            R.string.settings_sb_summary,
            SponsorBlock.Category.entries.count { SponsorBlock.actionFor(it) == SponsorBlock.Action.Auto }.toString(),
            SponsorBlock.Category.entries.count { SponsorBlock.actionFor(it) == SponsorBlock.Action.Button }.toString(),
        )
    SettingsPage.Appearance -> listOfNotNull(
        themeName(),
        modeLabel(ThemeSettings.mode),
        if (ThemeSettings.amoled && ThemeSettings.mode != "light") "AMOLED" else null,
    ).joinDots()
    SettingsPage.Language -> AppLanguage.choices.firstOrNull { it.tag == AppLanguage.pickedChoice }?.name ?: phoneLanguageLabel()
    SettingsPage.Notifications -> Bells.on.size.let { n ->
        if (n == 0) stringResource(R.string.settings_bells_none) else pluralStringResource(R.plurals.settings_bells_count, n, n.toString())
    }
    SettingsPage.About -> listOfNotNull(
        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
        "${stringResource(R.string.developer_name)} (EZTube)",
        AppUpdate.available?.let { stringResource(R.string.settings_update_available, it.version) },
    ).joinDots()
    SettingsPage.Account -> Account.profile?.let { p -> listOfNotNull(p.name, p.email, p.handle?.takeIf { it != p.email }).joinDots() } ?: stringResource(R.string.ui_account_pitch_title)
}

@Composable
private fun defaultQualityLabel(mode: VideoQualityMode): String = when (mode) {
    VideoQualityMode.Auto -> stringResource(R.string.video_quality_auto)
    VideoQualityMode.Highest -> stringResource(R.string.settings_default_quality_highest)
    is VideoQualityMode.Fixed -> "${mode.height}p"
}

@Composable
private fun defaultQualityNote(mode: VideoQualityMode): String? = when (mode) {
    VideoQualityMode.Auto -> stringResource(R.string.settings_default_quality_auto_line)
    VideoQualityMode.Highest -> stringResource(R.string.settings_default_quality_highest_line)
    is VideoQualityMode.Fixed -> when {
        mode.height >= 1080 -> stringResource(R.string.video_quality_full_hd)
        mode.height <= 240 -> stringResource(R.string.video_quality_data_saver)
        else -> null
    }
}

@Composable
private fun captionShowLabel(show: CaptionSettings.Show): String = when (show) {
    CaptionSettings.Show.Never -> stringResource(R.string.captions_show_never)
    CaptionSettings.Show.Always -> stringResource(R.string.captions_show_always)
    CaptionSettings.Show.Foreign -> stringResource(R.string.captions_show_foreign, CaptionNames.language(CaptionSettings.appLanguage))
}

@Composable
private fun themeName(): String {
    val seed = ThemeSettings.seed ?: return stringResource(R.string.ui_theme_wallpaper)
    val match = CLIPFLOW_THEMES.firstOrNull { it.hex.equals(seed, ignoreCase = true) } ?: return seed
    return if (match.nameRes != 0) stringResource(match.nameRes) else match.name
}

@Composable
private fun modeLabel(mode: String): String = when (mode) {
    "light" -> stringResource(R.string.ui_mode_light)
    "dark" -> stringResource(R.string.ui_mode_dark)
    else -> stringResource(R.string.ui_mode_system)
}

@Composable
private fun phoneLanguageLabel(): String {
    val phone = AppLanguage.phoneLocale
    val name = AppLanguage.choices.firstOrNull { java.util.Locale.forLanguageTag(it.tag).language == phone.language }?.name
        ?: phone.getDisplayLanguage(phone).replaceFirstChar { it.titlecase(phone) }
    return stringResource(R.string.language_phone_named, name)
}

@Composable
private fun updateStatus(): String = AppUpdate.available?.let { stringResource(R.string.settings_update_available, it.version) }
    ?: stringResource(if (AppUpdate.checking) R.string.settings_checking else R.string.settings_up_to_date)

// ── Search ────────────────────────────────────────────────────────────────────

/** One searchable setting: where it lives ([key] = its row there; null = the category itself) and its words. */
private class Entry(val page: SettingsPage, val key: String?, val title: String, val words: List<String>)

private class Hit(val page: SettingsPage, val key: String?, val title: String, val line: String?)

/** Every setting, in the app's language. Built per search; cheap. */
private fun entries(liveSupported: Boolean): List<Entry> = buildList {
    SettingsPage.entries.forEach { add(Entry(it, null, str(it.titleRes), emptyList())) }
    val pb = SettingsPage.Playback
    add(Entry(pb, "controls", str(R.string.ui_player_controls), listOf(str(R.string.ui_player_controls_pill), str(R.string.ui_player_controls_on_video))))
    add(Entry(pb, "quality", str(R.string.settings_default_quality_title), listOf(str(R.string.video_quality_auto), str(R.string.settings_default_quality_highest), str(R.string.settings_default_quality_words))))
    add(Entry(pb, "data_saver", str(R.string.settings_data_saver_title), listOf(str(R.string.settings_data_saver_line), "480p", "data saver")))
    add(Entry(pb, "speed", str(R.string.settings_speed_remember_title), listOf(str(R.string.settings_speed_keep), str(R.string.bar_speed_remember))))
    add(Entry(pb, "seek", str(R.string.settings_seek_step_title), listOf(str(R.string.settings_seek_step_line)) + SeekStep.choices.map { str(R.string.settings_seconds_short, it.toString()) }))
    add(Entry(pb, "chapters", str(R.string.settings_chapters_open_title), listOf(str(R.string.settings_chapters_open_line), str(R.string.bar_chapters))))
    add(Entry(pb, "pip", str(R.string.settings_auto_pip_title), listOf(str(R.string.settings_auto_pip_line))))
    add(Entry(pb, "zoom", str(R.string.settings_zoom_fill_title), listOf(str(R.string.settings_zoom_fill_on), str(R.string.settings_zoom_fill_off))))
    add(Entry(pb, "swipe_brightness", str(R.string.settings_swipe_brightness_title), listOf(str(R.string.settings_swipe_brightness_line), str(R.string.settings_swipe_words))))
    add(Entry(pb, "swipe_volume", str(R.string.settings_swipe_volume_title), listOf(str(R.string.settings_swipe_volume_line), str(R.string.settings_swipe_words))))
    add(Entry(pb, "audio", str(R.string.bar_audio_only), listOf(str(R.string.settings_audio_only_line))))
    if (liveSupported) {
        add(Entry(pb, "live", str(R.string.settings_live_title), listOf(str(R.string.settings_live_line), str(R.string.settings_live_words))))
    }
    val cc = SettingsPage.Captions
    add(Entry(cc, "show", str(R.string.captions_show), listOf(str(R.string.captions_show_never), str(R.string.captions_show_always))))
    add(Entry(cc, "look", str(R.string.captions_look), CaptionSettings.Look.entries.map { str(it.labelRes) }))
    add(Entry(cc, "size", str(R.string.captions_size), CaptionSettings.sizes.map { str(it.second) }))
    add(Entry(cc, "kept", str(R.string.captions_kept_languages), emptyList()))
    val sb = SettingsPage.SponsorBlock
    add(Entry(sb, "sb", str(R.string.settings_sponsorblock), listOf(str(R.string.ui_sponsorblock_line))))
    SponsorBlock.Category.entries.forEach { add(Entry(sb, "sb_${it.key}", it.label, listOf(str(it.shortRes), str(it.descRes)))) }
    val ap = SettingsPage.Appearance
    add(Entry(ap, "mode", str(R.string.settings_mode), listOf(str(R.string.ui_mode_system), str(R.string.ui_mode_light), str(R.string.ui_mode_dark))))
    add(Entry(ap, "amoled", str(R.string.settings_amoled_title), listOf(str(R.string.settings_amoled_line), "AMOLED", "OLED")))
    add(
        Entry(
            ap,
            "theme",
            str(R.string.settings_theme),
            listOf(str(R.string.ui_theme_wallpaper)) + CLIPFLOW_THEMES.flatMap { listOfNotNull(if (it.nameRes != 0) str(it.nameRes) else null, it.name) },
        ),
    )
    add(Entry(SettingsPage.Language, "lang", str(R.string.language_title), AppLanguage.choices.map { it.name } + str(R.string.language_phone)))
    val nt = SettingsPage.Notifications
    add(Entry(nt, "system", str(R.string.settings_system_notifications), listOf(str(R.string.settings_system_notifications_line))))
    Bells.on.values.forEach { ch -> ch.name?.let { add(Entry(nt, "bell_${ch.id}", it, listOf(str(R.string.settings_bell_line)))) } }
    add(
        Entry(
            SettingsPage.Account,
            "account",
            str(if (Account.signedIn) R.string.ui_sign_out else R.string.ui_sign_in),
            listOfNotNull(Account.profile?.name, "Google", str(R.string.avatar_picker_title)),
        ),
    )
    val ab = SettingsPage.About
    add(Entry(ab, "version", str(R.string.settings_version, BuildConfig.VERSION_NAME), listOf(str(R.string.settings_checking))))
    add(Entry(ab, "developer", str(R.string.developer), listOf(str(R.string.developer_name), "Ezzat Emam", "EZTube")))
    add(Entry(ab, "clear_cache", str(R.string.settings_clear_cache), listOf("cache", "storage", str(R.string.settings_clear_cache_empty))))
}

/** Case and accents don't matter ("espanol" finds Español). */
private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** Rows a TV doesn't show (PlaybackPage): never found by a search there. */
private val PhoneOnlyRows = setOf("controls", "chapters", "pip", "zoom", "swipe_brightness", "swipe_volume", "live")

private fun hits(query: String, tv: Boolean = false, liveSupported: Boolean = false): List<Hit> {
    val q = fold(query.trim())
    if (q.isEmpty()) return emptyList()
    return entries(liveSupported).filterNot { tv && it.key in PhoneOnlyRows }.mapNotNull { e ->
        when {
            fold(e.title).contains(q) -> Hit(e.page, e.key, e.title, null)
            else -> e.words.firstOrNull { fold(it).contains(q) }?.let { Hit(e.page, e.key, e.title, it) }
        }
    }.let { found ->
        // A category and its own switch share a name (SponsorBlock): keep the switch, it's the more exact spot.
        found.filterNot { hit -> hit.key == null && found.any { it.key != null && it.page == hit.page && it.title == hit.title } }
    }.take(24)
}

@Composable
private fun Results(query: String, onPick: (Hit) -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val liveSupported = remember(context) { LiveControls.supported(context) }
    val found = hits(query, tv = LocalWindowLayout.current.tv, liveSupported = liveSupported)
    if (found.isEmpty()) {
        Text(
            stringResource(R.string.settings_no_results, query.trim()),
            color = c.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 12.dp),
        )
        return
    }
    val mark = SpanStyle(background = c.primaryContainer, color = c.onPrimaryContainer)
    val settingsTitle = stringResource(R.string.settings_title)
    Spacer(Modifier.height(16.dp))
    Joined(found.size) { i, shape ->
        val hit = found[i]
        Tile(
            hit.page.icon,
            marked(hit.title, query, mark),
            hit.line?.let { marked(it, query, mark) },
            shape,
            crumb = if (hit.key == null) settingsTitle else stringResource(hit.page.titleRes),
        ) { onPick(hit) }
    }
}

/** [text] with every match of [query] marked (only when folding kept its length, so the spots line up). */
private fun marked(text: String, query: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    val folded = fold(text)
    val q = fold(query.trim())
    if (q.isEmpty() || folded.length != text.length) return@buildAnnotatedString
    var from = 0
    while (true) {
        val at = folded.indexOf(q, from)
        if (at < 0) break
        addStyle(style, at, at + q.length)
        from = at + q.length
    }
}

// ── Finding a setting: scroll to it and light it up ───────────────────────────

/** The setting a search result opened, found once the category has laid out. */
private class Find(var key: String?) {
    val rows = HashMap<String, LayoutCoordinates>()
    var content: LayoutCoordinates? = null
    var scroll: ScrollState? = null
    var topPx = 0f
    var lit by mutableStateOf<String?>(null)

    suspend fun reach() {
        val key = key ?: return
        // The same row picked again (wide: its category stays open) lights up again.
        lit = null
        // Once the category has slid in.
        delay(380)
        val row = rows[key]?.takeIf { it.isAttached } ?: return
        val page = content?.takeIf { it.isAttached } ?: return
        val y = page.localPositionOf(row, Offset.Zero).y
        scroll?.animateScrollTo((y - topPx).toInt().coerceAtLeast(0), tween(420, easing = SettingsEase))
        lit = key
    }
}

private val LocalFind = staticCompositionLocalOf<Find?> { null }

/**
 * One setting's surface. [key] lets search find it: it then lights up (container → primary container and
 * back, both opaque) after the page scrolls to it.
 */
@Composable
private fun SettingBox(key: String?, shape: Shape, modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val find = LocalFind.current
    val glow = remember { Animatable(0f) }
    if (key != null && find != null) {
        LaunchedEffect(find.lit) {
            if (find.lit == key) {
                glow.animateTo(1f, tween(260))
                glow.animateTo(0f, tween(900, delayMillis = 700))
            }
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { if (key != null) find?.rows?.put(key, it) }
            .clip(shape)
            .drawBehind { drawRect(lerp(c.surfaceContainer, c.primaryContainer, glow.value)) }
            .padding(horizontal = padding, vertical = 15.dp),
        content = content,
    )
}

/** Title and line, with a switch; the whole row toggles. On an RTL-language phone the switch sits left, mirrored (0.5.64). */
@Composable
private fun SwitchSetting(
    key: String,
    shape: Shape,
    title: String,
    line: String,
    checked: Boolean,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val rtl = rememberRtlLanguage()
    val toggle = @Composable {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = c.primary))
        }
    }
    SettingBox(key, shape, Modifier.pressable(enabled = enabled, pressedScale = 0.98f) { onChange(!checked) }) {
        // Not available here (the line says why): dimmed, as SponsorBlock's segments are while it's off.
        Row(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.4f }, verticalAlignment = Alignment.CenterVertically) {
            if (rtl) {
                toggle()
                Spacer(Modifier.width(14.dp))
            }
            if (icon != null) {
                IconSquare(icon)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(3.dp))
                Text(line, color = c.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (!rtl) {
                Spacer(Modifier.width(12.dp))
                toggle()
            }
        }
    }
}

/** A title (and line) over a row of choice chips. */
@Composable
private fun <T> ChipsSetting(
    key: String,
    shape: Shape,
    title: String,
    line: String?,
    options: List<Pair<T, String>>,
    selected: T,
    icon: ImageVector? = null,
    onPick: (T) -> Unit,
) {
    SettingBox(key, shape) {
        SettingHeader(title, line, icon)
        Spacer(Modifier.height(12.dp))
        Chips(options, selected, onPick = onPick)
    }
}

/** A title (and line) over a fixed-column grid of equal-width choice buttons (3x2, 2x2, etc.). */
@Composable
private fun <T> ChipGridSetting(
    key: String,
    shape: Shape,
    title: String,
    line: String?,
    options: List<Pair<T, String>>,
    selected: T,
    columns: Int,
    icon: ImageVector? = null,
    onPick: (T) -> Unit,
) {
    SettingBox(key, shape) {
        SettingHeader(title, line, icon)
        Spacer(Modifier.height(12.dp))
        ChipGrid(options, selected, columns = columns, onPick = onPick)
    }
}

@Composable
private fun SettingHeader(title: String, line: String?, icon: ImageVector?) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconSquare(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (line != null) {
                Spacer(Modifier.height(3.dp))
                Text(line, color = c.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ChipPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    centerText: Boolean = false,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(if (selected) 18.dp else 10.dp))
            // Full primary when picked, as the quick toggles: secondaryContainer was too close to the
            // others' tone in some themes to tell which was on (Derek).
            .background(if (selected) c.primary else c.surfaceContainerHighest)
            .padding(horizontal = if (centerText) 8.dp else 16.dp, vertical = 10.dp),
        contentAlignment = if (centerText) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = if (selected) c.onPrimary else c.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = if (centerText) TextAlign.Center else TextAlign.Start,
            maxLines = if (centerText) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun <T> ChipGrid(
    options: List<Pair<T, String>>,
    selected: T,
    columns: Int,
    enabled: Boolean = true,
    onPick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { (value, label) ->
                    ChipPill(
                        label = label,
                        selected = value == selected,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        centerText = true,
                    ) { onPick(value) }
                }
                repeat(columns - rowOptions.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Choice chips wrapped with FlowRow so multi-line labels or many options never clip horizontally. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onPick: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            ChipPill(
                label = label,
                selected = value == selected,
                enabled = enabled,
            ) { onPick(value) }
        }
    }
}

/** A row that goes somewhere (a link, a check). */
@Composable
private fun LinkSetting(key: String, shape: Shape, icon: ImageVector, title: String, line: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    SettingBox(key, shape, Modifier.pressable(pressedScale = 0.98f, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSquare(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(3.dp))
                Text(line, color = c.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp),
    )
}

// ── Categories ────────────────────────────────────────────────────────────────

@Composable
private fun PlaybackPage(player: PlayerState) {
    val context = LocalContext.current
    val pipSupported = LocalPictureInPicture.current?.supported != false
    val liveSupported = remember(context) { LiveControls.supported(context) }
    // A TV (TvPlayer) has no playback pill, chapter list, picture-in-picture, pinch or live activity: only what still
    // applies there is shown.
    val tv = LocalWindowLayout.current.tv
    var showQualityPicker by remember { mutableStateOf(false) }
    val controls = when {
        tv -> listOf(1)
        pipSupported -> listOf(0, 1, 2, 3)
        else -> listOf(0, 1, 2)
    }
    val picture = if (tv) listOf(0, 1, 2, 4) else listOf(0, 1, 2, 3, 4)
    Spacer(Modifier.height(14.dp))
    // The controls and how they behave.
    Joined(controls.size) { i, shape ->
        when (controls[i]) {
            0 -> ChipsSetting(
                "controls", shape,
                stringResource(R.string.ui_player_controls),
                stringResource(if (PlayerControls.inBar) R.string.ui_player_controls_in_bar_line else R.string.ui_player_controls_on_video_line),
                listOf(true to stringResource(R.string.ui_player_controls_pill), false to stringResource(R.string.ui_player_controls_on_video)),
                PlayerControls.inBar,
                icon = Icons.Rounded.Tune,
            ) { PlayerControls.set(it) }
            // Its icon is the −/+ buttons' own glyph, so it changes with the pick (#36).
            1 -> ChipGridSetting(
                "seek", shape,
                stringResource(R.string.settings_seek_step_title),
                stringResource(R.string.settings_seek_step_line),
                SeekStep.choices.map { it to stringResource(R.string.settings_seconds_short, it.toString()) },
                SeekStep.seconds,
                columns = 3,
                icon = seekIcon(forward = true),
            ) { SeekStep.set(it) }
            2 -> SwitchSetting(
                "chapters", shape,
                stringResource(R.string.settings_chapters_open_title),
                stringResource(R.string.settings_chapters_open_line),
                ChaptersStayOpen.on,
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
            ) { ChaptersStayOpen.set(it) }
            else -> SwitchSetting(
                "pip", shape,
                stringResource(R.string.settings_auto_pip_title),
                stringResource(R.string.settings_auto_pip_line),
                AutoPip.on,
                icon = Icons.Rounded.PictureInPictureAlt,
            ) { AutoPip.set(it) }
        }
    }
    Spacer(Modifier.height(14.dp))
    // Speed, picture and sound.
    Joined(picture.size) { i, shape ->
        when (picture[i]) {
            0 -> LinkSetting(
                "quality", shape,
                Icons.Rounded.HighQuality,
                stringResource(R.string.settings_default_quality_title),
                defaultQualityLabel(QualityPreference.defaultMode),
            ) { showQualityPicker = true }
            1 -> SwitchSetting(
                "data_saver", shape,
                stringResource(R.string.settings_data_saver_title),
                stringResource(R.string.settings_data_saver_line),
                DataSaver.enabled,
                icon = Icons.Rounded.DataSaverOn,
            ) { player.setDataSaver(it) }
            2 -> SwitchSetting(
                "speed", shape,
                stringResource(R.string.settings_speed_remember_title),
                if (PlaybackSpeed.remember) stringResource(R.string.settings_speed_remember_on, PlaybackSpeed.label(PlaybackSpeed.saved))
                else stringResource(R.string.settings_speed_remember_off),
                PlaybackSpeed.remember,
                icon = Icons.Rounded.Speed,
            ) { if (it != PlaybackSpeed.remember) player.toggleRememberSpeed() }
            3 -> SwitchSetting(
                "zoom", shape,
                stringResource(R.string.settings_zoom_fill_title),
                stringResource(if (FullscreenZoom.fill) R.string.settings_zoom_fill_on else R.string.settings_zoom_fill_off),
                FullscreenZoom.fill,
                icon = Icons.Rounded.ZoomOutMap,
            ) { player.setZoomFillSetting(it) }
            else -> SwitchSetting(
                "audio", shape,
                stringResource(R.string.bar_audio_only),
                stringResource(R.string.settings_audio_only_line),
                AudioOnly.enabled,
                icon = Icons.Rounded.Headphones,
            ) { player.setAudioOnly(it) }
        }
    }
    // Fullscreen's side swipes (#62). A TV's player has no touch gestures.
    if (!tv) {
        Spacer(Modifier.height(14.dp))
        Joined(2) { i, shape ->
            if (i == 0) SwitchSetting(
                "swipe_brightness", shape,
                stringResource(R.string.settings_swipe_brightness_title),
                stringResource(R.string.settings_swipe_brightness_line),
                FullscreenSwipes.brightness,
                icon = Icons.Rounded.BrightnessMedium,
            ) { FullscreenSwipes.turnBrightness(it) }
            else SwitchSetting(
                "swipe_volume", shape,
                stringResource(R.string.settings_swipe_volume_title),
                stringResource(R.string.settings_swipe_volume_line),
                FullscreenSwipes.volume,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
            ) { FullscreenSwipes.turnVolume(it) }
        }
    }
    if (!tv && liveSupported) {
        Spacer(Modifier.height(14.dp))
        // Off screen.
        LiveActivitySetting(player)
    }
    if (showQualityPicker) {
        DefaultVideoQualitySheet(
            selected = QualityPreference.defaultMode,
            onPick = { mode ->
                QualityPreference.setDefault(mode)
                showQualityPicker = false
            },
            onDismiss = { showQualityPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultVideoQualitySheet(
    selected: VideoQualityMode,
    onPick: (VideoQualityMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.settings_default_quality_title),
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            val choices = VideoQualityMode.settingsChoices
            Joined(choices.size) { i, shape ->
                val option = choices[i]
                val on = option == selected
                SettingBox(null, shape, Modifier.pressable(pressedScale = 0.98f) { onPick(option) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                defaultQualityLabel(option),
                                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            defaultQualityNote(option)?.let { note ->
                                Spacer(Modifier.height(2.dp))
                                Text(note, color = c.onSurfaceVariant, fontSize = 12.5.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            if (on) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                            if (on) stringResource(R.string.ui_selected) else null,
                            tint = if (on) c.primary else c.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Playback controls in a live activity once the app is off screen (LiveActivity.kt). Turning it on asks for
 * notifications (Android 13+); with Live Updates off for ClipFlow in Android's settings, a row under it opens that page.
 */
@Composable
private fun LiveActivitySetting(player: PlayerState) {
    val context = LocalContext.current
    // Read again whenever the page comes back (from Android's settings, say).
    val supported = remember { LiveControls.supported(context) }
    var allowed by remember { mutableStateOf(LiveControls.canPromote(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = LiveControls.canPromote(context)
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = LiveControls.canPromote(context)
        if (!allowed) {
            player.actionMessage = str(R.string.settings_live_allow_line)
            LiveControls.openSettings(context)
        }
    }
    val blocked = supported && LiveControls.on && !allowed
    Joined(if (blocked) 2 else 1) { i, shape ->
        if (i == 0) {
            SwitchSetting(
                "live", shape,
                stringResource(R.string.settings_live_title),
                stringResource(
                    when {
                        !supported -> R.string.settings_live_needs
                        LiveControls.on && LiveControls.refusedBySystem -> R.string.settings_live_refused
                        else -> R.string.settings_live_line
                    },
                ),
                LiveControls.on && supported,
                icon = Icons.Rounded.Sensors,
                enabled = supported,
            ) { on ->
                LiveControls.set(on)
                if (on && !LiveControls.canPromote(context)) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        player.actionMessage = str(R.string.settings_live_allow_line)
                        LiveControls.openSettings(context)
                    }
                }
            }
        } else {
            LinkSetting(
                "live_allow", shape, Icons.Rounded.Notifications,
                stringResource(R.string.settings_live_allow_title),
                stringResource(R.string.settings_live_allow_line),
            ) { LiveControls.openSettings(context) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptionsPage() {
    val c = MaterialTheme.colorScheme
    val kept = CaptionTranslator.downloaded.filter { it != "en" }.sortedBy { CaptionNames.language(it) }
    val rows = if (kept.isEmpty()) 4 else 5
    Spacer(Modifier.height(14.dp))
    Joined(rows) { i, shape ->
        when (i) {
            0 -> SettingBox("preview", shape, padding = 8.dp) { CaptionPreview() }
            1 -> ChipsSetting(
                "show", shape, stringResource(R.string.captions_show), null,
                CaptionSettings.Show.entries.map { it to captionShowLabel(it) },
                CaptionSettings.show,
            ) { CaptionSettings.chooseShow(it) }
            2 -> ChipGridSetting(
                "look", shape, stringResource(R.string.captions_look), null,
                CaptionSettings.Look.entries.map { it to stringResource(it.labelRes) },
                CaptionSettings.look,
                columns = 2,
            ) { CaptionSettings.chooseLook(it) }
            3 -> ChipsSetting(
                "size", shape, stringResource(R.string.captions_size), null,
                CaptionSettings.sizes.map { it.first to stringResource(it.second) },
                CaptionSettings.size,
            ) { CaptionSettings.chooseSize(it) }
            else -> SettingBox("kept", shape) {
                Text(stringResource(R.string.captions_kept_languages), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    kept.forEach { lang ->
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(c.surfaceContainerHighest).padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(CaptionNames.language(lang), color = c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            Box(Modifier.size(34.dp).pressable { CaptionTranslator.delete(lang) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.captions_remove_language, CaptionNames.language(lang)), tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsorBlockPage() {
    val c = MaterialTheme.colorScheme
    Spacer(Modifier.height(14.dp))
    Joined(1) { _, shape ->
        SwitchSetting(
            "sb", shape, stringResource(R.string.settings_sponsorblock), stringResource(R.string.ui_sponsorblock_line),
            SponsorBlock.enabled, icon = Icons.Rounded.FastForward,
        ) { SponsorBlock.updateEnabled(it) }
    }
    Spacer(Modifier.height(12.dp))
    val categories = SponsorBlock.Category.entries
    // Off: the segments stay in place, dimmed, so a search can still point at one.
    Box(Modifier.graphicsLayer { alpha = if (SponsorBlock.enabled) 1f else 0.4f }) {
        Joined(categories.size) { i, shape ->
            val category = categories[i]
            var infoExpanded by rememberSaveable(category.key) { mutableStateOf(false) }
            SettingBox("sb_${category.key}", shape) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(category.color))
                    Spacer(Modifier.width(10.dp))
                    Text(category.label, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .pressable { infoExpanded = !infoExpanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (infoExpanded) Icons.Rounded.Info else Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.player_sb_info),
                            tint = if (infoExpanded) c.primary else c.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = infoExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(
                        text = stringResource(category.descRes),
                        color = c.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Chips(
                    SponsorBlock.Action.entries.map { it to stringResource(it.labelRes) },
                    SponsorBlock.actionFor(category),
                    enabled = SponsorBlock.enabled,
                ) { SponsorBlock.setAction(category, it) }
            }
        }
    }
    Note(stringResource(R.string.ui_sponsorblock_credit))
}

@Composable
private fun AppearancePage() {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dark = isDarkTheme()
    Spacer(Modifier.height(14.dp))
    Joined(3) { i, shape ->
        if (i == 0) {
            ChipsSetting(
                "mode", shape, stringResource(R.string.settings_mode), null,
                listOf("system", "light", "dark").map { it to modeLabel(it) },
                ThemeSettings.mode,
                icon = Icons.Rounded.DarkMode,
            ) { ThemeSettings.chooseMode(it) }
        } else if (i == 1) {
            // Applies whenever the app is dark (Dark, or System at night); Light never is, so it's dimmed there.
            val light = ThemeSettings.mode == "light"
            SwitchSetting(
                "amoled", shape, stringResource(R.string.settings_amoled_title),
                stringResource(if (light) R.string.settings_amoled_light_line else R.string.settings_amoled_line),
                ThemeSettings.amoled, icon = Icons.Rounded.Contrast, enabled = !light,
            ) { ThemeSettings.chooseAmoled(it) }
        } else {
            SettingBox("theme", shape) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconSquare(Icons.Rounded.Palette)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                val wallpaper = stringResource(R.string.ui_theme_wallpaper)
                val localizedThemes = CLIPFLOW_THEMES.map {
                    it.hex to if (it.nameRes != 0) stringResource(it.nameRes) else it.name
                }
                val choices = buildList {
                    if (ThemeSettings.wallpaperAvailable) add(null to wallpaper)
                    addAll(localizedThemes)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    choices.forEach { (hex, name) ->
                        val selected = ThemeSettings.seed?.lowercase() == hex?.lowercase()
                        val scheme = themedScheme(context, hex, dark)
                        Column(Modifier.weight(1f).pressable { ThemeSettings.choose(hex) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(if (selected) CookieShape else CircleShape)
                                    .background(scheme.surface)
                                    .padding(4.dp)
                                    .clip(if (selected) CookieShape else CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            0f to scheme.primary, 0.5f to scheme.primary,
                                            0.5f to scheme.primaryContainer, 1f to scheme.primaryContainer,
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, stringResource(R.string.ui_selected), tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                name,
                                fontSize = 11.5.sp,
                                lineHeight = 14.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (selected) c.onSurface else c.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePage() {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val chosen = AppLanguage.pickedChoice
    val options = listOf("" to phoneLanguageLabel()) + AppLanguage.choices.map { it.tag to it.name }
    Spacer(Modifier.height(14.dp))
    Joined(options.size) { i, shape ->
        val (tag, label) = options[i]
        val on = tag == chosen
        SettingBox(if (i == 0) "lang" else null, shape, Modifier.pressable(pressedScale = 0.98f) { context.findActivity()?.let { AppLanguage.pick(it, tag) } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Each language named in itself, so one picked by mistake is easy to undo.
                Text(label, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (on) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    if (on) stringResource(R.string.ui_selected) else null,
                    tint = if (on) c.primary else c.onSurfaceVariant,
                )
            }
        }
    }
    Note(stringResource(R.string.language_line))
}

@Composable
private fun NotificationsPage(player: PlayerState) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val toggle = rememberBellToggle { player.actionMessage = it }
    // The channels as the page opened: one turned off stays listed (bell off) so it can go back on.
    val channels = remember { Bells.on.values.sortedBy { it.name?.lowercase() ?: it.id } }
    Spacer(Modifier.height(14.dp))
    if (channels.isEmpty()) {
        Joined(1) { _, shape ->
            SettingBox(null, shape) {
                Text(stringResource(R.string.settings_bells_empty), color = c.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp)
            }
        }
    } else {
        Joined(channels.size) { i, shape ->
            val channel = channels[i]
            val on = Bells.isOn(channel.id)
            SettingBox("bell_${channel.id}", shape, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelAvatar(channel.avatar, channel.name.orEmpty(), size = 40)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.name ?: channel.id, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(if (on) R.string.settings_bell_line else R.string.settings_off),
                            color = c.onSurfaceVariant, fontSize = 12.5.sp,
                        )
                    }
                    val key by animateColorAsState(if (on) c.primary else c.surfaceContainerHighest, tween(260), label = "bellKey")
                    Box(
                        Modifier
                            .size(44.dp)
                            .pressable { toggle(channel) }
                            .clip(CircleShape)
                            .drawBehind { drawRect(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (on) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                            stringResource(if (on) R.string.app_bell_on_description else R.string.app_bell_off_description),
                            tint = if (on) c.onPrimary else c.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Joined(1) { _, shape ->
        LinkSetting(
            "system", shape, Icons.Rounded.Notifications,
            stringResource(R.string.settings_system_notifications), stringResource(R.string.settings_system_notifications_line),
        ) {
            context.openOrSay(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
            ) { player.actionMessage = it }
        }
    }
}

@Composable
private fun AccountPage(player: PlayerState, onSignIn: () -> Unit, onSignedOut: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val profile = Account.profile
    var showAvatarPicker by remember { mutableStateOf(false) }
    Spacer(Modifier.height(14.dp))
    if (profile == null) {
        Joined(2) { i, shape ->
            if (i == 0) {
                SettingBox("account", shape, padding = 20.dp) {
                    // The pitch's title is already the page's line, right above.
                    Text(stringResource(R.string.ui_account_pitch_body), color = c.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PillButton(stringResource(R.string.ui_sign_in_with_google), onSignIn)
                    }
                }
            } else {
                LinkSetting(
                    "avatar", shape, Icons.Rounded.PhotoLibrary,
                    stringResource(R.string.avatar_picker_title),
                    stringResource(R.string.avatar_picker_subtitle),
                ) { showAvatarPicker = true }
            }
        }
    } else {
        Joined(2) { i, shape ->
            if (i == 0) {
                SettingBox("account", shape, padding = 20.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(72.dp).pressable { showAvatarPicker = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            AccountAvatar(profile, size = 72)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(c.surfaceContainerHigh)
                                    .border(1.5.dp, c.surfaceContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.avatar_picker_cd),
                                    tint = c.onSurface,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(profile.name, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center)
                        profile.email?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = c.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                        profile.handle?.takeIf { it != profile.email }?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, color = c.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.settings_full_sync), color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        TextButton(onClick = { showAvatarPicker = true }) {
                            Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.avatar_picker_cd), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        PillButton(stringResource(R.string.ui_sign_out), { Account.signOut(); onSignedOut() }, Icons.AutoMirrored.Rounded.Logout)
                    }
                }
            } else {
                SettingBox("sync", shape) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Sync, null, tint = c.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.settings_sync_note), color = c.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
    if (showAvatarPicker) {
        AvatarPickerSheet(
            profile = profile,
            onDismiss = { showAvatarPicker = false },
            onMessage = { player.actionMessage = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AvatarPickerSheet(
    profile: Profile?,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (LocalAvatarStore.saveGalleryUri(context, uri)) {
            onDismiss()
        } else {
            onMessage(str(R.string.avatar_picker_error))
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(profile, size = 48)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.avatar_picker_title),
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.avatar_picker_subtitle),
                        color = c.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.avatar_picker_presets),
                color = c.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            val presetRows = LocalAvatarStore.Preset.entries.chunked(4)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                presetRows.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowPresets.forEach { preset ->
                            val selected = LocalAvatarStore.mode == LocalAvatarStore.Mode.Preset && LocalAvatarStore.preset == preset
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (selected) 3.dp else 1.5.dp,
                                            color = if (selected) c.primary else c.outlineVariant,
                                            shape = CircleShape,
                                        )
                                        .padding(if (selected) 4.dp else 2.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.bgColor))
                                        .pressable(pressedScale = 0.94f) {
                                            LocalAvatarStore.selectPreset(preset)
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(preset.drawableRes),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    )
                                }
                            }
                        }
                        repeat(4 - rowPresets.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            val actionCount = if (LocalAvatarStore.mode != LocalAvatarStore.Mode.Default) 2 else 1
            Joined(actionCount) { i, shape ->
                if (i == 0) {
                    LinkSetting(
                        "avatar_gallery",
                        shape,
                        Icons.Rounded.PhotoLibrary,
                        stringResource(R.string.avatar_picker_gallery),
                        stringResource(R.string.avatar_picker_subtitle),
                    ) { galleryLauncher.launch("image/*") }
                } else {
                    LinkSetting(
                        "avatar_reset",
                        shape,
                        Icons.Rounded.RestartAlt,
                        stringResource(R.string.avatar_picker_reset),
                        profile?.name ?: stringResource(R.string.settings_off),
                    ) {
                        LocalAvatarStore.resetToDefault()
                        onDismiss()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutPage(player: PlayerState) {
    val context = LocalContext.current
    var whatsNew by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { measureClearableCacheBytes(context) }
    }
    Spacer(Modifier.height(14.dp))
    val developerCredit = "${stringResource(R.string.developer_name)} (EZTube)"
    val cacheLine = if (cacheBytes > 0L) {
        stringResource(R.string.settings_clear_cache_line, Formatter.formatShortFileSize(context, cacheBytes))
    } else {
        stringResource(R.string.settings_clear_cache_empty)
    }
    val clearedMessage = stringResource(R.string.settings_clear_cache_done)
    Joined(3) { i, shape ->
        when (i) {
            0 -> LinkSetting("version", shape, Icons.Rounded.SystemUpdateAlt, stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), updateStatus()) {
                if (AppUpdate.available != null) whatsNew = true else AppUpdate.checkNow()
            }
            1 -> LinkSetting("developer", shape, Icons.Rounded.Person, stringResource(R.string.developer), developerCredit) {
                android.widget.Toast.makeText(
                    context,
                    developerCredit,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            else -> LinkSetting("clear_cache", shape, Icons.Rounded.CleaningServices, stringResource(R.string.settings_clear_cache), cacheLine) {
                scope.launch {
                    val remaining = withContext(Dispatchers.IO) { clearAppTransientCaches(context) }
                    cacheBytes = remaining
                    player.actionMessage = clearedMessage
                }
            }
        }
    }
    if (whatsNew) AppUpdate.available?.let { WhatsNewSheet(it, onDismiss = { whatsNew = false }) }
}

private val PRESERVED_CACHE_ENTRIES = setOf("clipflow_yt_auth_cookies.txt", "update")

private fun measureClearableCacheBytes(context: android.content.Context): Long {
    fun dirBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles().orEmpty().sumOf { child ->
            if (child.name in PRESERVED_CACHE_ENTRIES) 0L else dirBytes(child)
        }
    }
    return dirBytes(context.cacheDir) + dirBytes(context.codeCacheDir) + dirBytes(context.externalCacheDir)
}

@OptIn(ExperimentalCoilApi::class)
private fun clearAppTransientCaches(context: android.content.Context): Long {
    StreamCache.invalidateAll()
    runCatching {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }
    fun wipeChildren(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles().orEmpty().forEach { child ->
            if (child.name !in PRESERVED_CACHE_ENTRIES) {
                runCatching { child.deleteRecursively() }
            }
        }
    }
    wipeChildren(context.cacheDir)
    wipeChildren(context.codeCacheDir)
    wipeChildren(context.externalCacheDir)
    return measureClearableCacheBytes(context)
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
