package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.biliapi.entity.home.RecommendPage
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.biliapi.repositories.FavoriteRepository
import dev.aaa1115910.biliapi.repositories.RecommendVideoRepository
import dev.aaa1115910.biliapi.repositories.ToViewRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.addAllWithMainContext
import dev.aaa1115910.bv.util.fError
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class RecommendViewModel(
    private val recommendVideoRepository: RecommendVideoRepository,
    private val favoriteRepository: FavoriteRepository,
    private val toViewRepository: ToViewRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val recommendVideoList = mutableStateListOf<UgcItem>()

    private var nextPage = RecommendPage()
    var refreshing by mutableStateOf(true)
    var loading by mutableStateOf(false)

    suspend fun loadMore(
        beforeAppendData: () -> Unit = {}
    ) {
        var loadCount = 0
        val maxLoadMoreCount = 3
        if (!loading) {
            if (recommendVideoList.size == 0) {
                // first load data
                while (recommendVideoList.size < 24 && loadCount < maxLoadMoreCount) {
                    val emptyFun: () -> Unit = {}
                    loadData(beforeAppendData = if (loadCount == 0) beforeAppendData else emptyFun)
                    if (loadCount != 0) logger.fInfo { "Load more recommend videos because items too less" }
                    loadCount++
                }
            } else {
                val emptyFun: () -> Unit = {}
                loadData(beforeAppendData = if (loadCount == 0) beforeAppendData else emptyFun)
            }
        }
    }

    private suspend fun loadData(
        beforeAppendData: () -> Unit
    ) {
        loading = true
        logger.fInfo { "Load more recommend videos" }
        runCatching {
            val recommendData = recommendVideoRepository.getRecommendVideos(
                page = nextPage,
                preferApiType = Prefs.apiType
            )
            beforeAppendData()
            nextPage = recommendData.nextPage
            recommendVideoList.addAllWithMainContext(recommendData.items)
        }.onFailure {
            logger.fError { "Load recommend video list failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载推荐视频失败: ${it.localizedMessage}".toast(BVApp.context)
            }
        }
        loading = false
    }

    fun clearData() {
        recommendVideoList.clear()
        resetPage()
        loading = false
    }

    fun resetPage() {
        nextPage = RecommendPage()
        refreshing = true
    }

    fun addToFavorite(aid: Long) {
        scope.launch {
            runCatching {
                val folders = favoriteRepository.getAllFavoriteFolderMetadataList(
                    mid = Prefs.uid,
                    preferApiType = Prefs.apiType
                )
                val defaultFolderId = folders.firstOrNull()?.id
                if (defaultFolderId == null) {
                    withContext(Dispatchers.Main) {
                        "未找到默认收藏夹".toast(BVApp.context)
                    }
                    return@launch
                }
                favoriteRepository.addVideoToFavoriteFolder(
                    aid = aid,
                    addMediaIds = listOf(defaultFolderId),
                    preferApiType = Prefs.apiType
                )
                withContext(Dispatchers.Main) {
                    "已加入收藏".toast(BVApp.context)
                }
            }.onFailure {
                logger.fError { "Add to favorite failed: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    "收藏失败: ${it.localizedMessage ?: it.javaClass.simpleName}".toast(BVApp.context)
                }
            }
        }
    }

    fun addToWatchLater(aid: Long) {
        scope.launch {
            runCatching {
                toViewRepository.addToView(avid = aid, preferApiType = Prefs.apiType)
                withContext(Dispatchers.Main) {
                    "已加入稍后再看".toast(BVApp.context)
                }
            }.onFailure {
                logger.fError { "Add to watch later failed: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    "加入稍后再看失败: ${it.localizedMessage ?: it.javaClass.simpleName}".toast(BVApp.context)
                }
            }
        }
    }
}