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
import androidx.compose.ui.input.pointer.pointerInput
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
    var title by mutableStateOf("A Seguir")
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
    var googleUserPhoto by remember { mutableStateOf(prefs.getString("google_user_photo", "") ?: "") }
    var showSettingsModal by remember { mutableStateOf(false) }

    val defaultPinned = remember {
        listOf(
            "com.apple.tv",
            "com.nuvio.tv",
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.spotify.tv.android",
            "com.amazon.amazonvideo.livingroom"
        )
    }

    var pinnedList by remember {
        val saved = prefs.getString("pinned_list_order", null)
        if (!saved.isNullOrEmpty()) {
            mutableStateOf(saved.split(",").filter { it.isNotBlank() })
        } else {
            mutableStateOf(defaultPinned)
        }
    }

    val savePinned = { newList: List<String> ->
        pinnedList = newList
        prefs.edit().putString("pinned_list_order", newList.joinToString(",")).apply()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            allApps = loadInstalledApps(context)
            if (prefs.getString("pinned_list_order", null) == null) {
                val installedDefaults = allApps.map { it.packageName }.filter { it in defaultPinned }
                val initialList = if (installedDefaults.isNotEmpty()) installedDefaults else allApps.take(6).map { it.packageName }
                savePinned(initialList)
            }
        }
    }

    LaunchedEffect(resumeTrigger) {
        withContext(Dispatchers.IO) {
            watchCards = loadWatchNextCards(context)
            val seed = System.currentTimeMillis() % 1000
            val dynamicCinemaPool = listOf(
                "https://picsum.photos/seed/cinema$seed/1920/1080",
                "https://picsum.photos/seed/movie$seed/1920/1080",
                "https://picsum.photos/seed/noir$seed/1920/1080",
                "https://picsum.photos/seed/darkfilm$seed/1920/1080",
                "https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=1920&auto=format&fit=crop",
                "https://images.unsplash.com/photo-1485846234645-a62644f84728?q=80&w=1920&auto=format&fit=crop",
                "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=1920&auto=format&fit=crop"
            )
            randomCinematicBg = dynamicCinemaPool.random()
        }
    }

    val dockApps = remember(allApps, pinnedList) {
        pinnedList.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
    }
    val gridApps = remember(allApps, pinnedList) {
        allApps.filter { it.packageName !in pinnedList }
    }

    val headerState = remember { FocusHeaderState() }
    val firstDockFocus = remember { FocusRequester() }

    LaunchedEffect(dockApps.isNotEmpty()) {
        if (dockApps.isNotEmpty() && !showSettingsModal) {
            delay(100)
            runCatching { firstDockFocus.requestFocus() }
        }
    }

    BackHandler(enabled = showSettingsModal || headerState.activeZone != "DOCK") {
        if (showSettingsModal) {
            showSettingsModal = false
        } else {
            runCatching { firstDockFocus.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DynamicHeroBackground(headerState = headerState, fallbackUrl = randomCinematicBg)

        // Conteúdo principal (fica inativo se o modal estiver aberto)
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
                HeroTitleBanner(headerState = headerState, modifier = Modifier.align(Alignment.TopStart))

                TopRightProfileClock(
                    userPhotoUrl = googleUserPhoto,
                    onOpenSettings = { showSettingsModal = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                if (!isGrid && watchCards.isNotEmpty() && !showSettingsModal) {
                    LazyRow(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(watchCards, key = { it.id }, contentType = { "watch_card" }) { card ->
                            WatchNextCardItem(
                                card = card,
                                onFocus = {
                                    headerState.activeZone = "TOP"
                                    headerState.title = "A Seguir"
                                    headerState.subtitle = card.subtitle
                                    headerState.artUri = card.posterArtUri
                                },
                                onClick = { if (!showSettingsModal) launchWatchCard(context, card) }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "︿",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Deslize para cima para tela cheia",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }

            val dockShape = remember { RoundedCornerShape(32.dp) }
            val liquidGlassBrush = remember {
                Brush.verticalGradient(
                    colors = listOf(Color(0x884E464A), Color(0xAA1C181A))
                )
            }
            val liquidGlassBorder = remember {
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.10f))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .background(liquidGlassBrush, dockShape)
                        .border(1.2.dp, liquidGlassBorder, dockShape)
                        .padding(horizontal = 26.dp, vertical = 16.dp)
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
                                    headerState.title = "A Seguir"
                                    headerState.subtitle = ""
                                    headerState.artUri = null
                                },
                                onClick = { if (!showSettingsModal) launchApp(context, app.packageName) },
                                onLongClick = { showSettingsModal = true }
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
                            headerState.subtitle = "Clique no ícone tvOS para gerir a Dock"
                            headerState.artUri = null
                        },
                        onClick = { if (!showSettingsModal) launchApp(context, app.packageName) },
                        onLongClick = {
                            if (!showSettingsModal) {
                                val newList = pinnedList + app.packageName
                                savePinned(newList)
                            }
                        }
                    )
                }
            }
        }

        // Modal de Definições com Bloqueio Total de Foco na Tela de Trás
        if (showSettingsModal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60A0A0E))
                    .pointerInput(Unit) {} // Bloqueia cliques que passem para trás
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .fillMaxHeight(0.85f)
                        .background(Color(0xFF1E1E28), RoundedCornerShape(24.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ajustar Aplicativos da Dock",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Selecione um app para removê-lo da Dock ou adicione novos abaixo",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = "Na Dock (Clique para remover):", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().height(95.dp)
                    ) {
                        items(pinnedList, key = { it }) { pkg ->
                            val app = allApps.find { it.packageName == pkg }
                            if (app != null) {
                                var modalItemFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .size(110.dp, 75.dp)
                                        .graphicsLayer {
                                            scaleX = if (modalItemFocused) 1.1f else 1.0f
                                            scaleY = if (modalItemFocused) 1.1f else 1.0f
                                        }
                                        .background(app.primaryColor, RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (modalItemFocused) 3.5.dp else 1.dp,
                                            color = if (modalItemFocused) Color.White else Color.White.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .onFocusChanged { modalItemFocused = it.isFocused }
                                        .clickable {
                                            val newList = pinnedList.toMutableList().apply { remove(pkg) }
                                            savePinned(newList)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Text(
                                            text = app.label,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "✖ Remover",
                                            color = Color.Yellow,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(text = "Disponíveis para Adicionar:", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(gridApps, key = { it.packageName }) { app ->
                            var gridItemFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .height(60.dp)
                                    .graphicsLayer {
                                        scaleX = if (gridItemFocused) 1.05f else 1.0f
                                        scaleY = if (gridItemFocused) 1.05f else 1.0f
                                    }
                                    .background(app.secondaryColor, RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (gridItemFocused) 3.dp else 1.dp,
                                        color = if (gridItemFocused) Color.White else Color.White.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .onFocusChanged { gridItemFocused = it.isFocused }
                                    .clickable {
                                        val newList = pinnedList + app.packageName
                                        savePinned(newList)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+ " + app.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF007AFF))
                            .clickable { showSettingsModal = false }
                            .padding(horizontal = 36.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Concluído", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
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
            0.0f to Color.Black.copy(alpha = 0.20f),
            0.45f to Color.Transparent,
            0.76f to Color(0xDD070709),
            1.0f to Color(0xFF040405)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
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
        Text(
            text = "A Seguir",
            color = Color.White,
            fontSize = if (headerState.activeZone == "GRID") 24.sp else 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TopRightProfileClock(
    userPhotoUrl: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var timeText by remember { mutableStateOf("") }
    var gearFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        while (true) {
            timeText = sdf.format(Date())
            delay(15_000)
        }
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x66333338))
            .border(
                width = 1.2.dp,
                color = if (gearFocused) Color.White else Color.White.copy(alpha = 0.3f),
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
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (gearFocused) Color.White else Color(0xFF2C2C2E))
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .onFocusChanged { gearFocused = it.isFocused }
                .clickable { onOpenSettings() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚙️",
                fontSize = 13.sp
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
        animationSpec = tween(120),
        label = "s"
    )

    val cardBrush = remember(app.packageName) {
        Brush.linearGradient(listOf(app.primaryColor, app.secondaryColor))
    }
    val cardShape = remember { RoundedCornerShape(20.dp) }

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
                width = if (isFocused) 3.dp else 0.6.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.22f),
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
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, tween(120), label = "ws")
    val cardShape = remember { RoundedCornerShape(18.dp) }

    Box(
        modifier = Modifier
            .size(width = 240.dp, height = 135.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = cardShape
                clip = true
            }
            .background(Color(0xFF1E1E28), cardShape)
            .border(
                width = if (isFocused) 3.dp else 0.6.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.28f),
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
                    .size(480, 270)
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xB5000000)),
                        startY = 50f
                    )
                )
                .padding(14.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = card.subtitle.ifEmpty { "Continuar" },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = card.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .fillMaxHeight()
                            .background(Color.White, RoundedCornerShape(99.dp))
                    )
                }
            }
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
    val hiddenBloat = setOf("com.mitv.tvhome", "com.google.android.tv", "com.android.tv.settings")
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

                while (cursor.moveToNext() && list.size < 10) {
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
