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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.internal.randomColor
import dev.chrisbanes.internal.swipeAcrossCenterWithVelocity

/**
 * Contains [SnapperFlingBehavior] tests using [LazyRow]. This class is extended
 * in both the `androidTest` and `test` source sets for setup of the relevant
 * test runner.
 */
@OptIn(ExperimentalSnapperApi::class) // SnapFlingBehavior is currently experimental
abstract class BaseSnapperFlingLazyRowTest(
    snapIndexDelta: Int,
    private val contentPadding: PaddingValues,
    // We don't use the Dp type due to https://youtrack.jetbrains.com/issue/KT-35523
    private val itemSpacingDp: Int,
    private val layoutDirection: LayoutDirection,
    private val reverseLayout: Boolean,
) : SnapperFlingBehaviorTest(snapIndexDelta) {

    override val endContentPadding: Int
        get() = with(rule.density) {
            contentPadding.calculateEndPadding(layoutDirection).roundToPx()
        }

    /**
     * Returns the expected resolved layout direction for pages
     */
    private val laidOutRtl: Boolean
        get() = if (layoutDirection == LayoutDirection.Rtl) !reverseLayout else reverseLayout

    override fun SemanticsNodeInteraction.swipeAcrossCenter(
        distancePercentage: Float,
        velocityPerSec: Dp,
    ): SemanticsNodeInteraction = swipeAcrossCenterWithVelocity(
        distancePercentageX = if (laidOutRtl) -distancePercentage else distancePercentage,
        velocityPerSec = velocityPerSec,
    )

    override fun setTestContent(
        flingBehavior: SnapperFlingBehavior,
        count: () -> Int,
        lazyListState: LazyListState,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                applierScope = rememberCoroutineScope()
                val itemCount = count()

                Box {
                    LazyRow(
                        state = lazyListState,
                        flingBehavior = flingBehavior,
                        horizontalArrangement = Arrangement.spacedBy(itemSpacingDp.dp),
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
}
