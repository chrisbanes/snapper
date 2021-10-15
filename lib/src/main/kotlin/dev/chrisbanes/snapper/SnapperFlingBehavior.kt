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
 * Create and remember a snapping [FlingBehavior] to be used with the given [layoutInfo].
 *
 * @param layoutInfo The [SnapperLayoutInfo] to use. For lazy layouts,
 * you can use [rememberLazyListSnapperLayoutInfo].
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

/**
 * Contains the necessary information about the scrolling layout for [SnapperFlingBehavior]
 * to determine how to fling.
 */
@ExperimentalSnapperApi
abstract class SnapperLayoutInfo {
    /**
     * The start offset of where items can be scrolled to. This value should only include
     * scrollable regions. For example this should not include fixed content padding.
     * For most layouts, this will be 0.
     */
    abstract val startScrollOffset: Int

    /**
     * The end offset of where items can be scrolled to. This value should only include
     * scrollable regions. For example this should not include fixed content padding.
     * For most layouts, this will the width of the container, minus content padding.
     */
    abstract val endScrollOffset: Int

    /**
     * A sequence containing the currently visible items in the layout.
     */
    abstract val visibleItems: Sequence<SnapperLayoutItemInfo>

    /**
     * The current item which covers the desired snap point, or null if there is no item.
     * The item returned may not yet currently be snapped into the final position.
     */
    abstract val currentItem: SnapperLayoutItemInfo?

    /**
     * Calculate the desired target which should be scrolled to for the given [velocity].
     *
     * @param velocity Velocity of the fling. This can be 0.
     * @param decayAnimationSpec The decay fling animation spec.
     * @param maximumFlingDistance The maximum distance in pixels which should be scrolled.
     */
    abstract fun determineTargetIndex(
        velocity: Float,
        decayAnimationSpec: DecayAnimationSpec<Float>,
        maximumFlingDistance: Float,
    ): Int

    /**
     * Calculate the distance in pixels needed to scroll to the given [index]. The value returned
     * signifies which direction to scroll in:
     *
     * - Positive values indicate to scroll towards the end.
     * - Negative values indicate to scroll towards the start.
     *
     * If a precise calculation can not be found, a realistic estimate is acceptable.
     */
    abstract fun distanceToIndexSnap(index: Int): Int

    /**
     * Returns true if the layout has some scroll range remaining to scroll towards the start.
     */
    abstract fun canScrollTowardsStart(): Boolean

    /**
     * Returns true if the layout has some scroll range remaining to scroll towards the end.
     */
    abstract fun canScrollTowardsEnd(): Boolean
}

/**
 * Contains information about a single item in a scrolling layout.
 */
abstract class SnapperLayoutItemInfo {
    abstract val index: Int
    abstract val offset: Int
    abstract val size: Int

    override fun toString(): String {
        return "SnapperLayoutItemInfo(index=$index, offset=$offset, size=$size)"
    }
}

/**
 * Contains a number of values which can be used for the `snapOffsetForItem` parameter on
 * [rememberLazyListSnapperLayoutInfo] and [LazyListSnapperLayoutInfo].
 */
@ExperimentalSnapperApi
@Suppress("unused") // public vals which aren't used in the project
object SnapOffsets {
    /**
     * Snap offset which results in the start edge of the item, snapping to the start scrolling
     * edge of the lazy list.
     */
    val Start: (SnapperLayoutInfo, SnapperLayoutItemInfo) -> Int =
        { layout, _ -> layout.startScrollOffset }

    /**
     * Snap offset which results in the item snapping in the center of the scrolling viewport
     * of the lazy list.
     */
    val Center: (SnapperLayoutInfo, SnapperLayoutItemInfo) -> Int = { layout, item ->
        layout.startScrollOffset + (layout.endScrollOffset - layout.startScrollOffset - item.size) / 2
    }

    /**
     * Snap offset which results in the end edge of the item, snapping to the end scrolling
     * edge of the lazy list.
     */
    val End: (SnapperLayoutInfo, SnapperLayoutItemInfo) -> Int = { layout, item ->
        layout.endScrollOffset - item.size
    }
}

