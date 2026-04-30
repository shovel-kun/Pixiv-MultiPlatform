package top.kagg886.pmf.ui.route.main.archive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.archive.IllustArchiveManager
import top.kagg886.pmf.backend.archive.previewUrisOrNull
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.res.Res
import top.kagg886.pmf.res.page_is_empty
import top.kagg886.pmf.ui.component.ErrorPage
import top.kagg886.pmf.ui.component.Loading
import top.kagg886.pmf.ui.component.icon.R18
import top.kagg886.pmf.ui.component.icon.R18G
import top.kagg886.pmf.ui.component.icon.Robot
import top.kagg886.pmf.ui.route.main.detail.illust.IllustDetailRoute
import top.kagg886.pmf.util.stringResource

class IllustArchiveViewModel : ViewModel(), KoinComponent {
    private val database by inject<AppDatabase>()
    private val archiveManager = IllustArchiveManager()
    val ready = MutableStateFlow(false)

    val data = Pager(
        PagingConfig(30, enablePlaceholders = false),
        pagingSourceFactory = { database.illustArchiveDao().source() },
    ).flow
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            archiveManager.deleteIncompleteArchives()
            ready.value = true
        }
    }
}

@Composable
fun ArchiveScreen() {
    val model = koinViewModel<IllustArchiveViewModel>()
    val ready by model.ready.collectAsState()
    if (!ready) {
        Loading()
        return
    }
    val data = model.data.collectAsLazyPagingItems()
    when {
        !data.loadState.isIdle && data.itemCount == 0 -> Loading()
        data.itemCount == 0 && data.loadState.isIdle -> ErrorPage(text = stringResource(Res.string.page_is_empty)) {
            data.retry()
        }

        else -> {
            val columns = remember {
                when (val it = AppConfig.galleryOptions) {
                    is AppConfig.Gallery.FixColumnCount -> StaggeredGridCells.Fixed(it.size)
                    is AppConfig.Gallery.FixWidth -> StaggeredGridCells.Adaptive(it.size.dp)
                }
            }
            val stack = LocalNavBackStack.current
            LazyVerticalStaggeredGrid(
                columns = columns,
                modifier = Modifier.fillMaxSize().padding(end = 8.dp),
            ) {
                items(data.itemCount, key = { data.peek(it)?.illustId ?: it }) { i ->
                    val item = data[i] ?: return@items
                    val illust = item.illust
                    Box(modifier = Modifier.padding(5.dp)) {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            onClick = { stack += IllustDetailRoute(illust) },
                        ) {
                            AsyncImage(
                                model = item.previewUrisOrNull()?.firstOrNull() ?: illust.imageUrls.content,
                                modifier = Modifier.fillMaxWidth()
                                    .aspectRatio(illust.width.toFloat() / illust.height.toFloat()),
                                contentDescription = null,
                            )
                        }

                        Row(modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp)) {
                            if (illust.isR18) {
                                if (illust.isR18G) {
                                    Icon(
                                        modifier = Modifier.padding(end = 4.dp),
                                        imageVector = R18G,
                                        contentDescription = null,
                                        tint = Color.Red,
                                    )
                                }
                                Icon(
                                    modifier = Modifier.padding(end = 4.dp),
                                    imageVector = R18,
                                    contentDescription = null,
                                    tint = Color.Red,
                                )
                            }
                            if (illust.isAI) {
                                Icon(
                                    modifier = Modifier.padding(end = 4.dp),
                                    imageVector = Robot,
                                    contentDescription = null,
                                    tint = Color.Yellow,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
