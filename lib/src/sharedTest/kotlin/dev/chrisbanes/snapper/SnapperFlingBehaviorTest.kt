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

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.node.Ref
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Test

private const val MediumSwipeDistance = 0.75f
private const val ShortSwipeDistance = 0.4f

private val FastVelocity = 2000.dp
private val MediumVelocity = 700.dp
private val SlowVelocity = 100.dp

internal val ItemSize = 200.dp

@OptIn(ExperimentalSnapperApi::class) // Pager is currently experimental
abstract class SnapperFlingBehaviorTest(
    private val snapIndexDelta: Int,
) {
    @get:Rule
    val rule = createComposeRule()

    abstract val endContentPadding: Int

    /**
     * This is a workaround for https://issuetracker.google.com/issues/179492185.
     * Ideally we would have a way to get the applier scope from the rule
     */
    protected lateinit var applierScope: CoroutineScope

    @Test
    fun swipe() {
        val lazyListState = LazyListState()
        val snappingFlingBehavior = createSnapFlingBehavior(lazyListState)
        setTestContent(
            flingBehavior = snappingFlingBehavior,
            lazyListState = lazyListState,
            count = 10,
        )

        // First test swiping towards end, from 0 to -1, which should no-op
        rule.onNodeWithTag("0").swipeAcrossCenter(MediumSwipeDistance)
        rule.waitForIdle()
        // ...and assert that nothing happened
        lazyListState.assertCurrentItem(index = 0, offset = 0)

        // Now swipe towards start, from page 0
        rule.onNodeWithTag("0").swipeAcrossCenter(-MediumSwipeDistance)
        rule.waitForIdle()

        // ...and assert that we now laid out from page 1
        lazyListState.assertCurrentItem(minIndex = 1, offset = 0)
    }

    @Test
    fun swipeForwardAndBackFromZero() = swipeToEndAndBack(
        initialIndex = 0,
        count = 4
    )

    @Test
    fun swipeForwardAndBackFromLargeIndex() = swipeToEndAndBack(
        initialIndex = Int.MAX_VALUE / 2,
        count = Int.MAX_VALUE
    )

    private fun swipeToEndAndBack(initialIndex: Int, count: Int) {
        val lazyListState = LazyListState(firstVisibleItemIndex = initialIndex)
        val snappingFlingBehavior = createSnapFlingBehavior(lazyListState)
        setTestContent(
            flingBehavior = snappingFlingBehavior,
            lazyListState = lazyListState,
            count = count,
        )

        var lastItemIndex = lazyListState.currentItem.index

        // Now swipe towards start, from page 0 to page 1 and assert the layout
        rule.onNodeWithTag("layout").swipeAcrossCenter(-MediumSwipeDistance)
        rule.waitForIdle()
        lazyListState.assertCurrentItem(minIndex = lastItemIndex + 1)
        lastItemIndex = lazyListState.currentItem.index

        // Repeat for 1 -> 2
        rule.onNodeWithTag("layout").swipeAcrossCenter(-MediumSwipeDistance)
        rule.waitForIdle()
        lazyListState.assertCurrentItem(minIndex = lastItemIndex + 1)
        lastItemIndex = lazyListState.currentItem.index

        // Repeat for 2 -> 3
        rule.onNodeWithTag("layout").swipeAcrossCenter(-MediumSwipeDistance)
        rule.waitForIdle()
        lazyListState.assertCurrentItem(minIndex = lastItemIndex + 1)
        lastItemIndex = lazyListState.currentItem.index

        // Swipe past the last item (if it is the last item). We shouldn't move
        if (count - initialIndex == 4) {
            rule.onNodeWithTag("layout").swipeAcrossCenter(-MediumSwipeDistance)
            rule.waitForIdle()
            lazyListState.assertCurrentItem(index = lastItemIndex, offset = 0)
        }

        // Swipe back from 3 -> 2
        rule.onNodeWithTag("layout").swipeAcrossCenter(MediumSwipeDistance)
        rule.waitForIdle()
        lazyListState.assertCurrentItem(maxIndex = (lastItemIndex - 1).coerceAtLeast(0))
        lastItemIndex = lazyListState.currentItem.index

        // Swipe back from 2 -> 1
        rule.onNodeWithTag("layout").swipeAcrossCenter(MediumSwipeDistance)
        rule.waitForIdle()
        lazyListState.assertCurrentItem(maxIndex = (lastItemIndex - 1).coerceAtLeast(0))
        lastItemIndex = lazyListState.currentItem.index

        // Swipe back from 1 -> 0
        rule.onNodeWithTag("layout").swipeAcrossCenter(MediumSwipeDistance)
        rule.waitForIdle()
        lazyListState.assertCurrentItem(maxIndex = (lastItemIndex - 1).coerceAtLeast(0))
    }

    @Test
    fun mediumDistance_fastSwipe_toFling() {
        rule.mainClock.autoAdvance = false

        val lazyListState = LazyListState()
        val snappingFlingBehavior = createSnapFlingBehavior(lazyListState)
        setTestContent(
            flingBehavior = snappingFlingBehavior,
            lazyListState = lazyListState,
            count = 10,
        )

        assertThat(lazyListState.isScrollInProgress).isFalse()
        assertThat(snappingFlingBehavior.animationTarget).isNull()
        lazyListState.assertCurrentItem(index = 0, offset = 0)

        // Now swipe towards start, from page 0 to page 1, over a medium distance of the item width.
        // This should trigger a fling
        rule.onNodeWithTag("0").swipeAcrossCenter(
            distancePercentage = -MediumSwipeDistance,
            velocityPerSec = FastVelocity,
        )

        assertThat(lazyListState.isScrollInProgress).isTrue()
        assertThat(snappingFlingBehavior.animationTarget).isAtLeast(1)

        // Now re-enable the clock advancement and let the fling animation run
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        // ...and assert that we now laid out from page 1
        lazyListState.assertCurrentItem(minIndex = 1, offset = 0)
    }

    @Test
    fun mediumDistance_slowSwipe_toSnapForward() {
        rule.mainClock.autoAdvance = false

        val lazyListState = LazyListState()
        val snappingFlingBehavior = createSnapFlingBehavior(lazyListState)
        setTestContent(
            flingBehavior = snappingFlingBehavior,
            lazyListState = lazyListState,
            count = 10,
        )

        assertThat(lazyListState.isScrollInProgress).isFalse()
        assertThat(snappingFlingBehavior.animationTarget).isNull()
        lazyListState.assertCurrentItem(index = 0, offset = 0)

        // Now swipe towards start, from page 0 to page 1, over a medium distance of the item width.
        // This should trigger a spring to position 1
        rule.onNodeWithTag("0").swipeAcrossCenter(
            distancePercentage = -MediumSwipeDistance,
            velocityPerSec = SlowVelocity,
        )

        assertThat(lazyListState.isScrollInProgress).isTrue()
        assertThat(snappingFlingBehavior.animationTarget).isNotNull()

        // Now re-enable the clock advancement and let the snap animation run
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        // ...and assert that we now laid out from page 1
        lazyListState.assertCurrentItem(minIndex = 1, offset = 0)
    }

    @Test
    fun shortDistance_fastSwipe_toFling() {
        rule.mainClock.autoAdvance = false

        val lazyListState = LazyListState()
        val snappingFlingBehavior = createSnapFlingBehavior(lazyListState)
        setTestContent(
            flingBehavior = snappingFlingBehavior,
            lazyListState = lazyListState,
            count = 10,
        )

        assertThat(lazyListState.isScrollInProgress).isFalse()
        assertThat(snappingFlingBehavior.animationTarget).isNull()
        lazyListState.assertCurrentItem(index = 0, offset = 0)

        // Now swipe towards start, from page 0 to page 1, over a short distance of the item width.
        // This should trigger a spring back to the original position
        rule.onNodeWithTag("0").swipeAcrossCenter(
            distancePercentage = -ShortSwipeDistance,
            velocityPerSec = FastVelocity,
        )

        assertThat(lazyListState.isScrollInProgress).isTrue()
        assertThat(snappingFlingBehavior.animationTarget).isAtLeast(1)

        // Now re-enable the clock advancement and let the fling animation run
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        // ...and assert that we now laid out from page 1
        lazyListState.assertCurrentItem(minIndex = 1, offset = 0)
    }

    @Test
    fun shortDistance_slowSwipe_toSnapBack() {
        rule.mainClock.autoAdvance = false

        val lazyListState = LazyListState()
        val snappingFlingBehavior = createSnapFlingBehavior(lazyListState)
        setTestContent(
            flingBehavior = snappingFlingBehavior,
            lazyListState = lazyListState,
            count = 10,
        )

        assertThat(lazyListState.isScrollInProgress).isFalse()
        assertThat(snappingFlingBehavior.animationTarget).isNull()
        lazyListState.assertCurrentItem(index = 0, offset = 0)

        // Now swipe towards start, from page 0 to page 1, over a short distance of the item width.
        // This should trigger a spring back to the original position
        rule.onNodeWithTag("0").swipeAcrossCenter(
            distancePercentage = -ShortSwipeDistance,
            velocityPerSec = SlowVelocity,
        )

        assertThat(lazyListState.isScrollInProgress).isTrue()
        assertThat(snappingFlingBehavior.animationTarget).isEqualTo(0)

        // Now re-enable the clock advancement and let the snap animation run
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        // ...and assert that we 'sprang back' to page 0
        lazyListState.assertCurrentItem(index = 0, offset = 0)
    }

    @Test
    fun snapIndex() {
        val lazyListState = LazyListState()
        val snappedIndex = Ref<Int>()
        var snapIndex = 0
        val snappingFlingBehavior = createSnapFlingBehavior(
            lazyListState = lazyListState,
            snapIndex = { _, _ ->
                // We increase the calculated index by 3
                snapIndex.also { snappedIndex.value = it }
            }
        )
        setTestContent(
            flingBehavior = snappingFlingBehavior,
            lazyListState = lazyListState,
            count = 10,
        )

        // Forward fling
        snapIndex = 5
        rule.onNodeWithTag("layout").swipeAcrossCenter(-MediumSwipeDistance)
        rule.waitForIdle()
        // ...and assert that we now laid out from our increased snap index
        lazyListState.assertCurrentItem(index = 5)

        // Backwards fling, but snapIndex is forward
        snapIndex = 9
        rule.onNodeWithTag("layout").swipeAcrossCenter(MediumSwipeDistance)
        rule.waitForIdle()
        // ...and assert that we now laid out from our increased snap index
        lazyListState.assertCurrentItem(index = 9)

        // Backwards fling
        snapIndex = 0
        rule.onNodeWithTag("layout").swipeAcrossCenter(MediumSwipeDistance)
        rule.waitForIdle()
        // ...and assert that we now laid out from our increased snap index
        lazyListState.assertCurrentItem(index = 0)

        // Forward fling
        snapIndex = 9
        rule.onNodeWithTag("layout").swipeAcrossCenter(-MediumSwipeDistance)
        rule.waitForIdle()
        // ...and assert that we now laid out from our increased snap index
        lazyListState.assertCurrentItem(index = 9)

        // Forward fling, but snapIndex is backwards
        snapIndex = 5
        rule.onNodeWithTag("layout").swipeAcrossCenter(-MediumSwipeDistance)
        rule.waitForIdle()
        // ...and assert that we now laid out from our increased snap index
        lazyListState.assertCurrentItem(index = 5)
    }

    /**
     * Swipe across the center of the node. The major axis of the swipe is defined by the
     * overriding test.
     *
     * @param distancePercentage The swipe distance in percentage of the node's size.
     * Negative numbers mean swipe towards the start, positive towards the end.
     * @param velocityPerSec Target end velocity for the swipe in Dps per second
     */
    abstract fun SemanticsNodeInteraction.swipeAcrossCenter(
        distancePercentage: Float,
        velocityPerSec: Dp = MediumVelocity
    ): SemanticsNodeInteraction

    private fun setTestContent(
        count: Int,
        lazyListState: LazyListState = LazyListState(),
        flingBehavior: SnapperFlingBehavior = createSnapFlingBehavior(lazyListState),
    ) {
        setTestContent(
            flingBehavior = flingBehavior,
            count = { count },
            lazyListState = lazyListState,
        )
    }

    protected abstract fun setTestContent(
        flingBehavior: SnapperFlingBehavior,
        count: () -> Int,
        lazyListState: LazyListState = LazyListState(),
    )

    private fun createSnapFlingBehavior(
        lazyListState: LazyListState,
        snapIndex: ((SnapperLayoutInfo, targetIndex: Int) -> Int)? = null,
    ): SnapperFlingBehavior = SnapperFlingBehavior(
        layoutInfo = LazyListSnapperLayoutInfo(
            lazyListState = lazyListState,
            endContentPadding = endContentPadding,
            snapOffsetForItem = SnapOffsets.Start,
        ),
        decayAnimationSpec = exponentialDecay(),
        snapIndex = snapIndex ?: { layout, index ->
            val currentIndex = layout.currentItem!!.index
            val forwardFling = index > currentIndex
            when {
                forwardFling -> currentIndex + snapIndexDelta
                else -> currentIndex + 1 - snapIndexDelta
            }.coerceIn(0, layout.totalItemsCount - 1)
        },
    )
}

