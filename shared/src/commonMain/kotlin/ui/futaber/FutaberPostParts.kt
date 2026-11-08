package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.board.PosterIdLabel
import kotlin.math.roundToInt

// L-B3「カタログとスレッドの行」(docs/futaber-evolution-look-2026-10-31.md §1.3 V08・V09・V10・V11・V25, §1.2 F22): the pieces
// of a post the original draws, kept out of FutaberPostRow / FutaberThreadScreen so those stay small.

/**
 * The space above and below a post's text, and between its header and its body: futaber's post "1" (a header and four
 * body lines) is 121.5pt high, the header's middle 18pt under the post's top and the body's first line 21.5pt under it.
 */
internal const val FUTABER_POST_PADDING_VERTICAL_DP = 10 // the default of 「レス間の余白」
internal const val FUTABER_POST_HEADER_GAP_DP = 3

/** What a piece of a post's header is; its colour follows from it ([futaberHeaderPartColor]). */
internal enum class FutaberHeaderPartKind { Ordinal, Subject, Name, Mail, Date, Replies, Ip, IpCount, Id, No }

/**
 * One piece of a post's header. [posterKey] is the IP or ID that picks the poster's colour (null: the plain grey);
 * [bold] marks an ID that posts many times.
 */
@Immutable
internal data class FutaberHeaderPart(
    val kind: FutaberHeaderPartKind,
    val text: String,
    val posterKey: String? = null,
    val bold: Boolean = false
)

/**
 * V08・V25: the header of a post as futaber writes it, one sentence in this order: the number, the subject and the name
 * (when they differ from the board's defaults, or always), "[mail]", the date, the replies, the IP and how many times it
 * posted, the ID, "No.". The replies are left out when there are none, the IP on a board that shows none.
 */
internal fun futaberPostHeaderParts(
    ordinal: Int,
    post: Post,
    idLabel: PosterIdLabel?,
    ipCount: String?,
    replyCount: Int,
    alwaysShowTitleAndName: Boolean = false
): List<FutaberHeaderPart> {
    val (date, ip) = futaberTimestampAndIp(post.timestamp)
    val (subject, name) = futaberPostSubjectAndName(post.subject, post.author, alwaysShowTitleAndName)
    return buildList {
        add(FutaberHeaderPart(FutaberHeaderPartKind.Ordinal, ordinal.toString()))
        subject?.let { add(FutaberHeaderPart(FutaberHeaderPartKind.Subject, it)) }
        name?.let { add(FutaberHeaderPart(FutaberHeaderPartKind.Name, it)) }
        post.mail?.trim()?.takeIf { it.isNotEmpty() }?.let { add(FutaberHeaderPart(FutaberHeaderPartKind.Mail, "[$it]")) }
        if (date.isNotEmpty()) add(FutaberHeaderPart(FutaberHeaderPartKind.Date, date))
        if (replyCount > 0) add(FutaberHeaderPart(FutaberHeaderPartKind.Replies, replyCount.toString()))
        // An IP seen once is grey; one that posts again takes its poster's colour, and so does its "n/N".
        ip?.let { add(FutaberHeaderPart(FutaberHeaderPartKind.Ip, it, posterKey = it.takeIf { ipCount != null })) }
        if (ip != null && ipCount != null) add(FutaberHeaderPart(FutaberHeaderPartKind.IpCount, ipCount, posterKey = ip))
        idLabel?.let {
            add(FutaberHeaderPart(FutaberHeaderPartKind.Id, it.text, posterKey = post.posterId?.takeIf { id -> id.isNotBlank() }, bold = it.highlight))
        }
        add(FutaberHeaderPart(FutaberHeaderPartKind.No, "No.${post.id}"))
    }
}

/** The colour of a header piece: the number blue, the subject red, the name green, the mail blue, the rest grey or the poster's. */
internal fun futaberHeaderPartColor(part: FutaberHeaderPart, colors: FutaberColors): Color = when (part.kind) {
    FutaberHeaderPartKind.Ordinal -> colors.ordinal
    FutaberHeaderPartKind.Subject -> colors.accent
    FutaberHeaderPartKind.Name -> colors.name
    FutaberHeaderPartKind.Mail -> colors.mail
    FutaberHeaderPartKind.Replies -> colors.link
    FutaberHeaderPartKind.Date, FutaberHeaderPartKind.No -> colors.meta
    FutaberHeaderPartKind.Ip, FutaberHeaderPartKind.IpCount, FutaberHeaderPartKind.Id ->
        part.posterKey?.let { futaberPosterColor(it, colors.isDark) } ?: colors.meta
}

