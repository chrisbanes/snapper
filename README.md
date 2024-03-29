[![Maven Central](https://img.shields.io/maven-central/v/dev.chrisbanes.snapper/snapper)](https://search.maven.org/search?q=g:dev.chrisbanes.snapper) ![Build status](https://github.com/chrisbanes/snapper/actions/workflows/build.yml/badge.svg)

## Deprecated

Snapper is now deprecated, due to it's functionality being replaced by [`SnapFlingBehavior`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/gestures/snapping/SnapFlingBehavior) which is available in Jetpack Compose 1.3.0.

The `SnapFlingBehavior` API is very similar to Snapper, so migration should be very easy. I haven't provided an automatic migration path, as I feel that it's important to learn the new API by performing the migration yourself.

## Library

![](docs/assets/header.png)

Snapper is a library which brings snapping to the Compose scrolling layouts (currently only LazyColumn and LazyRow).

Check out the website for more information: https://chrisbanes.github.io/snapper

## License

```
Copyright 2021 Chris Banes
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```