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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import dev.chrisbanes.internal.combineWithParameters
import dev.chrisbanes.internal.parameterizedParams
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Version of [BaseSnapperFlingLazyColumnTest] which is designed to be run on device/emulators.
 */
@RunWith(Parameterized::class)
class InstrumentedSnapperFlingLazyColumnTest(
    snapIndexDelta: Int,
    contentPadding: PaddingValues,
    itemSpacingDp: Int,
    reverseLayout: Boolean,
) : BaseSnapperFlingLazyColumnTest(
    snapIndexDelta,
    contentPadding,
    itemSpacingDp,
    reverseLayout,
) {
    companion object {
        /**
         * On device we only test a subset of the combined parameters.
         */
        @JvmStatic
        @Parameterized.Parameters(
            name = "snapIndexDelta={0}," +
                "contentPadding={1}," +
                "itemSpacing={2}," +
                "reverseLayout={3}"
        )
        fun data() = parameterizedParams()
            // snapIndexDelta
            .combineWithParameters(1, 4)
            // contentPadding
            .combineWithParameters(
                PaddingValues(bottom = 32.dp), // Alignment.Top
                PaddingValues(vertical = 32.dp), // Alignment.Center
                PaddingValues(top = 32.dp), // Alignment.Bottom
            )
            // itemSpacingDp
            .combineWithParameters(0, 4)
            // reverseLayout
            .combineWithParameters(false)
    }
}
