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
    val MaximumFlingDistance: (SnapperLayoutInfo) -> Float = { Float.MAX_VALUE }
}

/**
 * Create and remember a snapping [FlingBehavior] to be used with [LazyListState].
 *
 * @param layoutInfo The [SnapperLayoutInfo] to use.
 * @param decayAnimationSpec The decay animation spec to use for decayed flings.
 * @param springAnimationSpec The animation spec to use when snapping.
 * @param maximumFlingDistance Block which returns the maximum fling distance in pixels.
 * The returned value should be > 0.
 */
@ExperimentalSnapperApi
@Composable
fun rememberSnapperFlingBehavior(
    layoutInfo: SnapperLayoutInfo,
    decayAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
    springAnimationSpec: AnimationSpec<Float> = SnapperFlingBehaviorDefaults.SpringAnimationSpec,
    maximumFlingDistance: (SnapperLayoutInfo) -> Float = SnapperFlingBehaviorDefaults.MaximumFlingDistance,
): SnapperFlingBehavior = remember(
    layoutInfo,
    decayAnimationSpec,
    springAnimationSpec,
    maximumFlingDistance,
) {
    SnapperFlingBehavior(
        layoutInfo = layoutInfo,
        decayAnimationSpec = decayAnimationSpec,
        springAnimationSpec = springAnimationSpec,
        maximumFlingDistance = maximumFlingDistance,
    )
}

@ExperimentalSnapperApi
abstract class SnapperLayoutInfo {
    abstract val snapOffsetForItem: (layoutInfo: SnapperLayoutInfo, index: Int) -> Int

    abstract val startOffset: Int
    abstract val endOffset: Int
    abstract val currentItemIndex: Int

    abstract fun determineTargetIndexForDecay(
        currentIndex: Int,
        velocity: Float,
        decayAnimationSpec: DecayAnimationSpec<Float>,
        maximumFlingDistance: Float,
    ): Int

    abstract fun determineTargetIndexForSpring(
        currentIndex: Int,
        velocity: Float,
    ): Int

    abstract fun itemSize(index: Int): Int
    abstract fun itemOffset(index: Int): Int

    abstract fun distanceToCurrentItemSnap(): Int
    abstract fun distanceToNextItemSnap(): Int

    abstract fun isAtScrollStart(): Boolean
    abstract fun isAtScrollEnd(): Boolean
}

/**
 * Contains a number of values which can be used for the `snapOffsetForItem` parameter on
 * [rememberSnapperFlingBehavior] and [SnapperFlingBehavior].
 */
@ExperimentalSnapperApi
@Suppress("unused") // public vals which aren't used in the project
object SnapOffsets {
    /**
     * Snap offset which results in the start edge of the item, snapping to the start scrolling
     * edge of the lazy list.
     */
    val Start: (SnapperLayoutInfo, Int) -> Int = { layout, _ -> layout.startOffset }

    /**
     * Snap offset which results in the item snapping in the center of the scrolling viewport
     * of the lazy list.
     */
    val Center: (SnapperLayoutInfo, Int) -> Int = { layout, index ->
        layout.startOffset + (layout.endOffset - layout.startOffset - layout.itemSize(index)) / 2
    }

