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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Create and remember a snapping [FlingBehavior] to be used with [LazyListState].
 *
 * This is a convenience function for using [rememberLazyListSnapperLayoutInfo] and
 * [rememberSnapperFlingBehavior]. If you require access to the layout info, you can safely use
 * those APIs directly.
 *
 * @param lazyListState The [LazyListState] to update.
 * @param snapOffsetForItem Block which returns which offset the given item should 'snap' to.
 * See [SnapOffsets] for provided values.
 * @param endContentPadding The amount of content padding on the end edge of the lazy list
 * in dps (end/bottom depending on the scrolling direction).
 * @param decayAnimationSpec The decay animation spec to use for decayed flings.
 * @param springAnimationSpec The animation spec to use when snapping.
 * @param maximumFlingDistance Block which returns the maximum fling distance in pixels.
 * The returned value should be > 0.
 */
@ExperimentalSnapperApi
@Composable
fun rememberSnapperFlingBehavior(
    lazyListState: LazyListState,
    snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int = SnapOffsets.Center,
    endContentPadding: Dp = 0.dp,
    snapItemsCount: Int = 1,
    decayAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
    springAnimationSpec: AnimationSpec<Float> = SnapperFlingBehaviorDefaults.SpringAnimationSpec,
    maximumFlingDistance: (SnapperLayoutInfo) -> Float = SnapperFlingBehaviorDefaults.MaximumFlingDistance,
): SnapperFlingBehavior = rememberSnapperFlingBehavior(
    layoutInfo = rememberLazyListSnapperLayoutInfo(
        lazyListState = lazyListState,
        snapItemsCount = snapItemsCount,
        snapOffsetForItem = snapOffsetForItem,
        endContentPadding = endContentPadding
    ),
    snapItemsCount = snapItemsCount,
    decayAnimationSpec = decayAnimationSpec,
    springAnimationSpec = springAnimationSpec,
    maximumFlingDistance = maximumFlingDistance
)

/**
 * Create and remember a [SnapperLayoutInfo] which works with [LazyListState].
 *
 * @param lazyListState The [LazyListState] to update.
 * @param snapOffsetForItem Block which returns which offset the given item should 'snap' to.
 * See [SnapOffsets] for provided values.
 * @param endContentPadding The amount of content padding on the end edge of the lazy list
 * in dps (end/bottom depending on the scrolling direction).
 */
@ExperimentalSnapperApi
@Composable
fun rememberLazyListSnapperLayoutInfo(
    lazyListState: LazyListState,
    snapItemsCount: Int = 1,
    snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int = SnapOffsets.Center,
    endContentPadding: Dp = 0.dp,
): LazyListSnapperLayoutInfo = remember(lazyListState, snapOffsetForItem, snapItemsCount) {
    LazyListSnapperLayoutInfo(
        lazyListState = lazyListState,
        snapOffsetForItem = snapOffsetForItem,
        snapItemsCount = snapItemsCount
    )
}.apply {
    this.endContentPadding = with(LocalDensity.current) { endContentPadding.roundToPx() }
}

/**
 * A [SnapperLayoutInfo] which works with [LazyListState]. Typically this would be remembered
 * using [rememberLazyListSnapperLayoutInfo].
 *
 * @param lazyListState The [LazyListState] to update.
 * @param snapOffsetForItem Block which returns which offset the given item should 'snap' to.
 * See [SnapOffsets] for provided values.
 * @param endContentPadding The amount of content padding on the end edge of the lazy list
 * in pixels (end/bottom depending on the scrolling direction).
 */
