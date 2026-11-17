package com.valoser.futacha.shared.compat.aimoge

// AM-B0 golden captured from the code at 24ebd515; regenerate only on purpose (see the test class).
internal val FUTABA_ARCHIVE_FALLBACK_GOLDEN: List<String> = listOf(
    "## performThreadArchiveFallback -> ok:NotFound" +
        "\n" + "GET https://may.inqueuet.com/b/res/1439323740.htm [User-Agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36, Accept=text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8, Accept-Language=ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7, Cache-Control=no-cache, Pragma=no-cache, Referer=https://may.inqueuet.com/b/res/] type=null body=" +
        "\n" + "GET https://dev2.ftbucket.info/scdev2/scrapshot.php?rooturl=https%3A%2F%2Fmay.2chan.net%2Fb%2Fres%2F1439323740.htm [Accept=*/*] type=null body=" +
        "\n" + "GET https://futabaforest.net/b/res/1439323740.htm [Accept=*/*] type=null body=" +
        "\n" + "GET https://kako.futakuro.com/futa/may_b/1439323740/ [Accept=*/*] type=null body=",
    "## futaberArchivedThread -> ok:null" +
        "\n" + "GET https://may.inqueuet.com/b/res/1439323740.htm [User-Agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36, Accept=text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8, Accept-Language=ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7, Cache-Control=no-cache, Pragma=no-cache, Referer=https://may.inqueuet.com/b/res/] type=null body=" +
        "\n" + "GET https://dev2.ftbucket.info/scdev2/scrapshot.php?rooturl=https%3A%2F%2Fmay.2chan.net%2Fb%2Fres%2F1439323740.htm [Accept=*/*] type=null body=" +
        "\n" + "GET https://futabaforest.net/b/res/1439323740.htm [Accept=*/*] type=null body=" +
        "\n" + "GET https://kako.futakuro.com/futa/may_b/1439323740/ [Accept=*/*] type=null body=",
    "## tryRefreshHistoryEntryFromArchive -> ok:NotFound" +
        "\n" + "GET https://may.inqueuet.com/b/res/1439323740.htm [User-Agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36, Accept=text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8, Accept-Language=ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7, Cache-Control=no-cache, Pragma=no-cache, Referer=https://may.inqueuet.com/b/res/] type=null body=",
    "## loadCompatThreadWithFallback -> ok:CompatThreadFetchException" +
        "\n" + "GET https://may.2chan.net/b/res/1439323740.htm [User-Agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36, Accept=text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8, Accept-Language=ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7, Cache-Control=no-cache, Pragma=no-cache, Referer=https://may.2chan.net/b/res/] type=null body=" +
        "\n" + "GET https://may.inqueuet.com/b/res/1439323740.htm [Accept=*/*] type=null body=" +
        "\n" + "GET https://dev2.ftbucket.info/scdev2/scrapshot.php?rooturl=https%3A%2F%2Fmay.2chan.net%2Fb%2Fres%2F1439323740.htm [Accept=*/*] type=null body=" +
        "\n" + "GET https://futabaforest.net/b/res/1439323740.htm [Accept=*/*] type=null body=" +
        "\n" + "GET https://kako.futakuro.com/futa/may_b/1439323740/ [Accept=*/*] type=null body=",
)