    /**
     * Snap offset which results in the end edge of the item, snapping to the end scrolling
     * edge of the lazy list.
     */
    val End: (SnapperLayoutInfo, Int) -> Int = { layout, index ->
        layout.endOffset - layout.itemSize(index)
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
 * The returned value should be > 0.
 */
@ExperimentalSnapperApi
class SnapperFlingBehavior(
    private val layoutInfo: SnapperLayoutInfo,
    private val decayAnimationSpec: DecayAnimationSpec<Float>,
    private val springAnimationSpec: AnimationSpec<Float> = SnapperFlingBehaviorDefaults.SpringAnimationSpec,
    private val maximumFlingDistance: (SnapperLayoutInfo) -> Float = SnapperFlingBehaviorDefaults.MaximumFlingDistance,
) : FlingBehavior {
    /**
     * The target item index for any on-going animations.
     */
    var animationTarget: Int? by mutableStateOf(null)
        private set

    override suspend fun ScrollScope.performFling(
        initialVelocity: Float
    ): Float {
        // If we're at the start/end of the scroll range, we don't snap and assume the user
        // wanted to scroll here.
        if (layoutInfo.isAtScrollStart() || layoutInfo.isAtScrollEnd()) {
            return initialVelocity
        }

        Napier.d(message = { "performFling. initialVelocity: $initialVelocity" })

        val maxFlingDistance = maximumFlingDistance(layoutInfo)
        require(maxFlingDistance > 0) {
            "Distance returned by maximumFlingDistance should be greater than 0"
        }

        val currentIndex = layoutInfo.currentItemIndex
        return if (decayAnimationSpec.canDecayBeyondCurrentItem(initialVelocity)) {
            // If the decay fling can scroll past the current item, fling with decay
            performDecayFling(
                initialIndex = currentIndex,
                targetIndex = layoutInfo.determineTargetIndexForDecay(
                    currentIndex = currentIndex,
                    velocity = initialVelocity,
                    decayAnimationSpec = decayAnimationSpec,
                    maximumFlingDistance = maxFlingDistance,
                ),
                initialVelocity = initialVelocity,
            )
        } else {
            // Otherwise we 'spring' to current/next item
            val targetIndex = layoutInfo.determineTargetIndexForSpring(currentIndex, initialVelocity)
            performSpringFling(
                initialIndex = currentIndex,
                targetIndex = targetIndex,
                initialVelocity = when {
                    // Only pass through the velocity if it is in the determined direction
                    targetIndex > currentIndex && initialVelocity > 0 -> initialVelocity
                    targetIndex <= currentIndex && initialVelocity < 0 -> initialVelocity
                    // Otherwise start at 0 velocity
                    else -> 0f
                },
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
        if (initialIndex == targetIndex && layoutInfo.distanceToCurrentItemSnap() == 0) {
            Napier.d(
                message = {
                    "Skipping decay: already at target. " +
                        "vel:$initialVelocity, " +
                        "current item: ${itemString(initialIndex)}, " +
                        "target: $targetIndex"
                }
            )
            return initialVelocity
        }

        Napier.d(
            message = {
                "Performing decay fling. " +
                    "vel:$initialVelocity, " +
                    "current item: ${itemString(initialIndex)}, " +
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
                    if (velocity > 0 && layoutInfo.currentItemIndex == targetIndex - 1) {
                        needSpringAfter = true
                        cancelAnimation()
                    } else if (velocity < 0 && layoutInfo.currentItemIndex == targetIndex) {
                        needSpringAfter = true
                        cancelAnimation()
                    }
                }

                if (isRunning && performSnapBackIfNeeded(targetIndex, ::scrollBy)) {
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
            return performSpringFling(layoutInfo.currentItemIndex, targetIndex, velocityLeft)
        }

        return consumeVelocityIfNotAtScrollEdge(velocityLeft)
    }

    private suspend fun ScrollScope.performSpringFling(
        initialIndex: Int,
        targetIndex: Int,
        initialVelocity: Float = 0f,
    ): Float {
        // If we're already at the target + snap offset, skip
        if (initialIndex == targetIndex && layoutInfo.distanceToCurrentItemSnap() == 0) {
            Napier.d(
                message = {
                    "Skipping spring: already at target. " +
                        "vel:$initialVelocity, " +
                        "initial item: ${itemString(initialIndex)}, " +
                        "target: $targetIndex"
                }
            )
            return initialVelocity
        }

        Napier.d(
            message = {
                "Performing spring. " +
                    "vel:$initialVelocity, " +
                    "initial item: ${itemString(initialIndex)}, " +
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
                    targetIndex > initialIndex -> layoutInfo.distanceToNextItemSnap()
                    else -> layoutInfo.distanceToCurrentItemSnap()
                }.toFloat(),
                animationSpec = springAnimationSpec,
            ) {
                val delta = value - lastValue
                val consumed = scrollBy(delta)
                lastValue = value
                velocityLeft = velocity

                if (performSnapBackIfNeeded(targetIndex, ::scrollBy)) {
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

        return consumeVelocityIfNotAtScrollEdge(velocityLeft)
    }

    /**
     * Returns true if we needed to perform a snap back, and the animation should be cancelled.
     */
    private fun AnimationScope<Float, AnimationVector1D>.performSnapBackIfNeeded(
        targetIndex: Int,
        scrollBy: (pixels: Float) -> Float,
    ): Boolean {
        val currentIndex = layoutInfo.currentItemIndex

        Napier.d(
            message = {
                "scroll tick. " +
                    "vel:$velocity, " +
                    "current item: ${itemString()}"
            }
        )

        // Calculate the 'snap back'. If the returned value is 0, we don't need to do anything.
        val snapBackAmount = calculateSnapBack(velocity, currentIndex, targetIndex)

        if (snapBackAmount != 0) {
            // If we've scrolled to/past the item, stop the animation. We may also need to
            // 'snap back' to the item as we may have scrolled past it
            Napier.d(
                message = {
                    "Scrolled past item. " +
                        "vel:$velocity, " +
                        "current item: ${itemString()} " +
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
                    "flingDistance: $flingDistance, " +
                    "current item: ${itemString()}"
            }
        )

        return if (initialVelocity < 0) {
            // backwards, towards 0
            flingDistance <= layoutInfo.distanceToCurrentItemSnap()
        } else {
            // forwards, toward index + 1
            flingDistance >= layoutInfo.distanceToNextItemSnap()
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
        initialVelocity > 0 && currentIndex == targetIndex -> {
            layoutInfo.distanceToCurrentItemSnap()
        }
        initialVelocity < 0 && currentIndex == targetIndex - 1 -> {
            layoutInfo.distanceToNextItemSnap()
        }
        else -> 0
    }

    private fun consumeVelocityIfNotAtScrollEdge(velocity: Float): Float {
        if (velocity < 0 && layoutInfo.isAtScrollStart()) {
            // If there is remaining velocity towards the start and we're at the scroll start,
            // we don't consume. This enables the overscroll effect where supported
            return velocity
        } else if (velocity > 0 && layoutInfo.isAtScrollEnd()) {
            // If there is remaining velocity towards the end and we're at the scroll end,
            // we don't consume. This enables the overscroll effect where supported
            return velocity
        }
        // Else we return 0 to consume the remaining velocity
        return 0f
    }

    private companion object {
        init {
            if (DebugLog) {
                Napier.base(DebugAntilog(defaultTag = "SnapFlingBehavior"))
            }
        }
    }

    private fun itemString(index: Int = layoutInfo.currentItemIndex): String {
        return "[" +
            "i:$index, " +
            "o:${layoutInfo.itemOffset(index)}, " +
            "s:${layoutInfo.itemSize(index)}" +
            "]"
    }
}
