package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock

/*
 * L-B12 (C14〜C17): futaber's wallpapers. A picture the person picks is copied into the app's own folder (not in the
 * settings backup: it lives under `private/`), and drawn faintly over the catalog (0.10), the thread (0.10) and the writing
 * screen (0.20); the board list (futaber's 「メニュー」) takes it behind its rows at 0.65, or keeps the dark polygons
 * (デフォルト) or the blue gradient (デフォルト(旧)).
 */

internal const val FUTABER_WALLPAPER_DIRECTORY = "private/futaber_wallpaper"
internal const val FUTABER_WALLPAPER_MAX_BYTES = 8L * 1024 * 1024
private val FUTABER_WALLPAPER_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/** The screens that can have a wallpaper. [defaultOpacityPercent] is futaber's 透過度 for it (0.10 / 0.20 / 0.65). */
internal enum class FutaberWallpaperSlot(val id: String, val label: String, val defaultOpacityPercent: Int) {
    Catalog("catalog", "カタログの壁紙", 10),
    Thread("thread", "スレッドの壁紙", 10),
    Post("post", "書き込み画面の壁紙", 20),
    Menu("menu", "メニューの壁紙", 65);

    val fileKey: String get() = "compat.futaber.wallpaper.$id"
    val opacityKey: String get() = "compat.futaber.wallpaper.$id.opacity"
}

/** futaber's 「メニュー」 background: the polygons, the old blue gradient, or the picked picture. */
internal enum class FutaberMenuBackground(val persisted: String, val label: String) {
    Default("default", "デフォルト"), Old("old", "デフォルト(旧)"), Image("image", "画像");

    companion object {
        fun from(stored: String?): FutaberMenuBackground = entries.firstOrNull { it.persisted == stored } ?: Default
    }
}

internal const val FUTABER_MENU_BACKGROUND_KEY = "compat.futaber.wallpaper.menuKind"
internal const val FUTABER_WALLPAPER_STEP_PERCENT = 5

/** What the settings hold about the wallpapers. */
internal data class FutaberWallpapers(
    val files: Map<FutaberWallpaperSlot, String> = emptyMap(),
    val opacityPercent: Map<FutaberWallpaperSlot, Int> = emptyMap(),
    val menuBackground: FutaberMenuBackground = FutaberMenuBackground.Default
) {
    fun file(slot: FutaberWallpaperSlot): String? = files[slot]
    fun opacity(slot: FutaberWallpaperSlot): Int = opacityPercent[slot] ?: slot.defaultOpacityPercent

    companion object {
        fun from(preferences: Map<String, String>): FutaberWallpapers = FutaberWallpapers(
            files = FutaberWallpaperSlot.entries.mapNotNull { slot ->
                preferences[slot.fileKey]?.takeIf { futaberWallpaperFileNameIsValid(it) }?.let { slot to it }
            }.toMap(),
            opacityPercent = FutaberWallpaperSlot.entries.mapNotNull { slot ->
                preferences[slot.opacityKey]?.toIntOrNull()?.takeIf { it in 0..100 }?.let { slot to it }
            }.toMap(),
            menuBackground = FutaberMenuBackground.from(preferences[FUTABER_MENU_BACKGROUND_KEY])
        )
    }
}

/** A stored name is one we wrote: `<slot>-<time>.<ext>` and nothing that leaves the folder. */
internal fun futaberWallpaperFileNameIsValid(name: String): Boolean =
    name.isNotEmpty() && name.length <= 80 && '/' !in name && '\\' !in name && ".." !in name &&
        name.substringAfterLast('.', "").lowercase() in FUTABER_WALLPAPER_EXTENSIONS

/** The opacity after a − (−1) or ＋ (+1) step, kept inside 0〜100. */
internal fun futaberWallpaperOpacityStep(percent: Int, direction: Int): Int =
    (percent + direction * FUTABER_WALLPAPER_STEP_PERCENT).coerceIn(0, 100)

/** futaber's 透過度 as it is shown ("0.10"). */
internal fun futaberWallpaperOpacityShown(percent: Int): String = "${percent / 100}.${(percent % 100).toString().padStart(2, '0')}"

