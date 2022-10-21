/*
 * Copyright 2022 Chris Banes
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

import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

internal actual object SnapperLog {
    actual inline fun d(tag: String, message: () -> String) {
        if (DebugLog) {
            NSLog("$tag: ${message()}")
        }
    }
}

internal actual fun Double.formatToString(): String = NSString.stringWithFormat("%.3d", this)
