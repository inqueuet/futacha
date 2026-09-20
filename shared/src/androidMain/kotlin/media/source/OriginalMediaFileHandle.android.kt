package com.valoser.futacha.shared.media.source

import okio.FileHandle
import okio.FileSystem
import okio.Path

internal actual fun FileSystem.openOriginalMediaReadHandle(path: Path): FileHandle = openReadOnly(path)
