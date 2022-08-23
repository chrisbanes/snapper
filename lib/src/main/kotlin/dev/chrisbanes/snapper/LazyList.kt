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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * @param decayAnimationSpec The decay animation spec to use for decayed flings.
 * @param springAnimationSpec The animation spec to use when snapping.
 * @param snapIndex Block which returns the index to snap to. The block is provided with the
 * [SnapperLayoutInfo], the index where the fling started, and the index which Snapper has
 * determined is the correct target index. Callers can override this value to any valid index
 * for the layout. Some common use cases include limiting the fling distance, and rounding up/down
 * to achieve snapping to groups of items.
 */
@ExperimentalSnapperApi
@Composable
public fun rememberSnapperFlingBehavior(
    lazyListState: LazyListState,
    snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int = SnapOffsets.Center,
    decayAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
    springAnimationSpec: AnimationSpec<Float> = SnapperFlingBehaviorDefaults.SpringAnimationSpec,
    snapIndex: (SnapperLayoutInfo, startIndex: Int, targetIndex: Int) -> Int = SnapperFlingBehaviorDefaults.SnapIndex,
): SnapperFlingBehavior = rememberSnapperFlingBehavior(
    layoutInfo = rememberLazyListSnapperLayoutInfo(lazyListState, snapOffsetForItem),
    decayAnimationSpec = decayAnimationSpec,
    springAnimationSpec = springAnimationSpec,
    snapIndex = snapIndex,
)

@Deprecated(
    "endContentPadding is no longer necessary to be passed in",
    ReplaceWith("rememberSnapperFlingBehavior(lazyListState, snapOffsetForItem, decayAnimationSpec, springAnimationSpec, snapIndex)")
)
@ExperimentalSnapperApi
@Composable
public fun rememberSnapperFlingBehavior(
    lazyListState: LazyListState,
    snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int = SnapOffsets.Center,
    @Suppress("UNUSED_PARAMETER") endContentPadding: Dp = 0.dp,
    decayAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
    springAnimationSpec: AnimationSpec<Float> = SnapperFlingBehaviorDefaults.SpringAnimationSpec,
    snapIndex: (SnapperLayoutInfo, startIndex: Int, targetIndex: Int) -> Int = SnapperFlingBehaviorDefaults.SnapIndex,
): SnapperFlingBehavior = rememberSnapperFlingBehavior(
    layoutInfo = rememberLazyListSnapperLayoutInfo(lazyListState, snapOffsetForItem),
    decayAnimationSpec = decayAnimationSpec,
    springAnimationSpec = springAnimationSpec,
    snapIndex = snapIndex,
)

@Composable
@Deprecated("The maximumFlingDistance parameter has been replaced with snapIndex")
@Suppress("DEPRECATION")
@ExperimentalSnapperApi
public fun rememberSnapperFlingBehavior(
    lazyListState: LazyListState,
    snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int = SnapOffsets.Center,
    endContentPadding: Dp = 0.dp,
    decayAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
    springAnimationSpec: AnimationSpec<Float> = SnapperFlingBehaviorDefaults.SpringAnimationSpec,
    maximumFlingDistance: (SnapperLayoutInfo) -> Float,
): SnapperFlingBehavior = rememberSnapperFlingBehavior(
    layoutInfo = rememberLazyListSnapperLayoutInfo(
        lazyListState = lazyListState,
        snapOffsetForItem = snapOffsetForItem,
        endContentPadding = endContentPadding
    ),
    decayAnimationSpec = decayAnimationSpec,
    springAnimationSpec = springAnimationSpec,
    maximumFlingDistance = maximumFlingDistance,
)

/**
 * Create and remember a [SnapperLayoutInfo] which works with [LazyListState].
 *
 * @param lazyListState The [LazyListState] to update.
 * @param snapOffsetForItem Block which returns which offset the given item should 'snap' to.
 * See [SnapOffsets] for provided values.
 */
@Deprecated(
    "endContentPadding is no longer necessary to be passed in",
    ReplaceWith("rememberLazyListSnapperLayoutInfo(lazyListState, snapOffsetForItem)")
)
@ExperimentalSnapperApi
@Composable
public fun rememberLazyListSnapperLayoutInfo(
    lazyListState: LazyListState,
    snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int = SnapOffsets.Center,
    @Suppress("UNUSED_PARAMETER") endContentPadding: Dp = 0.dp,
): LazyListSnapperLayoutInfo {
    return rememberLazyListSnapperLayoutInfo(lazyListState, snapOffsetForItem)
}

/**
 * Create and remember a [SnapperLayoutInfo] which works with [LazyListState].
 *
 * @param lazyListState The [LazyListState] to update.
 * @param snapOffsetForItem Block which returns which offset the given item should 'snap' to.
 * See [SnapOffsets] for provided values.
 */
