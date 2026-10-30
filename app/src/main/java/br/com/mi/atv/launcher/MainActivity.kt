package br.com.mi.atv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Immutable
data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val primaryColor: Color,
    val secondaryColor: Color
)

@Immutable
data class WatchCard(
    val id: Long,
    val title: String,
    val subtitle: String,
    val posterArtUri: String?,
    val intentUri: String?,
    val packageName: String
)

@Stable
class FocusHeaderState {
    var title by mutableStateOf("Sugar") // Estilo da referência visual
    var subtitle by mutableStateOf("")
    var artUri by mutableStateOf<String?>(null)
    var activeZone by mutableStateOf("DOCK")
}

class MainActivity : ComponentActivity() {
    private var resumeTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, "android.permission.READ_TV_LISTINGS") != PackageManager.PERMISSION_GRANTED) {
            runCatching {
                ActivityCompat.requestPermissions(this, arrayOf("android.permission.READ_TV_LISTINGS"), 101)
            }
        }
        setContent {
            TvOsLauncherScreen(resumeTrigger = resumeTrigger)
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTrigger++
    }
}

@Composable
fun TvOsLauncherScreen(resumeTrigger: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tvos_launcher_prefs", Context.MODE_PRIVATE) }

    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var watchCards by remember { mutableStateOf<List<WatchCard>>(emptyList()) }
    var randomCinematicBg by remember { mutableStateOf("") }

    val defaultPinned = remember {
        setOf(
            "com.apple.tv",
            "com.nuvio.tv",
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.spotify.tv.android",
            "com.amazon.amazonvideo.livingroom"
        )
    }

    var pinnedPackages by remember {
        mutableStateOf(prefs.getStringSet("pinned_pkgs", null) ?: defaultPinned)
    }

    val togglePin = remember {
        { pkg: String ->
            val current = pinnedPackages.toMutableSet()
            if (!current.add(pkg)) current.remove(pkg)
            pinnedPackages = current
            prefs.edit().putStringSet("pinned_pkgs", current).apply()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            allApps = loadInstalledApps(context)
            if (prefs.getStringSet("pinned_pkgs", null) == null) {
                val installedDefaults = allApps.map { it.packageName }.filter { it in defaultPinned }.toSet()
                val initialSet = if (installedDefaults.isNotEmpty()) installedDefaults else allApps.take(6).map { it.packageName }.toSet()
                pinnedPackages = initialSet
                prefs.edit().putStringSet("pinned_pkgs", initialSet).apply()
            }
        }
    }

    // Puxa um fundo cinematográfico aleatório da internet toda vez que volta para a home (onResume)
    LaunchedEffect(resumeTrigger) {
        withContext(Dispatchers.IO) {
            watchCards = loadWatchNextCards(context)
            // Lista de URLs de fundos cinemáticos em alta definição (Unsplash Curated Cinema & Noir)
            val cinemaWallpapers = listOf(
                "https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=1920&auto=format&fit=crop", // Noir retro car
                "https://images.unsplash.com/photo-1485846234645-a62644f84728?q=80&w=1920&auto=format&fit=crop", // Cinema projector
                "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=1920&auto=format&fit=crop", // Cinematic dark portrait
                "https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=1920&auto=format&fit=crop", // Movie theater
                "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?q=80&w=1920&auto=format&fit=crop"  // Dramatic lighting
            )
            randomCinematicBg = cinemaWallpapers.random()
        }
    }

    val dockApps = remember(allApps, pinnedPackages) {
        allApps.filter { it.packageName in pinnedPackages }
    }
    val gridApps = remember(allApps, pinnedPackages) {
        allApps.filter { it.packageName !in pinnedPackages }
    }

    val headerState = remember { FocusHeaderState() }
    val firstDockFocus = remember { FocusRequester() }

    LaunchedEffect(dockApps.isNotEmpty()) {
        if (dockApps.isNotEmpty()) {
            delay(100)
            runCatching { firstDockFocus.requestFocus() }
        }
    }

    BackHandler(enabled = headerState.activeZone != "DOCK") {
        runCatching { firstDockFocus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fundo dinâmico da internet ou cartão selecionado
        DynamicHeroBackground(headerState = headerState, fallbackUrl = randomCinematicBg)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp)
        ) {
            val isGrid = headerState.activeZone == "GRID"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isGrid) 110.dp else 290.dp)
                    .padding(top = 22.dp)
            ) {
                // Título minimalista estilo "Sugar" no canto superior esquerdo
                HeroTitleBanner(
                    headerState = headerState,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.70f)
                )

                // Relógio + Perfil igualzinho à referência da imagem
                TopRightProfileClock(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onOpenSettings = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                )

                if (!isGrid && watchCards.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(watchCards, key = { it.id }, contentType = { "watch_card" }) { card ->
                            WatchNextCardItem(
                                card = card,
                                onFocus = {
                                    headerState.activeZone = "TOP"
                                    headerState.title = card.title
                                    headerState.subtitle = card.subtitle.ifEmpty { "Continuar assistindo" }
                                    headerState.artUri = card.posterArtUri
                                },
                                onClick = { launchWatchCard(context, card) }
                            )
                        }
                    }
                }
            }

            // DOCK TRANSLÚCIDA COM BLUR (Exatamente igual à referência)
            val dockShape = remember { RoundedCornerShape(28.dp) }
            val dockBrush = remember {
                Brush.verticalGradient(
                    colors = listOf(Color(0x773A3538), Color(0x991E1A1C))
                )
            }
            val dockBorderBrush = remember {
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.1f))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .background(dockBrush, dockShape)
                        .border(1.dp, dockBorderBrush, dockShape)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(
                            count = dockApps.size,
                            key = { dockApps[it].packageName },
                            contentType = { "dock_tile" }
                        ) { index ->
                            val app = dockApps[index]
                            TvOsAppTile(
                                app = app,
                                width = 128.dp,
                                height = 72.dp,
                                modifier = if (index == 0) Modifier.focusRequester(firstDockFocus) else Modifier,
                                onFocus = {
                                    headerState.activeZone = "DOCK"
                                    headerState.title = "Sugar" // Mantém o branding elegante ao focar na dock
                                    headerState.subtitle = ""
                                    headerState.artUri = null
                                },
                                onClick = { launchApp(context, app.packageName) },
                                onLongClick = { togglePin(app.packageName) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // GRELHA INFERIOR (Mostrando os apps restantes com borda visível)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 42.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = gridApps,
                    key = { it.packageName },
                    contentType = { "grid_tile" }
                ) { app ->
                    TvOsAppTile(
                        app = app,
                        width = 124.dp,
                        height = 70.dp,
                        onFocus = {
                            headerState.activeZone = "GRID"
                            headerState.title = app.label
                            headerState.subtitle = "Segure OK para fixar na Dock"
                            headerState.artUri = null
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
fun DynamicHeroBackground(headerState: FocusHeaderState, fallbackUrl: String) {
    val context = LocalContext.current
    val activeUri = headerState.artUri.takeIf { !it.isNullOrEmpty() } ?: fallbackUrl

    val vignetteBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = 0.2f),
            0.5f to Color.Transparent,
            0.78f to Color(0xCC0D0D12),
            1.0f to Color(0xFF070709)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090D))
    ) {
        if (activeUri.isNotBlank()) {
            val req = remember(activeUri) {
                ImageRequest.Builder(context)
                    .data(activeUri)
                    .size(1280, 720)
                    .crossfade(300)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(vignetteBrush)
        )
    }
}

@Composable
fun HeroTitleBanner(
    headerState: FocusHeaderState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Título estilizado cursivo/elegante igual à referência "Sugar"
        Text(
            text = if (headerState.activeZone == "GRID" || headerState.activeZone == "TOP") headerState.title.uppercase(Locale.getDefault()) else "Sugar",
            color = Color.White,
            fontSize = if (headerState.activeZone == "GRID") 24.sp else 46.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (headerState.subtitle.isNotEmpty() && headerState.activeZone != "DOCK") {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = headerState.subtitle,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TopRightProfileClock(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    var timeText by remember { mutableStateOf("") }
    var gearFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            timeText = sdf.format(Date())
            delay(15_000)
        }
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x55000000))
            .border(
                width = 1.dp,
                color = if (gearFocused) Color.White else Color.White.copy(alpha = 0.25f),
                shape = CircleShape
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        // Ícone de avatar/engrenagem igual ao canto superior direito da referência
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (gearFocused) Color.White else Color(0xFFFF8C00))
                .onFocusChanged { gearFocused = it.isFocused }
                .clickable { onOpenSettings() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "👤",
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
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = tween(130),
        label = "s"
    )

    val cardBrush = remember(app.packageName) {
        Brush.linearGradient(listOf(app.primaryColor, app.secondaryColor))
    }
    val cardShape = remember { RoundedCornerShape(16.dp) }

    Box(
        modifier = modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = cardShape
                clip = true
            }
            .background(cardBrush, cardShape)
            .border(
                width = if (isFocused) 2.5.dp else 0.5.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.18f),
                shape = cardShape
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
                bitmap = app.banner,
                contentDescription = app.label,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
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
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(32.dp)
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
                fontSize = 12.sp,
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
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, tween(130), label = "ws")
    val cardShape = remember { RoundedCornerShape(14.dp) }

    Box(
        modifier = Modifier
            .size(width = 180.dp, height = 100.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = cardShape
                clip = true
            }
            .background(Color(0xFF1E1E28), cardShape)
            .border(
                width = if (isFocused) 2.5.dp else 0.5.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.22f),
                shape = cardShape
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable { onClick() }
    ) {
        if (!card.posterArtUri.isNullOrEmpty()) {
            val req = remember(card.posterArtUri) {
                ImageRequest.Builder(context)
                    .data(card.posterArtUri)
                    .size(360, 200)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x55000000))
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

fun drawableToBannerBitmap(drawable: Drawable?, targetW: Int, targetH: Int): ImageBitmap? {
    if (drawable == null) return null
    return runCatching {
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, targetW, targetH)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }.getOrNull()
}

fun loadInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val selfPkg = context.packageName
    val hiddenBloat = setOf(
        "com.mitv.tvhome",
        "com.google.android.tv",
        "com.android.tv.settings"
    )
    val results = LinkedHashMap<String, AppEntry>()

    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    )

    for (intent in intents) {
        val activities = pm.queryIntentActivities(intent, 0)
        for (ri in activities) {
            val pkg = ri.activityInfo.packageName
            if (pkg == selfPkg || pkg in hiddenBloat || results.containsKey(pkg)) continue
            val label = ri.loadLabel(pm)?.toString() ?: pkg
            if (label.equals("PatchWall", ignoreCase = true) || label.equals("Live Channels", ignoreCase = true)) continue

            val bannerDrawable = runCatching { ri.activityInfo.loadBanner(pm) ?: pm.getApplicationBanner(pkg) }.getOrNull()
            val iconDrawable = if (bannerDrawable == null) runCatching { ri.activityInfo.loadIcon(pm) }.getOrNull() else null
            val palette = Aurora.forKey(pkg)

            results[pkg] = AppEntry(
                packageName = pkg,
                label = label,
                banner = drawableToBannerBitmap(bannerDrawable, 320, 180),
                icon = drawableToBannerBitmap(iconDrawable, 80, 80),
                primaryColor = palette.primary,
                secondaryColor = palette.secondary
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
                val pkgIdx = cursor.getColumnIndex("package_name")
                val intentIdx = cursor.getColumnIndex("intent_uri")

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
