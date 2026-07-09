package com.zune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import com.zune.player.R
import androidx.media3.common.Player
import com.zune.player.data.AudioItem
import com.zune.player.player.AudioPlayer
import com.zune.player.ui.components.PivotLayout
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.theme.LocalZuneAccent
import com.zune.player.ui.theme.ZuneAccent
import com.zune.player.ui.theme.AeroBlueOrbGradient
import com.zune.player.ui.theme.ZuneTextPrimary
import com.zune.player.ui.theme.ZuneTextSecondary
import com.zune.player.ui.theme.ZuneTypography
import com.zune.player.ui.theme.SegoeUiLightFontFamily
import com.zune.player.ui.theme.SegoeUiFontFamily
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun HomeScreen(
    initialPage: Int = 1,
    player: AudioPlayer,
    audioItems: List<AudioItem>,
    pinnedItems: List<Pair<Long, Int>>,
    onNavigateToNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    onNavigateToCategory: (String) -> Unit,
    onNavigateToPhotos: (Long?) -> Unit = {},
    onNavigateToVideos: (Long?) -> Unit = {},
    onPlayAlbum: (String) -> Unit,
    onPlaySong: (AudioItem) -> Unit,
    onUnpin: (Long) -> Unit,
    onCycleSize: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    onScroll: (Float) -> Unit = {},
    isAeroTheme: Boolean = false,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> },
    onPageSelected: (Int) -> Unit = {}
) {
    val pages = listOf(0, 1)

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedBg by remember { mutableStateOf(prefs.getInt("bg_selection", 0)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasNotifPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasNotifPermission) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        val isListenerEnabled = try {
            val pkgName = context.packageName
            val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            flat?.split(":")?.any {
                val cn = android.content.ComponentName.unflattenFromString(it)
                cn != null && cn.packageName == pkgName
            } == true
        } catch (e: Exception) {
            false
        }
        
        if (!isListenerEnabled) {
            try {
                val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                android.widget.Toast.makeText(context, "please enable zune live tiles listener in settings", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "bg_selection") {
                selectedBg = sharedPreferences.getInt("bg_selection", 0)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var photosList by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var videosList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reloadTrigger++
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(reloadTrigger) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pList = queryLocalPhotos(context)
            val vList = queryLocalVideos(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                photosList = pList
                videosList = vList
            }
        }
    }

    val resolvedPinnedTiles = remember(pinnedItems, audioItems, photosList, videosList) {
        pinnedItems.mapNotNull { p ->
            val rawId = p.first
            val size = p.second
            val prefix = rawId ushr 60
            when (prefix) {
                1L -> {
                    val originalId = rawId xor 0x1000000000000000L
                    val photo = photosList.find { it.id == originalId }
                    if (photo != null) {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "photo",
                                title = photo.title,
                                subtitle = "photo",
                                imageUri = photo.uri,
                                gradientColors = photo.gradientColors,
                                size = size
                            ),
                            size
                        )
                    } else {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "photo",
                                title = "photo",
                                subtitle = "photo",
                                imageUri = null,
                                gradientColors = listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
                                size = size
                            ),
                            size
                        )
                    }
                }
                2L -> {
                    val originalId = rawId xor 0x2000000000000000L
                    val video = videosList.find { it.id == originalId }
                    if (video != null) {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "video",
                                title = video.title,
                                subtitle = "video",
                                imageUri = video.uri,
                                gradientColors = video.gradientColors,
                                size = size
                            ),
                            size
                        )
                    } else {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "video",
                                title = "video",
                                subtitle = "video",
                                imageUri = null,
                                gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
                                size = size
                            ),
                            size
                        )
                    }
                }
                3L -> {
                    val packageManager = context.packageManager
                    val pmIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfos = try {
                        packageManager.queryIntentActivities(pmIntent, 0)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val appInfo = resolveInfos.find { ri ->
                        val pkgName = ri.activityInfo.packageName
                        val appHash = pkgName.hashCode().toLong() and 0x0FFFFFFFFFFFFFFFL
                        val appId = appHash or 0x3000000000000000L
                        appId == rawId
                    }
                    if (appInfo != null) {
                        val appLabel = appInfo.loadLabel(packageManager).toString()
                        val rawIcon = appInfo.loadIcon(packageManager)
                        val appIcon = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && rawIcon is android.graphics.drawable.AdaptiveIconDrawable) {
                            try {
                                val size = 512
                                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                rawIcon.background.bounds = android.graphics.Rect(0, 0, size, size)
                                rawIcon.background.draw(canvas)
                                rawIcon.foreground.bounds = android.graphics.Rect(0, 0, size, size)
                                rawIcon.foreground.draw(canvas)
                                bitmap
                            } catch (e: Exception) {
                                rawIcon
                            }
                        } else {
                            rawIcon
                        }
                        val pkgName = appInfo.activityInfo.packageName
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "app",
                                title = appLabel,
                                subtitle = pkgName,
                                imageUri = appIcon,
                                gradientColors = emptyList(),
                                size = size
                            ),
                            size
                        )
                    } else {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "app",
                                title = "app",
                                subtitle = "unknown",
                                imageUri = null,
                                gradientColors = listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
                                size = size
                            ),
                            size
                        )
                    }
                }
                else -> {
                    val song = audioItems.find { it.id == rawId }
                    if (song != null) {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "song",
                                title = song.title,
                                subtitle = song.artist,
                                imageUri = song.albumArtUri,
                                gradientColors = emptyList(),
                                size = size
                            ),
                            size
                        )
                    } else null
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        PivotLayout(
            title = null,
            pages = pages,
            initialPage = initialPage,
            isBlackBackground = selectedBg == 0,
            isAeroTheme = isAeroTheme,
            onOffsetChanged = onScroll,
            onPageSelected = onPageSelected
        ) { page ->
            when (page) {
                0 -> {
                    val quickplayScrollState = rememberScrollState(
                        initial = getScrollPosition("home_quickplay").first
                    )
                    DisposableEffect(quickplayScrollState) {
                        onDispose {
                            onScrollPositionChanged("home_quickplay", quickplayScrollState.value, 0)
                        }
                    }
                    val currentPlaying by player.currentAudio.collectAsState()

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.72f)
                            .verticalScroll(quickplayScrollState)
                            .padding(bottom = 48.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Now Playing sub-section
                        Text(
                            text = "Now Playing",
                            style = ZuneTypography.h4.copy(
                                fontSize = 30.sp,
                                fontFamily = SegoeUiLightFontFamily,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 2.dp)
                        )
                        NowPlayingPanel(
                            player = player,
                            onNavigateToNowPlaying = onNavigateToNowPlaying,
                            onOpenQueue = onOpenQueue,
                            isAeroTheme = isAeroTheme
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Pins sub-section
                        Text(
                            text = "Pins",
                            style = ZuneTypography.h4.copy(
                                fontSize = 50.sp,
                                fontFamily = SegoeUiLightFontFamily,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(start = 24.dp, end = 0.dp, top = 8.dp, bottom = 0.dp)
                        )
                        PinnedPage(
                            pinnedItems = resolvedPinnedTiles,
                            currentPlayingId = currentPlaying?.id,
                            onPlay = { tile ->
                                when (tile.type) {
                                    "song" -> {
                                        val song = audioItems.find { it.id == tile.id }
                                        if (song != null) onPlaySong(song)
                                    }
                                    "photo" -> {
                                        onNavigateToPhotos(tile.id xor 0x1000000000000000L)
                                    }
                                    "video" -> {
                                        onNavigateToVideos(tile.id xor 0x2000000000000000L)
                                    }
                                    "app" -> {
                                        try {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(tile.subtitle)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            } else {
                                                android.widget.Toast.makeText(context, "Could not open app", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "App not found", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onUnpin = onUnpin,
                            onCycleSize = onCycleSize,
                            onMove = onMove,
                            isAeroTheme = isAeroTheme,
                            getScrollPosition = getScrollPosition,
                            onScrollPositionChanged = onScrollPositionChanged,
                            isNested = true
                        )

                        Spacer(modifier = Modifier.height(4.dp))


                        // Featured sub-section
                        Text(
                            text = " Featured",
                            style = ZuneTypography.h4.copy(
                                fontSize = 50.sp,
                                fontFamily = SegoeUiLightFontFamily,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(start = 24.dp, end = 0.dp, top = 8.dp, bottom = 0.dp)
                        )
                        FeaturedSectionView(
                            audioItems = audioItems,
                            onPlayAlbum = onPlayAlbum,
                            isAeroTheme = isAeroTheme
                        )
                    }
                }
                1 -> Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        MusicPage(
                            player = player,
                            onNavigateToNowPlaying = onNavigateToNowPlaying,
                            onNavigateToCategory = onNavigateToCategory,
                            onScroll = {},
                            isAeroTheme = isAeroTheme,
                            isBlackBackground = selectedBg == 0,
                            getScrollPosition = getScrollPosition,
                            onScrollPositionChanged = onScrollPositionChanged
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun SmallClock(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        while (true) {
            timeText = sdf.format(java.util.Date()).lowercase()
            kotlinx.coroutines.delay(1000)
        }
    }
    Text(
        text = timeText,
        style = ZuneTypography.body2.copy(
            fontFamily = SegoeUiFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        ),
        color = Color.White.copy(alpha = 0.6f),
        modifier = modifier
    )
}

@Composable
fun NowPlayingPanel(
    player: AudioPlayer,
    onNavigateToNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    isAeroTheme: Boolean = false
) {
    val currentItem by player.currentAudio.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val accent = LocalZuneAccent.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        val artGlassModifier = if (isAeroTheme) {
            Modifier
                .border(
                    width = 1.dp,
                    color = Color.Black.copy(alpha = 0.40f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(1.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.60f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = RoundedCornerShape(5.dp)
                )
                .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(5.dp))
                .clip(RoundedCornerShape(5.dp))
        } else {
            Modifier
                .background(Color(0xFF1A1A1A))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.0f)
                .then(artGlassModifier)
                .metroClickable { onNavigateToNowPlaying() },
            contentAlignment = Alignment.BottomStart
        ) {
            if (currentItem?.albumArtUri != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = currentItem?.albumArtUri,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isBuffering) {
                        androidx.compose.material.CircularProgressIndicator(
                            color = accent,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accent.copy(alpha = 0.15f))
                )
            }

            if (isAeroTheme) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w * 0.4f, 0f)
                        lineTo(0f, h * 0.4f)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w * 0.3f, h * 0.3f)
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.BottomStart)
            ) {
                Text(
                    text = currentItem?.title?.lowercase() ?: " ",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SegoeUiFontFamily,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (currentItem != null) {
                    Text(
                        text = (currentItem?.artist?.lowercase() ?: "").let { if (it.isNotBlank()) "by $it" else "" },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontFamily = SegoeUiFontFamily,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, isAeroTheme: Boolean = false) {
    val displayTitle = if (isAeroTheme) {
        title.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    } else {
        title.uppercase()
    }
    Text(
        text = displayTitle,
        style = if (isAeroTheme) {
            ZuneTypography.h2.copy(
                fontSize = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                letterSpacing = 0.5.sp,
                brush = AeroBlueOrbGradient
            )
        } else {
            ZuneTypography.h2.copy(
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                letterSpacing = 2.sp
            )
        },
        color = if (isAeroTheme) Color.Unspecified else Color.White,
        modifier = Modifier.padding(start = 24.dp, bottom = 16.dp, top = 32.dp)
    )
}

@Composable
fun MusicPage(
    player: AudioPlayer,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToCategory: (String) -> Unit,
    onScroll: (Float) -> Unit = {},
    isAeroTheme: Boolean = false,
    isBlackBackground: Boolean = false,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val currentItem by player.currentAudio.collectAsState()
    val initialPos = remember { getScrollPosition("home_music") }
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialPos.first,
        initialFirstVisibleItemScrollOffset = initialPos.second
    )

    DisposableEffect(scrollState) {
        onDispose {
            onScrollPositionChanged("home_music", scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var isLauncher by remember { mutableStateOf(isDefaultLauncher(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isLauncher = isDefaultLauncher(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val categories = remember(isLauncher) {
        if (isLauncher) {
            listOf("music", "videos", "pictures", "podcasts", "apps", "search", "settings")
        } else {
            listOf("music", "videos", "pictures", "podcasts", "search", "settings")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.65f)
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            itemsIndexed(categories, key = { _, category -> category }) { index, category ->
                val accentColor = LocalZuneAccent.current
                val textColor = if (isAeroTheme) {
                    accentColor.copy(alpha = 0.7f)
                } else {
                    if (isBlackBackground) accentColor.lightenForText() else Color.White.copy(alpha = 0.6f)
                }
                Text(
                    text = category,
                    style = ZuneTypography.h2.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 56.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    ),
                    color = textColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .metroClickable {
                            onNavigateToCategory(category)
                        }
                )
            }
        }
    }
}

@Composable
fun FeaturedAlbumsPage(
    audioItems: List<AudioItem>,
    onPlayAlbum: (String) -> Unit,
    onScroll: (Float) -> Unit = {},
    isAeroTheme: Boolean = false,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val initialPos = remember { getScrollPosition("home_featured") }
    val scrollState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = initialPos.first,
        initialFirstVisibleItemScrollOffset = initialPos.second
    )

    DisposableEffect(scrollState) {
        onDispose {
            onScrollPositionChanged("home_featured", scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset)
        }
    }

    val albums = remember(audioItems) {
        audioItems.distinctBy { it.album }.shuffled().take(8)
    }

    LazyVerticalGrid(
        state = scrollState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(albums, key = { _, item -> item.id }) { index, item ->
            val cardGlassModifier = if (isAeroTheme) {
                Modifier
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(1.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        ),
                        shape = RoundedCornerShape(5.dp)
                    )
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(5.dp))
                    .clip(RoundedCornerShape(5.dp))
            } else {
                Modifier
                    .background(Color(0xFF1E1E1E))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .then(cardGlassModifier)
                    .metroClickable { onPlayAlbum(item.album) },
                contentAlignment = Alignment.BottomStart
            ) {
                if (item.albumArtUri != null) {
                    AsyncImage(
                        model = item.albumArtUri,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isAeroTheme) {
                    val aeroBrush = remember {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.5f),
                            0.5f to Color.White.copy(alpha = 0.1f),
                            0.5f to Color.Transparent,
                            1f to Color.Transparent
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().background(aeroBrush))
                }
            }
        }
    }
}

@Composable
fun PersonalizePage(
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedBg by remember { mutableStateOf(prefs.getInt("bg_selection", 0)) }

    val options = listOf(
        0 to "pure black",
        com.zune.player.R.drawable.bg_1 to "background 1",
        com.zune.player.R.drawable.bg_2 to "background 2",
        com.zune.player.R.drawable.bg_3 to "background 3",
        -1 to "custom image"
    )

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            prefs.edit()
                .putInt("bg_selection", -1)
                .putString("bg_custom_uri", uri.toString())
                .apply()
            selectedBg = -1
        }
    }

    val initialPos = remember { getScrollPosition("home_personalize") }
    val scrollState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = initialPos.first,
        initialFirstVisibleItemScrollOffset = initialPos.second
    )
    DisposableEffect(scrollState) {
        onDispose {
            onScrollPositionChanged("home_personalize", scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset)
        }
    }

    var accentSource by remember { mutableStateOf(prefs.getString("accent_source", "music") ?: "music") }
    var customAccentColorVal by remember { mutableIntStateOf(prefs.getInt("accent_custom_color", 0xFF0083D7.toInt())) }

    val metroColors = remember {
        listOf(
            Color(0xFFE5A600), // Gold / Amber
            Color(0xFF8CBF26), // Lime Green
            Color(0xFF0083D7), // Sky Blue / Aero Blue
            Color(0xFFE51400), // Red
            Color(0xFF339933), // Green
            Color(0xFF9900FF), // Violet / Purple
            Color(0xFFA55112), // Brown
            Color(0xFFDF0024), // Crimson
            Color(0xFFF0A30A), // Yellow
            Color(0xFF1BA1E2), // Cyan
            Color(0xFFD80073), // Magenta / Hot Pink
            Color(0xFFA2C139), // Grass Green
            Color(0xFF0050EF), // Cobalt Blue
            Color(0xFF6A00FF), // Indigo
            Color(0xFFE3C800), // Yellow-Gold
            Color(0xFFF472D0), // Pink
            Color(0xFFE05206), // Orange
            Color(0xFF00ABA9), // Teal
            Color(0xFF2D89EF), // Steel Blue
            Color(0xFF647687), // Slate
            Color(0xFF76608A), // Mauve
            Color(0xFF87794E), // Olive
            Color(0xFF6D8764), // Sage
            Color(0xFFBD761E)  // Peach / Ochre
        )
    }

    LazyVerticalGrid(
        state = scrollState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Accent Color Source Selection
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    text = "ACCENT COLOR SOURCE",
                    style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                    color = ZuneTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        text = "music dynamic",
                        color = if (accentSource == "music") Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (accentSource == "music") FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            accentSource = "music"
                            prefs.edit().putString("accent_source", "music").apply()
                        }
                    )
                    Text(
                        text = "custom color",
                        color = if (accentSource == "custom") Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (accentSource == "custom") FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            accentSource = "custom"
                            prefs.edit().putString("accent_source", "custom").apply()
                        }
                    )
                }
            }
        }

        // Section: Accent Custom Color Picker Bar
        if (accentSource == "custom") {
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(
                        text = "CHOOSE ACCENT COLOR",
                        style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                        color = ZuneTextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    val lazyRowState = androidx.compose.foundation.lazy.rememberLazyListState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = Color.White.copy(alpha = 0.2f))
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        androidx.compose.foundation.lazy.LazyRow(
                            state = lazyRowState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(metroColors) { color ->
                                val isColorSelected = customAccentColorVal == color.toArgb()
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(36.dp)
                                        .metroClickable {
                                            customAccentColorVal = color.toArgb()
                                            prefs.edit().putInt("accent_custom_color", color.toArgb()).apply()
                                        }
                                ) {
                                    Text(
                                        text = "▼",
                                        fontSize = 8.sp,
                                        color = if (isColorSelected) Color.White else Color.Transparent,
                                        modifier = Modifier.height(10.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(color)
                                            .border(
                                                width = if (isColorSelected) 2.dp else 0.dp,
                                                color = if (isColorSelected) Color.White else Color.Transparent
                                            )
                                    )
                                    Text(
                                        text = "▲",
                                        fontSize = 8.sp,
                                        color = if (isColorSelected) Color.White else Color.Transparent,
                                        modifier = Modifier.height(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Header for Background settings
        item(span = { GridItemSpan(2) }) {
            Text(
                text = "WALLPAPER BACKGROUND",
                style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                color = ZuneTextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        itemsIndexed(options, key = { _, option -> option.first }) { index, (drawableRes, label) ->
            val isSelected = selectedBg == drawableRes
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .metroClickable {
                        if (drawableRes == -1) {
                            pickerLauncher.launch("image/*")
                        } else {
                            selectedBg = drawableRes
                            prefs.edit().putInt("bg_selection", drawableRes).apply()
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (drawableRes == 0) Color.Black else Color.Transparent)
                        .padding(if (isSelected) 4.dp else 0.dp)
                        .then(
                            if (isSelected) Modifier.background(LocalZuneAccent.current).padding(4.dp)
                            else Modifier
                        )
                ) {
                    if (drawableRes == -1) {
                        val customBgUriStr = remember(selectedBg) { prefs.getString("bg_custom_uri", null) }
                        if (!customBgUriStr.isNullOrEmpty()) {
                            AsyncImage(
                                model = android.net.Uri.parse(customBgUriStr),
                                contentDescription = label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF222222)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "tap to choose",
                                    style = ZuneTypography.body2.copy(color = Color.White.copy(alpha = 0.6f))
                                )
                            }
                        }
                    } else if (drawableRes != 0) {
                        AsyncImage(
                            model = drawableRes,
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = ZuneTypography.body2,
                    color = if (isSelected) LocalZuneAccent.current else Color.White
                )
            }
        }
    }
}

@Composable
fun PlaceholderPage(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = "no $title found.",
            style = ZuneTypography.body1,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp)
        )
    }
}

private fun Color.lightenForText(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = hsl[2].coerceAtLeast(0.6f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
fun rememberVideoTileThumbnail(context: android.content.Context, videoUri: android.net.Uri?): android.graphics.Bitmap? {
    var bitmap by remember(videoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(videoUri, android.util.Size(512, 512), null)
                    } else {
                        var retriever: android.media.MediaMetadataRetriever? = null
                        try {
                            retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(context, videoUri)
                            retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } finally {
                            retriever?.release()
                        }
                    }
                    if (bmp != null) {
                        bitmap = bmp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    return bitmap
}

@Composable
fun TileEqualizer(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "EqAnim")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqBar1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqBar2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqBar3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqBar4"
    )

    Row(
        modifier = modifier.height(24.dp).width(36.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(h1, h2, h3, h4)
        heights.forEach { heightVal ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightVal)
                    .background(color)
            )
        }
    }
}

@Composable
fun PinnedTileView(
    tileItem: PinnedTileItem,
    size: Int,
    isPlaying: Boolean,
    isEditMode: Boolean,
    isHovered: Boolean,
    isDragged: Boolean,
    dragOffset: Offset,
    isAeroTheme: Boolean,
    onPlay: (PinnedTileItem) -> Unit,
    onUnpin: (Long) -> Unit,
    onCycleSize: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val videoThumbnail = if (tileItem.type == "video") rememberVideoTileThumbnail(context, tileItem.imageUri as? android.net.Uri) else null

    // Determine photo/gallery app exception
    val isPhotoOrGalleryApp = remember(tileItem) {
        tileItem.type == "app" && (tileItem.title.lowercase().contains("photo") || tileItem.title.lowercase().contains("gallery"))
    }
    val shouldLoadPhotos = tileItem.type == "photo" || isPhotoOrGalleryApp

    // Photo cycling slideshow
    val localPhotos = remember { mutableStateListOf<PhotoItem>() }
    LaunchedEffect(shouldLoadPhotos) {
        if (shouldLoadPhotos) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val list = queryLocalPhotos(context)
                if (list.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        localPhotos.clear()
                        localPhotos.addAll(list)
                    }
                }
            }
        }
    }

    // Notification Reader for app status using ZuneNotificationListenerService
    val activeNotifications by com.zune.player.service.ZuneNotificationListenerService.activeNotifications.collectAsState()
    val matchingNotifs = remember(activeNotifications, tileItem.title) {
        val titleLabel = tileItem.title.lowercase().trim()
        activeNotifications.filter { sbn ->
            val pkg = sbn.packageName.lowercase()
            val appLabel = try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString().lowercase()
            } catch (e: Exception) {
                ""
            }
            
            // Check direct match
            val isDirectMatch = pkg.contains(titleLabel) || appLabel.contains(titleLabel)
            
            // Check generic alias maps for native Metro/Zune apps to standard Android apps
            isDirectMatch || when (titleLabel) {
                "phone", "dialer", "call", "calls" -> {
                    pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("telecom") || pkg.contains("telephony") ||
                    appLabel.contains("phone") || appLabel.contains("dialer") || appLabel.contains("call")
                }
                "people", "contacts" -> {
                    pkg.contains("contacts") || pkg.contains("people") ||
                    appLabel.contains("contacts") || appLabel.contains("people")
                }
                "messaging", "messages", "sms", "text" -> {
                    pkg.contains("messaging") || pkg.contains("message") || pkg.contains("mms") || pkg.contains("sms") ||
                    appLabel.contains("message") || appLabel.contains("messaging") || appLabel.contains("sms") || appLabel.contains("chat")
                }
                "email", "mail", "gmail", "outlook" -> {
                    pkg.contains("mail") || pkg.contains("gm") || pkg.contains("outlook") ||
                    appLabel.contains("mail") || appLabel.contains("gmail") || appLabel.contains("outlook")
                }
                "internet", "browser", "chrome", "explorer" -> {
                    pkg.contains("browser") || pkg.contains("chrome") || pkg.contains("webview") || pkg.contains("firefox") ||
                    appLabel.contains("browser") || appLabel.contains("chrome") || appLabel.contains("internet")
                }
                "music", "zune", "player" -> {
                    pkg.contains("music") || pkg.contains("player") || pkg.contains("zune") ||
                    appLabel.contains("music") || appLabel.contains("player") || appLabel.contains("zune")
                }
                "photos", "gallery", "camera" -> {
                    pkg.contains("photo") || pkg.contains("gallery") || pkg.contains("camera") || pkg.contains("media") ||
                    appLabel.contains("photo") || appLabel.contains("gallery") || appLabel.contains("camera")
                }
                else -> false
            }
        }
    }

    var currentNotifIndex by remember { mutableIntStateOf(0) }
    var activeNotificationsText by remember { mutableStateOf("") }
    
    // Periodic update fallback to ensure listener service list is updated
    LaunchedEffect(Unit) {
        while (true) {
            try {
                com.zune.player.service.ZuneNotificationListenerService.updateNotifications()
            } catch (e: Exception) {
                // ignore
            }
            delay(5000)
        }
    }

    LaunchedEffect(matchingNotifs, currentNotifIndex, tileItem.title) {
        if (matchingNotifs.isNotEmpty()) {
            val matchingNotif = matchingNotifs.getOrNull(currentNotifIndex % matchingNotifs.size) ?: matchingNotifs.first()
            val notif = matchingNotif.notification
            val extras = notif?.extras
            val appLabel = try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(matchingNotif.packageName, 0)).toString()
            } catch (e: Exception) {
                matchingNotif.packageName
            }
            
            // Extract title and text
            val titleStr = extras?.get("android.title")?.toString() ?: extras?.get("android.title.big")?.toString()
            var textStr = extras?.get("android.text")?.toString()
            if (textStr.isNullOrEmpty()) {
                textStr = extras?.get("android.bigText")?.toString()
            }
            if (textStr.isNullOrEmpty()) {
                val lines = extras?.getCharSequenceArray("android.textLines")
                if (lines != null && lines.isNotEmpty()) {
                    textStr = lines.lastOrNull()?.toString()
                }
            }
            if (textStr.isNullOrEmpty()) {
                textStr = notif?.tickerText?.toString()
            }
            
            val detailsText = when {
                !titleStr.isNullOrEmpty() && !textStr.isNullOrEmpty() -> "$titleStr: $textStr"
                !titleStr.isNullOrEmpty() -> titleStr
                !textStr.isNullOrEmpty() -> textStr
                else -> ""
            }
            
            activeNotificationsText = if (detailsText.isNotEmpty()) {
                detailsText
            } else {
                appLabel
            }
        } else {
            activeNotificationsText = "no notifications"
        }
    }

    // Check if live tile updates are allowed
    val isLiveAllowed = remember(tileItem, activeNotificationsText) {
        if (tileItem.type != "app") {
            true
        } else {
            val label = tileItem.title.lowercase()
            val isException = label.contains("photo") || label.contains("gallery")
            // Calendar and Clock are statically rendered on front face, so they don't slide/flip
            val isCalOrClock = label.contains("calendar") || label.contains("clock") || label.contains("time")
            !isCalOrClock && (isException || (activeNotificationsText.isNotEmpty() && activeNotificationsText != "no notifications"))
        }
    }

    var tileState by remember { mutableIntStateOf(0) }

    if (size > 1 || tileItem.type == "app") {
        LaunchedEffect(tileItem.id, isLiveAllowed) {
            if (!isLiveAllowed) {
                tileState = 0
                return@LaunchedEffect
            }
            delay(kotlin.random.Random.nextLong(500, 3000))
            while (true) {
                delay(kotlin.random.Random.nextLong(4000, 10000))
                tileState = if (tileState == 0) {
                    if (kotlin.random.Random.nextBoolean()) 1 else 2
                } else {
                    0
                }
            }
        }
    }

    var currentPhotoIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(tileState) {
        if (tileState == 0 && localPhotos.isNotEmpty()) {
            currentPhotoIndex = (currentPhotoIndex + 1) % localPhotos.size
        }
    }

    LaunchedEffect(tileState) {
        if (tileState == 0 && matchingNotifs.isNotEmpty()) {
            currentNotifIndex = (currentNotifIndex + 1) % matchingNotifs.size
        }
    }

    val activePhotoUri = if (shouldLoadPhotos && localPhotos.isNotEmpty()) {
        localPhotos.getOrNull(currentPhotoIndex)?.uri
    } else {
        null
    }

    val backPhotoUri = if (shouldLoadPhotos && localPhotos.size > 1) {
        localPhotos.getOrNull((currentPhotoIndex + 1) % localPhotos.size)?.uri
    } else {
        null
    }

    val imageModel = if (tileItem.type == "video") videoThumbnail else (activePhotoUri ?: tileItem.imageUri)

    // Dynamic Live Tile transitions based on tile ID
    val transitionStyle = remember(tileItem.id) {
        (tileItem.id % 3).toInt() // 0 = 3D Flip, 1 = Vertical Slide, 2 = Horizontal Slide
    }

    val flipAngle by animateFloatAsState(
        targetValue = if (tileState != 0) 180f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "TileFlipAngle"
    )

    val slidePercent by animateFloatAsState(
        targetValue = when (tileState) {
            1 -> if (tileItem.type == "song") 0.5f else 1f
            2 -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "TileSlidePercent"
    )

    val isFlipped = transitionStyle == 0 && flipAngle > 90f

    // Dynamic text info for Calendar and Clock exceptions
    val calendarText = remember {
        val sdf = java.text.SimpleDateFormat("EEEE, MMMM dd", java.util.Locale.US)
        sdf.format(java.util.Date())
    }

    var currentTimeText by remember { mutableStateOf("") }
    LaunchedEffect(tileItem.id) {
        val label = tileItem.title.lowercase()
        if (label.contains("clock") || label.contains("time")) {
            while (true) {
                val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
                currentTimeText = sdf.format(java.util.Date())
                delay(10000)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        val isCalendar = remember(tileItem) { tileItem.title.lowercase().contains("calendar") }
        val isClock = remember(tileItem) { tileItem.title.lowercase().contains("clock") || tileItem.title.lowercase().contains("time") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .then(
                    if (isAeroTheme) {
                        val tileShape = RoundedCornerShape(6.dp)
                        val innerShape = RoundedCornerShape(5.dp)
                        if (isPlaying) {
                            Modifier
                                .border(width = 1.dp, color = LocalZuneAccent.current.copy(alpha = 0.5f), shape = tileShape)
                                .padding(1.dp)
                                .border(width = 1.5.dp, brush = Brush.verticalGradient(listOf(Color.White, LocalZuneAccent.current)), shape = innerShape)
                                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), LocalZuneAccent.current.copy(alpha = 0.08f))), shape = innerShape)
                                .clip(innerShape)
                        } else {
                            Modifier
                                .border(width = 1.dp, color = Color.Black.copy(alpha = 0.35f), shape = tileShape)
                                .padding(1.dp)
                                .border(width = 1.dp, brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))), shape = innerShape)
                                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))), shape = innerShape)
                                .clip(innerShape)
                        }
                    } else {
                        Modifier
                            .then(if (isPlaying) Modifier.border(3.dp, LocalZuneAccent.current) else Modifier)
                            .background(LocalZuneAccent.current)
                    }
                )
                .graphicsLayer {
                    if (transitionStyle == 0) {
                        rotationY = flipAngle
                        cameraDistance = 12f * density
                    }
                }
        ) {
            if (isFlipped) {
                // BACK FACE (Flip details)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                ) {
                    if (shouldLoadPhotos && backPhotoUri != null) {
                        AsyncImage(
                            model = backPhotoUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val backText = remember(tileItem, activeNotificationsText) {
                                when (tileItem.type) {
                                    "app" -> activeNotificationsText.lowercase()
                                    "song" -> "artist: ${tileItem.subtitle.lowercase()}"
                                    "photo" -> "view gallery"
                                    "video" -> "play clip"
                                    else -> "pin status"
                                }
                            }
                            val adjustedBackFontSize = remember(backText, size) {
                                val length = backText.length
                                val base = if (size == 4) 18 else 13
                                val adjusted = when {
                                    length > 40 -> base - 3
                                    length > 20 -> base - 1
                                    else -> base
                                }
                                adjusted.coerceAtLeast(10).sp
                            }
                            Text(
                                text = backText,
                                style = ZuneTypography.h2.copy(fontSize = adjustedBackFontSize),
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // FRONT FACE
                if (isCalendar) {
                    val dayOfWeekAbbr = remember {
                        val sdf = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
                        sdf.format(java.util.Date()).lowercase()
                    }
                    val dayOfMonth = remember {
                        val sdf = java.text.SimpleDateFormat("dd", java.util.Locale.US)
                        sdf.format(java.util.Date())
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // Top-left: Event info
                        Column(modifier = Modifier.align(Alignment.TopStart)) {
                            Text(
                                text = "no upcoming events",
                                style = ZuneTypography.h2.copy(
                                    fontSize = if (size == 4) 14.sp else 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = SegoeUiFontFamily
                                ),
                                color = Color.White
                            )
                        }

                        // Bottom-right: Day and Date
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = dayOfWeekAbbr,
                                style = ZuneTypography.h2.copy(
                                    fontSize = if (size == 4) 18.sp else 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = SegoeUiFontFamily
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = dayOfMonth,
                                style = ZuneTypography.h1.copy(
                                    fontSize = if (size == 4) 54.sp else 42.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = SegoeUiLightFontFamily
                                ),
                                color = Color.White
                            )
                        }
                    }
                } else if (isClock) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Text(
                            text = currentTimeText.lowercase(),
                            style = ZuneTypography.h1.copy(
                                fontSize = if (size == 4) 32.sp else 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SegoeUiLightFontFamily
                            ),
                            color = Color.White
                        )
                    }
                } else {
                    // Under slide details shown under slides
                    if (size > 1 && transitionStyle != 0) {
                        if (shouldLoadPhotos && backPhotoUri != null) {
                            AsyncImage(
                                model = backPhotoUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                val displayText = remember(tileItem, activeNotificationsText) {
                                    if (tileItem.type == "app" && activeNotificationsText.isNotEmpty() && activeNotificationsText != "no notifications") {
                                        activeNotificationsText
                                    } else {
                                        tileItem.title
                                    }
                                }
                                val adjustedSlideFontSize = remember(displayText, size) {
                                    val length = displayText.length
                                    val base = if (size == 4) 22 else 18
                                    val adjusted = when {
                                        length > 40 -> base - 6
                                        length > 20 -> base - 3
                                        else -> base
                                    }
                                    adjusted.coerceAtLeast(11).sp
                                }
                                Text(
                                    text = displayText.lowercase(),
                                    style = ZuneTypography.h2.copy(fontSize = adjustedSlideFontSize),
                                    color = Color.White,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Front slide container
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (transitionStyle == 1) {
                                    translationY = this.size.height * slidePercent
                                } else if (transitionStyle == 2) {
                                    translationX = this.size.width * slidePercent
                                }
                            }
                    ) {
                        if (imageModel != null) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = tileItem.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                alpha = if (isEditMode && !isHovered) 0.7f else 1f
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isAeroTheme) LocalZuneAccent.current.copy(alpha = 0.6f) else LocalZuneAccent.current)
                            )
                        }

                        // Notification count badge overlay on the front face of standard app tiles (excluding Calendar/Clock)
                        if (tileItem.type == "app" && matchingNotifs.isNotEmpty() && !isCalendar && !isClock) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = matchingNotifs.size.toString(),
                                    style = ZuneTypography.h1.copy(
                                        fontSize = if (size == 4) 28.sp else 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = SegoeUiFontFamily
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Title overlay at the bottom
                if ((tileItem.type == "app" || size > 1) && !isCalendar && !isClock) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tileItem.title.lowercase(),
                            style = ZuneTypography.h2.copy(
                                fontSize = if (size == 4) 14.sp else 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Edit control buttons overlay
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .metroClickable { onUnpin(tileItem.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Unpin", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .metroClickable { onCycleSize(tileItem.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun PinnedPage(
    pinnedItems: List<Pair<PinnedTileItem, Int>>,
    currentPlayingId: Long?,
    onPlay: (PinnedTileItem) -> Unit,
    onUnpin: (Long) -> Unit,
    onCycleSize: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    isAeroTheme: Boolean,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> },
    isNested: Boolean = false
) {
    var isEditMode by remember { mutableStateOf(false) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var hoveredId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var pointerOffset by remember { mutableStateOf(Offset.Zero) }
    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }

    val initialPos = remember { getScrollPosition("home_pinned") }
    val scrollState = if (isNested) null else rememberScrollState(initial = initialPos.first)

    if (!isNested && scrollState != null) {
        DisposableEffect(scrollState) {
            onDispose {
                onScrollPositionChanged("home_pinned", scrollState.value, 0)
            }
        }
    }

    val columnModifier = if (isNested) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState!!)
    }

    Column(
        modifier = columnModifier
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectTapGestures { isEditMode = false }
                }
            }
    ) {
        if (!isNested) {
            val pinsTitleStyle = if (isAeroTheme) {
                ZuneTypography.h2.copy(
                    fontSize = 48.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    letterSpacing = 0.sp,
                    brush = AeroBlueOrbGradient
                )
            } else {
                ZuneTypography.h2.copy(
                    fontSize = 80.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    letterSpacing = 2.sp
                )
            }
            val pinsTitleColor = if (isAeroTheme) Color.Unspecified else Color.White
            val pinsTitlePadding = if (isAeroTheme) {
                Modifier.padding(start = 24.dp, bottom = 16.dp, top = 24.dp)
            } else {
                Modifier.padding(start = 24.dp, bottom = 16.dp, top = 8.dp)
            }

            androidx.compose.material.Text(
                text = if (isAeroTheme) "Pins" else "pins",
                style = pinsTitleStyle,
                color = pinsTitleColor,
                modifier = pinsTitlePadding
            )
        }

        if (pinnedItems.isEmpty()) {
            PlaceholderPage("pinned items")
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val columns = if (isLandscape) 7 else 4
                val horizontalSpacing = 8.dp
                val verticalSpacing = 8.dp
                val colWidth = (maxWidth - horizontalSpacing * (columns - 1)) / columns

                val occupied = remember(pinnedItems, columns) {
                    mutableSetOf<Pair<Int, Int>>()
                }
                val placements = remember(pinnedItems, columns) {
                    val map = mutableMapOf<Long, Rect>()
                    occupied.clear()
                    for ((tileItem, size) in pinnedItems) {
                        val w = if (size == 4) 4 else if (size == 2) 2 else 1
                        val h = if (size == 4) 2 else if (size == 2) 2 else 1
                        var found = false
                        var searchY = 0
                        while (!found) {
                            for (searchX in 0..columns - w) {
                                var collision = false
                                for (dy in 0 until h) {
                                    for (dx in 0 until w) {
                                        if (occupied.contains(Pair(searchX + dx, searchY + dy))) {
                                            collision = true
                                            break
                                        }
                                    }
                                    if (collision) break
                                }
                                if (!collision) {
                                    for (dy in 0 until h) {
                                        for (dx in 0 until w) {
                                            occupied.add(Pair(searchX + dx, searchY + dy))
                                        }
                                    }
                                    map[tileItem.id] = Rect(
                                        left = searchX.toFloat(),
                                        top = searchY.toFloat(),
                                        right = (searchX + w).toFloat(),
                                        bottom = (searchY + h).toFloat()
                                    )
                                    found = true
                                    break
                                }
                            }
                            if (!found) searchY++
                        }
                    }
                    map
                }

                val maxY = if (occupied.isEmpty()) 0 else occupied.maxOf { it.second } + 1
                val totalHeight = if (maxY > 0) (colWidth * maxY) + (verticalSpacing * (maxY - 1)) else 0.dp

                Box(modifier = Modifier.fillMaxWidth().height(totalHeight + 0.dp).padding(top = 0.dp)) {
                    pinnedItems.forEachIndexed { index, (tileItem, size) ->
                        val rect = placements[tileItem.id] ?: return@forEachIndexed
                        val xOffset = (colWidth * rect.left) + (horizontalSpacing * rect.left)
                        val yOffset = (colWidth * rect.top) + (verticalSpacing * rect.top)
                        val animatedXOffset by animateDpAsState(
                            targetValue = xOffset,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                            label = "TileXOffset"
                        )
                        val animatedYOffset by animateDpAsState(
                            targetValue = yOffset,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                            label = "TileYOffset"
                        )
                        val width = (colWidth * rect.width) + (horizontalSpacing * (rect.width - 1f))
                        val height = (colWidth * rect.height) + (verticalSpacing * (rect.height - 1f))

                        val id = tileItem.id
                        val isDragged = draggedId == id
                        val isHovered = hoveredId == id
                        val isPlaying = currentPlayingId == id

                        val targetScale = if (isDragged) 1.05f else if (isEditMode) {
                            if (isHovered) 0.85f else 0.92f
                        } else 1f
                        val animatedScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                            label = "TileScale"
                        )

                        val targetAlpha = if (isDragged) 0.9f else if (isEditMode) {
                            if (isHovered) 0.5f else 1f
                        } else 1f
                        val animatedAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(durationMillis = 200),
                            label = "TileAlpha"
                        )

                        Box(
                            modifier = Modifier
                                .offset(x = animatedXOffset, y = animatedYOffset)
                                .size(width = width, height = height)
                                .onGloballyPositioned { coordinates ->
                                    itemBounds[id] = coordinates.boundsInWindow()
                                }
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    alpha = animatedAlpha
                                    if (isDragged) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                    }
                                }
                                .then(
                                    if (isAeroTheme) {
                                        val tileShape = RoundedCornerShape(6.dp)
                                        val innerShape = RoundedCornerShape(5.dp)
                                        if (isPlaying) {
                                            Modifier
                                                .border(
                                                    width = 1.dp,
                                                    color = LocalZuneAccent.current.copy(alpha = 0.5f),
                                                    shape = tileShape
                                                )
                                                .padding(1.dp)
                                                .border(
                                                    width = 1.5.dp,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White,
                                                            LocalZuneAccent.current
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.18f),
                                                            LocalZuneAccent.current.copy(alpha = 0.08f)
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .clip(innerShape)
                                        } else {
                                            Modifier
                                                .border(
                                                    width = 1.dp,
                                                    color = Color.Black.copy(alpha = 0.35f),
                                                    shape = tileShape
                                                )
                                                .padding(1.dp)
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.55f),
                                                            Color.White.copy(alpha = 0.08f)
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.14f),
                                                            Color.White.copy(alpha = 0.04f)
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .clip(innerShape)
                                        }
                                    } else {
                                        Modifier
                                            .then(if (isPlaying) Modifier.border(3.dp, LocalZuneAccent.current) else Modifier)
                                            .background(LocalZuneAccent.current)
                                    }
                                )
                                .pointerInput(isEditMode, id) {
                                    if (isEditMode) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                draggedId = id
                                                hoveredId = null
                                                dragOffset = Offset.Zero
                                                pointerOffset = offset
                                            },
                                            onDragEnd = {
                                                if (hoveredId != null && draggedId != null && hoveredId != draggedId) {
                                                    val sourceIndex = pinnedItems.indexOfFirst { it.first.id == draggedId }
                                                    val targetIndex = pinnedItems.indexOfFirst { it.first.id == hoveredId }
                                                    if (sourceIndex != -1 && targetIndex != -1) {
                                                        onMove(sourceIndex, targetIndex)
                                                    }
                                                }
                                                draggedId = null
                                                hoveredId = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggedId = null
                                                hoveredId = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount

                                                val myBounds = itemBounds[id] ?: return@detectDragGestures
                                                val absoluteFingerPos = myBounds.topLeft + pointerOffset + dragOffset

                                                var newHovered: Long? = null
                                                for ((targetId, bounds) in itemBounds) {
                                                    if (targetId != id && bounds.contains(absoluteFingerPos)) {
                                                        newHovered = targetId
                                                        break
                                                    }
                                                }

                                                if (newHovered == null) {
                                                    var closestItem: Long? = null
                                                    var minDistance = Float.MAX_VALUE

                                                    for ((targetId, bounds) in itemBounds) {
                                                        if (targetId == id) continue
                                                        val cx = bounds.left + bounds.width / 2f
                                                        val cy = bounds.top + bounds.height / 2f
                                                        val dx = cx - absoluteFingerPos.x
                                                        val dy = cy - absoluteFingerPos.y
                                                        val dist = dx * dx + dy * dy
                                                        if (dist < minDistance) {
                                                            minDistance = dist
                                                            closestItem = targetId
                                                        }
                                                    }
                                                    newHovered = closestItem
                                                }

                                                hoveredId = newHovered
                                            }
                                        )
                                    } else {
                                        detectTapGestures(
                                            onTap = { onPlay(tileItem) },
                                            onLongPress = { isEditMode = true }
                                        )
                                    }
                                }
                        ) {
                            PinnedTileView(
                                tileItem = tileItem,
                                size = size,
                                isPlaying = isPlaying,
                                isEditMode = isEditMode,
                                isHovered = isHovered,
                                isDragged = isDragged,
                                dragOffset = dragOffset,
                                isAeroTheme = isAeroTheme,
                                onPlay = onPlay,
                                onUnpin = onUnpin,
                                onCycleSize = onCycleSize
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun WmcStartOrbAndClock() {
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("h:mm", java.util.Locale.getDefault())
        while (true) {
            timeText = sdf.format(java.util.Date()).uppercase()
            kotlinx.coroutines.delay(1000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = timeText,
            style = ZuneTypography.h2.copy(
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}


data class PinnedTileItem(
    val id: Long,
    val type: String,
    val title: String,
    val subtitle: String,
    val imageUri: Any?,
    val gradientColors: List<Color>,
    val size: Int
)

private fun queryLocalPhotos(context: android.content.Context): List<PhotoItem> {
    val list = mutableListOf<PhotoItem>()
    val permissionString = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, permissionString) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
        try {
            val resolver = context.contentResolver
            val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Images.Media._ID,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                android.provider.MediaStore.Images.Media.DATE_TAKEN,
                android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            resolver.query(uri, projection, null, null, "${android.provider.MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                val bucketColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "photo_$id"
                    val date = cursor.getLong(dateColumn)
                    val album = cursor.getString(bucketColumn) ?: "camera roll"
                    val contentUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    list.add(
                        PhotoItem(
                            id = id,
                            uri = contentUri,
                            dateTaken = date,
                            albumName = album.lowercase(),
                            title = name
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (list.isEmpty()) {
        list.addAll(generateMockPhotos())
    }
    return list
}

private fun queryLocalVideos(context: android.content.Context): List<VideoItem> {
    val list = mutableListOf<VideoItem>()
    val permissionString = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, permissionString) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
        try {
            val resolver = context.contentResolver
            val uri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Video.Media._ID,
                android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                android.provider.MediaStore.Video.Media.DURATION,
                android.provider.MediaStore.Video.Media.DATE_ADDED
            )
            resolver.query(uri, projection, null, null, "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "video_$id"
                    val duration = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val contentUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    list.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = name.removeSuffix(".mp4").lowercase(),
                            subtitle = "local video",
                            durationMs = duration,
                            dateAdded = dateAdded,
                            gradientColors = listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
                            videoUrl = contentUri.toString()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (list.isEmpty()) {
        list.addAll(generateMockVideos())
    }
    return list.sortedByDescending { it.dateAdded }
}

private sealed class LiveTileItem {
    data class ImageUri(val uri: android.net.Uri) : LiveTileItem()
    data class Gradient(val colors: List<Color>) : LiveTileItem()
}

@Composable
fun PicturesAndVideosPagePreview(
    isAeroTheme: Boolean,
    photosList: List<PhotoItem>,
    videosList: List<VideoItem> = emptyList(),
    onNavigateToPhotos: () -> Unit,
    onNavigateToVideos: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val liveTileItems = remember(photosList) {
        val localPhotos = photosList.filter { it.uri != null }
        if (localPhotos.isNotEmpty()) {
            localPhotos.take(10).map { LiveTileItem.ImageUri(it.uri!!) }
        } else {
            photosList.take(10).map {
                if (it.gradientColors.isNotEmpty()) {
                    LiveTileItem.Gradient(it.gradientColors)
                } else {
                    LiveTileItem.Gradient(listOf(Color(0xFFEE0979), Color(0xFFFF6A00)))
                }
            }
        }
    }

    var currentIndex by remember { mutableStateOf(0) }
    LaunchedEffect(liveTileItems) {
        if (liveTileItems.isNotEmpty()) {
            while (true) {
                delay(6000)
                currentIndex = (currentIndex + 1) % liveTileItems.size
            }
        }
    }

    val latestVideo = remember(videosList) { videosList.firstOrNull() }
    val videoThumbnail = rememberVideoTileThumbnail(context, latestVideo?.uri)

    val infiniteTransition = rememberInfiniteTransition(label = "ken_burns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val previewHeight = if (isLandscape) 130.dp else 180.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(previewHeight),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val photosBorderModifier = Modifier
                .border(1.5.dp, Color.White.copy(alpha = 0.15f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(photosBorderModifier)
                    .background(Color(0xFF1E1E1E))
                    .metroClickable { onNavigateToPhotos() }
                    .clipToBounds(),
                contentAlignment = Alignment.BottomStart
            ) {
                if (liveTileItems.isNotEmpty()) {
                    val currentItem = liveTileItems.getOrNull(currentIndex)
                    AnimatedContent(
                        targetState = currentItem,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(1000)) + slideInVertically(
                                animationSpec = tween(1000),
                                initialOffsetY = { it }
                            )).togetherWith(
                                fadeOut(animationSpec = tween(1000)) + slideOutVertically(
                                    animationSpec = tween(1000),
                                    targetOffsetY = { -it }
                                )
                            )
                        },
                        label = "live_tile_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            when (item) {
                                is LiveTileItem.ImageUri -> {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                is LiveTileItem.Gradient -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(LocalZuneAccent.current)
                                    )
                                }
                                null -> {}
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalZuneAccent.current)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                startY = 80f
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "photos",
                        style = ZuneTypography.h4.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                        color = Color.White
                    )
                    Text(
                        text = "${photosList.size} items",
                        style = ZuneTypography.body2.copy(fontSize = 11.sp, fontFamily = SegoeUiFontFamily),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            val videosBorderModifier = Modifier
                .border(1.5.dp, Color.White.copy(alpha = 0.15f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(videosBorderModifier)
                    .background(Color(0xFF1E1E1E))
                    .metroClickable { onNavigateToVideos() }
                    .clipToBounds(),
                contentAlignment = Alignment.BottomStart
            ) {
                if (videoThumbnail != null) {
                    Image(
                        bitmap = videoThumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalZuneAccent.current)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                startY = 80f
                            )
                        )
                )

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(4.dp)
                        .align(Alignment.TopEnd)
                )

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "videos",
                        style = ZuneTypography.h4.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                        color = Color.White
                    )
                    Text(
                        text = "${videosList.size} clips",
                        style = ZuneTypography.body2.copy(fontSize = 11.sp, fontFamily = SegoeUiFontFamily),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Text(
            text = "open pictures + videos",
            style = ZuneTypography.h2.copy(
                fontFamily = SegoeUiLightFontFamily,
                fontSize = 24.sp,
                color = LocalZuneAccent.current
            ),
            modifier = Modifier.metroClickable { onNavigateToPhotos() }
        )
        Text(
            text = "view and organize photos and local videos from your device.",
            style = ZuneTypography.body1,
            color = ZuneTextSecondary
        )
    }
}

// Private helper function for formatting millisecond track timelines
private fun formatPanelTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "$minutes:${String.format("%02d", seconds)}"
}

@Composable
fun FeaturedSectionView(
    audioItems: List<AudioItem>,
    onPlayAlbum: (String) -> Unit,
    isAeroTheme: Boolean = false
) {
    val albums = remember(audioItems) {
        audioItems.distinctBy { it.album }.shuffled().take(4)
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        albums.chunked(2).forEach { rowAlbums ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowAlbums.forEach { item ->
                    val cardGlassModifier = Modifier.background(Color(0xFF1E1E1E))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(cardGlassModifier)
                            .metroClickable { onPlayAlbum(item.album) },
                        contentAlignment = Alignment.BottomStart
                    ) {
                        if (item.albumArtUri != null) {
                            AsyncImage(
                                model = item.albumArtUri,
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // No Aero overlay box
                    }
                }
                if (rowAlbums.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PersonalizeScreen(
    onBack: () -> Unit,
    isAeroTheme: Boolean = false,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.zune.player.R.drawable.zune_back),
            contentDescription = "Back",
            modifier = Modifier
                .padding(bottom = 4.dp)
                .offset(x = (-20).dp, y = (-8).dp)
                .size(80.dp)
                .metroClickable { onBack() }
        )

        Text(
            text = "SETTINGS",
            style = ZuneTypography.h4.copy(
                fontFamily = SegoeUiFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = ZuneTextSecondary,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
        )

        Text(
            text = "personalize",
            style = ZuneTypography.h1.copy(
                fontFamily = SegoeUiFontFamily,
                fontSize = 42.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            val context = androidx.compose.ui.platform.LocalContext.current
            var isLauncher by remember { mutableStateOf(isDefaultLauncher(context)) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isLauncher = isDefaultLauncher(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    PersonalizePage(
                        getScrollPosition = getScrollPosition,
                        onScrollPositionChanged = onScrollPositionChanged
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .background(Color.White.copy(alpha = 0.05f))
                        .metroClickable {
                            openLauncherSelection(context)
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "default launcher",
                            style = ZuneTypography.h4.copy(
                                fontFamily = SegoeUiFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = if (isLauncher) "zune is active" else "tap to set as default launcher",
                            style = ZuneTypography.body2,
                            color = if (isLauncher) LocalZuneAccent.current else Color.LightGray
                        )
                    }
                    if (isLauncher) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active",
                            tint = LocalZuneAccent.current
                        )
                    }
                }
            }
        }
    }
}

fun isDefaultLauncher(context: android.content.Context): Boolean {
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_HOME)
    }
    val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

fun openLauncherSelection(context: android.content.Context) {
    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    } else {
        android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
    context.startActivity(intent)
}
