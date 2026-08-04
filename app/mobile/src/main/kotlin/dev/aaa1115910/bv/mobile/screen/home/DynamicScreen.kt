package dev.aaa1115910.bv.mobile.screen.home

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.origeek.imageViewer.previewer.ImagePreviewerState
import dev.aaa1115910.biliapi.entity.Picture
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.mobile.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.util.BilibiliIntent
import dev.aaa1115910.bv.util.OnBottomReached
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.ifElse
import dev.aaa1115910.bv.viewmodel.home.DynamicViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class DatePickerTarget { Start, End }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun DynamicScreen(
    modifier: Modifier = Modifier,
    dynamicViewModel: DynamicViewModel = koinViewModel(),
    dynamicGridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    previewerState: ImagePreviewerState,
    onShowPreviewer: (newPictures: List<Picture>, afterSetPictures: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger { }
    val windowSize = calculateWindowSizeClass(context as Activity).widthSizeClass

    val videoGridState = rememberLazyGridState()

    LaunchedEffect(dynamicViewModel.isLogin) {
        if (dynamicViewModel.isLogin && dynamicViewModel.dynamicVideoList.isEmpty()) {
            scope.launch(Dispatchers.IO) {
                dynamicViewModel.loadMoreVideo()
            }
        }
    }

    val onClickVideo: (dev.aaa1115910.biliapi.entity.user.DynamicVideo) -> Unit = { video ->
        BilibiliIntent.openVideo(
            context = context,
            aid = video.aid,
            bvid = video.bvid
        )
    }

    videoGridState.OnBottomReached(
        loading = dynamicViewModel.loadingVideo
    ) {
        logger.fInfo { "on reached video dynamic page bottom" }
        scope.launch(Dispatchers.IO) {
            dynamicViewModel.loadMoreVideo()
        }
    }

    var showDatePicker by remember { mutableStateOf<DatePickerTarget?>(null) }
    var menuTargetAid by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "动态") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            DateFilterBar(
                startTs = dynamicViewModel.dateStart,
                endTs = dynamicViewModel.dateEnd,
                onPickStart = { showDatePicker = DatePickerTarget.Start },
                onPickEnd = { showDatePicker = DatePickerTarget.End },
                onClear = { dynamicViewModel.setDateRange(null, null) }
            )

            if (!dynamicViewModel.isLogin) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "请先登录")
                }
            } else {
                val hasFilter =
                    dynamicViewModel.dateStart != null || dynamicViewModel.dateEnd != null
                val filteredEmpty =
                    dynamicViewModel.filteredDynamicVideoList.isEmpty() && hasFilter
                if (filteredEmpty && dynamicViewModel.loadingVideo) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "正在加载更多动态…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (filteredEmpty) {
                    val loadedRange = remember(dynamicViewModel.dynamicVideoList) {
                        val pubs = dynamicViewModel.dynamicVideoList
                            .map { it.pubTs }
                            .filter { it > 0L }
                        if (pubs.isEmpty()) {
                            "已加载 ${dynamicViewModel.dynamicVideoList.size} 条动态"
                        } else {
                            val min = pubs.min()
                            val max = pubs.max()
                            val dateFmt = java.text.SimpleDateFormat(
                                "MM-dd HH:mm",
                                java.util.Locale.getDefault()
                            ).apply {
                                timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                            }
                            "已加载 ${dynamicViewModel.dynamicVideoList.size} 条动态（" +
                                "${dateFmt.format(java.util.Date(min * 1000L))} ~ " +
                                "${dateFmt.format(java.util.Date(max * 1000L))}）"
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "当前筛选条件下暂无动态",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = loadedRange,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "请放宽日期范围，或下拉加载更多动态",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { dynamicViewModel.setDateRange(null, null) }) {
                                Text("清除筛选")
                            }
                        }
                    }
                }
                LazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxSize()
                        .ifElse(
                            { windowSize != WindowWidthSizeClass.Compact },
                            Modifier.clip(MaterialTheme.shapes.large)
                        )
                        .background(MaterialTheme.colorScheme.surface),
                    columns = GridCells.Fixed(2),
                    state = videoGridState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(items = dynamicViewModel.filteredDynamicVideoList) { video ->
                        Box {
                            SmallVideoCard(
                                modifier = Modifier
                                    .ifElse(
                                        windowSize != WindowWidthSizeClass.Compact,
                                        Modifier.clip(MaterialTheme.shapes.medium)
                                    ),
                                data = VideoCardData(
                                    avid = video.aid,
                                    title = video.title,
                                    cover = video.cover,
                                    upName = video.author,
                                    play = video.play,
                                    danmaku = video.danmaku,
                                    time = video.duration * 1000L,
                                    epId = video.epid,
                                    jumpToSeason = video.seasonId != null
                                ),
                                onClick = { onClickVideo(video) },
                                onMore = { menuTargetAid = video.aid }
                            )
                            DropdownMenu(
                                expanded = menuTargetAid == video.aid,
                                onDismissRequest = { menuTargetAid = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("收藏") },
                                    onClick = {
                                        dynamicViewModel.addToFavorite(video.aid)
                                        menuTargetAid = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("稍后再看") },
                                    onClick = {
                                        dynamicViewModel.addToWatchLater(video.aid)
                                        menuTargetAid = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pickerTarget = showDatePicker
    if (pickerTarget != null) {
        val initialMillis = when (pickerTarget) {
            DatePickerTarget.Start -> (dynamicViewModel.dateStart ?: System.currentTimeMillis() / 1000) * 1000L
            DatePickerTarget.End -> (dynamicViewModel.dateEnd ?: System.currentTimeMillis() / 1000) * 1000L
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        when (pickerTarget) {
                            DatePickerTarget.Start -> {
                                val newStart = startOfDaySeconds(millis)
                                val end = dynamicViewModel.dateEnd
                                dynamicViewModel.setDateRange(newStart, end)
                                dynamicViewModel.autoLoadUntilFilterMatches()
                            }

                            DatePickerTarget.End -> {
                                val newEnd = endOfDaySeconds(millis)
                                val start = dynamicViewModel.dateStart
                                dynamicViewModel.setDateRange(start, newEnd)
                                dynamicViewModel.autoLoadUntilFilterMatches()
                            }
                        }
                    }
                    showDatePicker = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = null }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateFilterBar(
    startTs: Long?,
    endTs: Long?,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClear: () -> Unit
) {
    val dateFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
    }
    val hasFilter = startTs != null || endTs != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = startTs != null,
            onClick = onPickStart,
            label = {
                Text(
                    text = startTs?.let { dateFormatter.format(Date(it * 1000L)) } ?: "开始日期"
                )
            },
            leadingIcon = {
                Icon(Icons.Default.DateRange, contentDescription = null)
            }
        )
        Text(text = "至", style = MaterialTheme.typography.bodyMedium)
        FilterChip(
            selected = endTs != null,
            onClick = onPickEnd,
            label = {
                Text(
                    text = endTs?.let { dateFormatter.format(Date(it * 1000L)) } ?: "结束日期"
                )
            },
            leadingIcon = {
                Icon(Icons.Default.DateRange, contentDescription = null)
            }
        )
        if (hasFilter) {
            TextButton(onClick = onClear) { Text("清除") }
        }
    }
}

private fun startOfDaySeconds(millis: Long): Long {
    val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
    cal.timeInMillis = millis
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 1000L
}

private fun endOfDaySeconds(millis: Long): Long {
    val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
    cal.timeInMillis = millis
    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
    cal.set(java.util.Calendar.MINUTE, 59)
    cal.set(java.util.Calendar.SECOND, 59)
    cal.set(java.util.Calendar.MILLISECOND, 999)
    return cal.timeInMillis / 1000L
}
