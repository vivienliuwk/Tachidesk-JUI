/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ca.gosyer.jui.ui.reader

import ca.gosyer.jui.core.lang.launchDefault
import ca.gosyer.jui.core.prefs.getAsFlow
import ca.gosyer.jui.domain.chapter.interactor.GetChapter
import ca.gosyer.jui.domain.chapter.interactor.GetChapterPage
import ca.gosyer.jui.domain.chapter.interactor.GetChapters
import ca.gosyer.jui.domain.chapter.interactor.UpdateChapterFlags
import ca.gosyer.jui.domain.chapter.interactor.UpdateChapterMeta
import ca.gosyer.jui.domain.chapter.model.Chapter
import ca.gosyer.jui.domain.manga.interactor.GetManga
import ca.gosyer.jui.domain.manga.interactor.UpdateMangaMeta
import ca.gosyer.jui.domain.manga.model.Manga
import ca.gosyer.jui.domain.manga.model.MangaMeta
import ca.gosyer.jui.domain.reader.ReaderModeWatch
import ca.gosyer.jui.domain.reader.model.Direction
import ca.gosyer.jui.domain.reader.service.ReaderPreferences
import ca.gosyer.jui.ui.base.ChapterCache
import ca.gosyer.jui.ui.base.image.BitmapDecoderFactory
import ca.gosyer.jui.ui.base.model.StableHolder
import ca.gosyer.jui.ui.reader.loader.PagesState
import ca.gosyer.jui.ui.reader.model.MoveTo
import ca.gosyer.jui.ui.reader.model.Navigation
import ca.gosyer.jui.ui.reader.model.PageMove
import ca.gosyer.jui.ui.reader.model.ReaderChapter
import ca.gosyer.jui.ui.reader.model.ReaderPage
import ca.gosyer.jui.ui.reader.model.ViewerChapters
import ca.gosyer.jui.uicore.prefs.asStateIn
import ca.gosyer.jui.uicore.vm.ContextWrapper
import ca.gosyer.jui.uicore.vm.ViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import org.lighthousegames.logging.logging

