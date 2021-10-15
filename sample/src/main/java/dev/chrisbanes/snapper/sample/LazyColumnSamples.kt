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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.snapper.ExperimentalSnapperApi
import dev.chrisbanes.snapper.rememberLazyListSnapperLayoutInfo
import dev.chrisbanes.snapper.rememberSnapperFlingBehavior

internal val LazyColumnSamples = listOf(
    Sample(title = "LazyColumn sample") { LazyColumnSample() }
)

@OptIn(ExperimentalSnapperApi::class)
@Composable
private fun LazyColumnSample() {
    val lazyListState = rememberLazyListState()
    val contentPadding = PaddingValues(16.dp)

    LazyColumn(
        state = lazyListState,
        flingBehavior = rememberSnapperFlingBehavior(
            layoutInfo = rememberLazyListSnapperLayoutInfo(
                lazyListState = lazyListState,
                endContentPadding = contentPadding.calculateBottomPadding(),
            )
        ),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(20) { index ->
            ImageItem(
                text = "$index",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}
