package dev.chrisbanes.snapper.sample

import androidx.compose.runtime.Composable

@OptIn(ExperimentalStdlibApi::class)
val Samples = buildList {
    addAll(LazyRowSamples)
}

data class Sample(
    val title: String,
    val content: @Composable () -> Unit,
)

