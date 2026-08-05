package dev.aaa1115910.bv.mobile.screen.home

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
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
    // 切日期后滚回顶部
    LaunchedEffect(dynamicViewModel.selectedDate) {
        videoGridState.scrollToItem(0)
    }

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

    var showDatePicker by remember { mutableStateOf(false) }
    var menuTargetAid by remember { mutableStateOf<Long?>(null) }
    // 三点按钮在屏幕里的位置（用 px 存，渲染时换 dp）
    var menuAnchorPx by remember { mutableStateOf<Pair<Float, Float>?>(null) }

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
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize()
            ) {
                DateFilterBar(
                    selectedDate = dynamicViewModel.selectedDate,
                    onShiftDate = { days -> dynamicViewModel.shiftDate(days) },
                    onPickDate = { showDatePicker = true },
                    onClear = { dynamicViewModel.setDate(null) }
                )

            if (!dynamicViewModel.isLogin) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "请先登录")
                }
            } else {
                val hasFilter = dynamicViewModel.selectedDate != null
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
                                text = "请切换日期，或下拉加载更多动态",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { dynamicViewModel.setDate(null) }) {
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
                        // 跟踪三点按钮的窗口坐标
                        var moreIconWinX by remember { mutableStateOf(0f) }
                        var moreIconWinY by remember { mutableStateOf(0f) }
                        // LazyGrid item Box 的窗口坐标（用来换算 offset）
                        var itemBoxWinX by remember { mutableStateOf(0f) }
                        var itemBoxWinY by remember { mutableStateOf(0f) }
                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                val pos = coords.positionInWindow()
                                itemBoxWinX = pos.x
                                itemBoxWinY = pos.y
                            }
                        ) {
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
                                onMore = { menuTargetAid = video.aid },
                                onMorePositioned = { winX, winY ->
                                    moreIconWinX = winX
                                    moreIconWinY = winY
                                }
                            )
                            // 用 Popup 显示菜单，offset 相对当前 Box（item 的 wrapper）
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val expanded = menuTargetAid == video.aid
                            if (expanded) {
                                androidx.compose.ui.window.Popup(
                                    alignment = androidx.compose.ui.Alignment.TopStart,
                                    offset = with(density) {
                                        androidx.compose.ui.unit.IntOffset(
                                            // 窗口坐标 - 父 Box 窗口坐标 = 相对父 Box 的偏移
                                            x = (moreIconWinX - itemBoxWinX - 50.dp.toPx()).toInt(),
                                            y = (moreIconWinY - itemBoxWinY + 8.dp.toPx()).toInt()
                                        )
                                    }
                                ) {
                                    androidx.compose.material3.Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        shadowElevation = 8.dp,
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.width(120.dp)
                                    ) {
                                        Column {
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text("收藏") },
                                                onClick = {
                                                    dynamicViewModel.addToFavorite(video.aid)
                                                    menuTargetAid = null
                                                }
                                            )
                                            androidx.compose.material3.DropdownMenuItem(
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
            }
            }
            // 全屏点击遮罩：菜单打开时点其他位置关闭菜单（位于 outer Box 中、Column 之后，不挤压内容）
            if (menuTargetAid != null) {
                val scrimInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = scrimInteractionSource,
                            indication = null,
                            onClick = { menuTargetAid = null }
                        )
                )
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = (dynamicViewModel.selectedDate ?: System.currentTimeMillis() / 1000) * 1000L
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = startOfDaySeconds(millis)
                        dynamicViewModel.setDate(newDate)
                        dynamicViewModel.autoLoadUntilFilterMatches()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateFilterBar(
    selectedDate: Long?,
    onShiftDate: (days: Int) -> Unit,
    onPickDate: () -> Unit,
    onClear: () -> Unit
) {
    val dateFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
    }
    val hasFilter = selectedDate != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledIconButton(
            onClick = { onShiftDate(-1) },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "前一天")
        }
        FilterChip(
            selected = hasFilter,
            onClick = onPickDate,
            label = {
                Text(
                    text = selectedDate?.let { dateFormatter.format(Date(it * 1000L)) } ?: "选择日期"
                )
            },
            leadingIcon = {
                Icon(Icons.Default.DateRange, contentDescription = null)
            }
        )
        FilledIconButton(
            onClick = { onShiftDate(1) },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "后一天")
        }
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
