package br.com.mi.atv.launcher

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: Bitmap?,
    val icon: Bitmap?
)

data class WatchCard(
    val id: Long,
    val title: String,
    val subtitle: String,
    val posterArtUri: String?,
    val intentUri: String?,
    val packageName: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvOsLauncherScreen()
        }
    }
}

@Composable
fun TvOsLauncherScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tvos_launcher_prefs", Context.MODE_PRIVATE) }

    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var watchCards by remember { mutableStateOf<List<WatchCard>>(emptyList()) }

    val defaultPinned = remember {
        setOf(
            "com.nuvio.tv",
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.spotify.tv.android"
        )
    }

    var pinnedPackages by remember {
        mutableStateOf(prefs.getStringSet("pinned_pkgs", null) ?: defaultPinned)
    }

    fun togglePin(pkg: String) {
        val current = pinnedPackages.toMutableSet()
        if (!current.add(pkg)) {
            current.remove(pkg)
        }
        pinnedPackages = current
        prefs.edit().putStringSet("pinned_pkgs", current).apply()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loadedApps = loadInstalledApps(context)
            allApps = loadedApps
            if (prefs.getStringSet("pinned_pkgs", null) == null) {
                val installedDefaults = loadedApps.map { it.packageName }.filter { it in defaultPinned }.toSet()
                val initialSet = if (installedDefaults.isNotEmpty()) {
                    installedDefaults
                } else {
                    loadedApps.take(6).map { it.packageName }.toSet()
                }
                pinnedPackages = initialSet
                prefs.edit().putStringSet("pinned_pkgs", initialSet).apply()
            }
            watchCards = loadWatchNextCards(context)
        }
    }

    val dockApps = remember(allApps, pinnedPackages) {
        allApps.filter { it.packageName in pinnedPackages }
    }
    val gridApps = remember(allApps, pinnedPackages) {
        allApps.filter { it.packageName !in pinnedPackages }
    }

    var focusedTitle by remember { mutableStateOf("Apple TV") }
    var focusedSubtitle by remember { mutableStateOf("Segure OK em um app para fixar ou desafixar da Dock") }
    var focusedArtUri by remember { mutableStateOf<String?>(null) }
    var activeZone by remember { mutableStateOf("DOCK") }

    val firstDockFocus = remember { FocusRequester() }

    LaunchedEffect(dockApps.isNotEmpty()) {
        if (dockApps.isNotEmpty()) {
            delay(150)
            runCatching { firstDockFocus.requestFocus() }
        }
    }

    BackHandler(enabled = activeZone != "DOCK") {
        runCatching { firstDockFocus.requestFocus() }
    }

    val palette = Aurora.forKey(focusedTitle)
    val bgPrimary by animateColorAsState(palette.primary, tween(700), label = "bg1")
    val bgSecondary by animateColorAsState(palette.secondary, tween(700), label = "bg2")

    val heroHeight by animateDpAsState(
        targetValue = if (activeZone == "GRID") 155.dp else 325.dp,
        animationSpec = tween(420),
        label = "heroHeight"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgPrimary, bgSecondary, Color(0xFF09090D))
                )
            )
    ) {
        if (!focusedArtUri.isNullOrEmpty()) {
            AsyncImage(
                model = focusedArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.35f),
                        0.55f to Color.Transparent,
                        0.82f to Color(0xCC0D0D12),
                        1.0f to Color(0xF509090D)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .padding(top = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.65f)
                ) {
                    Text(
                        text = focusedTitle.uppercase(Locale.getDefault()),
                        color = Color.White,
                        fontSize = if (activeZone == "GRID") 24.sp else 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = focusedSubtitle,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TopRightClockPill(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onOpenSettings = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                )

                if (activeZone != "GRID" && watchCards.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(watchCards, key = { it.id }) { card ->
                            WatchNextCardItem(
                                card = card,
                                onFocus = {
                                    activeZone = "TOP"
                                    focusedTitle = card.title
                                    focusedSubtitle = card.subtitle.ifEmpty { "Continuar assistindo" }
                                    focusedArtUri = card.posterArtUri
                                },
                                onClick = {
                                    launchWatchCard(context, card)
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "Segure OK para fixar/remover da Dock  •  Desça para todos os apps",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x883A3335),
                                    Color(0x991F1B1D)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(dockApps.size, key = { dockApps[it].packageName }) { index ->
                            val app = dockApps[index]
                            TvOsAppTile(
                                app = app,
                                width = 122.dp,
                                height = 70.dp,
                                modifier = if (index == 0) Modifier.focusRequester(firstDockFocus) else Modifier,
                                onFocus = {
                                    activeZone = "DOCK"
                                    focusedTitle = app.label
                                    focusedSubtitle = "Aplicativo fixado na Dock (Segure OK para desafixar)"
                                    focusedArtUri = null
                                },
                                onClick = { launchApp(context, app.packageName) },
                                onLongClick = { togglePin(app.packageName) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(gridApps, key = { it.packageName }) { app ->
                    TvOsAppTile(
                        app = app,
                        width = 122.dp,
                        height = 68.dp,
                        onFocus = {
                            activeZone = "GRID"
                            focusedTitle = app.label
                            focusedSubtitle = "Mais aplicativos (Segure OK para fixar na Dock)"
                            focusedArtUri = null
                        },
                        onClick = { launchApp(context, app.packageName) },
                        onLongClick = { togglePin(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun TopRightClockPill(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    var timeText by remember { mutableStateOf("") }
    var gearFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            timeText = sdf.format(Date())
            delay(10_000)
        }
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .border(
                width = 1.dp,
                color = if (gearFocused) Color.White else Color.White.copy(alpha = 0.22f),
                shape = CircleShape
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (gearFocused) Color.White else Color.White.copy(alpha = 0.18f))
                .onFocusChanged { gearFocused = it.isFocused }
                .clickable { onOpenSettings() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚙",
                color = if (gearFocused) Color.Black else Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvOsAppTile(
    app: AppEntry,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.13f else 1.0f,
        animationSpec = tween(180),
        label = "tileScale"
    )

    val tileColor = remember(app.packageName) {
        val p = Aurora.forKey(app.packageName)
        listOf(p.primary, p.secondary)
    }

    Box(
        modifier = modifier
            .size(width, height)
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 18.dp else 4.dp,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(tileColor))
            .border(
                width = if (isFocused) 2.5.dp else 0.5.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocus()
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (app.banner != null) {
            Image(
                bitmap = app.banner.asImageBitmap(),
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (app.icon != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun WatchNextCardItem(
    card: WatchCard,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, tween(180), label = "watchScale")

    Box(
        modifier = Modifier
            .size(width = 168.dp, height = 94.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E1E28))
            .border(
                width = if (isFocused) 2.dp else 0.5.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable { onClick() }
    ) {
        if (!card.posterArtUri.isNullOrEmpty()) {
            AsyncImage(
                model = card.posterArtUri,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
                .padding(8.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = card.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun drawableToBitmap(drawable: Drawable?, maxW: Int, maxH: Int): Bitmap? {
    if (drawable == null) return null
    return runCatching {
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(maxW) else maxW
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(maxH) else maxH
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp
    }.getOrNull()
}

fun loadInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val selfPkg = context.packageName
    val results = LinkedHashMap<String, AppEntry>()

    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    )

    for (intent in intents) {
        val activities = pm.queryIntentActivities(intent, 0)
        for (ri in activities) {
            val pkg = ri.activityInfo.packageName
            if (pkg == selfPkg || results.containsKey(pkg)) continue
            val label = ri.loadLabel(pm)?.toString() ?: pkg
            val bannerDrawable = runCatching { ri.activityInfo.loadBanner(pm) ?: pm.getApplicationBanner(pkg) }.getOrNull()
            val iconDrawable = runCatching { ri.activityInfo.loadIcon(pm) }.getOrNull()

            results[pkg] = AppEntry(
                packageName = pkg,
                label = label,
                banner = drawableToBitmap(bannerDrawable, 320, 180),
                icon = drawableToBitmap(iconDrawable, 128, 128)
            )
        }
    }
    return results.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

fun loadWatchNextCards(context: Context): List<WatchCard> {
    val list = mutableListOf<WatchCard>()
    val uris = listOf(
        "content://android.media.tv/watch_next_program",
        "content://android.media.tv/preview_program"
    )
    for (uriStr in uris) {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(Uri.parse(uriStr), null, null, null, null)
            if (cursor != null) {
                val idIdx = cursor.getColumnIndex("_id")
                val titleIdx = cursor.getColumnIndex("title")
                val descIdx = cursor.getColumnIndex("short_description")
                val artIdx = cursor.getColumnIndex("poster_art_uri")
                val intentIdx = cursor.getColumnIndex("intent_uri")
                val pkgIdx = cursor.getColumnIndex("package_name")

                while (cursor.moveToNext() && list.size < 12) {
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) else null
                    if (title.isNullOrBlank()) continue
                    list.add(
                        WatchCard(
                            id = if (idIdx >= 0) cursor.getLong(idIdx) else list.size.toLong(),
                            title = title,
                            subtitle = if (descIdx >= 0) cursor.getString(descIdx) ?: "" else "",
                            posterArtUri = if (artIdx >= 0) cursor.getString(artIdx) else null,
                            intentUri = if (intentIdx >= 0) cursor.getString(intentIdx) else null,
                            packageName = if (pkgIdx >= 0) cursor.getString(pkgIdx) ?: "" else ""
                        )
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
    }
    return list
}

fun launchApp(context: Context, packageName: String) {
    val pm = context.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

fun launchWatchCard(context: Context, card: WatchCard) {
    if (!card.intentUri.isNullOrBlank()) {
        val parsed = runCatching { Intent.parseUri(card.intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull()
        if (parsed != null) {
            parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(parsed) }.isSuccess) return
        }
    }
    if (card.packageName.isNotBlank()) {
        launchApp(context, card.packageName)
    }
}
