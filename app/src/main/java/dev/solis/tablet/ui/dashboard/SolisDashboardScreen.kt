package dev.solis.tablet.ui.dashboard

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.solis.tablet.data.calendar.CalendarEvent
import dev.solis.tablet.data.gmail.GmailDigestItem
import dev.solis.tablet.data.photos.HighlightPhoto
import kotlinx.coroutines.delay
import dev.solis.tablet.ui.components.CalendarAgendaCard
import dev.solis.tablet.ui.components.ClockAndDateHeader
import dev.solis.tablet.ui.components.GmailDetailModal
import dev.solis.tablet.ui.components.GmailDigestCard
import dev.solis.tablet.ui.components.SolisSettingsDialog
import dev.solis.tablet.ui.components.WeatherDetailModal
import dev.solis.tablet.ui.components.WeatherWidgetCard
import dev.solis.tablet.ui.theme.LocalSolisPalette
import dev.solis.tablet.ui.theme.SolisGlassBorder
import dev.solis.tablet.ui.theme.SolisGlassCard
import dev.solis.tablet.ui.theme.SolisGold
import dev.solis.tablet.ui.theme.SolisSurfaceDark
import dev.solis.tablet.ui.theme.SolisTheme

@Composable
fun SolisDashboardScreen(
    viewModel: SolisDashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPhotoSelectionDialog by remember { mutableStateOf(false) }
    var showWeatherDetailModal by remember { mutableStateOf(false) }
    var selectedEmailItem by remember { mutableStateOf<GmailDigestItem?>(null) }

    // Launches the real Google Photos app (com.google.android.apps.photos) ExternalPickerActivity
    // so the user can directly open their "Favorites ⭐" virtual collection in Google Photos!
    val googlePhotosPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = mutableListOf<Uri>()
            val clipData = data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                }
            } else {
                data?.data?.let { uris.add(it) }
            }
            if (uris.isNotEmpty()) {
                viewModel.onPhotosPickedFromAccount(uris)
            }
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshCalendar()
        viewModel.refreshPhotos()
        viewModel.refreshGmailDigest()
    }

    val gmailConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.refreshGmailDigest()
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.GET_ACCOUNTS
            )
        )
    }

    val launchFavoritesPicker: () -> Unit = {
        val googlePhotosIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            setPackage("com.google.android.apps.photos")
        }
        runCatching {
            googlePhotosPickerLauncher.launch(googlePhotosIntent)
        }.onFailure {
            val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            googlePhotosPickerLauncher.launch(fallbackIntent)
        }
    }

    // Horizontal swipe detector across the entire screen:
    // Swipe Left (drag < -80px) -> Next Favorite Photo
    // Swipe Right (drag > +80px) -> Previous Favorite Photo
    var totalHorizontalDrag by remember { mutableFloatStateOf(0f) }

    SolisTheme(isLightMode = uiState.isLightMode) {
        val palette = LocalSolisPalette.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalHorizontalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalHorizontalDrag += dragAmount
                        },
                        onDragEnd = {
                            when {
                                totalHorizontalDrag < -80f -> viewModel.nextPhoto()
                                totalHorizontalDrag > 80f -> viewModel.previousPhoto()
                            }
                            totalHorizontalDrag = 0f
                        }
                    )
                }
        ) {
            SplitBentoLayout(
                state = uiState,
                cropPortraitToFill = uiState.layoutMode == DashboardLayoutMode.FULL_BLEED_OVERLAY,
                onPickAccountPhotos = { showPhotoSelectionDialog = true },
                onPreviousPhoto = { viewModel.previousPhoto() },
                onNextPhoto = { viewModel.nextPhoto() },
                onToggleLayout = { viewModel.toggleLayoutMode() },
                onToggleTheme = { viewModel.toggleThemeMode() },
                onOpenSettings = { showSettingsDialog = true },
                onWeatherClick = { showWeatherDetailModal = true },
                onCalendarEventClick = { event ->
                    openGoogleCalendarWeeklyView(context, event.startTimeMillis)
                },
                onCalendarHeaderClick = {
                    openGoogleCalendarWeeklyView(context, System.currentTimeMillis())
                },
                onAuthorizeGmailClick = {
                    showSettingsDialog = true
                },
                onOpenGmailAppClick = {
                    openGmailThreadOrApp(context, null)
                },
                onEmailClick = { item -> selectedEmailItem = item }
            )

            // Night-time auto dimming overlay (11 PM - 6 AM)
            if (uiState.isNightDimmed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                )
            }

            if (showPhotoSelectionDialog) {
                PhotoSelectionDialog(
                    photos = uiState.allManageablePhotos,
                    onDismiss = { showPhotoSelectionDialog = false },
                    onImportFromGooglePhotos = launchFavoritesPicker,
                    onApplySelection = { selectedIds ->
                        showPhotoSelectionDialog = false
                        viewModel.applyPhotoSelection(selectedIds)
                    },
                    onDeleteUnselected = { keepIds ->
                        showPhotoSelectionDialog = false
                        viewModel.deleteUnselectedPhotos(keepIds)
                    }
                )
            }

            if (showWeatherDetailModal) {
                WeatherDetailModal(
                    weather = uiState.weather,
                    onDismiss = { showWeatherDetailModal = false },
                    onOpenTabletWeatherApp = {
                        showWeatherDetailModal = false
                        openTabletWeatherDetailsApp(context, uiState.weather.locationName)
                    }
                )
            }

            selectedEmailItem?.let { emailItem ->
                GmailDetailModal(
                    item = emailItem,
                    onDismiss = { selectedEmailItem = null },
                    onOpenInGmailApp = { item ->
                        selectedEmailItem = null
                        openGmailThreadOrApp(context, item)
                    }
                )
            }

            if (showSettingsDialog) {
                SolisSettingsDialog(
                    currentConfig = uiState.config,
                    onDismiss = { showSettingsDialog = false },
                    onPickAccountPhotos = launchFavoritesPicker,
                    onRequestCalendarPermission = {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.READ_MEDIA_IMAGES
                            )
                        )
                    },
                    onSave = { albumUrl, interval, locName, lat, lon, geminiKey, gmailToken ->
                        viewModel.saveSettings(
                            sharedAlbumUrl = albumUrl,
                            photoIntervalSeconds = interval,
                            locationName = locName,
                            latitude = lat,
                            longitude = lon,
                            geminiApiKey = geminiKey,
                            gmailOAuthToken = gmailToken
                        )
                    }
                )
            }
        }
    }
}

