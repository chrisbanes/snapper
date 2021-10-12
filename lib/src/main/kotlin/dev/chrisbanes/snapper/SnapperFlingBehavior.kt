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

@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.snapper

import androidx.annotation.Px
import androidx.compose.animation.core.AnimationScope
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.truncate

private const val DebugLog = false

@RequiresOptIn(message = "Snapper is experimental. The API may be changed in the future.")
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalSnapperApi

/**
 * Default values used for [SnapperFlingBehavior] & [rememberSnapperFlingBehavior].
 */
@ExperimentalSnapperApi
object SnapperFlingBehaviorDefaults {
    /**
     * [AnimationSpec] used as the default value for the `snapAnimationSpec` parameter on
     * [rememberSnapperFlingBehavior] and [SnapperFlingBehavior].
     */
    val SpringAnimationSpec: AnimationSpec<Float> = spring(stiffness = 400f)

    /**
     * The default implementation for the `maximumFlingDistance` parameter of
     * [rememberSnapperFlingBehavior] and [SnapperFlingBehavior], which does not limit
     * the fling distance.
     */
    val MaximumFlingDistance: (SnapFlingLayout) -> Int = { Int.MAX_VALUE }
}

/**
 * Create and remember a snapping [FlingBehavior] to be used with [LazyListState].
 *
 * @param lazyListState The [LazyListState] to update.
 * @param decayAnimationSpec The decay animation spec to use for decayed flings.
 * @param springAnimationSpec The animation spec to use when snapping.
 * @param snapOffsetForItem Block which returns which offset the given item should 'snap' to.
 * See [SnapOffsets] for provided values.
 * @param maximumFlingDistance Block which returns the maximum fling distance in pixels.
 * The returned value should be >= 0.
 * @param endContentPadding The amount of content padding on the end edge of the lazy list
 * in pixels (end/bottom depending on the scrolling direction).
 */
@ExperimentalSnapperApi
@Composable
fun rememberSnapperFlingBehavior(
    lazyListState: LazyListState,
    decayAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
    springAnimationSpec: AnimationSpec<Float> = SnapperFlingBehaviorDefaults.SpringAnimationSpec,
    snapOffsetForItem: (layout: SnapFlingLayout, index: Int) -> Int = SnapOffsets.Center,
    maximumFlingDistance: (SnapFlingLayout) -> Int = SnapperFlingBehaviorDefaults.MaximumFlingDistance,
    @Px endContentPadding: Int = 0,
): SnapperFlingBehavior = remember(
    lazyListState,
    decayAnimationSpec,
    springAnimationSpec,
    snapOffsetForItem,
    maximumFlingDistance,
    endContentPadding,
) {
    SnapperFlingBehavior(
        layout = LazyListSnapFlingLayout(
            lazyListState = lazyListState,
            endContentPadding = endContentPadding,
            snapOffsetForItem = snapOffsetForItem,
        ),
        decayAnimationSpec = decayAnimationSpec,
        springAnimationSpec = springAnimationSpec,
        maximumFlingDistance = maximumFlingDistance,
    )
}

/**
 * Contains a number of values which can be used for the `snapOffsetForItem` parameter on
 * [rememberSnapperFlingBehavior] and [SnapperFlingBehavior].
 */
@Suppress("unused") // public vals which aren't used in the project
object SnapOffsets {
    /**
     * Snap offset which results in the start edge of the item, snapping to the start scrolling
     * edge of the lazy list.
     */
    val Start: (SnapFlingLayout, Int) -> Int = { _, _ -> 0 }

    /**
     * Snap offset which results in the item snapping in the center of the scrolling viewport
     * of the lazy list.
     */
    val Center: (SnapFlingLayout, Int) -> Int = { layout, index ->
        (layout.layoutSize - layout.sizeForItem(index)) / 2
    }