/** The id of the replies mark drawn inside the header ([InlineTextContent]). */
internal const val FUTABER_REPLY_INLINE_ID = "futaber-replies"

/** The annotation that marks the ID in the header text, for its tap. */
internal const val FUTABER_HEADER_ID_TAG = "futaber-header-id"

/** The annotation that marks the IP in the header text, for its long press (G28). */
internal const val FUTABER_HEADER_IP_TAG = "futaber-header-ip"

private const val FUTABER_BREAK_ANYWHERE = '​'

/**
 * A line may break between any two letters, as futaber's header does ("No.3251" / "25"): a zero-width space between
 * the letters of a word. Japanese already breaks between letters; spaces stay as they are.
 */
internal fun futaberBreakAnywhere(text: String): String {
    if (text.length < 2) return text
    val out = StringBuilder(text.length * 2)
    text.forEachIndexed { index, c ->
        if (index > 0 && !c.isWhitespace() && !text[index - 1].isWhitespace()) out.append(FUTABER_BREAK_ANYWHERE)
        out.append(c)
    }
    return out.toString()
}

/**
 * The header as one text (V08): the pieces separated by a space, each in its colour, the replies as the inline mark
 * ([FUTABER_REPLY_INLINE_ID], a bubble and the number), the ID annotated with [FUTABER_HEADER_ID_TAG] so a tap on it
 * can be found and the IP with [FUTABER_HEADER_IP_TAG] for its long press (G28). Lines may break anywhere
 * ([futaberBreakAnywhere]).
 */
internal fun futaberPostHeaderText(parts: List<FutaberHeaderPart>, colors: FutaberColors): AnnotatedString = buildAnnotatedString {
    parts.forEachIndexed { index, part ->
        if (index > 0) append(' ')
        if (part.kind == FutaberHeaderPartKind.Replies) {
            appendInlineContent(FUTABER_REPLY_INLINE_ID, " ")
            return@forEachIndexed
        }
        val start = length
        withStyle(SpanStyle(color = futaberHeaderPartColor(part, colors), fontWeight = if (part.bold) FontWeight.Bold else null)) {
            append(futaberBreakAnywhere(part.text))
        }
        if (part.kind == FutaberHeaderPartKind.Id) addStringAnnotation(FUTABER_HEADER_ID_TAG, part.text, start, length)
        if (part.kind == FutaberHeaderPartKind.Ip) addStringAnnotation(FUTABER_HEADER_IP_TAG, part.text, start, length)
    }
}

/** What a screen reader reads for the header: the pieces as written, without the replies (their mark is read on its own). */
internal fun futaberPostHeaderDescription(parts: List<FutaberHeaderPart>): String =
    parts.filter { it.kind != FutaberHeaderPartKind.Replies }.joinToString(" ") { it.text }

/** V09: the そうだね count's colour: red from five, the yellow (made readable) below. 3 and 4 are not captured yet (L-B0). */
internal fun futaberSaidaneColor(count: Int, colors: FutaberColors): Color =
    if (count >= FUTABER_SAIDANE_HIGH_FROM) colors.saidaneHigh else colors.saidaneLow

/** From this many そうだね the count is red (futaber: ×5 and more red, ×1 and ×2 yellow; ×3 and ×4 unconfirmed). */
internal const val FUTABER_SAIDANE_HIGH_FROM = 5

/**
 * The header of a post (V08・V09・V10・V25): one flowing text; the reply mark inside it opens the replies' bubble, the ID
 * (with the extension "IDをタップ") lists that poster's posts, and the そうだね count sits at the right end over it, as in
 * the original. The quick NG / そうだね buttons (extensions) keep their place after it.
 */
