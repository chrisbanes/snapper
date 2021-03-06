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

package dev.chrisbanes.snapper.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter

/**
 * Simple pager item which displays an image
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun ImageItem(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Box {
            Image(
                painter = rememberImagePainter(
                    data = rememberRandomSampleImageUrl(width = 400),
                    builder = { crossfade(true) },
                ),
                contentScale = ContentScale.Crop,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )

            Text(
                text = text,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(MaterialTheme.colors.surface, RoundedCornerShape(4.dp))
                    .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                    .padding(8.dp)
                    .wrapContentSize(Alignment.Center)
            )
        }
    }
}

private val rangeForRandom = (0..100000)

fun randomSampleImageUrl(
    seed: Int = rangeForRandom.random(),
    width: Int = 300,
    height: Int = width,
): String = "https://picsum.photos/seed/$seed/$width/$height"

/**
 * Remember a URL generate by [randomSampleImageUrl].
 */
@Composable
fun rememberRandomSampleImageUrl(
    seed: Int = rangeForRandom.random(),
    width: Int = 300,
    height: Int = width,
): String = remember { randomSampleImageUrl(seed, width, height) }
