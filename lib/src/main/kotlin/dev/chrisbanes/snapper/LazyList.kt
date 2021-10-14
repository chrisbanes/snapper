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

import androidx.annotation.Px
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.aakira.napier.Napier
import kotlin.math.max
import kotlin.math.min
import kotlin.math.truncate

/**
 * Create and remember a snapping [FlingBehavior] to be used with [LazyListState].
 *
 * @param lazyListState The [LazyListState] to update.
 * @param snapOffsetForItem Block which returns which offset the given item should 'snap' to.
 * See [SnapOffsets] for provided values.
 * @param endContentPadding The amount of content padding on the end edge of the lazy list
 * in pixels (end/bottom depending on the scrolling direction).
 */
@ExperimentalSnapperApi
@Composable
fun rememberLazyListSnapperLayoutInfo(
    lazyListState: LazyListState,
    snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, index: Int) -> Int = SnapOffsets.Center,
    @Px endContentPadding: Int = 0,
): LazyListSnapperLayoutInfo = remember(lazyListState, endContentPadding, snapOffsetForItem) {
    LazyListSnapperLayoutInfo(
        lazyListState = lazyListState,
        endContentPadding = endContentPadding,
        snapOffsetForItem = snapOffsetForItem,
    )
}

@ExperimentalSnapperApi
class LazyListSnapperLayoutInfo(
    private val lazyListState: LazyListState,
    private val endContentPadding: Int,
    override val snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, index: Int) -> Int,
) : SnapperLayoutInfo() {
    /**
     * Offset start for LazyLists is always 0
     */
    override val startOffset: Int = 0

    override val endOffset: Int
        get() = lazyListState.layoutInfo.viewportEndOffset - endContentPadding

    private val itemCount: Int get() = lazyListState.layoutInfo.totalItemsCount

    override val currentItemIndex: Int
        get() = currentItem.index

    private val currentItem: LazyListItemInfo
        get() = lazyListState.layoutInfo.let { layoutInfo ->
            layoutInfo.visibleItemsInfo.last { itemInfo ->
                itemInfo.offset <= snapOffsetForItem(this, itemInfo.index)
            }
        }

    override fun distanceToNextItemSnap(): Int {
        val current = currentItem
        return current.size +
            current.offset +
            calculateItemSpacing() -
            snapOffsetForItem(this, current.index)
    }

    override fun distanceToCurrentItemSnap(): Int {
        val current = currentItem
        return current.offset - snapOffsetForItem(this, current.index)
    }

    override fun itemSize(index: Int): Int {
        return lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index == index
        }?.size ?: 0
    }

    override fun itemOffset(index: Int): Int {
        return lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index == index
        }?.offset ?: 0
    }

    override fun isAtScrollStart(): Boolean {
        return lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
            it.index == 0 && it.offset == startOffset
        } ?: true
    }

    override fun isAtScrollEnd(): Boolean {
        return lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
            it.index == lazyListState.layoutInfo.totalItemsCount - 1 &&
                (it.offset + it.size) <= endOffset
        } ?: true
    }

    override fun determineTargetIndexForSpring(
        currentIndex: Int,
        velocity: Float,
    ): Int {
        // We can't trust the velocity right now. We're waiting on
        // https://android-review.googlesource.com/c/platform/frameworks/support/+/1826965/,
        // which will be available in Compose Foundation 1.1.
        // TODO: uncomment this once we move to Compose Foundation 1.1
        // if (initialVelocity.absoluteValue > 1) {
        //    // If the velocity isn't zero, spring in the relevant direction
        //    return when {
        //        initialVelocity > 0 -> {
        //            (currentItemInfo.index + 1).coerceIn(0, lazyListState.layoutInfo.lastIndex)
        //        }
        //        else -> currentItemInfo.index
        //    }
        // }

        // Otherwise we look at the current offset, and spring to whichever is closer
        val distanceToNextSnap = distanceToNextItemSnap()
        val distanceToPreviousSnap = distanceToCurrentItemSnap()

        return if (distanceToNextSnap < -distanceToPreviousSnap) {
            (currentIndex + 1).coerceIn(0, itemCount - 1)
        } else {
            currentIndex
        }
    }

    override fun determineTargetIndexForDecay(
        currentIndex: Int,
        velocity: Float,
        decayAnimationSpec: DecayAnimationSpec<Float>,
        maximumFlingDistance: Float,
    ): Int {
        val distancePerItem = distancePerItem()
        if (distancePerItem <= 0) {
            // If we don't have a valid distance, return the current item
            return currentIndex
        }

        val flingDistance = decayAnimationSpec.calculateTargetValue(0f, velocity)
            .coerceIn(-maximumFlingDistance, maximumFlingDistance)

        val distanceToNextSnap = if (velocity > 0) {
            // forwards, toward index + 1
            distanceToNextItemSnap()
        } else {
            distanceToCurrentItemSnap()
        }

        /**
         * We calculate the index delta by dividing the fling distance by the average
         * scroll per child.
         *
         * We take the current item offset into account by subtracting `distanceToNextSnap`
         * from the fling distance. This is then applied as an extra index delta below.
         */
        val indexDelta = truncate(
            (flingDistance - distanceToNextSnap) / distancePerItem
        ).let {
            // As we removed the `distanceToNextSnap` from the fling distance, we need to calculate
            // whether we need to take that into account...
            if (velocity > 0) {
                // If we're flinging forward, distanceToNextSnap represents the scroll distance
                // to index + 1, so we need to add that (1) to the calculate delta
                it.toInt() + 1
            } else {
                // If we're going backwards, distanceToNextSnap represents the scroll distance
                // to the snap point of the current index, so there's nothing to do
                it.toInt()
            }
        }

        Napier.d(
            message = {
                "determineTargetIndexForDecay. " +
                    "currentIndex: $currentIndex, " +
                    "distancePerChild: $distancePerItem, " +
                    "maximumFlingDistance: $maximumFlingDistance, " +
                    "flingDistance: $flingDistance, " +
                    "indexDelta: $indexDelta"
            }
        )

        return (currentIndex + indexDelta).coerceIn(0, itemCount - 1)
    }

    /**
     * This attempts to calculate the item spacing for the layout, by looking at the distance
     * between the visible items. If there's only 1 visible item available, it returns 0.
     */
    private fun calculateItemSpacing(): Int = with(lazyListState.layoutInfo) {
        if (visibleItemsInfo.size >= 2) {
            val first = visibleItemsInfo[0]
            val second = visibleItemsInfo[1]
            second.offset - (first.size + first.offset)
        } else 0
    }

    /**
     * Computes an average pixel value to pass a single child.
     *
     * Returns a negative value if it cannot be calculated.
     *
     * @return A float value that is the average number of pixels needed to scroll by one view in
     * the relevant direction.
     */
    private fun distancePerItem(): Float = with(lazyListState.layoutInfo) {
        if (visibleItemsInfo.isEmpty()) return -1f

        val minPosView = visibleItemsInfo.minByOrNull { it.offset } ?: return -1f
        val maxPosView = visibleItemsInfo.maxByOrNull { it.offset + it.size } ?: return -1f

        val start = min(minPosView.offset, maxPosView.offset)
        val end = max(minPosView.offset + minPosView.size, maxPosView.offset + maxPosView.size)

        // We add an extra `itemSpacing` onto the calculated total distance. This ensures that
        // the calculated mean contains an item spacing for each visible item
        // (not just spacing between items)
        return when (val distance = end - start) {
            0 -> -1f // If we don't have a distance, return -1
            else -> (distance + calculateItemSpacing()) / visibleItemsInfo.size.toFloat()
        }
    }
}