@ExperimentalSnapperApi
class LazyListSnapperLayoutInfo(
    private val lazyListState: LazyListState,
    private val snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int,
    private val snapItemsCount: Int,
    endContentPadding: Int = 0,
) : SnapperLayoutInfo() {
    private fun Int.minIndexToSnap(): Int {
        var indexToSnap = -1
        var firstItem: SnapperLayoutItemInfo? = null
        for (visibleItem in visibleItems.asIterable()) {
            if (firstItem == null) firstItem = visibleItem
            if (visibleItem.index % snapItemsCount == 0) {
                indexToSnap = visibleItem.index
                break
            }
        }
        indexToSnap = when {
            indexToSnap == -1 -> (this / snapItemsCount) * snapItemsCount
            indexToSnap != firstItem?.index -> indexToSnap - snapItemsCount
            else -> indexToSnap
        }

        return indexToSnap
    }

    override val startScrollOffset: Int = 0

    internal var endContentPadding: Int by mutableStateOf(endContentPadding)

    override val endScrollOffset: Int
        get() = lazyListState.layoutInfo.viewportEndOffset - endContentPadding

    private val itemCount: Int get() = lazyListState.layoutInfo.totalItemsCount

    override val currentItem: SnapperLayoutItemInfo?
        get() = visibleItems.lastOrNull { it.offset <= snapOffsetForItem(this, it) }

    override val visibleItems: Sequence<SnapperLayoutItemInfo>
        get() = lazyListState.layoutInfo.visibleItemsInfo.asSequence()
            .map(::LazyListSnapperLayoutItemInfo)

    override fun distanceToIndexSnap(index: Int): Int {
        val itemInfo = visibleItems.firstOrNull { it.index == index }
        if (snapItemsCount == 1 && itemInfo != null) {
            // If we have the item visible, we can calculate using the offset. Woop.
            return itemInfo.offset - snapOffsetForItem(this, itemInfo)
        }

        // Otherwise we need to guesstimate, using the current item snap point and
        // multiplying distancePerItem by the index delta
        val currentItem = currentItem ?: return 0 // TODO: throw?
        return ((index - currentItem.index) * estimateDistancePerItem()).roundToInt() +
            currentItem.offset -
            snapOffsetForItem(this, currentItem)
    }

    override fun canScrollTowardsStart(): Boolean {
        return lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
            it.index > 0 || it.offset < startScrollOffset
        } ?: false
    }

    override fun canScrollTowardsEnd(): Boolean {
        return lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
            it.index < itemCount - 1 || (it.offset + it.size) > endScrollOffset
        } ?: false
    }

    override fun determineTargetIndex(
        velocity: Float,
        decayAnimationSpec: DecayAnimationSpec<Float>,
        maximumFlingDistance: Float,
    ): Int {
        val curr = currentItem ?: return -1
        val indexToSnap = curr.index.minIndexToSnap()

        val distancePerItem = estimateDistancePerItem()
        if (distancePerItem <= 0) {
            // If we don't have a valid distance, return the current item
            return indexToSnap
        }

        val flingDistance = decayAnimationSpec.calculateTargetValue(0f, velocity)
            .coerceIn(-maximumFlingDistance, maximumFlingDistance)

        val distanceNext = (indexToSnap + snapItemsCount - curr.index) * distancePerItem
        val distanceCurrent = (curr.index - indexToSnap) * distancePerItem

        // If the fling doesn't reach the next snap point (in the fling direction), we try
        // and snap depending on which snap point is closer to the current scroll position
        if (
            (flingDistance >= 0 && flingDistance < distanceNext / 2) ||
            (flingDistance < 0 && -flingDistance < distanceCurrent / 2)
        ) {
            return if (distanceNext + curr.offset < distanceCurrent - curr.offset) {
                (indexToSnap + snapItemsCount).coerceIn(0, (itemCount / snapItemsCount) * snapItemsCount)
            } else {
                indexToSnap
            }
        }

        return if (flingDistance < 0) {
            val shifting = (flingDistance + (curr.index - indexToSnap - snapItemsCount) * distancePerItem - curr.offset) / distancePerItem
            var targetIndex = (shifting.toInt() / snapItemsCount) * snapItemsCount
            val snapOffset = shifting - targetIndex
            if (snapOffset > -snapItemsCount / 2f) targetIndex += snapItemsCount
            indexToSnap + targetIndex
        } else {
            val shifting = ((curr.index - indexToSnap) * distancePerItem + flingDistance - curr.offset) / distancePerItem
            var targetIndex = (shifting.toInt() / snapItemsCount) * snapItemsCount
            val snapOffset = shifting - targetIndex
            if (snapOffset > snapItemsCount / 2f) targetIndex += snapItemsCount
            indexToSnap + targetIndex
        }
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
    private fun estimateDistancePerItem(): Float = with(lazyListState.layoutInfo) {
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

private class LazyListSnapperLayoutItemInfo(
    private val lazyListItem: LazyListItemInfo,
) : SnapperLayoutItemInfo() {
    override val index: Int get() = lazyListItem.index
    override val offset: Int get() = lazyListItem.offset
    override val size: Int get() = lazyListItem.size
}