/**
 * This doesn't handle the scroll range < lazy size, but that won't happen in these tests
 */
private fun LazyListState.isScrolledToEnd(): Boolean {
    val lastVisibleItem = layoutInfo.visibleItemsInfo.last()
    if (lastVisibleItem.index == layoutInfo.totalItemsCount - 1) {
        // This isn't perfect as it doesn't properly handle content padding, but good enough
        return (lastVisibleItem.offset + lastVisibleItem.size) <= layoutInfo.viewportEndOffset
    }
    return false
}

private fun LazyListState.assertCurrentItem(
    index: Int,
    offset: Int = 0,
) = assertCurrentItem(minIndex = index, maxIndex = index, offset = offset)

private fun LazyListState.assertCurrentItem(
    minIndex: Int = 0,
    maxIndex: Int = Int.MAX_VALUE,
    offset: Int = 0,
) {
    if (isScrolledToEnd()) return

    currentItem.let {
        assertThat(it.index).isAtLeast(minIndex)
        assertThat(it.index).isAtMost(maxIndex)
        assertThat(it.offset).isEqualTo(offset)
    }
}

private val LazyListState.currentItem: LazyListItemInfo
    get() = layoutInfo.visibleItemsInfo.asSequence()
        .filter { it.offset <= 0 }
        .last()
