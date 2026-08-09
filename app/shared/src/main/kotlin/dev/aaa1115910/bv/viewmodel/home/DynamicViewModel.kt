package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.user.DynamicItem
import dev.aaa1115910.biliapi.entity.user.DynamicVideo
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.FavoriteRepository
import dev.aaa1115910.biliapi.repositories.ToViewRepository
import dev.aaa1115910.biliapi.repositories.UserRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.addAllWithMainContext
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import dev.aaa1115910.bv.repository.UserRepository as BvUserRepository

@KoinViewModel
class DynamicViewModel(
    private val bvUserRepository: BvUserRepository,
    private val userRepository: UserRepository,
    private val favoriteRepository: FavoriteRepository,
    private val toViewRepository: ToViewRepository,
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val dynamicVideoList = mutableStateListOf<DynamicVideo>()
    val dynamicAllList = mutableStateListOf<DynamicItem>()

    // Single date filter (epoch seconds, start of day).
    // Default: 今天 0 点（用户要求"打开订阅默认当天"）。
    // 不持久化 selectedDate：杀进程后永远回到今天。
    // 但每个日期的 scroll position 仍然持久化（切换/重启后切回某天能恢复）。
    // 用 lazy 延迟初始化：构建时算一次，之后保持稳定（避免日期跨越午夜后页面 index 变化）。
    private val tz = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    // 保留 by mutableStateOf 让 UI 跟随重组；构造默认值用 today。
    var selectedDate by mutableStateOf<Long?>(startOfTodaySeconds(tz))
        private set

    // 当前 LazyGrid 的 firstVisibleItemIndex，由 UI 侧 LaunchedEffect 持续更新。
    // 切日期时把 currentScrollIndex 持久化到 Prefs（带 selectedDate key），再读目标日期的 saved index 恢复。
    var currentScrollIndex: Int = 0

    // 首次列表非空且 selectedDate 有 saved index > 0 时，让 UI 重新跑一次 scrollToItem(saved)。
    // 进程被系统杀后重启时，列表从空开始加载，LaunchedEffect(selectedDate) 在空列表上调
    // scrollToItem 是 noop，saved index 会丢失。这个 flag 触发 UI 重新恢复。
    // 用户主动切日期时这个 flag 也置 true。
    // 注意：必须 by mutableStateOf，UI 用 snapshotFlow 监听，写后才会触发重组。
    var pendingRestore by mutableStateOf(true)

    /**
     * 读取指定日期保存的 scroll index；没有就 0。
     * 由 [setDate] / [shiftDate] / [clearDate] 切换目标日期后调用，决定是否 `scrollToItem`。
     */
    fun savedScrollIndexFor(day: Long): Int {
        val map = loadScrollIndexMap()
        return map[day] ?: 0
    }

    val filteredDynamicVideoList by derivedStateOf {
        if (selectedDate == null) {
            dynamicVideoList
        } else {
            val day = selectedDate ?: return@derivedStateOf dynamicVideoList
            // selectedDate 是「那天 0 点」的秒数，匹配 [day, day+86400)
            val dayEnd = day + 86400L
            dynamicVideoList.filter { v ->
                if (v.pubTs == 0L) return@filter true
                v.pubTs in day until dayEnd
            }
        }
    }

    fun setDate(date: Long?) {
        // 切走前先把当前日期的 scroll index 存进 Prefs
        selectedDate?.let { saveScrollIndexFor(it, currentScrollIndex) }
        selectedDate = date
        // 新日期进入后等列表加载完再恢复；flag 触发 UI 监听列表首次非空
        pendingRestore = true
    }

    /**
     * 在当前选中日期上移动 [days] 天，可为负。
     * 没选日期时基于「今天」偏移。
     */
    fun shiftDate(days: Int) {
        val base = selectedDate ?: startOfTodaySeconds(tz)
        setDate(base + days * 86400L)
        // 切换后自动加载更多页，直到找到匹配或翻完
        autoLoadUntilFilterMatches()
    }

    /**
     * 选中某个具体日期后调用：自动循环加载更多页，直到：
     *  - 找到当天匹配的视频（列表非空），或
     *  - 已加载数据的最早发布时间 <= 选中日期（不可能再找到），或
     *  - API 报告没有更多数据（hasMore = false），或
     *  - 达到 50 次上限。
     * 防止用户因为「只看了 6 页（最近 1 天）就筛选，匹配数据在更后面」而看不到内容。
     */
    fun autoLoadUntilFilterMatches() {
        if (selectedDate == null) return
        val target = selectedDate ?: return
        viewModelScope.launch(Dispatchers.IO) {
            var guard = 0
            while (guard < 50) {
                if (filteredDynamicVideoList.isNotEmpty()) break
                if (!videoHasMore) break
                if (loadingVideo) {
                    // 上一次 loadVideoData 还没结束，等一会
                    delay(150)
                    continue
                }
                // 已加载数据中最早一条 pubTs <= 选中日期就说明永远找不到了
                val oldestPubTs = dynamicVideoList
                    .filter { it.pubTs > 0L }
                    .minOfOrNull { it.pubTs } ?: Long.MAX_VALUE
                if (oldestPubTs != Long.MAX_VALUE && oldestPubTs < target) break
                loadVideoData()
                guard++
            }
        }
    }

    private fun startOfTodaySeconds(tz: java.util.TimeZone): Long {
        val cal = java.util.Calendar.getInstance(tz).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / 1000L
    }

    private var currentVideoPage = 0
    var loadingVideo by mutableStateOf(false)
    var videoHasMore by mutableStateOf(true)
    private var videoHistoryOffset: String? = null
    private var videoUpdateBaseline: String? = null

    private var currentAllPage = 0
    var loadingAll by mutableStateOf(false)
    var allHasMore by mutableStateOf(true)
    private var allHistoryOffset: String? = null
    private var allUpdateBaseline: String? = null

    val isLogin get() = bvUserRepository.isLogin

    init {
        println("=====init DynamicViewModel")
        // 持续监听 currentScrollIndex 变化，debounce 500ms 写 Prefs。
        // 这样滚动中实时持久化，杀进程也不会丢失最新位置。
        viewModelScope.launch(Dispatchers.IO) {
            var lastWritten = -1
            var pendingWrite = false
            while (true) {
                val current = currentScrollIndex
                if (current > 0 && current != lastWritten && selectedDate != null) {
                    pendingWrite = true
                }
                if (pendingWrite) {
                    saveScrollIndexFor(selectedDate ?: break, current)
                    lastWritten = current
                    pendingWrite = false
                    delay(500)
                } else {
                    delay(100)
                }
            }
        }
    }

    suspend fun loadMoreVideo() {
        if (!loadingVideo) loadVideoData()
    }

    suspend fun loadMoreAll() {
        if (!loadingAll) loadAllData()
    }

    private suspend fun loadVideoData() {
        if (!videoHasMore || !bvUserRepository.isLogin) return
        loadingVideo = true
        logger.fInfo { "Load more dynamic videos [apiType=${Prefs.apiType}, offset=$videoHistoryOffset, page=${currentVideoPage + 1}]" }
        runCatching {
            val dynamicVideoData = userRepository.getDynamicVideos(
                page = ++currentVideoPage,
                offset = videoHistoryOffset ?: "",
                updateBaseline = videoUpdateBaseline ?: "",
                preferApiType = Prefs.apiType
            )
            dynamicVideoList.addAllWithMainContext(dynamicVideoData.videos)
            videoHistoryOffset = dynamicVideoData.historyOffset
            videoUpdateBaseline = dynamicVideoData.updateBaseline
            videoHasMore = dynamicVideoData.hasMore

            logger.fInfo { "Load dynamic video list page: ${currentVideoPage},size: ${dynamicVideoData.videos.size}" }
            val avList = dynamicVideoData.videos.map {
                it.aid
            }
            logger.fInfo { "Load dynamic video size: ${avList.size}" }
            logger.info { "Load dynamic video list ${avList}}" }
        }.onFailure {
            logger.fWarn { "Load dynamic video list failed: ${it.stackTraceToString()}" }
            when (it) {
                is AuthFailureException -> {
                    withContext(Dispatchers.Main) {
                        BVApp.context.getString(R.string.exception_auth_failure)
                            .toast(BVApp.context)
                    }
                    logger.fInfo { "User auth failure" }
                    if (!BuildConfig.DEBUG) bvUserRepository.logout()
                }

                else -> {
                    withContext(Dispatchers.Main) {
                        "加载动态失败: ${it.localizedMessage}".toast(BVApp.context)
                    }
                }
            }
        }
        loadingVideo = false
    }

    private suspend fun loadAllData() {
        if (!allHasMore || !bvUserRepository.isLogin) return
        loadingAll = true
        logger.fInfo { "Load more dynamic all [apiType=${Prefs.apiType}, offset=$allHistoryOffset, page=${currentVideoPage + 1}]" }
        runCatching {
            val dynamicData = userRepository.getDynamics(
                page = ++currentVideoPage,
                offset = allHistoryOffset ?: "",
                updateBaseline = allUpdateBaseline ?: "",
                preferApiType = Prefs.apiType
            )
            dynamicAllList.addAll(dynamicData.dynamics)
            allHistoryOffset = dynamicData.historyOffset
            allUpdateBaseline = dynamicData.updateBaseline
            allHasMore = dynamicData.hasMore

            logger.fInfo { "Load dynamic all list page: ${currentVideoPage},size: ${dynamicData.dynamics.size}" }
        }.onFailure {
            logger.fWarn { "Load dynamic all list failed: ${it.stackTraceToString()}" }
            when (it) {
                is AuthFailureException -> {
                    withContext(Dispatchers.Main) {
                        BVApp.context.getString(R.string.exception_auth_failure)
                            .toast(BVApp.context)
                    }
                    logger.fInfo { "User auth failure" }
                }

                else -> {
                    withContext(Dispatchers.Main) {
                        "加载动态失败: ${it.localizedMessage}".toast(BVApp.context)
                    }
                }
            }
        }
        loadingAll = false
    }

    fun clearVideoData() {
        dynamicVideoList.clear()
        currentVideoPage = 0
        loadingVideo = false
        videoHasMore = true
        videoHistoryOffset = null
    }

    fun clearAllData() {
        dynamicAllList.clear()
        currentAllPage = 0
        loadingAll = false
        allHasMore = true
        allHistoryOffset = null
    }

    fun addToFavorite(aid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
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
                    return@runCatching
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
                logger.fWarn { "Add to favorite failed: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    "收藏失败: ${it.localizedMessage ?: it.javaClass.simpleName}".toast(BVApp.context)
                }
            }
        }
    }

    fun addToWatchLater(aid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                toViewRepository.addToView(avid = aid, preferApiType = Prefs.apiType)
                withContext(Dispatchers.Main) {
                    "已加入稍后再看".toast(BVApp.context)
                }
            }.onFailure {
                logger.fWarn { "Add to watch later failed: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    "加入稍后再看失败: ${it.localizedMessage ?: it.javaClass.simpleName}".toast(BVApp.context)
                }
            }
        }
    }

    // ---------------- Scroll index 持久化 ----------------
    //
    // 目的：每个日期的 LazyGrid firstVisibleItemIndex 单独保留，切换日期能恢复。
    // 存储：Prefs 里一个 JSON 字符串，key=日期 epoch 秒，value=index。
    // 写入时机：[setDate] 切走时；读取时机：[savedScrollIndexFor] 由 UI 切完日期调用。

    private fun loadScrollIndexMap(): MutableMap<Long, Int> {
        val raw = runCatching { Prefs.dynamicScrollIndexMap }.getOrNull().orEmpty()
        if (raw.isBlank()) return mutableMapOf()
        return runCatching {
            // 轻量 JSON：{"<day>": <index>, ...}，用正则解析避免引入新依赖。
            // 格式严格，不抛就 OK。
            val map = mutableMapOf<Long, Int>()
            val re = Regex("\"(\\d+)\"\\s*:\\s*(-?\\d+)")
            for (m in re.findAll(raw)) {
                val k = m.groupValues[1].toLongOrNull() ?: continue
                val v = m.groupValues[2].toIntOrNull() ?: continue
                map[k] = v
            }
            map
        }.getOrElse { mutableMapOf() }
    }

    private fun saveScrollIndexFor(day: Long, index: Int) {
        val map = loadScrollIndexMap()
        if (index <= 0) {
            // 顶部位置没必要存（默认值就是 0），节省 Prefs 大小
            map.remove(day)
        } else {
            map[day] = index
        }
        // 序列化成紧凑 JSON：{"<day>": <index>, ...}
        val json = buildString {
            append('{')
            var first = true
            for ((k, v) in map) {
                if (!first) append(',')
                append('"').append(k).append('"').append(':').append(v)
                first = false
            }
            append('}')
        }
        runCatching { Prefs.dynamicScrollIndexMap = json }
            .onFailure { logger.fWarn { "saveScrollIndexMap failed: ${it.stackTraceToString()}" } }
    }
}