@Composable
internal fun FutaberPostHeader(
    ordinal: Int,
    post: Post,
    idLabel: PosterIdLabel?,
    ipCount: String?,
    replyCount: Int,
    onRepliesTap: (FutaberAnchor) -> Unit,
    onIdTap: (() -> Unit)?,
    onSaidaneTap: (() -> Unit)?,
    onNgTap: (() -> Unit)?,
    /**
     * The post's own long press (its sheet). The reply mark and the ID take a long press near them too (their touch area
     * is widened to the minimum size), so they hand it to the post instead of swallowing it.
     */
    onLongPress: (() -> Unit)? = null,
    /** G28: a long press on the IP opens its sheet (「IP:…」 / 「IPをNG」) with the IP as written; null: the IP is plain text. */
    onIpLongPress: ((String) -> Unit)? = null,
    /** The post's tap (the tap scroll), in root coordinates: a tap on the IP is handed on to it. */
    onTap: ((Offset) -> Unit)? = null
) {
    val colors = LocalFutaberColors.current
    val display = LocalFutaberDisplaySettings.current
    val parts = remember(ordinal, post, idLabel, ipCount, replyCount, display.alwaysShowTitleAndName) {
        futaberPostHeaderParts(ordinal, post, idLabel, ipCount, replyCount, display.alwaysShowTitleAndName)
    }
    val text = remember(parts, colors) { futaberPostHeaderText(parts, colors) }
    val description = remember(parts) { futaberPostHeaderDescription(parts) }
    val fontSize = display.sp(12f)
    val style = LocalTextStyle.current.merge(
        TextStyle(fontSize = fontSize, lineHeight = FUTABER_LABEL_LINE_HEIGHT.em, lineBreak = LineBreak.Simple, color = colors.meta)
    )
    val inline = futaberReplyInlineContent(replyCount, style)
    val saidane = futaberSaidaneCount(post.saidaneLabel)
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) {
            // The text's layout places the targets over the reply mark, the ID and the IP (only kept when there is one).
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            val ipLongPress = onIpLongPress.takeIf { parts.any { it.kind == FutaberHeaderPartKind.Ip } }
            val needsLayout = replyCount > 0 || onIdTap != null || ipLongPress != null
            // Where the header is, for a tap on the IP handed on to the post (read at the tap).
            val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
            Text(
                text = text,
                style = style,
                inlineContent = inline,
                maxLines = if (display.headerOneLine) 1 else Int.MAX_VALUE,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                onTextLayout = if (needsLayout) ({ layout = it }) else ({}),
                modifier = Modifier.fillMaxWidth()
                    .then(if (ipLongPress != null && onTap != null) Modifier.onPlaced { placed[0] = it } else Modifier)
                    .semantics { contentDescription = description }.testTag("futaber-post-header")
            )
            if (needsLayout) layout?.let { result ->
                if (replyCount > 0) result.placeholderRects.firstOrNull()?.let { mark ->
                    FutaberReplyTarget(mark, replyCount, onRepliesTap, onLongPress)
                }
                if (onIdTap != null) text.getStringAnnotations(FUTABER_HEADER_ID_TAG, 0, text.length).firstOrNull()?.let { id ->
                    FutaberHeaderRangeTargets(
                        result, id.start, id.end, id.item, "futaber-id-tap", "同じIDのレスを表示", onIdTap,
                        onLongClick = onLongPress, longClickLabel = "レスの操作を開く"
                    )
                }
                // G28: the IP's long press opens its own sheet; a tap goes on to the post (the tap scroll) as elsewhere.
                if (ipLongPress != null) text.getStringAnnotations(FUTABER_HEADER_IP_TAG, 0, text.length).firstOrNull()?.let { ip ->
                    val ipText = parts.first { it.kind == FutaberHeaderPartKind.Ip }.text
                    FutaberHeaderRangeTargets(
                        result, ip.start, ip.end, ipText, "futaber-ip-press", clickLabel = null, onClick = null,
                        onLongClick = { ipLongPress(ipText) }, longClickLabel = "IPの操作を開く",
                        onPassTap = onTap?.let { tap ->
                            { local -> placed[0]?.takeIf { it.isAttached }?.let { tap(it.localToRoot(local)) } }
                        }
                    )
                }
            }
            // The count at the right end of the header, over it, as futaber draws it.
            if (onSaidaneTap == null && saidane != null) {
                Text(
                    "×$saidane", style = style, color = futaberSaidaneColor(saidane, colors),
                    modifier = Modifier.align(Alignment.TopEnd).testTag("futaber-saidane-count")
                )
            }
        }
        if (onNgTap != null) TextButton(onClick = onNgTap) { Text("NG", color = colors.iosBlue) }
        if (onSaidaneTap != null) TextButton(onClick = onSaidaneTap) {
            Text("そうだね ${saidane ?: 0}", color = colors.saidaneHigh)
        }
    }
}

