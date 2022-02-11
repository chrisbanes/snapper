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

package dev.chrisbanes.snapper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.internal.randomColor
import dev.chrisbanes.internal.swipeAcrossCenterWithVelocity

/**
 * Contains [SnapperFlingBehavior] tests using [LazyColumn]. This class is extended
 * in both the `androidTest` and `test` source sets for setup of the relevant
 * test runner.
 */
@OptIn(ExperimentalSnapperApi::class) // SnapFlingBehavior is currently experimental
abstract class BaseSnapperFlingLazyColumnTest(
    snapIndexDelta: Int,
    private val contentPadding: PaddingValues,
    // We don't use the Dp type due to https://youtrack.jetbrains.com/issue/KT-35523
    private val itemSpacingDp: Int,
    private val reverseLayout: Boolean,
) : SnapperFlingBehaviorTest(snapIndexDelta) {

    override val endContentPadding: Int
        get() = with(rule.density) { contentPadding.calculateBottomPadding().roundToPx() }

    override fun SemanticsNodeInteraction.swipeAcrossCenter(
        distancePercentage: Float,
        velocityPerSec: Dp,
    ): SemanticsNodeInteraction = swipeAcrossCenterWithVelocity(
        distancePercentageY = if (reverseLayout) -distancePercentage else distancePercentage,
        velocityPerSec = velocityPerSec,
    )

    override fun setTestContent(
        flingBehavior: SnapperFlingBehavior,
        count: () -> Int,
        lazyListState: LazyListState,
    ) {
        rule.setContent {
            applierScope = rememberCoroutineScope()
            val itemCount = count()

            Box {
                LazyColumn(
                    state = lazyListState,
                    flingBehavior = flingBehavior,
                    verticalArrangement = Arrangement.spacedBy(itemSpacingDp.dp),
                    reverseLayout = reverseLayout,
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("layout"),
                ) {
                    items(itemCount) { index ->
                        Box(
                            modifier = Modifier
                                .size(ItemSize)
                                .background(randomColor())
                                .testTag(index.toString())
                        ) {
                            BasicText(
                                text = index.toString(),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}
