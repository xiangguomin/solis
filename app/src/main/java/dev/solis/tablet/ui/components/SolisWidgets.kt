package dev.solis.tablet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.solis.tablet.data.calendar.CalendarEvent
import dev.solis.tablet.data.gmail.DigestCategory
import dev.solis.tablet.data.gmail.GmailDigestItem
import dev.solis.tablet.data.weather.WeatherForecast
import dev.solis.tablet.ui.theme.LocalSolisPalette
import dev.solis.tablet.ui.theme.SolisGlassBorder
import dev.solis.tablet.ui.theme.SolisGlassCard
import dev.solis.tablet.ui.theme.SolisGold
import dev.solis.tablet.ui.theme.SolisSurfaceDark
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    verticalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val palette = LocalSolisPalette.current
    val shape = RoundedCornerShape(24.dp)
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.cardBackground)
            .border(1.dp, palette.cardBorder, shape)
            .then(clickableModifier)
            .padding(horizontal = 20.dp, vertical = verticalPadding)
    ) {
        content()
    }
}

@Composable
fun ClockAndDateHeader(modifier: Modifier = Modifier) {
    val palette = LocalSolisPalette.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(10_000L)
        }
    }

    val timeText = remember(now) { now.format(DateTimeFormatter.ofPattern("h:mm")) }
    val amPmText = remember(now) { now.format(DateTimeFormatter.ofPattern("a")) }
    val dateText = remember(now) { now.format(DateTimeFormatter.ofPattern("EEE, MMM d")) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = timeText,
                fontSize = 68.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
                color = palette.primaryText
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = amPmText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = palette.accent,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        Text(
            text = dateText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = palette.secondaryText
        )
    }
}

