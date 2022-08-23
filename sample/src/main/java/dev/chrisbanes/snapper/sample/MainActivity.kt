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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.chrisbanes.snapper.sample.ui.theme.SnapperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Samples(appTitle = title.toString())
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun Samples(appTitle: String) {
    SnapperTheme {
        var currentSample by remember { mutableStateOf<Sample?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = currentSample?.title ?: appTitle) },
                    navigationIcon = {
                        if (currentSample != null) {
                            IconButton(onClick = { currentSample = null }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Navigate back"
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { contentPadding ->
            BackHandler(enabled = currentSample != null) {
                currentSample = null
            }

            Crossfade(
                targetState = currentSample,
                modifier = Modifier.padding(contentPadding)
            ) { sample ->
                if (sample != null) {
                    sample.content()
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(Samples) { sample ->
                            ListItem(
                                text = { Text(text = sample.title) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentSample = sample }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Samples(appTitle = "Snapper Sample")
}