    /**
     * Snap offset which results in the end edge of the item, snapping to the end scrolling
     * edge of the lazy list.
     */
    val End: (SnapFlingLayout, Int) -> Int = { layout, index ->
        layout.layoutSize - layout.sizeForItem(index)
    }
}

/**
 * A snapping [FlingBehavior] for [LazyListState]. Typically this would be created
 * via [rememberSnapperFlingBehavior].
 *
 * Note: the default parameter value for [decayAnimationSpec] is different to the value used in
 * [rememberSnapperFlingBehavior], due to not being able to access composable functions.
 *
 * @param lazyListState The [LazyListState] to update.
 * @param decayAnimationSpec The decay animation spec to use for decayed flings.
 * @param springAnimationSpec The animation spec to use when snapping.
 * @param maximumFlingDistance Block which returns the maximum fling distance in pixels.
 * The returned value should be >= 0.
 */
@ExperimentalSnapperApi
class SnapperFlingBehavior(
    private val layout: SnapFlingLayout,
    private val decayAnimationSpec: DecayAnimationSpec<Float>,
    private val springAnimationSpec: AnimationSpec<Float> = SnapperFlingBehaviorDefaults.SpringAnimationSpec,
    private val maximumFlingDistance: (SnapFlingLayout) -> Int = SnapperFlingBehaviorDefaults.MaximumFlingDistance,
) : FlingBehavior {
    /**
     * The target item index for any on-going animations.
     */
    var animationTarget: Int? by mutableStateOf(null)
        private set

    override suspend fun ScrollScope.performFling(
        initialVelocity: Float
    ): Float {
        Napier.d(message = { "performFling. initialVelocity: $initialVelocity" })

        val currentIndex = layout.currentItemIndex
        return if (decayAnimationSpec.canDecayBeyondCurrentItem(initialVelocity)) {
            // If the decay fling can scroll past the current item, fling with decay
            performDecayFling(
                initialIndex = currentIndex,
                targetIndex = determineTargetIndexForDecay(currentIndex, initialVelocity),
                initialVelocity = initialVelocity,
            )
        } else {
            // Otherwise we 'spring' to current/next item
            performSpringFling(
                initialIndex = currentIndex,
                targetIndex = determineTargetIndexForSpring(currentIndex, initialVelocity),
                initialVelocity = initialVelocity,
            )
        }
    }

    /**
     * Performs a decaying fling.
     *
     * If [flingThenSpring] is set to true, then a fling-then-spring animation might be used.
     * If used, a decay fling will be run until we've scrolled to the preceding item of
     * [targetIndex]. Once that happens, the decay animation is stopped and a spring animation
     * is started to scroll the remainder of the distance. Visually this results in a much
     * smoother finish to the animation, as it will slowly come to a stop at [targetIndex].
     * Even if [flingThenSpring] is set to true, fling-then-spring animations are only available
     * when scrolling 2 items or more.
     *
     * When [flingThenSpring] is not used, the decay animation will be stopped immediately upon
     * scrolling past [targetIndex], which can result in an abrupt stop.
     */
    private suspend fun ScrollScope.performDecayFling(
        initialIndex: Int,
        targetIndex: Int,
        initialVelocity: Float,
        flingThenSpring: Boolean = true,
    ): Float {
        // If we're already at the target + snap offset, skip
        if (initialIndex == targetIndex && layout.distanceToCurrentItemSnap() == 0) {
            Napier.d(
                message = {
                    "Skipping decay: already at target. " +
                        "vel:$initialVelocity, " +
                        "current: $initialIndex, " +
                        "target: $targetIndex"
                }
            )
            return initialVelocity
        }

        Napier.d(
            message = {
                "Performing decay fling. " +
                    "vel:$initialVelocity, " +
                    "current item: $initialIndex, " +
                    "target: $targetIndex"
            }
        )

        var velocityLeft = initialVelocity
        var lastValue = 0f

        // We can only fling-then-spring if we're flinging >= 2 items...
        val canSpringThenFling = flingThenSpring && abs(targetIndex - initialIndex) >= 2
        var needSpringAfter = false

        try {
            // Update the animationTarget
            animationTarget = targetIndex

            AnimationState(
                initialValue = 0f,
                initialVelocity = initialVelocity,
            ).animateDecay(decayAnimationSpec) {
                val delta = value - lastValue
                val consumed = scrollBy(delta)
                lastValue = value
                velocityLeft = velocity

                if (abs(delta - consumed) > 0.5f) {
                    // If some of the scroll was not consumed, cancel the animation now as we're
                    // likely at the end of the scroll range
                    cancelAnimation()
                }

                if (isRunning && canSpringThenFling) {
                    // If we're still running and fling-then-spring is enabled, check to see
                    // if we're at the 1 item width away (in the relevant direction). If we are,
                    // set the spring-after flag and cancel the current decay
                    if (initialVelocity > 0 && layout.currentItemIndex == targetIndex - 1) {
                        needSpringAfter = true
                        cancelAnimation()
                    } else if (initialVelocity < 0 && layout.currentItemIndex == targetIndex) {
                        needSpringAfter = true
                        cancelAnimation()
                    }
                }

                if (isRunning && isSnapBackNeeded(initialVelocity, targetIndex, ::scrollBy)) {
                    // If we're still running, check to see if we need to snap-back
                    // (if we've scrolled past the target)
                    cancelAnimation()
                }
            }
        } finally {
            animationTarget = null
        }

        Napier.d(
            message = {
                "Decay fling finished. Distance: $lastValue. Final vel: $velocityLeft"
            }
        )

        if (needSpringAfter) {
            // The needSpringAfter flag is enabled, so start a spring to the target using the
            // remaining velocity
            return performSpringFling(layout.currentItemIndex, targetIndex, velocityLeft)
        }

        return velocityLeft
    }

    private fun determineTargetIndexForDecay(
        currentIndex: Int,
        initialVelocity: Float,
    ): Int {
        val distancePerItem = layout.distancePerItem()
        if (distancePerItem <= 0) {
            // If we don't have a valid distance, return the current item
            return currentIndex
        }

        val maximumFlingDistance = maximumFlingDistance(layout)
        require(maximumFlingDistance >= 0) {
            "Values returned from maximumFlingDistance should be >= 0"
        }
        val flingDistance = decayAnimationSpec.calculateTargetValue(0f, initialVelocity)
            .coerceIn(-maximumFlingDistance.toFloat(), maximumFlingDistance.toFloat())

        val distanceToNextSnap = if (initialVelocity > 0) {
            // forwards, toward index + 1
            layout.distanceToNextItemSnap()
        } else {
            layout.distanceToCurrentItemSnap()
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
            if (initialVelocity > 0) {
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

        return (currentIndex + indexDelta).coerceIn(0, layout.itemCount - 1)
    }

    @Suppress("unused_parameter")
    private fun determineTargetIndexForSpring(
        currentIndex: Int,
        initialVelocity: Float,
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
        val distanceToNextSnap = layout.distanceToNextItemSnap()
        val distanceToPreviousSnap = layout.distanceToCurrentItemSnap()

        return if (distanceToNextSnap < -distanceToPreviousSnap) {
            (currentIndex + 1).coerceIn(0, layout.itemCount - 1)
        } else {
            currentIndex
        }
    }

    private suspend fun ScrollScope.performSpringFling(
        initialIndex: Int,
        targetIndex: Int,
        initialVelocity: Float = 0f,
    ): Float {
        // If we're already at the target + snap offset, skip
        if (initialIndex == targetIndex && layout.distanceToCurrentItemSnap() == 0) {
            Napier.d(
                message = {
                    "Skipping spring: already at target. " +
                        "vel:$initialVelocity, " +
                        "initial item: $initialIndex, " +
                        "target: $targetIndex"
                }
            )
            return initialVelocity
        }

        Napier.d(
            message = {
                "Performing spring. " +
                    "vel:$initialVelocity, " +
                    "initial item: $initialIndex, " +
                    "target: $targetIndex"
            }
        )

        var velocityLeft = initialVelocity
        var lastValue = 0f

        try {
            // Update the animationTarget
            animationTarget = targetIndex

            AnimationState(
                initialValue = 0f,
                initialVelocity = initialVelocity,
            ).animateTo(
                targetValue = when {
                    targetIndex > initialIndex -> layout.distanceToNextItemSnap()
                    else -> layout.distanceToCurrentItemSnap()
                }.toFloat(),
                animationSpec = springAnimationSpec,
            ) {
                val delta = value - lastValue
                val consumed = scrollBy(delta)
                lastValue = value
                velocityLeft = velocity

                if (isSnapBackNeeded(initialVelocity, targetIndex, ::scrollBy)) {
                    cancelAnimation()
                } else if (abs(delta - consumed) > 0.5f) {
                    // If we're still running but some of the scroll was not consumed,
                    // cancel the animation now
                    cancelAnimation()
                }
            }
        } finally {
            animationTarget = null
        }

        Napier.d(
            message = {
                "Spring fling finished. Distance: $lastValue. Final vel: $velocityLeft"
            }
        )

        return velocityLeft
    }

    /**
     * Returns true if we needed to perform a snap back, and the animation should be cancelled.
     */
    private fun AnimationScope<Float, AnimationVector1D>.isSnapBackNeeded(
        initialVelocity: Float,
        targetIndex: Int,
        scrollBy: (pixels: Float) -> Float,
    ): Boolean {
        val currentIndex = layout.currentItemIndex

        Napier.d(message = { "scroll tick. vel:$velocity, current item: $currentIndex" })

        // Calculate the 'snap back'. If the returned value is 0, we don't need to do anything.
        val snapBackAmount = calculateSnapBack(initialVelocity, currentIndex, targetIndex)

        if (snapBackAmount != 0) {
            // If we've scrolled to/past the item, stop the animation. We may also need to
            // 'snap back' to the item as we may have scrolled past it
            Napier.d(
                message = {
                    "Scrolled past item. " +
                        "vel:$initialVelocity, " +
                        "current item: $currentIndex, " +
                        "target:$targetIndex"
                }
            )
            scrollBy(snapBackAmount.toFloat())
            return true
        }

        return false
    }

    private fun DecayAnimationSpec<Float>.canDecayBeyondCurrentItem(
        initialVelocity: Float,
    ): Boolean {
        // If we don't have a velocity, return false
        if (initialVelocity.absoluteValue < 0.5f) return false

        val flingDistance = calculateTargetValue(0f, initialVelocity)

        Napier.d(
            message = {
                "canDecayBeyondCurrentItem. " +
                    "initialVelocity: $initialVelocity, " +
                    "flingDistance: $flingDistance"
            }
        )

        return if (initialVelocity < 0) {
            // backwards, towards 0
            flingDistance <= layout.distanceToCurrentItemSnap()
        } else {
            // forwards, toward index + 1
            flingDistance >= layout.distanceToNextItemSnap()
        }
    }

    /**
     * Returns the distance in pixels that is required to 'snap back' to the [targetIndex].
     * Returns 0 if a snap back is not needed.
     */
    private fun calculateSnapBack(
        initialVelocity: Float,
        currentIndex: Int,
        targetIndex: Int,
    ): Int = when {
        // forwards
        initialVelocity > 0 && currentIndex == targetIndex + 1 -> {
            layout.distanceToCurrentItemSnap()
        }
        initialVelocity <= 0 && currentIndex == targetIndex - 1 -> {
            layout.distanceToNextItemSnap()
        }
        else -> 0
    }

    private companion object {
        init {
            if (DebugLog) {
                Napier.base(DebugAntilog(defaultTag = "SnapFlingBehavior"))
            }
        }
    }
}
