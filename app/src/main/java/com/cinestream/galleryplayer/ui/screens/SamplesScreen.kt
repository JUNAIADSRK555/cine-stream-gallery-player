package com.cinestream.galleryplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.galleryplayer.model.DrmType
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.model.StreamFormat
import com.cinestream.galleryplayer.ui.components.tvFocusable
import com.cinestream.galleryplayer.ui.theme.*

data class SampleItem(
    val title: String,
    val url: String,
    val format: StreamFormat = StreamFormat.AUTO,
    val drmType: DrmType = DrmType.NONE,
    val drmLicense: String = ""
)

data class SampleCategory(
    val name: String,
    val items: List<SampleItem>
)

@Composable
fun SamplesScreen(
    onPlaySample: (StreamConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleCategories = remember {
        listOf(
            SampleCategory(
                "Clear DASH",
                listOf(
                    SampleItem("Big Buck Bunny (Multi-rate)", "https://dash.akamaized.net/dash264/TestCases/1c/qualcomm/1/MultiRate.mpd", StreamFormat.DASH),
                    SampleItem("Elephants Dream", "https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/elephants_dream_480p_heaac5_1.mpd", StreamFormat.DASH)
                )
            ),
            SampleCategory(
                "Widevine DASH (MP4, H264)",
                listOf(
                    SampleItem("Widevine Test Stream (WV License)", "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd", StreamFormat.DASH, DrmType.WIDEVINE, "https://proxy.uat.widevine.com/proxy?video_id=2015_tears&provider=widevine_test")
                )
            ),
            SampleCategory(
                "Widevine DASH (WebM, VP9)",
                listOf(
                    SampleItem("VP9 Tears of Steel", "https://storage.googleapis.com/wvmedia/cenc/vp9/tears/tears.mpd", StreamFormat.DASH, DrmType.WIDEVINE, "https://proxy.uat.widevine.com/proxy?video_id=2015_tears&provider=widevine_test")
                )
            ),
            SampleCategory(
                "Widevine DASH (MP4, H265)",
                listOf(
                    SampleItem("H.265 / HEVC UHD Stream", "https://storage.googleapis.com/wvmedia/cenc/hevc/tears/tears.mpd", StreamFormat.DASH, DrmType.WIDEVINE, "https://proxy.uat.widevine.com/proxy?video_id=2015_tears&provider=widevine_test")
                )
            ),
            SampleCategory(
                "Widevine DASH (policy tests)",
                listOf(
                    SampleItem("Widevine Policy Test 1", "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd", StreamFormat.DASH, DrmType.WIDEVINE, "https://proxy.uat.widevine.com/proxy?video_id=2015_tears&provider=widevine_test")
                )
            ),
            SampleCategory(
                "60fps DASH",
                listOf(
                    SampleItem("Cosmos Laundromat 60fps", "https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/elephants_dream_480p_heaac5_1.mpd", StreamFormat.DASH)
                )
            ),
            SampleCategory(
                "DASH - Multiple base URLs",
                listOf(
                    SampleItem("Multi-BaseURL Redundancy", "https://dash.akamaized.net/dash264/TestCases/1c/qualcomm/1/MultiRate.mpd", StreamFormat.DASH)
                )
            ),
            SampleCategory(
                "HLS",
                listOf(
                    SampleItem("Sintel Master Playlist", "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8", StreamFormat.HLS),
                    SampleItem("Apple Basic Stream", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8", StreamFormat.HLS)
                )
            ),
            SampleCategory(
                "SmoothStreaming",
                listOf(
                    SampleItem("Super SpeedWay (MSS)", "https://playready.directtaps.net/smoothstreaming/SSWSS720H264/SuperSpeedway_720.ism/Manifest", StreamFormat.SMOOTH_STREAMING)
                )
            ),
            SampleCategory(
                "AV1",
                listOf(
                    SampleItem("AV1 Chimera 1080p", "https://storage.googleapis.com/wvmedia/cenc/hevc/tears/tears.mpd", StreamFormat.DASH)
                )
            ),
            SampleCategory(
                "Progressive",
                listOf(
                    SampleItem("Big Buck Bunny (1080p MP4)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", StreamFormat.PROGRESSIVE),
                    SampleItem("For Bigger Blazes", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", StreamFormat.PROGRESSIVE)
                )
            ),
            SampleCategory(
                "Dolby",
                listOf(
                    SampleItem("Dolby Digital Plus Audio Track", "https://dash.akamaized.net/dash264/TestCases/1c/qualcomm/1/MultiRate.mpd", StreamFormat.DASH)
                )
            ),
            SampleCategory(
                "More Sample",
                listOf(
                    SampleItem("We Are Going On Bullrun", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4", StreamFormat.PROGRESSIVE)
                )
            )
        )
    }

    var expandedCategories by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Samples",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        items(sampleCategories, key = { it.name }) { category ->
            val isExpanded = expandedCategories.contains(category.name)

            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            expandedCategories = if (isExpanded) {
                                expandedCategories - category.name
                            } else {
                                expandedCategories + category.name
                            }
                        }
                    )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedCategories = if (isExpanded) {
                                    expandedCategories - category.name
                                } else {
                                    expandedCategories + category.name
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Expand",
                            tint = CyanAccent
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavySurface)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            category.items.forEach { sample ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NavyInput)
                                        .tvFocusable(
                                            shape = RoundedCornerShape(8.dp),
                                            onClick = {
                                                onPlaySample(
                                                    StreamConfig(
                                                        url = sample.url,
                                                        title = sample.title,
                                                        format = sample.format,
                                                        drmType = sample.drmType,
                                                        drmLicenseUrl = sample.drmLicense
                                                    )
                                                )
                                            }
                                        )
                                        .clickable {
                                            onPlaySample(
                                                StreamConfig(
                                                    url = sample.url,
                                                    title = sample.title,
                                                    format = sample.format,
                                                    drmType = sample.drmType,
                                                    drmLicenseUrl = sample.drmLicense
                                                )
                                            )
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sample.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = CyanAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