@ExperimentalSnapperApi
@Composable
public fun rememberLazyListSnapperLayoutInfo(
    lazyListState: LazyListState,
    snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int = SnapOffsets.Center,
): LazyListSnapperLayoutInfo = remember(lazyListState, snapOffsetForItem) {
    LazyListSnapperLayoutInfo(
        lazyListState = lazyListState,
        snapOffsetForItem = snapOffsetForItem,
    )
}

/**
 * A [SnapperLayoutInfo] which works with [LazyListState]. Typically this would be remembered
 * using [rememberLazyListSnapperLayoutInfo].
 *
 * @param lazyListState The [LazyListState] to update.
 * @param snapOffsetForItem Block which returns which offset the given item should 'snap' to.
 * See [SnapOffsets] for provided values.
 */
@ExperimentalSnapperApi
public class LazyListSnapperLayoutInfo(
    private val lazyListState: LazyListState,
    private val snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int,
) : SnapperLayoutInfo() {

    @Deprecated(
        "endContentPadding is no longer necessary to be passed in",
        ReplaceWith("LazyListSnapperLayoutInfo(lazyListState, snapOffsetForItem)")
    )
    public constructor(
        lazyListState: LazyListState,
        snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int,
        @Suppress("UNUSED_PARAMETER") endContentPadding: Int = 0,
    ) : this(lazyListState, snapOffsetForItem)

    /**
     * Lazy lists always use 0 as the start scroll offset (within content padding)
     */
    override val startScrollOffset: Int = 0

    /**
     * viewportEndOffset is the last visible offset, so we need to remove any end content padding
     * to get the end of the scroll range
     */
    override val endScrollOffset: Int
        get() = lazyListState.layoutInfo.let { it.viewportEndOffset - it.afterContentPadding }

    private val itemCount: Int get() = lazyListState.layoutInfo.totalItemsCount

    override val totalItemsCount: Int
        get() = lazyListState.layoutInfo.totalItemsCount

    override val currentItem: SnapperLayoutItemInfo? by derivedStateOf {
        visibleItems.lastOrNull { it.offset <= snapOffsetForItem(this, it) }
    }

    override val visibleItems: Sequence<SnapperLayoutItemInfo>
        get() = lazyListState.layoutInfo.visibleItemsInfo.asSequence()
            .map(::LazyListSnapperLayoutItemInfo)

    override fun distanceToIndexSnap(index: Int): Int {
        val itemInfo = visibleItems.firstOrNull { it.index == index }
        if (itemInfo != null) {
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

        val distancePerItem = estimateDistancePerItem()
        if (distancePerItem <= 0) {
            // If we don't have a valid distance, return the current item
            return curr.index
        }

        val distanceToCurrent = distanceToIndexSnap(curr.index)
        val distanceToNext = distanceToIndexSnap(curr.index + 1)

        if (abs(velocity) < 0.5f) {
            // If we don't have a velocity, target whichever item is closer
            return when {
                distanceToCurrent.absoluteValue < distanceToNext.absoluteValue -> curr.index
                else -> curr.index + 1
            }.coerceIn(0, itemCount - 1)
        }

        // Otherwise we calculate using the velocity
        val flingDistance = decayAnimationSpec.calculateTargetValue(0f, velocity)
            .coerceIn(-maximumFlingDistance, maximumFlingDistance)
            .let { distance ->
                // It's likely that the user has already scrolled an amount before the fling
                // has been started. We compensate for that by removing the scrolled distance
                // from the calculated fling distance. This is necessary so that we don't fling
                // past the max fling distance.
                if (velocity < 0) {
                    (distance + distanceToNext).coerceAtMost(0f)
                } else {
                    (distance + distanceToCurrent).coerceAtLeast(0f)
                }
            }

        val flingIndexDelta = flingDistance / distancePerItem.toDouble()
        val currentItemOffsetRatio = distanceToCurrent / distancePerItem.toDouble()

        // The index offset from the current index. We round this value which results in
        // flings rounding towards the (relative) infinity. The key use case for this is to
        // support short + fast flings. These could result in a fling distance of ~70% of the
        // item distance (example). The rounding ensures that we target the next page.
        val indexOffset = (flingIndexDelta - currentItemOffsetRatio).roundToInt()

        return (curr.index + indexOffset).coerceIn(0, itemCount - 1)
            .also { result ->
                SnapperLog.d {
                    "determineTargetIndex. " +
                        "result: $result, " +
                        "current item: $curr, " +
                        "current item offset: ${"%.3f".format(currentItemOffsetRatio)}, " +
                        "distancePerItem: $distancePerItem, " +
                        "maximumFlingDistance: ${"%.3f".format(maximumFlingDistance)}, " +
                        "flingDistance: ${"%.3f".format(flingDistance)}, " +
                        "flingIndexDelta: ${"%.3f".format(flingIndexDelta)}"
                }
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
