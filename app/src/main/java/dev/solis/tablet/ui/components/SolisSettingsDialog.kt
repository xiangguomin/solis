package dev.solis.tablet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.solis.tablet.data.local.SolisConfig
import dev.solis.tablet.ui.theme.SolisGlassBorder
import dev.solis.tablet.ui.theme.SolisGold
import dev.solis.tablet.ui.theme.SolisSurfaceDark

@Composable
fun SolisSettingsDialog(
    currentConfig: SolisConfig,
    onDismiss: () -> Unit,
    onPickAccountPhotos: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onSave: (
        sharedAlbumUrl: String,
        photoIntervalSeconds: Int,
        locationName: String,
        latitude: Double,
        longitude: Double,
        geminiApiKey: String,
        gmailOAuthToken: String
    ) -> Unit
) {
    var albumUrl by remember { mutableStateOf(currentConfig.sharedAlbumUrl) }
    var intervalSec by remember { mutableStateOf(currentConfig.photoIntervalSeconds.toString()) }
    var locationName by remember { mutableStateOf(currentConfig.locationName) }
    var latText by remember { mutableStateOf(currentConfig.latitude.toString()) }
    var lonText by remember { mutableStateOf(currentConfig.longitude.toString()) }
    var geminiKey by remember { mutableStateOf(currentConfig.geminiApiKey) }
    var gmailToken by remember { mutableStateOf(currentConfig.gmailOAuthToken) }

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SolisSurfaceDark)
                .border(1.dp, SolisGlassBorder, shape)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Solis — Smart Display Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = SolisGold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, SolisGlassBorder, RoundedCornerShape(12.dp))
                        .clickable { onPickAccountPhotos() }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📷 Pick My Google Photos",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SolisGold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, SolisGlassBorder, RoundedCornerShape(12.dp))
                        .clickable { onRequestCalendarPermission() }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📅 Sync Tablet Calendar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = SolisGold,
                unfocusedBorderColor = SolisGlassBorder,
                focusedLabelColor = SolisGold,
                unfocusedLabelColor = Color(0xFF94A3B8)
            )

            OutlinedTextField(
                value = albumUrl,
                onValueChange = { albumUrl = it },
                label = { Text("Shared Google Photos Album URL") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("City / Location Label") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = intervalSec,
                    onValueChange = { intervalSec = it },
                    label = { Text("Slide Interval (s)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.weight(0.8f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text("Gemini API Key (for AI Gmail Daily Digest)") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = gmailToken,
                onValueChange = { gmailToken = it },
                label = { Text("Gmail 16-char App Password (for xiangguomin@gmail.com)") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        onSave(
                            albumUrl,
                            intervalSec.toIntOrNull() ?: 60,
                            locationName,
                            latText.toDoubleOrNull() ?: 37.3382,
                            lonText.toDoubleOrNull() ?: -121.8863,
                            geminiKey,
                            gmailToken
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SolisGold,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Save & Sync Now", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