/**
 * V10: the replies as futaber draws them, a line bubble and the number in the link colour with no frame, inside the
 * header text. Only the picture is drawn here; the tap and what a screen reader finds are on [FutaberReplyTarget], laid
 * over it as a sibling of the text (a text is one element for iOS's accessibility, which would hide a child of it).
 */
@Composable
private fun futaberReplyInlineContent(replyCount: Int, style: TextStyle): Map<String, InlineTextContent> {
    if (replyCount <= 0) return emptyMap()
    val colors = LocalFutaberColors.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val label = " $replyCount"
    val placeholder = remember(label, style, density) {
        val textWidth = measurer.measure(label, style, maxLines = 1, softWrap = false).size.width
        val iconWidth = with(density) { (style.fontSize * FUTABER_REPLY_ICON_EM).toPx() }
        // A pixel more, so rounding never cuts the last digit.
        val width = with(density) { (textWidth + iconWidth + 1f).toSp() }
        Placeholder(width, FUTABER_LABEL_LINE_HEIGHT.em, PlaceholderVerticalAlign.TextCenter)
    }
    return remember(placeholder, label, colors, style) {
        mapOf(FUTABER_REPLY_INLINE_ID to InlineTextContent(placeholder) { FutaberReplyMark(label, style, colors.link) })
    }
}

/** The bubble is a little larger than the letters (futaber's is about the cap height of the header). */
private const val FUTABER_REPLY_ICON_EM = 1.15f

@Composable
private fun FutaberReplyMark(label: String, style: TextStyle, color: Color) {
    val iconSize = with(LocalDensity.current) { (style.fontSize * FUTABER_REPLY_ICON_EM).toDp() }
    Row(Modifier.fillMaxSize().clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        FutaberIcon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        Text(label, style = style, color = color, maxLines = 1, softWrap = false)
    }
}

/**
 * The reply mark's tap target (tag `futaber-reply-count`, read "返信N"), over the mark's place in the header text. It is
 * small, so Compose widens where a touch reaches it to the minimum touch size (48dp); a long press there is the post's
 * own (its sheet), as anywhere else on the post.
 */
@Composable
private fun FutaberReplyTarget(
    mark: androidx.compose.ui.geometry.Rect,
    replyCount: Int,
    onTap: (FutaberAnchor) -> Unit,
    onLongPress: (() -> Unit)?
) {
    val density = LocalDensity.current
    // Where it is is read at the tap, not written to a state on every frame of a scroll (re-audit L9).
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        Modifier.offset { IntOffset(mark.left.roundToInt(), mark.top.roundToInt()) }
            .size(with(density) { mark.width.toDp() }, with(density) { mark.height.toDp() })
            .onPlaced { placed[0] = it }
            .combinedClickable(
                onClickLabel = "このレスへの返信を表示", role = Role.Button,
                onLongClickLabel = if (onLongPress != null) "レスの操作を開く" else null,
                onLongClick = onLongPress
            ) {
                placed[0]?.takeIf { it.isAttached }?.boundsInRoot()?.let {
                    onTap(FutaberAnchor(it.left + it.width / 2f, it.top, it.bottom))
                }
            }
            .semantics { contentDescription = "返信$replyCount" }
            .testTag("futaber-reply-count")
    )
}

/**
 * Tap targets laid over a range of a text (one per line the range covers), for a piece of a header that does something
 * when tapped or long-pressed (the ID's tap, the IP's long press of G28). The first carries [tag] and what a screen
 * reader reads. Without [onClick] a tap is handed to [onPassTap] at its place in the text (the post's own tap).
 */
@Composable
internal fun FutaberHeaderRangeTargets(
    layout: TextLayoutResult,
    start: Int,
    end: Int,
    description: String,
    tag: String,
    clickLabel: String?,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onPassTap: ((Offset) -> Unit)? = null
) {
    if (end <= start || end > layout.layoutInput.text.length) return
    val density = LocalDensity.current
    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(end - 1)
    for (line in firstLine..lastLine) {
        val from = maxOf(start, layout.getLineStart(line))
        val to = minOf(end, layout.getLineEnd(line, visibleEnd = true))
        if (to <= from) continue
        val left = layout.getBoundingBox(from).left
        val right = layout.getBoundingBox(to - 1).right
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)
        val first = line == firstLine
        Box(
            Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { (right - left).coerceAtLeast(1f).toDp() }, with(density) { (bottom - top).toDp() })
                .pointerInput(onClick, onLongClick, onPassTap) {
                    detectTapGestures(
                        onTap = { position ->
                            if (onClick != null) onClick() else onPassTap?.invoke(Offset(left + position.x, top + position.y))
                        },
                        onLongPress = onLongClick?.let { long -> { long() } }
                    )
                }
                .then(
                    if (first) Modifier.semantics {
                        contentDescription = description
                        if (onClick != null) {
                            role = Role.Button
                            onClick(label = clickLabel) { onClick(); true }
                        }
                        onLongClick?.let { long -> onLongClick(label = longClickLabel) { long(); true } }
                    }.testTag(tag) else Modifier.clearAndSetSemantics { }
                )
        )
    }
}

