package com.valoser.futacha.shared.media.video.model

// The primitive-array JVM extension is unavailable in Kotlin/Native.
internal fun LongArray.binarySearch(value: Long): Int {
    var low = 0
    var high = lastIndex
    while (low <= high) {
        val middle = low + (high - low) / 2
        when {
            this[middle] < value -> low = middle + 1
            this[middle] > value -> high = middle - 1
            else -> return middle
        }
    }
    return -low - 1
}