/**
 * Opens Google Calendar directly at the requested timestamp (tablet Weekly View mode).
 */
private fun openGoogleCalendarWeeklyView(context: Context, timeMillis: Long) {
    runCatching {
        val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(builder, timeMillis)
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(builder.build())
            .setPackage("com.google.android.calendar")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        // Fallback without explicit package name if needed
        runCatching {
            val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            ContentUris.appendId(builder, timeMillis)
            val fallback = Intent(Intent.ACTION_VIEW, builder.build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }
}

/**
 * Opens the Lenovo tablet's native Weather app (`com.zui.weather`) or detailed web forecast.
 */
private fun openTabletWeatherDetailsApp(context: Context, locationName: String) {
    val launchedNative = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.zui.weather")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    if (!launchedNative) {
        runCatching {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=weather+${Uri.encode(locationName)}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }
}

/**
 * Opens the Gmail thread directly in the Lenovo tablet's Gmail app (`com.google.android.gm`)
 * or falls back to the Gmail web view for that specific message/thread.
 */
private fun openGmailThreadOrApp(context: Context, item: GmailDigestItem?) {
    val launchedNativeGmail = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    if (!launchedNativeGmail && item != null) {
        runCatching {
            val threadUrl = "https://mail.google.com/mail/u/0/#inbox/${Uri.encode(item.threadId)}"
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(threadUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }
}

@Composable
private fun SplitBentoLayout(
    state: DashboardUiState,
    cropPortraitToFill: Boolean,
    onPickAccountPhotos: () -> Unit,
    onPreviousPhoto: () -> Unit,
    onNextPhoto: () -> Unit,
    onToggleLayout: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onWeatherClick: () -> Unit,
    onCalendarEventClick: (CalendarEvent) -> Unit,
    onCalendarHeaderClick: () -> Unit,
    onAuthorizeGmailClick: () -> Unit,
    onOpenGmailAppClick: () -> Unit,
    onEmailClick: (GmailDigestItem) -> Unit
) {
    val palette = LocalSolisPalette.current
    var controlsWakeTick by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(controlsWakeTick) {
        showControls = true
        delay(6_000L)
        showControls = false
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(24.dp)
            .offset { IntOffset(state.pixelShiftOffsetX, state.pixelShiftOffsetY) },
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left 54%:
        // 1. Top Row: Clock/Date + Weather Widget
        // 2. Bottom Area (weight 1f): Wider & Taller Important Gmail Brief Executive Summary!
        Column(
            modifier = Modifier
                .weight(0.54f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Row: Clock/Date on left + Wider Weather card aligned horizontally
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClockAndDateHeader()
                Spacer(modifier = Modifier.width(16.dp))
                WeatherWidgetCard(
                    weather = state.weather,
                    onClick = onWeatherClick,
                    modifier = Modifier.weight(1f)
                )
            }

            GmailDigestCard(
                digestItems = state.gmailDigest,
                statusText = state.gmailStatusText,
                hasConsentPrompt = state.gmailConsentIntent != null,
                onAuthorizeGmailClick = onAuthorizeGmailClick,
                onOpenGmailAppClick = onOpenGmailAppClick,
                onEmailClick = onEmailClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Right 46%:
        // 1. Top (42%): Scrollable Upcoming Schedule (Google Calendar)
        // 2. Bottom (58%): Dedicated Single-Photo Frame aligned with the bottom of the Gmail Brief!
        Column(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CalendarAgendaCard(
                events = state.upcomingEvents,
                onEventClick = onCalendarEventClick,
                onCardHeaderClick = onCalendarHeaderClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        controlsWakeTick++
                    }
            ) {
                SinglePhotoFrameCanvas(
                    photo = state.currentPhoto,
                    cropPortraitToFill = cropPortraitToFill,
                    enableKenBurns = true,
                    modifier = Modifier.fillMaxSize()
                )

                // Subtle bottom gradient vignette so photo info & controls are crisp on top of the photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    BottomActionPillBar(
                        state = state,
                        cropPortraitToFill = cropPortraitToFill,
                        showControls = showControls,
                        onWakeControls = { controlsWakeTick++ },
                        onPickAccountPhotos = onPickAccountPhotos,
                        onPreviousPhoto = onPreviousPhoto,
                        onNextPhoto = onNextPhoto,
                        onToggleLayout = onToggleLayout,
                        onToggleTheme = onToggleTheme,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Displays strictly 1 photo at a time inside the dedicated left-side photo frame (no collage):
 * - Landscape photos fill the frame with a gentle Ken Burns zoom.
 * - Portrait photos either display uncropped (`ContentScale.Fit`) over a soft blurred ambient
 *   backdrop of the same photo, or fill the frame if `cropPortraitToFill` is toggled.
 */
@Composable
private fun SinglePhotoFrameCanvas(
    photo: HighlightPhoto,
    cropPortraitToFill: Boolean,
    enableKenBurns: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "single_photo_ken_burns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (enableKenBurns) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "single_photo_scale"
    )

    Crossfade(
        targetState = photo,
        animationSpec = tween(durationMillis = 1000),
        label = "single_photo_crossfade",
        modifier = modifier
    ) { current ->
        var detectedPortraitAtRuntime by remember(current.imageUrl) {
            mutableStateOf(current.isPortrait)
        }

        if (detectedPortraitAtRuntime && !cropPortraitToFill) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0E14)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = current.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(28.dp)
                        .graphicsLayer(alpha = 0.50f, scaleX = 1.15f, scaleY = 1.15f)
                )
                AsyncImage(
                    model = current.imageUrl,
                    contentDescription = current.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                )
            }
        } else {
            AsyncImage(
                model = current.imageUrl,
                contentDescription = current.caption,
                contentScale = ContentScale.Crop,
                onSuccess = { success ->
                    val size = success.painter.intrinsicSize
                    if (size.width > 0f && size.height > size.width * 1.08f) {
                        detectedPortraitAtRuntime = true
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
        }
    }
}

@Composable
private fun BottomActionPillBar(
    state: DashboardUiState,
    cropPortraitToFill: Boolean,
    showControls: Boolean,
    onWakeControls: () -> Unit,
    onPickAccountPhotos: () -> Unit,
    onPreviousPhoto: () -> Unit,
    onNextPhoto: () -> Unit,
    onToggleLayout: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val takenTime = state.currentPhoto.capturedDateText?.takeIf { it.isNotBlank() } ?: "Recent Photo"
    val locationSegment = state.currentPhoto.locationText
        ?.takeIf { it.isNotBlank() }
        ?.let { "  •  📍 $it" }
        .orEmpty()

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            onWakeControls()
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Clean ambient attribution line below the photo (never overlapping the photo, and never faking location!)
        Text(
            text = "🕒 $takenTime$locationSegment  •  (${state.currentPhotoIndex}/${state.totalPhotosCount})  •  🔋 ${state.batteryPercent}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.90f)
        )

        // Action Pills auto-hide after 6 seconds of inactivity (tap photo or caption to reveal)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionPillButton(
                    label = "★ Favorites",
                    textColor = SolisGold,
                    onClick = {
                        onWakeControls()
                        onPickAccountPhotos()
                    }
                )
                ActionPillButton(
                    label = "⏮ Prev",
                    onClick = {
                        onWakeControls()
                        onPreviousPhoto()
                    }
                )
                ActionPillButton(
                    label = "Next ⏭",
                    onClick = {
                        onWakeControls()
                        onNextPhoto()
                    }
                )
                ActionPillButton(
                    label = if (cropPortraitToFill) "Fit" else "Fill",
                    onClick = {
                        onWakeControls()
                        onToggleLayout()
                    }
                )
                ActionPillButton(
                    label = if (state.isLightMode) "🌙 Dark" else "☀️ Light",
                    onClick = {
                        onWakeControls()
                        onToggleTheme()
                    }
                )
                ActionPillButton(
                    label = "⚙ Settings",
                    onClick = {
                        onWakeControls()
                        onOpenSettings()
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionPillButton(
    label: String,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SolisGlassCard)
            .border(1.dp, SolisGlassBorder, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PhotoSelectionDialog(
    photos: List<HighlightPhoto>,
    onDismiss: () -> Unit,
    onImportFromGooglePhotos: () -> Unit,
    onApplySelection: (Set<String>) -> Unit,
    onDeleteUnselected: (Set<String>) -> Unit
) {
    val palette = LocalSolisPalette.current
    val context = LocalContext.current
    var selectedIds by remember(photos) {
        mutableStateOf(photos.filter { it.isSelected }.map { it.id }.toSet())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .heightIn(max = 650.dp)
                .clip(shape)
                .background(palette.modalBackground)
                .border(1.dp, palette.cardBorder, shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Select All / Unselect All / Add from Google Photos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Manage Slideshow Photos (${selectedIds.size}/${photos.size} selected)",
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = 21.sp,
                        color = palette.primaryText
                    )
                    Text(
                        text = "💡 Tip for Google Photos Picker: Touch & hold the first photo, then drag down to the bottom edge to auto-scroll & select all at once!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = palette.accent
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { selectedIds = photos.map { it.id }.toSet() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.secondaryButtonBg,
                            contentColor = palette.secondaryButtonText
                        )
                    ) {
                        Text("☑ Select All", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { selectedIds = emptySet() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.secondaryButtonBg,
                            contentColor = palette.secondaryButtonText
                        )
                    ) {
                        Text("☐ Unselect All", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            runCatching {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.secondaryButtonBg,
                            contentColor = palette.secondaryButtonText
                        )
                    ) {
                        Text("Google Photos App ↗", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onImportFromGooglePhotos,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = if (palette.isLight) Color.White else Color.Black
                        )
                    ) {
                        Text("+ Pick Photos", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (photos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No favorite photos imported yet. Tap '+ Add from Google Photos' to import your Favorites.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.mutedText
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        val isChecked = photo.id in selectedIds
                        Box(
                            modifier = Modifier
                                .height(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isChecked) 3.dp else 1.dp,
                                    color = if (isChecked) palette.accent else palette.cardBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    selectedIds = if (isChecked) {
                                        selectedIds - photo.id
                                    } else {
                                        selectedIds + photo.id
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = photo.imageUrl,
                                contentDescription = photo.caption,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(alpha = if (isChecked) 1f else 0.42f)
                            )

                            // Checkbox badge in top-right corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isChecked) palette.accent else Color.Black.copy(alpha = 0.6f)
                                    )
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isChecked) "✓" else "",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (palette.isLight) Color.White else Color.Black
                                )
                            }

                            // Date caption at bottom
                            photo.capturedDateText?.let { dateStr ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = dateStr.substringBefore("•").trim(),
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Footer buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val unselectedCount = photos.size - selectedIds.size
                if (unselectedCount > 0 && selectedIds.isNotEmpty()) {
                    Button(
                        onClick = { onDeleteUnselected(selectedIds) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.85f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("🗑 Remove $unselectedCount Unselected")
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.secondaryButtonBg,
                            contentColor = palette.secondaryButtonText
                        )
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                onApplySelection(selectedIds)
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = if (palette.isLight) Color.White else Color.Black
                        )
                    ) {
                        Text("Save Selection (${selectedIds.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