/**
 * Copies [bytes] (named [pickedName]) to the wallpaper folder as the picture of [slot] and stores its name; the file it
 * replaces is removed after the new one is stored. A name or size that is not a picture is refused.
 */
internal suspend fun futaberSaveWallpaper(
    store: CompatibilityStore,
    fileSystem: FileSystem,
    slot: FutaberWallpaperSlot,
    pickedName: String,
    bytes: ByteArray,
    previous: String?
): Result<String> {
    val extension = pickedName.substringAfterLast('.', "").lowercase()
    if (extension !in FUTABER_WALLPAPER_EXTENSIONS) return Result.failure(IllegalArgumentException("壁紙にできる画像はJPEG・PNG・WebPです"))
    if (bytes.isEmpty() || bytes.size > FUTABER_WALLPAPER_MAX_BYTES) {
        return Result.failure(IllegalArgumentException("壁紙の画像が大きすぎます（8MBまで）"))
    }
    return try {
        val name = "${slot.id}-${Clock.System.now().toEpochMilliseconds()}.$extension"
        fileSystem.createDirectory(FUTABER_WALLPAPER_DIRECTORY).getOrThrow()
        fileSystem.writeBytes("$FUTABER_WALLPAPER_DIRECTORY/$name", bytes).getOrThrow()
        store.savePreference(slot.fileKey, name)
        if (previous != null && previous != name && futaberWallpaperFileNameIsValid(previous)) {
            fileSystem.delete("$FUTABER_WALLPAPER_DIRECTORY/$previous")
        }
        Result.success(name)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
}

/** Takes [slot]'s picture away (「選択なし」): the name is cleared first, then the file removed. */
internal suspend fun futaberClearWallpaper(
    store: CompatibilityStore,
    fileSystem: FileSystem?,
    slot: FutaberWallpaperSlot,
    previous: String?
): Result<Unit> = try {
    store.savePreference(slot.fileKey, "")
    if (previous != null && futaberWallpaperFileNameIsValid(previous)) fileSystem?.delete("$FUTABER_WALLPAPER_DIRECTORY/$previous")
    Result.success(Unit)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}

/** Where the wallpaper files are read from (the app provides it from its file system; null draws no wallpaper). */
internal val LocalFutaberWallpaperFiles = compositionLocalOf<FileSystem?> { null }

/** The picture of [slot] at its opacity, drawn over (or, for the menu, behind) the screen; nothing while none is chosen. */
@Composable
internal fun FutaberWallpaperImage(slot: FutaberWallpaperSlot, modifier: Modifier = Modifier) {
    val wallpapers = LocalFutaberDisplaySettings.current.wallpapers
    val files = LocalFutaberWallpaperFiles.current ?: return
    val name = wallpapers.file(slot) ?: return
    // Fully transparent draws nothing, so the picture is not read or decoded (audit A7).
    if (wallpapers.opacity(slot) <= 0) return
    // The bytes are kept per file (one per screen), so a screen that comes back does not read up to 8MB again, and the
    // same array lets the image loader's memory cache find the decoded picture (audit A7).
    var bytes by remember(name) { mutableStateOf(futaberWallpaperBytesCache.get(name)) }
    LaunchedEffect(name) {
        if (bytes == null) bytes = files.readBytes("$FUTABER_WALLPAPER_DIRECTORY/$name").getOrNull()?.also { futaberWallpaperBytesCache.put(name, it) }
    }
    val loaded = bytes ?: return
    AsyncImage(
        model = loaded, imageLoader = LocalFutachaImageLoader.current, contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize().alpha(wallpapers.opacity(slot) / 100f).testTag("futaber-wallpaper-${slot.id}")
    )
}

/** Over the screen that [slot] names; it takes no touch (an image draws and does not click). */
@Composable
internal fun FutaberWallpaperOverlay(slot: FutaberWallpaperSlot) {
    Box(Modifier.fillMaxSize()) { FutaberWallpaperImage(slot) }
}

/**
 * The wallpaper sections of the テーマ page (C14〜C17): for each screen 「選択なし」／「画像を選択」 and the 透過度 −＋; the
 * menu's has the two defaults as well. A picture needs the app's file system; without it the rows stay but say so.
 */
@Composable
internal fun FutaberWallpaperSections(
    settings: FutaberDisplaySettings,
    services: FutaberMediaServices?,
    scope: kotlinx.coroutines.CoroutineScope,
    onSettingChange: (String, String) -> Unit,
    onNotice: (String) -> Unit
) {
    val wallpapers = settings.wallpapers
    var picking by remember { mutableStateOf<FutaberWallpaperSlot?>(null) }
    val pick = rememberFutaberPhotoPicker(
        maxBytes = FUTABER_WALLPAPER_MAX_BYTES,
        onPicked = { image ->
            val slot = picking ?: return@rememberFutaberPhotoPicker
            val target = services
            val files = target?.fileSystem
            if (target == null || files == null) onNotice("壁紙を保存できません")
            else scope.launch {
                futaberSaveWallpaper(target.store, files, slot, image.fileName, image.bytes, wallpapers.file(slot))
                    .onFailure { onNotice(if (it is IllegalArgumentException) it.message.orEmpty() else "壁紙を保存できませんでした") }
            }
        },
        onFinished = { picking = null },
        onError = { onNotice(it) }
    )
    FutaberWallpaperSlot.entries.forEach { slot ->
        val chosen = wallpapers.file(slot) != null
        val menuKind = wallpapers.menuBackground
        FutaberTableSection(slot.label, tag = "futaber-wallpaper-${slot.id}-title") {
            if (slot == FutaberWallpaperSlot.Menu) {
                FutaberMenuBackground.entries.forEachIndexed { index, kind ->
                    FutaberTableCheckRow(
                        kind.label, "futaber-wallpaper-menu-${kind.persisted}", menuKind == kind,
                        last = false
                    ) { onSettingChange(FUTABER_MENU_BACKGROUND_KEY, kind.persisted) }
                }
            } else {
                FutaberTableCheckRow("選択なし", "futaber-wallpaper-${slot.id}-none", !chosen) {
                    val target = services
                    if (chosen && target != null) scope.launch {
                        futaberClearWallpaper(target.store, target.fileSystem, slot, wallpapers.file(slot))
                    }
                }
            }
            FutaberTableActionRow(
                "画像を選択", "futaber-wallpaper-${slot.id}-pick", last = false, enabled = services?.fileSystem != null
            ) {
                picking = slot
                if (slot == FutaberWallpaperSlot.Menu) onSettingChange(FUTABER_MENU_BACKGROUND_KEY, FutaberMenuBackground.Image.persisted)
                pick()
            }
            val percent = wallpapers.opacity(slot)
            FutaberTableStepperRow(
                label = "透過度 = ${futaberWallpaperOpacityShown(percent)}", rowTag = "futaber-wallpaper-${slot.id}-opacity",
                valueTag = "futaber-wallpaper-${slot.id}-opacity-value", minusTag = "futaber-wallpaper-${slot.id}-minus",
                plusTag = "futaber-wallpaper-${slot.id}-plus", last = true,
                canDecrease = percent > 0, canIncrease = percent < 100,
                onDecrease = { onSettingChange(slot.opacityKey, futaberWallpaperOpacityStep(percent, -1).toString()) },
                onIncrease = { onSettingChange(slot.opacityKey, futaberWallpaperOpacityStep(percent, 1).toString()) }
            )
        }
    }
}

/** The wallpaper files read so far, by file name; at most one per screen ([FutaberWallpaperSlot]), the oldest dropped. */
internal class FutaberWallpaperBytesCache(private val capacity: Int) {
    private val entries = LinkedHashMap<String, ByteArray>()
    fun get(name: String): ByteArray? = entries[name]
    fun put(name: String, bytes: ByteArray) {
        entries.remove(name)
        entries[name] = bytes
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
}

internal val futaberWallpaperBytesCache = FutaberWallpaperBytesCache(FutaberWallpaperSlot.entries.size)
