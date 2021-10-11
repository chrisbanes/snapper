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

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.max
import kotlin.math.min

interface SnapFlingLayout {

    val snapOffsetForItem: (layout: SnapFlingLayout, index: Int) -> Int

    val endContentPadding: Int

    val layoutSize: Int
    val itemCount: Int

    val currentItemIndex: Int

    fun sizeForItem(index: Int): Int
    fun offsetForItem(index: Int): Int

    fun distanceToPreviousSnapPoint(): Int
    fun distanceToNextSnapPoint(): Int

    /**
     * Computes an average pixel value to pass a single child.
     *
     * Returns a negative value if it cannot be calculated.
     *
     * @return A float value that is the average number of pixels needed to scroll by one view in
     * the relevant direction.
     */
    fun distancePerItem(): Float
}

internal class LazyListSnapFlingLayout(
    private val lazyListState: LazyListState,
    override val endContentPadding: Int,
    override val snapOffsetForItem: (layout: SnapFlingLayout, index: Int) -> Int,
) : SnapFlingLayout {

    override val layoutSize: Int
        get() {
            return lazyListState.layoutInfo.viewportEndOffset +
                lazyListState.layoutInfo.viewportStartOffset -
                endContentPadding
        }

    override val itemCount: Int
        get() = lazyListState.layoutInfo.totalItemsCount

    override val currentItemIndex: Int
        get() = currentItem.index

    private val currentItem: LazyListItemInfo
        get() = lazyListState.layoutInfo.let { layoutInfo ->
            layoutInfo.visibleItemsInfo.last { itemInfo ->
                itemInfo.offset <= snapOffsetForItem(this, itemInfo.index)
            }
        }

    override fun distanceToNextSnapPoint(): Int {
        val current = currentItem
        return current.size +
                current.offset +
                calculateItemSpacing() -
                snapOffsetForItem(this, current.index)
    }

    override fun distanceToPreviousSnapPoint(): Int {
        val current = currentItem
        return current.offset - snapOffsetForItem(this, current.index)
    }

    override fun sizeForItem(index: Int): Int {
        return lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index == index
        }?.size ?: 0
    }

    override fun offsetForItem(index: Int): Int {
        return lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index == index
        }?.offset ?: 0
    }

    override fun distancePerItem(): Float = with(lazyListState.layoutInfo) {
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
}

internal fun LazyListItemInfo.log(): String = "[i:$index,o:$offset,s:$size]"
