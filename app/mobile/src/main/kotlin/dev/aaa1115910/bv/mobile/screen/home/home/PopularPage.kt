package dev.aaa1115910.bv.mobile.screen.home.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.mobile.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.util.BilibiliIntent
import dev.aaa1115910.bv.util.OnBottomReached
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.home.PopularViewModel
import io.github.oshai.kotlinlogging.KotlinLogging

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PopularPage(
    state: LazyGridState,
    windowSize: WindowWidthSizeClass,
    videos: List<UgcItem>,
    popularViewModel: PopularViewModel,
    onClickVideo: (aid: Long) -> Unit,
    loading: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    loadMore: () -> Unit
) {
    val logger = KotlinLogging.logger { }
    val pullRefreshState = rememberPullRefreshState(refreshing, { onRefresh() })
    val context = LocalContext.current
    var menuTargetAid by remember { mutableStateOf<Long?>(null) }

    state.OnBottomReached(
        loading = loading
    ) {
        logger.fInfo { "on reached popular page bottom" }
        loadMore()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(state = pullRefreshState)
    ) {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(videos) { video ->
                Box {
                    SmallVideoCard(
                        data = VideoCardData(
                            avid = video.aid,
                            title = video.title,
                            cover = video.cover,
                            play = video.play,
                            danmaku = video.danmaku,
                            upName = video.author,
                            time = video.duration * 1000L
                        ),
                        onClick = {
                            BilibiliIntent.openVideo(
                                context = context,
                                aid = video.aid,
                                bvid = video.bvid
                            )
                        },
                        onMore = { menuTargetAid = video.aid }
                    )
                    DropdownMenu(
                        expanded = menuTargetAid == video.aid,
                        onDismissRequest = { menuTargetAid = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("收藏") },
                            onClick = {
                                popularViewModel.addToFavorite(video.aid)
                                menuTargetAid = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("稍后再看") },
                            onClick = {
                                popularViewModel.addToWatchLater(video.aid)
                                menuTargetAid = null
                            }
                        )
                    }
                }
            }
        }
        PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
    }
}
