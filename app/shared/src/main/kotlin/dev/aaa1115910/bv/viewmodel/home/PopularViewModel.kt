package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.biliapi.entity.rank.PopularVideoPage
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
class PopularViewModel(
    private val recommendVideoRepository: RecommendVideoRepository,
    private val favoriteRepository: FavoriteRepository,
    private val toViewRepository: ToViewRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val popularVideoList = mutableStateListOf<UgcItem>()

    private var nextPage = PopularVideoPage()
    var refreshing by mutableStateOf(false)
    var loading by mutableStateOf(false)

    init {
        println("=====init PopularViewModel")
    }

    suspend fun loadMore(
        beforeAppendData: () -> Unit = {}
    ) {
        if (!loading) loadData(
            beforeAppendData = beforeAppendData
        )
    }

    private suspend fun loadData(
        beforeAppendData: () -> Unit
    ) {
        loading = true
        logger.fInfo { "Load more popular videos" }
        runCatching {
            val popularVideoData = recommendVideoRepository.getPopularVideos(
                page = nextPage,
                preferApiType = Prefs.apiType
            )
            beforeAppendData()
            nextPage = popularVideoData.nextPage
            popularVideoList.addAllWithMainContext(popularVideoData.list)
        }.onFailure {
            logger.fError { "Load popular video list failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载热门视频失败: ${it.localizedMessage}".toast(BVApp.context)
            }
        }
        loading = false
    }

    fun clearData() {
        popularVideoList.clear()
        resetPage()
        loading = false
    }

    fun resetPage() {
        nextPage = PopularVideoPage()
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