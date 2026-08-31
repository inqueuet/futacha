package com.valoser.futacha.shared.ai

fun FutachaAiCommand.boardSelectorParameter(): String? {
    return parameter("board", "boardId", "board_id", "boardName", "board_name", "name", "query", "q", "url", "boardUrl", "board_url", "link")
}

fun FutachaAiCommand.searchQueryParameter(): String? {
    return parameter("query", "q", "word", "keyword", "search", "text", "value")
}

fun FutachaAiCommand.titleParameter(): String? {
    return parameter("title", "subject", "topic", "query", "q", "text")
}

fun FutachaAiCommand.wordParameter(): String? {
    return parameter("word", "query", "q", "keyword", "text", "value")
}

fun FutachaAiCommand.boardUrlParameter(): String? {
    return parameter("url", "boardUrl", "board_url", "link")
}

fun FutachaAiCommand.threadUrlParameter(): String? {
    return parameter("url", "threadUrl", "thread_url", "link")
}

fun FutachaAiCommand.threadIdParameter(): String? {
    return parameter("thread", "threadId", "thread_id", "threadNo", "thread_no", "postId", "post_id", "no", "res")
}

fun FutachaAiCommand.catalogModeParameter(): String? {
    return parameter("mode", "catalogMode", "catalog_mode", "sort", "order", "value")
}

fun FutachaAiCommand.draftNameParameter(): String? {
    return parameter("name", "author")
}

fun FutachaAiCommand.draftEmailParameter(): String? {
    return parameter("email", "mail", "mailAddress", "mail_address")
}

fun FutachaAiCommand.draftSubjectParameter(): String? {
    return parameter("subject", "title", "topic")
}

fun FutachaAiCommand.draftCommentParameter(): String? {
    return parameter("comment", "body", "text", "message", "content")
}

fun FutachaAiCommand.draftPasswordParameter(): String? {
    return parameter("password", "deleteKey", "delete_key", "deletePassword", "delete_password")
}
