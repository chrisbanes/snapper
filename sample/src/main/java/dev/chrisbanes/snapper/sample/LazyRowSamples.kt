/*
 * Copyright 2021 Chris Banes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.chrisbanes.snapper.sample

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.snapper.ExperimentalSnapperApi
import dev.chrisbanes.snapper.SnapOffsets
import dev.chrisbanes.snapper.rememberSnapperFlingBehavior

internal val LazyRowSamples = listOf(
    Sample(title = "LazyRow sample") { LazyRowSample() }
)

@OptIn(ExperimentalSnapperApi::class)
@Composable
private fun LazyRowSample() {
    val lazyListState = rememberLazyListState()
    val contentPadding = PaddingValues(16.dp)

    LazyRow(
        state = lazyListState,
        flingBehavior = rememberSnapperFlingBehavior(
            snapOffsetForItem = SnapOffsets.Start,
            snapItemsCount = 4,
            lazyListState = lazyListState,
            // We need to provide the unresolved end value, so use Ltr
            endContentPadding = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
        ),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(50) { index ->
            ImageItem(
                text = "$index",
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(3 / 4f)
                    .border(1.dp, if (index % 4 == 0) Color.Blue else Color.Red)
            )
        }
    }
}