@Composable
fun WeatherWidgetCard(
    weather: WeatherForecast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSolisPalette.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.cardBackground)
            .border(1.dp, palette.cardBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = weather.currentIcon,
                        fontSize = 28.sp
                    )
                    Text(
                        text = "${weather.currentTempF}°F",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryText
                    )
                    Text(
                        text = weather.locationName,
                        fontSize = 14.sp,
                        color = palette.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Text(
                    text = "H:${weather.highF}° L:${weather.lowF}°",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weather.upcomingDays.take(4).forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = day.dayName,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = palette.mutedText
                            )
                            Text(
                                text = day.icon,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "${day.highF}° / ${day.lowF}°",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = palette.primaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDetailModal(
    weather: WeatherForecast,
    onDismiss: () -> Unit,
    onOpenTabletWeatherApp: () -> Unit
) {
    val palette = LocalSolisPalette.current
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.modalBackground)
                .border(1.dp, palette.cardBorder, shape)
                .padding(28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = weather.locationName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.accent
                    )
                    Text(
                        text = "${weather.currentIcon}  Humidity ${weather.humidityPercent}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "${weather.currentIcon} ${weather.currentTempF}°F",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = palette.primaryText
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "EXTENDED DAILY FORECAST",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = palette.accent
            )
            Spacer(modifier = Modifier.height(12.dp))

            weather.upcomingDays.forEach { day ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = day.dayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(64.dp)
                    )
                    Text(
                        text = "${day.icon}  ${day.conditionLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "💧 ${day.precipProbability}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF0284C7),
                        modifier = Modifier.width(68.dp)
                    )
                    Text(
                        text = "${day.highF}° / ${day.lowF}°F",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenTabletWeatherApp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.secondaryButtonBg,
                        contentColor = palette.secondaryButtonText
                    )
                ) {
                    Text("Open Full Weather App ↗")
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = if (palette.isLight) Color.White else Color.Black
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CalendarAgendaCard(
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
    onCardHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSolisPalette.current
    GlassCard(
        modifier = modifier,
        verticalPadding = 14.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCardHeaderClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPCOMING SCHEDULE (${events.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = palette.accent
                )
                Text(
                    text = "Scroll ↕ • Tap for Weekly View ↗",
                    fontSize = 12.sp,
                    color = palette.mutedText
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (events.isEmpty()) {
                Text(
                    text = "No upcoming events in the next 7 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.mutedText,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(events) { index, event ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onEventClick(event) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(event.calendarColorHex))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 17.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = palette.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${event.dayGroupLabel} • ${event.timeRangeText}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp
                                    ),
                                    color = palette.mutedText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GmailDigestCard(
    digestItems: List<GmailDigestItem>,
    statusText: String,
    hasConsentPrompt: Boolean,
    onAuthorizeGmailClick: () -> Unit,
    onOpenGmailAppClick: () -> Unit,
    onEmailClick: (GmailDigestItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSolisPalette.current
    val actionItems = remember(digestItems) {
        digestItems.filter { it.category == DigestCategory.ACTION_REQUIRED }.take(4)
    }
    val attentionItems = remember(digestItems) {
        digestItems.filter { it.category != DigestCategory.ACTION_REQUIRED }.take(4)
    }

    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IMPORTANT GMAIL BRIEF",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = palette.accent
                )
                Text(
                    text = "Scroll ↕ • $statusText",
                    fontSize = 12.sp,
                    color = palette.mutedText,
                    modifier = Modifier.clickable { onAuthorizeGmailClick() }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (digestItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Listening for new Gmail notifications on tablet — or enter your 16-char Google App Password in ⚙ Settings to pull your Daily Email Digest from xiangguomin@gmail.com.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryText
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onAuthorizeGmailClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = if (palette.isLight) Color.White else Color.Black
                            )
                        ) {
                            Text("Configure Gmail", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onOpenGmailAppClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.secondaryButtonBg,
                                contentColor = palette.secondaryButtonText
                            )
                        ) {
                            Text("Open Gmail App ↗")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (actionItems.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "⚡ Action Required & Deadlines",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF28482)
                            )
                            actionItems.forEach { item ->
                                ExecutiveSummaryBullet(
                                    item = item,
                                    bulletColor = Color(0xFFF28482),
                                    onClick = { onEmailClick(item) }
                                )
                            }
                        }
                    }

                    if (attentionItems.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "📌 Things to Know & Pay Attention To",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accent
                            )
                            attentionItems.forEach { item ->
                                ExecutiveSummaryBullet(
                                    item = item,
                                    bulletColor = palette.accent,
                                    onClick = { onEmailClick(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutiveSummaryBullet(
    item: GmailDigestItem,
    bulletColor: Color,
    onClick: () -> Unit
) {
    val palette = LocalSolisPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = bulletColor,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryText
                    )
                ) {
                    append(item.senderName)
                    append(": ")
                }
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Normal,
                        color = palette.secondaryText
                    )
                ) {
                    append(item.summaryOneLiner)
                }
            },
            fontSize = 16.sp,
            lineHeight = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun GmailDetailModal(
    item: GmailDigestItem,
    onDismiss: () -> Unit,
    onOpenInGmailApp: (GmailDigestItem) -> Unit
) {
    val palette = LocalSolisPalette.current
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(shape)
                .background(palette.modalBackground)
                .border(1.dp, palette.cardBorder, shape)
                .padding(28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(item.category.colorHex).copy(alpha = 0.22f))
                        .border(
                            1.dp,
                            Color(item.category.colorHex).copy(alpha = 0.6f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.category.badgeLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(item.category.colorHex)
                    )
                }
                Text(
                    text = item.receivedTimeText,
                    fontSize = 13.sp,
                    color = palette.mutedText
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.subject,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 22.sp,
                color = palette.primaryText
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "From: ${item.senderName}" +
                    if (item.senderEmail.isNotBlank()) " <${item.senderEmail}>" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.accent
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // AI Executive Summary Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.secondaryButtonBg)
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "✨ AI SUMMARY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.summaryOneLiner,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "FULL DETAILS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    color = palette.mutedText
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.fullBodySnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryText
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onOpenInGmailApp(item) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.secondaryButtonBg,
                        contentColor = palette.secondaryButtonText
                    )
                ) {
                    Text("Open in Gmail App ↗")
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = if (palette.isLight) Color.White else Color.Black
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