// ---- V11: the picture of a post ----

/** futaber shows a post's picture at 0.7 of the thumbnail's own size (250×188 → 175×132). Provisional until L-B0. */
internal const val FUTABER_ATTACHMENT_RATIO = 0.7f

/** The thumbnail size assumed when the page gives none (futaba's thumbnails are at most 250 on their long side). */
internal const val FUTABER_ATTACHMENT_FALLBACK_PX = 250

/** A post picture's size on screen, in dp. */
@Immutable
internal data class FutaberAttachmentSize(val width: Float, val height: Float)

/**
 * V11: the size a post's picture is shown at: the thumbnail's width and height (the page's `width` / `height`) times
 * [ratio] (futaber 0.7; "画像を小さくする" and the coming ratio setting pass their own). An unknown size is taken as
 * a square of [FUTABER_ATTACHMENT_FALLBACK_PX]; a very long thumbnail keeps at most 4:1 (as before).
 */
internal fun futaberAttachmentSize(thumbWidth: Int?, thumbHeight: Int?, ratio: Float = FUTABER_ATTACHMENT_RATIO): FutaberAttachmentSize {
    val known = thumbWidth != null && thumbHeight != null && thumbWidth > 0 && thumbHeight > 0
    val width = if (known) thumbWidth!! else FUTABER_ATTACHMENT_FALLBACK_PX
    val height = if (known) thumbHeight!! else FUTABER_ATTACHMENT_FALLBACK_PX
    val aspect = futaberThumbnailAspectRatio(width, height)
    val shownWidth = width * ratio.coerceAtLeast(0.01f)
    return FutaberAttachmentSize(shownWidth, shownWidth / aspect)
}

/**
 * A post's picture (V11): the file name over it as a link, then the picture at [futaberAttachmentSize], with no rounded
 * corners. A tap opens it in the viewer, a long press opens the picture's own menu; the rest of the post keeps its menu.
 */
