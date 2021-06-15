/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ca.gosyer.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.gosyer.BuildConfig
import ca.gosyer.data.download.model.DownloadChapter
import ca.gosyer.data.download.model.DownloaderStatus
import ca.gosyer.data.models.Chapter
import ca.gosyer.ui.base.components.ActionIcon
import ca.gosyer.ui.base.components.DropdownIconButton
import ca.gosyer.ui.base.components.Toolbar
import ca.gosyer.ui.base.vm.viewModel
import ca.gosyer.util.compose.ThemedWindow

fun openDownloadsMenu() {
    ThemedWindow(BuildConfig.NAME) {
        DownloadsMenu()
    }
}

@Composable
fun DownloadsMenu() {
    val vm = viewModel<DownloadsMenuViewModel>()
    val downloadQueue by vm.downloadQueue.collectAsState()

    Surface {
        Column {
            Toolbar(
                "Downloads",
                closable = false,
                actions = {
                    val downloadStatus by vm.downloaderStatus.collectAsState()
                    if (downloadStatus == DownloaderStatus.Started) {
                        ActionIcon(onClick = vm::pause, "Pause", Icons.Default.Pause)
                    } else {
                        ActionIcon(onClick = vm::start, "Continue", Icons.Default.PlayArrow)
                    }
                    ActionIcon(onClick = vm::clear, "Clear queue", Icons.Default.ClearAll)
                }
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(downloadQueue) {
                    downloadsItem(
                        it,
                        vm::stopDownload,
                        vm::moveToBottom
                    )
                }
            }
        }
    }
}

@Composable
private fun downloadsItem(
    chapter: DownloadChapter,
    onDownloadCancel: (Chapter?) -> Unit,
    onMoveDownloadToBottom: (Chapter?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceAround) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chapter.chapter?.name.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Spacer(Modifier.width(16.dp))
                if (chapter.chapter?.pageCount != null && chapter.chapter.pageCount != -1) {
                    Text(
                        "${(chapter.chapter.pageCount * chapter.progress).toInt()}/${chapter.chapter.pageCount}",
                        Modifier.padding(start = 16.dp).requiredWidth(IntrinsicSize.Max),
                        style = MaterialTheme.typography.body2,
                        color = LocalContentColor.current.copy(alpha = ContentAlpha.disabled),
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                } else {
                    Spacer(Modifier.width(32.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                chapter.progress,
                Modifier.fillMaxWidth()
                    .padding(start = 32.dp, end = 16.dp, bottom = 8.dp)
            )
        }
        DropdownIconButton(
            chapter.mangaId to chapter.chapterIndex,
            {
                DropdownMenuItem(onClick = { onDownloadCancel(chapter.chapter) }) {
                    Text("Cancel")
                }
                DropdownMenuItem(onClick = { onMoveDownloadToBottom(chapter.chapter) }) {
                    Text("Move to bottom")
                }
            }
        ) {
            Icon(
                Icons.Default.MoreVert,
                null
            )
        }
    }
}
