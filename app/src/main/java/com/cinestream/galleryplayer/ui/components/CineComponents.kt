package com.cinestream.galleryplayer.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.galleryplayer.ui.theme.*

enum class AppTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Podcasts),
    LOCAL("Local", Icons.Default.SmartDisplay),
    PLAYLIST("Playlist", Icons.AutoMirrored.Filled.PlaylistPlay),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Top App Bar matching the screenshots:
 * Hamburger [≡] | Cine Player | Send [✈] | [★ Ads Free]
 */
@Composable
fun CineTopBar(
    onMenuClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onAdsFreeClick: () -> Unit = {}
) {
    Surface(
        color = NavyBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger Menu + 3D Logo + JD Video Player HD
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hamburger Button [≡]
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(TealButtonBg)
                        .tvFocusable(shape = RoundedCornerShape(10.dp), onClick = onMenuClick)
                        .clickable(onClick = onMenuClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = CyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 3D Neon Logo
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.cinestream.galleryplayer.R.drawable.app_logo),
                    contentDescription = "JD Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                // Title: "JD Video Player HD"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "JD ",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                    Text(
                        text = "Video Player HD",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Bottom Navigation Bar matching the screenshots:
 * Home (pill when active) | Local | Playlist | Settings
 */
@Composable
fun CineBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Surface(
        color = NavyBackground,
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, NavyBorder.copy(alpha = 0.5f)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val isSelected = tab == currentTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                ) {
                    // Pill container for active tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) TealPill else Color.Transparent)
                            .tvFocusable(
                                shape = RoundedCornerShape(18.dp),
                                onClick = { onTabSelected(tab) }
                            )
                            .padding(
                                horizontal = if (isSelected) 22.dp else 12.dp,
                                vertical = 6.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) CyanAccent else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = tab.title,
                        color = if (isSelected) CyanAccent else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Input field matching the screenshots:
 * Dark rounded container with text, placeholder, and Cyan clipboard paste icon on right.
 */
@Composable
fun CineInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyInput)
            .border(BorderStroke(1.dp, NavyBorder), RoundedCornerShape(14.dp))
            .tvFocusable(shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Text Input / Placeholder
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(CyanAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Cyan Clipboard Paste Icon
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = "Paste",
                tint = CyanAccent,
                modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            onValueChange(clip.trim())
                            Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                        }
                    }
            )
        }
    }
}

/**
 * Side-by-side dropdown selector with cyan border outline matching the screenshot:
 * [ Default  ▼ ]  [ ClearKey  ▼ ]
 */
@Composable
fun <T> CineDropdownSelector(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    getItemLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NavyInput)
            .border(BorderStroke(1.5.dp, CyanAccent), RoundedCornerShape(14.dp))
            .tvFocusable(
                shape = RoundedCornerShape(14.dp),
                onClick = { expanded = !expanded }
            )
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getItemLabel(selectedItem),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Expand",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(NavyCard)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = getItemLabel(item),
                            color = if (item == selectedItem) CyanAccent else TextPrimary,
                            fontWeight = if (item == selectedItem) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