@Composable
internal fun FutaberPostAttachment(
    post: Post,
    smallImages: Boolean,
    onImageTap: (() -> Unit)?,
    onImageLongPress: (() -> Unit)?,
    /** The post's own long press: the file name's widened touch area hands it on instead of swallowing it. */
    onPostLongPress: (() -> Unit)? = null
) {
    val thumbnail = post.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return
    val colors = LocalFutaberColors.current
    val display = LocalFutaberDisplaySettings.current
    // The picture's file name over it, a link that opens it, as in the original.
    futaberImageFileName(post)?.let { fileName ->
        Text(
            fileName, color = colors.link, fontSize = display.sp(14f),
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(top = 2.dp)
                .then(if (onImageTap == null) Modifier else Modifier.combinedClickable(
                    onClickLabel = "画像を開く", role = Role.Button,
                    onLongClickLabel = if (onPostLongPress != null) "レスの操作を開く" else null,
                    onLongClick = onPostLongPress, onClick = onImageTap
                ))
                .testTag("futaber-post-file")
        )
    }
    // M10: "画像を小さくする" draws it at 0.3 of the usual size, as futaber does; the usual size is the settings' (C21).
    val base = futaberSettingsImageRatio(display.smallImages, display.imageRatioPercent)
    val size = futaberAttachmentSize(post.thumbnailWidth, post.thumbnailHeight, futaberAttachmentRatio(smallImages, base))
    // F-B4/F-B5 E4: the same sharper-original upgrade the other modes draw, laid over the thumbnail only once it has loaded
    // (the thumbnail is never hidden while the original is looked for).
    val quality = futaberHighQualityAttachment(post, thumbnail, size.width, size.height)
    Box(
        modifier = Modifier.padding(top = 4.dp).width(size.width.dp).aspectRatio(size.width / size.height)
            .background(colors.fill)
            .then(
                if (onImageTap == null && onImageLongPress == null) Modifier else Modifier
                    // Both actions belong to the picture; a scrolling drag cancels detection.
                    .pointerInput(onImageTap, onImageLongPress) {
                        detectTapGestures(
                            onTap = onImageTap?.let { callback -> { callback() } },
                            onLongPress = onImageLongPress?.let { callback -> { callback() } }
                        )
                    }
                    .semantics {
                        onImageTap?.let { callback -> onClick(label = "画像を開く") { callback(); true } }
                        onImageLongPress?.let { callback -> onLongClick(label = "画像の操作を開く") { callback(); true } }
                    }
            ).testTag("futaber-post-image")
    ) {
        CatalogPreviewImage(
            thumbnailUrl = thumbnail,
            fullImageUrl = post.imageUrl,
            targetSizePx = 500,
            contentDescription = "添付画像",
            modifier = Modifier.fillMaxSize(),
            fallbackTint = colors.meta
        )
        quality?.let {
            androidx.compose.foundation.Image(
                painter = it, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    }
}

// ---- F22: the end of the thread ----

/** The key of the row after the last post in the thread's list. */
internal const val FUTABER_THREAD_END_KEY = "futaber-thread-end"

/** F22: what the end of the thread says: "…頃消えます" of the page, or null when the page has none. */
internal fun futaberThreadEndLabel(page: ThreadPage?): String? = page?.expiresAtLabel?.trim()?.takeIf { it.isNotEmpty() }

/**
 * The row after the last post: futaber's grey footer with "…頃消えます" at the right (F22) and under it the 50 high
 * 「引っ張って...」 (G21, L-B7): pulling up past it and letting go refreshes the thread ([rememberFutaberEndPull]). It is
 * always there, also on a page with no expiry (an archive), so the pull has its place.
 */
@Composable
internal fun FutaberThreadEndRow(expiry: String?) {
    val colors = LocalFutaberColors.current
    Column(Modifier.fillMaxWidth().background(colors.threadFooter).testTag("futaber-thread-end")) {
        if (expiry != null) {
            Text(
                expiry, color = colors.expiry, fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 9.dp, bottom = 9.dp)
                    .testTag("futaber-thread-expiry")
            )
        }
        Box(Modifier.fillMaxWidth().height(FUTABER_PULL_ROW_DP.dp), contentAlignment = Alignment.Center) {
            Text(
                FUTABER_PULL_LABEL, color = colors.expiry, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.testTag("futaber-thread-pull")
            )
        }
    }
}

/**
 * E4: the original of [post]'s picture once the shared plan allows it and it has loaded (null until then, and when the
 * setting is off, the picture is not a shared-host still, or it needs no sharpening).
 */
@Composable
private fun futaberHighQualityAttachment(post: Post, thumbnail: String, widthDp: Float, heightDp: Float): androidx.compose.ui.graphics.painter.Painter? {
    val mode = com.valoser.futacha.shared.ui.image.LocalHighQualityThumbnailMode.current
    if (mode == com.valoser.futacha.shared.ui.image.HighQualityThumbnailMode.OFF) return null
    val density = LocalDensity.current.density
    val platformContext = coil3.compose.LocalPlatformContext.current
    val imageLoader = com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader.current
    val plan = remember(mode, post.imageUrl, thumbnail, post.imageFileSizeBytes, post.thumbnailWidth, post.thumbnailHeight, widthDp, heightDp, density) {
        com.valoser.futacha.shared.ui.image.planHighQualityThumbnail(
            mode = mode,
            originalUrl = post.imageUrl,
            thumbnailUrl = thumbnail,
            thumbnailWidthPx = post.thumbnailWidth,
            thumbnailHeightPx = post.thumbnailHeight,
            boxWidthPx = (widthDp * density).toInt(),
            boxHeightPx = (heightDp * density).toInt(),
            originalSizeBytes = post.imageFileSizeBytes,
            isUnmeteredConnection = mode == com.valoser.futacha.shared.ui.image.HighQualityThumbnailMode.UNMETERED &&
                com.valoser.futacha.shared.ui.compat.isCompatWifiConnected(platformContext)
        )
    }
    // The original is asked for after the picture has had a moment to show.
    var settled by remember(plan) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(plan) { if (plan != null) { kotlinx.coroutines.delay(250); settled = true } }
    return com.valoser.futacha.shared.ui.image.rememberThreadHighQualityThumbnail(plan, settled, imageLoader).painter
}
