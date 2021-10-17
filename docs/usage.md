
## Snap positions

Snapper supports the customization of where items snap to. By default Snapper will snap
items to the center of the layout container, but you can provide your own 'snap offset' via
the `snapOffsetForItem` parameters.

`snapOffsetForItem` is a parameter which takes a block in the form of `(layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int`,
and allows apps to supply custom logic of where to snap each individual item.

A number of predefined values are supplied in the [SnapOffsets](../api/lib/dev.chrisbanes.snapper/-snap-offsets/) class,
for snapping items to the start, center and end.

``` kotlin
LazyRow(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(
        lazyListState = lazyListState,
        snapOffsetForItem = SnapOffsets.Start,
    ),
) {
    // content
}
```

## Content padding

When setting content padding on `LazyRow` or `LazyColumn` it is important to supply that information
to Snapper. Unfortunately `LazyListState` does not currently supply enough information for Snapper
to calculate the scrollable region of the container, which is needed for the SnapOffsets feature above.

We workaround this by apps supplying the the scrollable direction 'end' content padding which is 
being used, via the `endContentPadding` parameter.

=== "LazyRow"

    ``` kotlin
    val lazyListState = rememberLazyListState()
    val contentPadding = PaddingValues(...)

    LazyRow(
        state = lazyListState,
        flingBehavior = rememberSnapperFlingBehavior(
            lazyListState = lazyListState,
            // We need to provide the unresolved end value, so use Ltr
            endContentPadding = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
        ),
        contentPadding = contentPadding,
    ) {
        // content
    }
    ```

=== "LazyColumn"

    ``` kotlin
    val lazyListState = rememberLazyListState()
    val contentPadding = PaddingValues(...)

    LazyColumn(
        state = lazyListState,
        flingBehavior = rememberSnapperFlingBehavior(
            lazyListState = lazyListState,
            endContentPadding = contentPadding.calculateBottomPadding(),
        ),
        contentPadding = contentPadding,
    ) {
        // content
    }
    ```

## Finding the 'current' item

Most of the time apps will probably use the short-hand convenience function: 
`rememberSnapperFlingBehavior(LazyListState)`, but there are times when
it is useful to get access to the `SnapperLayoutInfo`.

SnapperLayoutInfo provides lots of information about the 'snapping state' of 
the scrollable container, and provides access to the 'current item'.

For example, if you wish to invoke some action when a fling + snap has finished
you can do the following:

``` kotlin
val lazyListState = rememberLazyListState()
val layoutInfo = rememberLazyListSnapperLayoutInfo(lazyListState)

LaunchedEffect(lazyListState.isScrollInProgress) {
    if (!lazyListState.isScrollInProgress) {
        // The scroll (fling) has finished, get the current item and
        // do something with it!
        val snappedItem = layoutInfo.currentItem
        // TODO: do something with snappedItem
    }
}

LazyColumn(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(layoutInfo),
) {
    // content
}
```


## Controlling the maximum fling distance

The `maximumFlingDistance` parameter allows customization of the maximum distance that a user can
fling (and snap to).

Apps can provide a block which will be called once a fling has been started. The block is given 
the [SnapperLayoutInfo][snapperlayoutinfo], if it needs to use the layout to determine 
the distance.

The following example sets the maximum fling distance to 3x the container width.

``` kotlin
LazyRow(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(
        lazyListState = lazyListState,
        maximumFlingDistance = { layoutInfo ->
            val scrollLength = layoutInfo.endScrollOffset - layoutInfo.startScrollOffset
            // Allow the user to scroll 3x the LazyRow 'width'
            scrollLength * 3f
        }
    ),
) {
    // content
}
```

## Animation specs

SnapperFlingBehavior allows setting of two different animation specs: `decayAnimationSpec` and `springAnimationSpec`.

- `decayAnimationSpec` is the main spec used for flinging, and is used when the fling has enough velocity to scroll past
the current item.
- `springAnimationSpec` is used when there is not enough velocity to fling using `decayAnimationSpec`, and instead 'snaps'
to the current item.

Both of the specs can be customized to apps wishes.

  [snapperlayoutinfo]: ../api/lib/dev.chrisbanes.snapper/-snapper-layout-info/
  [rememberlazylistsnapperlayoutinfo]: ../api/lib/dev.chrisbanes.snapper/remember-lazy-list-snapper-layout-info.html