class ReaderMenuViewModel @Inject constructor(
    private val readerPreferences: ReaderPreferences,
    private val getManga: GetManga,
    private val getChapters: GetChapters,
    private val getChapter: GetChapter,
    private val getChapterPage: GetChapterPage,
    private val updateChapterFlags: UpdateChapterFlags,
    private val updateMangaMeta: UpdateMangaMeta,
    private val updateChapterMeta: UpdateChapterMeta,
    private val chapterCache: ChapterCache,
    contextWrapper: ContextWrapper,
    private val params: Params
) : ViewModel(contextWrapper) {
    override val scope = MainScope()
    private val _manga = MutableStateFlow<Manga?>(null)
    private val viewerChapters = ViewerChapters(
        MutableStateFlow(null),
        MutableStateFlow(null),
        MutableStateFlow(null)
    )
    val previousChapter = viewerChapters.prevChapter.asStateFlow()
    val chapter = viewerChapters.currChapter.asStateFlow()
    val nextChapter = viewerChapters.nextChapter.asStateFlow()

    private val _state = MutableStateFlow<ReaderChapter.State>(ReaderChapter.State.Wait)
    val state = _state.asStateFlow()

    private val _pages = MutableStateFlow<ImmutableList<ReaderPage>>(persistentListOf())
    val pages = _pages.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage = _currentPage.asStateFlow()

    private val _currentPageOffset = MutableStateFlow(1)
    val currentPageOffset = _currentPageOffset.asStateFlow()

    private val _readerSettingsMenuOpen = MutableStateFlow(false)
    val readerSettingsMenuOpen = _readerSettingsMenuOpen.asStateFlow()

    private val _pageEmitter = MutableSharedFlow<PageMove>()
    val pageEmitter = StableHolder(_pageEmitter.asSharedFlow())

    val readerModes = readerPreferences.modes().asStateIn(scope)
        .map { it.toImmutableList() }
        .stateIn(scope, SharingStarted.Eagerly, persistentListOf())
    val readerMode = combine(readerPreferences.mode().getAsFlow(), _manga) { mode, manga ->
        if (
            manga != null &&
            manga.meta.juiReaderMode != MangaMeta.DEFAULT_READER_MODE &&
            manga.meta.juiReaderMode in readerModes.value
        ) {
            manga.meta.juiReaderMode
        } else {
            mode
        }
    }.stateIn(scope, SharingStarted.Eagerly, readerPreferences.mode().get())

    val readerModeSettings = ReaderModeWatch(readerPreferences, scope, readerMode)

    private val loader = ChapterLoader(
        readerPreferences = readerPreferences,
        getChapterPage = getChapterPage,
        chapterCache = chapterCache,
        bitmapDecoderFactory = BitmapDecoderFactory(contextWrapper)
    )

    init {
        init()
    }

    fun init() {
        scope.launchDefault {
            runCatching {
                initManga(params.mangaId)
                initChapters(params.mangaId, params.chapterIndex)
            }
        }
    }

    fun navigate(navigationRegion: Navigation) {
        scope.launch {
            val moveTo = when (navigationRegion) {
                Navigation.MENU -> {
                    setReaderSettingsMenuOpen(!readerSettingsMenuOpen.value)
                    null
                }
                Navigation.NEXT -> MoveTo.Next
                Navigation.PREV -> MoveTo.Previous
                Navigation.RIGHT -> when (readerModeSettings.direction.value) {
                    Direction.Left -> MoveTo.Previous
                    else -> MoveTo.Next
                }
                Navigation.LEFT -> when (readerModeSettings.direction.value) {
                    Direction.Left -> MoveTo.Next
                    else -> MoveTo.Previous
                }
            }
            if (moveTo != null) {
                _pageEmitter.emit(PageMove.Direction(moveTo, currentPage.value))
            }
        }
    }

    fun navigate(page: Int) {
        log.info { "Navigate to $page" }
        scope.launch {
            _pageEmitter.emit(PageMove.Page(page))
        }
    }

    fun progress(index: Int) {
        log.info { "Progressed to $index" }
        _currentPage.value = index
    }

    fun retry(page: ReaderPage) {
        log.info { "Retrying $page" }
        chapter.value?.pageLoader?.retryPage(page)
    }

    private fun resetValues() {
        viewerChapters.recycle()
        _pages.value = persistentListOf()
        _currentPage.value = 1
    }

    fun setMangaReaderMode(mode: String) {
        scope.launchDefault {
            _manga.value?.let {
                updateMangaMeta.await(it, mode)
            }
            initManga(params.mangaId)
        }
    }

    fun setReaderSettingsMenuOpen(open: Boolean) {
        _readerSettingsMenuOpen.value = open
    }

    fun prevChapter() {
        scope.launchDefault {
            val prevChapter = previousChapter.value ?: return@launchDefault
            try {
                _state.value = ReaderChapter.State.Wait
                sendProgress()
                initChapters(params.mangaId, prevChapter.chapter.index)
            } catch (e: Exception) {
                log.warn(e) { "Error loading prev chapter" }
            }
        }
    }

    fun nextChapter() {
        scope.launchDefault {
            val nextChapter = nextChapter.value ?: return@launchDefault
            try {
                _state.value = ReaderChapter.State.Wait
                sendProgress()
                initChapters(params.mangaId, nextChapter.chapter.index)
            } catch (e: Exception) {
                log.warn(e) { "Error loading next chapter" }
            }
        }
    }

    private suspend fun initManga(mangaId: Long) {
        getManga.asFlow(mangaId)
            .onEach {
                _manga.value = it
            }
            .catch {
                _state.value = ReaderChapter.State.Error(it)
                log.warn(it) { "Error loading manga" }
            }
            .collect()
    }

    private suspend fun initChapters(mangaId: Long, chapterIndex: Int) {
        resetValues()
        val chapter = ReaderChapter(
            getChapter.asFlow(mangaId, chapterIndex)
                .catch {
                    _state.value = ReaderChapter.State.Error(it)
                    log.warn(it) { "Error getting chapter" }
                }
                .singleOrNull() ?: return
        )
        val pages = loader.loadChapter(chapter)
        viewerChapters.currChapter.value = chapter
        scope.launchDefault {
            val chapters = getChapters.asFlow(mangaId)
                .catch {
                    log.warn(it) { "Error getting chapter list" }
                    // TODO: 2022-07-01 Error toast
                    emit(emptyList())
                }
                .single()

            val nextChapter = chapters.find { it.index == chapterIndex + 1 }
            if (nextChapter != null) {
                viewerChapters.nextChapter.value = ReaderChapter(
                    nextChapter
                )
            }
            val prevChapter = chapters.find { it.index == chapterIndex - 1 }
            if (prevChapter != null) {
                viewerChapters.prevChapter.value = ReaderChapter(
                    prevChapter
                )
            }
        }
        val lastPageRead = chapter.chapter.lastPageRead
        if (lastPageRead != 0) {
            _currentPage.value = lastPageRead.coerceAtMost(chapter.chapter.pageCount!!)
        }

        val lastPageReadOffset = chapter.chapter.meta.juiPageOffset
        if (lastPageReadOffset != 0) {
            _currentPageOffset.value = lastPageReadOffset
        }

        chapter.stateObserver
            .onEach {
                _state.value = it
            }
            .launchIn(chapter.scope)
        pages
            .filterIsInstance<PagesState.Success>()
            .onEach { (pageList) ->
                _pages.value = pageList.toImmutableList()
                pageList.getOrNull(_currentPage.value - 1)?.let { chapter.pageLoader?.loadPage(it) }
            }
            .launchIn(chapter.scope)

        _currentPage
            .onEach { index ->
                _pages.value.getOrNull(_currentPage.value - 1)?.let { chapter.pageLoader?.loadPage(it) }
                if (index == _pages.value.size) {
                    markChapterRead(chapter)
                }
            }
            .launchIn(chapter.scope)
    }

    private fun markChapterRead(chapter: ReaderChapter) {
        scope.launch { updateChapterFlags.await(chapter.chapter, read = true) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun sendProgress(chapter: Chapter? = this.chapter.value?.chapter, lastPageRead: Int = currentPage.value) {
        chapter ?: return
        if (chapter.read) return
        GlobalScope.launch { updateChapterFlags.await(chapter, lastPageRead = lastPageRead) }
    }

    fun updateLastPageReadOffset(offset: Int) {
        updateLastPageReadOffset(chapter.value?.chapter ?: return, offset)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun updateLastPageReadOffset(chapter: Chapter, offset: Int) {
        GlobalScope.launch { updateChapterMeta.await(chapter, offset) }
    }

    override fun onDispose() {
        viewerChapters.recycle()
        scope.cancel()
    }

    data class Params(val chapterIndex: Int, val mangaId: Long)

    private companion object {
        private val log = logging()
    }
}