/**
 * A snapping [FlingBehavior] for [LazyListState]. Typically this would be created
 * via [rememberSnapperFlingBehavior].
 *
 * Note: the default parameter value for [decayAnimationSpec] is different to the value used in
 * [rememberSnapperFlingBehavior], due to not being able to access composable functions.
 *
 * @param layoutInfo The [SnapperLayoutInfo] to use.
 * @param decayAnimationSpec The decay animation spec to use for decayed flings.
 * @param springAnimationSpec The animation spec to use when snapping.
 * @param maximumFlingDistance Block which returns the maximum fling distance in pixels.
 * The returned value should be > 0.
 */
@ExperimentalSnapperApi
class SnapperFlingBehavior(
    private val layoutInfo: SnapperLayoutInfo,
    private val maximumFlingDistance: (SnapperLayoutInfo) -> Float = SnapperFlingBehaviorDefaults.MaximumFlingDistance,
    private val decayAnimationSpec: DecayAnimationSpec<Float>,
    private val springAnimationSpec: AnimationSpec<Float> = SnapperFlingBehaviorDefaults.SpringAnimationSpec,
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
        if (!layoutInfo.canScrollTowardsStart() || !layoutInfo.canScrollTowardsEnd()) {
            return initialVelocity
        }

        Napier.d(message = { "initialVelocity: $initialVelocity" })

        val maxFlingDistance = maximumFlingDistance(layoutInfo)
        require(maxFlingDistance > 0) {
            "Distance returned by maximumFlingDistance should be greater than 0"
        }

        return flingToIndex(
            index = layoutInfo.determineTargetIndex(
                velocity = initialVelocity,
                decayAnimationSpec = decayAnimationSpec,
                maximumFlingDistance = maxFlingDistance,
            ),
            initialVelocity = initialVelocity,
        )
    }

    private suspend fun ScrollScope.flingToIndex(
        index: Int,
        initialVelocity: Float,
    ): Float {
        val initialItem = layoutInfo.currentItem ?: return initialVelocity

        if (initialItem.index == index && layoutInfo.distanceToIndexSnap(initialItem.index) == 0) {
            Napier.d(
                message = {
                    "Skipping fling: already at target. " +
                        "vel:$initialVelocity, " +
                        "initial item: $initialItem, " +
                        "target: $index"
                }
            )
            return consumeVelocityIfNotAtScrollEdge(initialVelocity)
        }

        return if (decayAnimationSpec.canDecayBeyondCurrentItem(initialVelocity, initialItem)) {
            // If the decay fling can scroll past the current item, fling with decay
            performDecayFling(
                initialItem = initialItem,
                targetIndex = index,
                initialVelocity = initialVelocity,
            )
        } else {
            // Otherwise we 'spring' to current/next item
            performSpringFling(
                initialItem = initialItem,
                targetIndex = index,
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
        initialItem: SnapperLayoutItemInfo,
        targetIndex: Int,
        initialVelocity: Float,
        flingThenSpring: Boolean = true,
    ): Float {
        // If we're already at the target + snap offset, skip
        if (initialItem.index == targetIndex && layoutInfo.distanceToIndexSnap(initialItem.index) == 0) {
            Napier.d(
                message = {
                    "Skipping decay: already at target. " +
                        "vel:$initialVelocity, " +
                        "current item: $initialItem, " +
                        "target: $targetIndex"
                }
            )
            return consumeVelocityIfNotAtScrollEdge(initialVelocity)
        }

        Napier.d(
            message = {
                "Performing decay fling. " +
                    "vel:$initialVelocity, " +
                    "current item: $initialItem, " +
                    "target: $targetIndex"
            }
        )

        var velocityLeft = initialVelocity
        var lastValue = 0f

        // We can only fling-then-spring if we're flinging >= 2 items...
        val canSpringThenFling = flingThenSpring && abs(targetIndex - initialItem.index) >= 2
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

                val currentItem = layoutInfo.currentItem
                if (currentItem == null) {
                    cancelAnimation()
                    return@animateDecay
                }

                if (isRunning && canSpringThenFling) {
                    // If we're still running and fling-then-spring is enabled, check to see
                    // if we're at the 1 item width away (in the relevant direction). If we are,
                    // set the spring-after flag and cancel the current decay
                    if (velocity > 0 && currentItem.index == targetIndex - 1) {
                        needSpringAfter = true
                        cancelAnimation()
                    } else if (velocity < 0 && currentItem.index == targetIndex) {
                        needSpringAfter = true
                        cancelAnimation()
                    }
                }

                if (isRunning && performSnapBackIfNeeded(currentItem, targetIndex, ::scrollBy)) {
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
            return performSpringFling(layoutInfo.currentItem!!, targetIndex, velocityLeft)
        }

        return consumeVelocityIfNotAtScrollEdge(velocityLeft)
    }

    private suspend fun ScrollScope.performSpringFling(
        initialItem: SnapperLayoutItemInfo,
        targetIndex: Int,
        initialVelocity: Float = 0f,
    ): Float {
        Napier.d(
            message = {
                "Performing spring. " +
                    "vel:$initialVelocity, " +
                    "initial item: $initialItem, " +
                    "target: $targetIndex"
            }
        )

        var velocityLeft = when {
            // Only use the initialVelocity if it is in the correct direction
            targetIndex > initialItem.index && initialVelocity > 0 -> initialVelocity
            targetIndex <= initialItem.index && initialVelocity < 0 -> initialVelocity
            // Otherwise start at 0 velocity
            else -> 0f
        }
        var lastValue = 0f

        try {
            // Update the animationTarget
            animationTarget = targetIndex

            AnimationState(
                initialValue = lastValue,
                initialVelocity = velocityLeft,
            ).animateTo(
                targetValue = layoutInfo.distanceToIndexSnap(targetIndex).toFloat(),
                animationSpec = springAnimationSpec,
            ) {
                val delta = value - lastValue
                val consumed = scrollBy(delta)
                lastValue = value
                velocityLeft = velocity

                val currentItem = layoutInfo.currentItem
                if (currentItem == null) {
                    cancelAnimation()
                    return@animateTo
                }

                if (performSnapBackIfNeeded(currentItem, targetIndex, ::scrollBy)) {
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
        currentItem: SnapperLayoutItemInfo,
        targetIndex: Int,
        scrollBy: (pixels: Float) -> Float,
    ): Boolean {
        Napier.d(
            message = {
                "scroll tick. " +
                    "vel:$velocity, " +
                    "current item: $currentItem"
            }
        )

        // Calculate the 'snap back'. If the returned value is 0, we don't need to do anything.
        val snapBackAmount = calculateSnapBack(velocity, currentItem, targetIndex)

        if (snapBackAmount != 0) {
            // If we've scrolled to/past the item, stop the animation. We may also need to
            // 'snap back' to the item as we may have scrolled past it
            Napier.d(
                message = {
                    "Scrolled past item. " +
                        "vel:$velocity, " +
                        "current item: $currentItem} " +
                        "target:$targetIndex"
                }
            )
            scrollBy(snapBackAmount.toFloat())
            return true
        }

        return false
    }

    private fun DecayAnimationSpec<Float>.canDecayBeyondCurrentItem(
        velocity: Float,
        currentItem: SnapperLayoutItemInfo,
    ): Boolean {
        // If we don't have a velocity, return false
        if (velocity.absoluteValue < 0.5f) return false

        val flingDistance = calculateTargetValue(0f, velocity)

        Napier.d(
            message = {
                "canDecayBeyondCurrentItem. " +
                    "initialVelocity: $velocity, " +
                    "flingDistance: $flingDistance, " +
                    "current item: $currentItem"
            }
        )

        return if (velocity < 0) {
            // backwards, towards 0
            flingDistance <= layoutInfo.distanceToIndexSnap(currentItem.index)
        } else {
            // forwards, toward index + 1
            flingDistance >= layoutInfo.distanceToIndexSnap(currentItem.index + 1)
        }
    }

    /**
     * Returns the distance in pixels that is required to 'snap back' to the [targetIndex].
     * Returns 0 if a snap back is not needed.
     */
    private fun calculateSnapBack(
        initialVelocity: Float,
        currentItem: SnapperLayoutItemInfo,
        targetIndex: Int,
    ): Int = when {
        // forwards
        initialVelocity > 0 && currentItem.index == targetIndex -> {
            layoutInfo.distanceToIndexSnap(currentItem.index)
        }
        initialVelocity < 0 && currentItem.index == targetIndex - 1 -> {
            layoutInfo.distanceToIndexSnap(currentItem.index + 1)
        }
        else -> 0
    }

    private fun consumeVelocityIfNotAtScrollEdge(velocity: Float): Float {
        if (velocity < 0 && !layoutInfo.canScrollTowardsStart()) {
            // If there is remaining velocity towards the start and we're at the scroll start,
            // we don't consume. This enables the overscroll effect where supported
            return velocity
        } else if (velocity > 0 && !layoutInfo.canScrollTowardsEnd()) {
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
}
