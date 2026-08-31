package com.valoser.futacha.shared.ui.compat

internal fun compatibilityReferenceHelpHtml(palette: CompatibilityPalette): String {
    val referenceAccent = when (palette.chrome) {
        androidx.compose.ui.graphics.Color(0xFF222222),
        androidx.compose.ui.graphics.Color.Black -> "#222222"
        androidx.compose.ui.graphics.Color(0xFF542D24) -> "#542d24"
        androidx.compose.ui.graphics.Color(0xFF03A9F4) -> "#03a9f4"
        androidx.compose.ui.graphics.Color(0xFFE91E63) -> "#e91e63"
        else -> "#009688"
    }
    return if (referenceAccent == "#009688") {
        COMPAT_REFERENCE_HELP_HTML
    } else {
        COMPAT_REFERENCE_HELP_HTML.replace("#009688", referenceAccent)
    }
}

// Exact sample/1.apk assets/help.html with its packaged drawable references inlined.
internal val COMPAT_REFERENCE_HELP_HTML: String = listOf(
    """
<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="UTF-8">
<title>ヘルプ</title>
<meta name="viewport" content="width=320px,initial-scale=1.0,minimum-scale=1.0,maximum-scale=1.0,user-scalable=no">
<style type="text/css">
<!--
body { margin: 0px; padding: 0px; background-color: #009688; }
img { border: none; }
a { text-decoration: none; }
a:link		{ color: #009688; }
a:visited	{ color: #009688; }
a:hover		{ color: #009688; text-decoration: underline; }

.fl { float: left; }
.fr { float: right; }
.cl { clear: both; }

label {
	font-size: 20px;
	color: #ffffff;
	padding: 10px;
	display: block;
	margin: 0;
	background-color: #009688;
	border-bottom: 1px solid #ffffff;
}

.menu div {
	-webkit-transition: all 0.5s;
	-moz-transition: all 0.5s;
	-ms-transition: all 0.5s;
	-o-transition: all 0.5s;
	transition: all 0.5s;
	margin: 0;
	padding: 0;
	list-style: none;
	background-color: #ffffff;
}

.menu div p {
	padding: 0px 10px 0px 10px;
	line-height: 150%;
}

input[type="checkbox"].on-off{
	display: none;
}

input[type="checkbox"].on-off + div{
	height: 0;
	overflow: hidden;
}

input[type="checkbox"].on-off:checked + div{
	height: auto;
}

/* デザイン */

.midashi { font-size: 18px; color: #009688; }
.title { margin: 10px 0px 0px 0px; color: #111111; font-size: 16px; }
.explain { margin: 0px 0px 15px 0px; color: #555555; font-size: 14px; }
.vb{ vertical-align: top; }
.toolbar_button { background-color: #009688; width: 25px; }
.on_button { background-color: #ffffff; width: 25px; }

hr { width: 100%; height: 1px; border: none; background-color: #eeeeee; }

.index {
	background-image: url( "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAARsAAAEbCAMAAADd89ATAAACc1BMVEX///////7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7///7+AgeOAAAA0XRSTlMAACF9zdEdCsDWhywTUqj0/7cMBKX3WBYphNCSftORMgI4tXFX/vrBSkD1+Ug17/tQT/bjLhzfYWzOFAm2iYyrB5epAQizjnDFF89f4CLo8i/wRiMRXW4SsH+KoQOuZP3ED8xW4uo25O45RNkNXGb8tAageZmApLvK2yThGNc/8byvlXWFcmNNuhDCPekbH90m7TTIU1l8Yp4g2B7H7Auj+HOPnEvmLdorR4tOjeUF3icVMJ1tQ5M356axv72a80VvWkwZQmt6rA7cQV6Dp7g8VF/0e3kAAAUuSURBVHja7dzpX1RVHMdxfpCKQTkmi5IVolBoiZESQuYypqaGSJBpJC5lmhaRZmq5ZItLlkqWKUlWUrSh7ZtRtu/1J8VLFL4Dgw6zPDj3ft6PkoE7cz7KnHvPPVNSEgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADAc6xTcsolAwZaQg1KHXxp2rn/dqdN+mWXDwkEAkOvSGSaYRkdT5E5NCvbpTbDR+QEOl2ZyDYjzz3JVVdfk+tIm1F5mYEuoxOXZkx+17MUXHudG20Kx3anCYy7PlFpbhgvT1M0wZHfqRuL5VXfNDExaSaVyJPk3+zM+02pvOzA5LJEpCm/RZ9jSq4789St+sKnTot/mtzp+gwzgg7N4TNv05c+K/5tZuvx56Q7dX5z+1x98fPinSbkHW3+HW6d+1lahbz6BYXxTVOpB19Y6Nh5sVnVEP2rvTOeaapz9B9ljTnXxu7KlwGkLorjpcLdmmaxOdjGlugQ7olfmyl63IxaJ9vMvFcHkRKvNEvr5KjLlpuTbax6hcZZGZ80hQvkmPd1v4851sYm6nn9/avikWb4A3LI1WvM2Tb2oFyRB9ZOij3Nujl6HfuQOdzGHq6XsTzSEPMU9aj+lq43p9uETiobgjG2eSxk6qt1vE3DRh1OaWxpsorkWI8PMsfbWPomGU9xVSxpRq2WQ21ONufb2JYnZEQVldGneXJrH1OUu21s23YZ04rsqKeoHXKY+l5nS262sRqdrJ6qjS5N7U5943raPNLGnglZv4yuzbN6jJ1Bz7TJzdCBPRdNml0FcoTd5eaZNrZnr650PR/FatY+OcDWF8xDbezFtTK2/Qf6m6ZsgPz4wUbzVJvQyeqldf28VJghP5x5yDzWxl7WyWpj/27LzLrwFOV8G0vRAb7SnzSHdTVretCDbexVvUmbFXmaKl3NyuvrX5zbbRYtkzGOj3ilK1lXsyb3eVrtdhs7sl9GebQpwhsur+naYaN5tI2tOibj3NEcUZvX5UeO7zLPtrGV4/TEP5I0i/UdfLZ5uI2t16G+EcFqlsZ80zzdxt7Ss7gTF13N0r1Zec0eb9O8W0bb8vaF0xx5R6eodPN4GxuoV1Y5ZRHvzWppNc+3sTUHI13p2qArze+aD9rYBL1b8F5ke7PyL3qHwhttbLQMumBpX9/1vt4T/cB80sZ0I+PYbeG/J+1D/dVr802b8jwZ98lTYS8VdMPgpo/MN22s6aiudFX3/oYG3Zs1/5T5qI19rLsZP+n9uO75yoxo27932tih4jB79rpXs+TBusPmszb2qc7QY0If+0w/LbLEfNfGPpfx7/tCHznwpTz01TQftmn7WgoMlk8vLv9G75+nmw/bWLZO06e7dtMM0x07JRFv2vZWG6tskQrfnv/qSPli+3fm0zZ24rh0+L7za2fkaqu+xnzbxkplX3/RD2ev0nVv1gjzcZuQO5YVrWY/btYpynzdJuTaYG5222n5495qf7exMt0qOVX3ZpUkm8/bWKuuRegU1c/Po3mxjf20MGybGqON2c9hytT9YrTpuSBxfj9+kDadVwm/9kyT+pvRJsxeiQ6/Nxltuj4xdlLTHIvmU2iebWN/tMv78BmjjfqzPrq9gH5oY391LVYEadNzGfDvzjT/RPnBRS+3sT1nJ6utTUab3raUdExRjUabcP5tD/xntAlvXgz/V7ckAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/gfjfIedwZ+87oAAAAASUVORK5CYII=" );
	background-size: contain;
	background-repeat: no-repeat;
	background-position: right;
}

-->
</style>
</head>
<body>
<div class="menu">

	<label for="board" class="index">板一覧</label>
	<input type="checkbox" id="board" class="on-off" />
	<div class="explain">

		<p class="midashi">操作</p>

		<p class="title">タッチ</p>
		<p class="explain">選択した板のカタログを表示します</p>

		<p class="title">ロングタッチ</p>
		<p class="explain">名前の変更と削除が行えます</p>

		<hr />

		<p class="midashi">上部メニュー</p>

		<p class="title">板一覧</p>
		<p class="explain">ふたばちゃんねるのアドレスを指定して一括登録が行えます<br />または二次裏の心得である○○○でも可能です</p>

		<p class="title">新規追加</p>
		<p class="explain">新しい板を追加する事ができます<br />ブラウザーからURLを受け取ることも可能です</p>

		<p class="title">並び替え</p>
		<p class="explain">つまみをドラッグして並び替えることができます</p>

		<p class="title">削除</p>
		<p class="explain">×ボタンで登録した板を削除します</p>

	</div>

	<label for="catalog" class="index">カタログ</label>
	<input type="checkbox" id="catalog" class="on-off" />
	<div class="explain">

		<p class="midashi">操作</p>

		<p class="title">タッチ</p>
		<p class="explain">スレッドを閲覧します</p>

		<p class="title">ロングタッチ → NGスレッドに登録</p>
		<p class="explain">スレッドを非表示にします</p>

		<p class="title">ロングタッチ → delを送信する</p>
		<p class="explain">スレッドを開かずにdelを送信します</p>

		<p class="title">ロングタッチ → タブに追加する</p>
		<p class="explain">スレッドを開かずにタブへ追加します</p>

		<hr />

		<p class="midashi">ツールバー</p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAgVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+xZKUEAAAAK3RSTlMAFnDxYvn/9nNQTzv3CrsCCcBmHEtCTj+9B6PmciwrUfU1yxf+5CFUDA/8P7Wq7wAAAb5JREFUeNrt0sdSw0AURFGBrbEJZkgmg8kG/v8DMdlJo2eKRXdxe6HSzOrckqqKMcYYY4wxxhhjjDHGGGOM/Wpr5v71TtfbX6de19ufnAve/cYF/Q+/bcG337Rgym9ZMOM3LJjz2xUs+M0KNhb9VgWby/xGBVvbKTkXDAY7ybkgV9XunnFBfnsUCvYd/MYF+evFtCD/vFoW5OmDYUGePdoV5PkLs4K8eNVc0DmQ8x8Oq3hB3ZfzH9W943CBpH/yY0cLRP3hAll/sEDY31RwYuMPFIj7Wwvk/S0FBv5igYW/UGDibyg4PTu/MPEvL8iXVzb+hm9g5PcoKPknBUNvv37BqMWvXtDu1y6I+JULYn7dgqg/pesbb39Kt3fe/vuHR2t/qkf48ePHjx8/fvz48ePHjx//P/CP8ePHjx//58b48ePHjx8/fvz48ePHjx9/YE/m/urZ3L9KgKR/hQBNfzzgRdMfDlD1RwNk/cEAXX8sQNgfClD2RwKk/YEAbX97gLi/NUDd3xYg728J0PeXAwz8xQAHfynAwl8I8PA3B5j4GwNc/E0BNn7GGGOMMcYYY4wxxhhjjDHGGGOM/cFeASW7fY3bi6UzAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">スレッド作成フォームを表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABGlBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9y/ChlAAAAXnRSTlMAGDMMMFR4nMzw/+TDmWxIJC11tPOlYB5LqOrejVLn0odaz645A9jAP3tjxsmiFZMP/CefBgmr7dWxXfm3cr2WZn5pilFvPEIS/vY2TiGBhLrhkbDHl9zvV5BY2yobsNs1HgAABQtJREFUeNrtnGtbGjkUgOMAChlAYUFEqLdKK+V+GRAQpYUqRWxtd9td9/L//8bKXlrJDMzkzJCDz3PeD/qJk7wMmSQnF8YIgiAIgiAIgiAIgiAIgiAIgiAIgiAIwis2tBm+51dxf2BzKxjiP9DDkej2zvOofCz+U4Jboyd3U2te+730Pl9OJqs5iPMiivLDyR5wJxxubthEivIj9dU/PuHOeXm6LFScKxfI7b7icrw+Wxgsz5ULvMlweQoLFI64agF/kcMolS2iVbhigWqlxqGEzC+bCFcssHfA3RCsz7elBlcr0DziLkkcP61/kqsVSBW4e7aM719Hi6sV2G5zLzj/b5DU7HC1At0e94YD/yycccHVCvS5ZyQeuwTfJVcrkLWr1X6wFPmHUtBuhMd7gep8c1q9wNXS30R6oFXnOgttkF76vq0JffnKBd4uG2zWrT9Tjx86/lGtWuDdooLbw9jS2c6wvRYCowWjh/2+7fzX19/HF/Bbvz9D7w0nnzbeh5AFqhnnY0tLyiVcgbTlXPdMJsR1BlEgalVgpyr5FDtoAjGLBtADJBGiPSSBsMVI4AYS6CaBIvDGot/VYKG0AwSBsbkj0sEpw+4H9QLmIVAoBp5OTNQ/gZipC57cQmNNewht4KWppBE0VADjLeQ3FTQEZyJrGP1AUiyn2ARGGqD0xCnxW0vcedmbr17go1hMHxgojjSYE0dgJzlYnDzScPpMLOUaFucT1oRmS1ylgIUZYs3IDHEiBRsCVdDmxHtCGfegKBG8Sb04DDoDxMglEdMqn+eLeAXJxrcQ80J3QhFxQP07mImtrlCE/Jr7XPrZhi8rbwIF+fpfSuSrf/ZeoODyGRthmYT7L94LJFz2wl+lVgy+rboNTwzZAFIPgP/K1o54RIYRIwiCIAiCIIjvjKQGk/H1EyhJDefDbvNFPc8FvkgJfJWOL2yePfdcQGp/ZVh6vmYIC35pVIFL6fqzayFEHlPgQr7+ph1g24gCHci6mTDlr43xBFqQ+qfEfXcMTSAJWnYSF82u0AQisOji+Y89LIEKLLi4+jYxkASg6/Zi2rrFcAQ+AWNrYqDfcATAnY+4hSRkoAiAR6BiL8wfGIYA+PBgznSCLoYhMABHNm38eM3UC9SOwYFTps2L2+oFegFw3KbpHNEhUy7Qm8LjDr3bxAYWmLh45lPz0TimWmByA48aMzWAmqZa4MPv8KA75r27V0z5E4Dv3N3RzUdYxuoFPN073WUIAh7uXr9gKAKenR84KCMJPI7gJa/v8FntHKrdMDQBnpHaPGF9hibPEAU4bzg/xdSwDHCfwxVwfI4san2OrFhlyALuTvL9UWYqBb7VPD5Lqa/67pt5gUK1u3D//0l8QV124otvL2nHmEqBS9/yEwyz88RzPyafNkjrtofSlQn8m36eTmxOyhcjkUil8vinaHf3xOc6Uynwf/r51t2dGE/ePxtMpcCP9HOq6En9GwZTKfA0/dzMuq9+YpcxlQKN+e5y6vZujPM6UypgSp+n7t1Uv1YxmFIBq/R5QIe3Xj9jSgWOrEcHQ9gNQ+0BY2oFFg53/0wCGm92zBQLLEs/1xtyT6GdV1v9mYDNtPGvB+cvpExf/f2L0Rf2ac5px8mFPe2rU7a2jN91lt96oSdHTbbm3G5eWEvorcEdeyaUTwcPnWAwOEv96I//kx93NYMRBEEQBEEQBEEQBEEQBEEQBEEQBEEQ6PwNmUUxQ+QUu1QAAAAASUVORK5CYII=" class="toolbar_button" /> <span class="vb">再読み込みします</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABBVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+fZH5/AAAAV3RSTlMADDM8ZlQkaZnD5//Mq4dICRJgpeTAgQZRsfzhIRuQ7cZaJ5z52AMeot518y1C1Y3bbBh+ljDqKnvSS89FqIpyFZ9OtzkPNvbweG+Trr2EP1e6Y7TJXfhCd459AAAFBElEQVR42u2ce1saOxCHd1cKQhaseEFXBW8FKVbwUrQttl7AS6lt9eD5/h/loKfnUjNZdpPsbPI88/5PMr/NZSaTCY5DEARBEARBEARBEARBEARBEARBEARhL643lfmbV17WMtuzuel8gf2GXyzNvLbC+Nny3DwTsLA4Zbj1laXlgIWysrhqrvlr1RqLwPqGmeZvbm2ziLwp140zv7HD4tAsGzb337ZYTPK7Btn/bo/FJ2i7pnz+EpOj45kx+9eZLNv7Btg/U2MKFFOfRpmAKeEfpGv/IVOlk6Znrh8xdQrv0xOgw37Gusdp2X/C9DC/lo79OaaLD7Np2P9x0v5TWJ9+pvhpcoBaScF/he7/C4u9/+/wm+XT+VAFn/HjhxD/6385A37ROw8L+C6wBXwWmnLZF/1mcCIetVoD1/4p4de/Cs1UHF4LJx3uBOrAVtzkJiYsLkUKcpgCbgWbSZR5sC84ed4g7qVr8HKsRtsMvRVYwRGegDnQgK+R9X+Aj2hoIcUm5MKCGMmSWXgPnsMScA71nonTwgAcgwApsm5Aq/A25ioCHfMWjoAh0PW3uI140FfYRjmevb4B8lSD2M3sQ0NwiCGgDEzevkQ7kEdrYqQc7zTt4FloEiGk38/4PbQm50Mhdz5MZQbdyrXkdoHrgzS8cE02OQXlZJK/TeMDme+yTQ1aKcSkx3yf8v7zPIVwIqfzJPIOSBLhx0EKg14HAoqkc0S+1mUHxOU/EhbQ0rrxlRWj2vgccB3uqDSXRXdlV1yHJ0rt8YHhZbICLrgOX2leUp+wAwm129Iin6pOVsAXrkO1M8gWn13BTgip1dBUufausQWotQdcMWMLqNsuYKDUXht7DeQ0xqJwaJVwNPdTc+xyz9cSJSugpzl2afJlONjnGaXbLZfPEJwmK6Ci94sBFz2lhMNp7ghyreLJvvMCHhIW8AfXY0+htTwv4CphAbznqWpNkgVJFxDx8XRX3hd/ZdjR9Pij6byj9tPILfJ3K3ca9yDF85Fk+CW77u6BRH3yd63AZ5M8xl6lc18PJaM+SjW0ntJ1PXBF1pEp94HqpYIzBAEe0HE7fjNgvdE3B4MR0zCJKnnGNM3F2GxA5T5xzzVtyP49nGcF9TdQ/Wq82QtesjKsRwVlqPORqziISJesz/MXrJcZRR+DB7jg8cHB4hGugo5a9iYomO0g1l7m4cK9SJuIWxTUnC3h2e+8FxS9liYvhL6g3E4+JtSUkPpVrZsJX4hZ0ednLdz3lm5TZMheyEw4OBKXK2O/RvHEr95GF/AoNEohpbt/or/K2g97ETDsvdxRVnMLE14RoCtYDn8ldlfN9L2nlMuqt/R2ZyXCOwhsBXDhnspLDmwFZx3bFWS7tivYrdmu4HjFdgWNPdsVzOZtV1BpK1gbGOHRLm5k7V/ud41Q0FiQMr81jt92zVDgPEp4hOLzAc4UBYNh3Fe4M//4EkMUOLtzMZ5GNzP/RavGKHBWpyP+M8aL/8UwR4FzcDvZr12fckd/gxSMT2rV0Fef92WoOMQoBU//LlQEN6WR+B+GDFMwZu1HZnjp+08nhpbv+1snP73QdIV5CmJvYqSAFJACUkAKSAEpIAUJKjgmBaSAFNivoODarSB4dBybFVhmP6fAOvtfKLDQ/t8UBBuOY7MCS+3/V4G19v9SYLH9zwqstn+soGC3/Y7jOgRBEARBEARBEARBEARBEAnyF9q1QVmN58OiAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">検索入力を開きます</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAFVBMVEX///////////////////////////9nSIHRAAAAB3RSTlMADDMnPP/DRfoNfgAAAIBJREFUeNrt2cERgDAIRUFijP2XbBEGnDC7HbwbzI8AAAAAAAAAAAAAGhlXvbkz4F71HgECBAgQIECAAAECBAgQAAAAfJG10JQFZD00AgQIECBAgAABAgQIECAAAIDGxgETQNY5LUCAAAECBAgQIECAAAECAAAAAAAAAAAAAH71Ar6fXruOujJXAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">任意の順番でカタログを取得します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAsVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+ei+1FAAAAO3RSTlMAA1GlzPz/J8ly9opXBuTbWtje8PmZeEjDvTbPtDAJZqsqD+qiJBWHHiGTGC2NEjmx84Hne5/h7dJjaVu4+qYAAAGNSURBVHja7d1LUxpBGIbRRphhQEwQIfcbSiAQNBcBxf//w1JZWGVZUbL5uhg9z7JrFu9ZzK5rJiVJkiRJkiRJkqRdNQ6araKMqWg1DxrB+9tVGVvVDt3f6ZbRdTuB+w/LHB2G7e+VeeoF7T96kQnw8igG0C9z1Y8BNLMBmjGA42yAQQzgJBtgGAMo8wXwH4DRjqp9B+x6+hUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwNMFvH7z9l2NAf33f48+fKwn4NPnwe3hl3H9AKeds7vHk6/1Akxn92/SFaNv9QHMF/+6Sz1cfK8HYDl56B5+9/xi/wE/fj624NfvmVuLAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAALUEFNn2FzGAy2yAyxhAlQ1QxQBW2QCrGEA710tQRH3Cf50JsA7an6abLPs30yhAWl5l2H+1THGNr8P3X49TZI1t7Cfkj7fRfxFJab69GcV0s50nSZIkSZIk6Tn0B//bQKz+yDWEAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">板一覧を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAElBMVEX///////////////////////+65XQCAAAABnRSTlMA/6WZZkKHwEbIAAAApUlEQVR42u3b2w2AQAhFQXz137IdbDDe1RjnNEAGvqmSJEmSJEmSJEmSJElSryXSCgAAAAAAAAAAkG4bNQPQGRjb7gxAZyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAjw+WfQ21e6CngjAAAAAAAAAIA/A/ZIR0mSJEmSJEmSJEmSJD3TCVJJMPLRjo8EAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">閲覧中のタブ一覧を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAz1BMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////LRjS1AAAARXRSTlMAIUVmmQZRn+H/J/AqqAyH+TDbYPZ7inhUA8xpEu2BD28eq978Qk5az5B+Y64tCaXkFTNXouokXdUYxmw/vcm0chvAw9KU+iyZAAACeklEQVR42u3b2VbiQBSF4UCYDkEZJIoBxQlHBEHAERX1/Z/JtrsvXN1Nm0QqnHL93x13e18QUqcOjgMAAAAAAAAAAABgQVJpN/OTm05ZFz6byxfkg0I+l7UofjHjyV+8TNGS+KUVmWOlZEH81XJF5qqUV7Xnr9bkv2pV3fnX6vKJ+prm/L6E4OvNvy6hrGvNvyEhbejM35DQGirfHLzwBTyF7xabgUQQbKor0JRImtryt7aiFdhqKSuwLRFt68rf9qIW8NqqCuxIZDuqCgTRCwSa8u9KDLv2PkP1PUn34hTYU1RgP06BfUUziIM4BQ70zClKEoueI34nXoGOmgKH8QocqilwFK/AkZoCuXgFcnwHeAp9l98B63+J7X8Xsv5t1PrzgHMcPf8xZ2KmEt9pLmT/ZM762aj902mnGOV+QOWdt+03NPbfkTnOSbj8J45ap2HynzqKnX1+U3/mqGb7roT92yrvR/zuvPxdG/aF3p3n/7WxlT937NFr9C8+pr/oN3qObVLp5q+txaaFW4sAAAAA4hoMO67rljO/lX986AwHNiSvXvqjYDzvTDwORv6l2rHE5Cp/HW4yd52/migLf+N2x9HuB8Zd90ZL+pJ/G29X4tZXMGZp3wXyBcHdcu/6hv26fFG9P1xa/PupLMT0fjnxH2RhHpKvMHmUhXpM+MH6VJEFqzwlGH/2LAY8z5LKf+SJEV4ye5i9kRgzSmCE3XoRg16Mr1DMXsWoV8NfhFRBDCsYvUsY1MS4msGDT3YqCZiaW4g9lUSYu8rvJ1OgTwEKUIACFKAABShAAQpQgAIU+NP71WMC9PxNFwAAAAAAAAAAAAAAAPhe3gBR3uplPaS/dQAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">プライバシーモードに切り替えます</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAACW1BMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////H1qrdAAAAyXRSTlMAHjNgZpOZLcz/FVeo6gZUtPwbkO0nnPmfEpYDcvAhyWPzwNXSD7F+RdvhDIcY9glCv4Eke73asothPDEwNkBSeqT0af52Og4rW63pKvGYASNz1zhTovgpDdnn3buK4zVOBDvR38NBFlHOJdgmjRlJdJGrZeS60BoTX6+wfFp9nXUFH8f6PaGuArOpCKVIm5VvMo6ahWJdf17GeUSXxPfv++anxWpubfK+QxC3yHiEKM8gHcrg/dYHF7Y/bEe1VZ5nWIhk5RGsj95s88XWAAAGgElEQVR42u2c+1sTRxSGFwyQCRAIBEOAiIgBDRdRQAtoRfEC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" class="toolbar_button" /> <span class="vb">通信の軽量化を設定します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAaVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9tAe1xAAAAI3RSTlMAMJk2gf9aq1T5Evxu+/4BDwIDjAQFBgcIYQmpZ4j9VtacnwXXhN0AAAH6SURBVHja7djZbsIwFEXR2wETGhra0nlu//8jOzETX4dGin2kvR4RSGejQAxmAAAAAAAAAAAAAAAAAAAAAAD4jo4TTkovOB35gokXlB+QKBAI8AsUAtwCiQCvQCPAKRAJiBeoBEQLZAJiBToBkQKhABuLB1QT7YD2/ToBkf0yAbH9KgHR/SIB8f0aAc7+DAFnfffX46wB0/Npz/3V1j156IBpM2qmPfdvnSrC4PtHhxW07d8sCMPvP6igff9GQciw/4CC2P51Qcixv3NBfP+qIGTZ37HA278sGC4gNFtrmtBz/6JgsIC9u+mk6rn/ryDk2p8sSO//LQjZ9icKuuz/KQj59rsF3fZ/F4SM+50Cb/9s65kXeU/DkQJv/2VRp/n2Am//Ve799bxOFXj7r7Pvr6xKFHj7bwrYb4kCb/9tEfv9Am//XSH7vQKN/fEC9/qZlbM/VrDzaLHvf6zA239f2P79ggd3/2Nx+/cKzp/E9u8VjNT2uwUS+52Cwj+/yYKivz87FBR2fk78dq/9/Vb4/pYCsf1mzzv/eKntN3sR3282X7/iNfP++n9X81x8/6pAdv+iQHi/2Wmjvd/s7V17f27sZz/7dffbh/h+s0/x/ZsFE8n96wLV/csC3f1/Bcr7fwq095sdi+8HAAAAAAAAAAAAAAAAAACAtC+GHjhk47LHNgAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">更新の確認を起動します</span></p>

		<hr />

		<p class="midashi">タブ一覧</p>

		<p class="title">シングルタッチ</p>
		<p class="explain">選択したスレッドを表示します</p>

		<p class="title">ロングタッチ</p>
		<p class="explain">選択メニューまたは割り当てた任意の機能</p>

		<p class="explain">※表示位置は設定→デザインから変更可能です</p>

		<hr />

		<p class="midashi">上部メニュー</p>

		<p class="title">表示オプション</p>
		<p class="explain">取得数、行列数、サムネイル、タイトルの長さ、文字サイズ、スクロールバーを変更します<br /></p>

		<p class="title">ツールバー編集</p>
		<p class="explain">チェックボックスで表示有無を設定します<br />つまみをドラッグして並び替えることができます</p>

		<hr />

		<p class="midashi">下部メニュー</p>

		<p class="title">監視ワード</p>
		<p class="explain">単語にマッチしたスレを優先して表示します</p>

		<p class="title">NG管理 → NGスレッド</p>
		<p class="explain">アドレスにマッチしたスレを表示しません</p>

		<p class="title">NG管理 → NGワード</p>
		<p class="explain">単語にマッチしたスレを表示しません</p>

		<p class="title">巡回検索</p>
		<p class="explain">バックグラウンドで定期的にスレッドを検索します<br />別アプリ にじろぐ(仮)が行います</p>

		<p class="title">過去スレ検索</p>
		<p class="explain">キャッシュサーバーに残っている落ちたスレッドを検索します<br />見そびれた前スレや特定の日時に立てられたスレを探し出せます</p>

		<p class="title">表示の切り替え</p>
		<p class="explain">グリッドビューとリストビューを切り替えます</p>

		<p class="title">プライバシー</p>
		<p class="explain">覗き見防止用に画像を薄くします</p>

		<p class="title">更新の確認</p>
		<p class="explain">カタログからレス数を取得して更新分を確認します</p>

	</div>

	<label for="thread" class="index">スレッド</label>
	<input type="checkbox" id="thread" class="on-off" />
	<div class="explain">

		<p class="midashi">操作</p>

		<p class="title">スワイプ</p>
		<p class="explain">スレッドを切り替えます</p>

		<p class="title">画像のロングタッチ</p>
		<p class="explain">メニューを表示します</p>

		<p class="title">引用文のタッチ</p>
		<p class="explain">引用元をポップアップで表示します</p>

		<p class="title">外部リンクのタッチ</p>
		<p class="explain">ブラウザーで開きます</p>

		<p class="title">ファイル名のタッチ</p>
		<p class="explain">塩辛瓶、あぷ、あぷ小を表示します</p>

		<p class="title">メール欄のタッチ</p>
		<p class="explain">外部リンクや塩辛瓶などに対応します</p>

		<p class="title">レスヘッダーのタッチ</p>
		<p class="explain">メール欄のリンクに対応します</p>

		<p class="title">レスヘッダーのロングタッチ</p>
		<p class="explain">返信レス、IP、IDから<br />抽出メニューが表示されます</p>

		<p class="title">レス本文のロングタッチ</p>
		<p class="explain">レス本文メニューを表示します</p>

		<p class="title">レスヘッダーの番号</p>
		<p class="explain">自分の書き込みは青色<br />返信は桃色になります</p>

		<hr />

		<p class="midashi">レス本文メニュー</p>

		<p class="title">WEB</p>
		<p class="explain">単語を抽出してweb検索をします</p>

		<p class="title">抽出</p>
		<p class="explain">抽出メニューを表示します</p>

		<p class="title">NG登録</p>
		<p class="explain">NGヘッダーに登録します</p>

		<p class="title">DEL</p>
		<p class="explain">理由を選んでdelを送信します</p>

		<p class="title">削除</p>
		<p class="explain">書き込みを削除します</p>

		<p class="title">そうだね</p>
		<p class="explain">そうだねを送信します</p>

		<p class="title">クイック</p>
		<p class="explain">引用記号をつけて送信画面に転送します</p>

		<p class="title">返信</p>
		<p class="explain">引用する行を選択して送信画面に転送します</p>

		<p class="title">コピー</p>
		<p class="explain">コピーする行を選択して送信画面に転送します</p>

		<hr />

		<p class="midashi">引用ポップアップ</p>

		<p class="title">レスヘッダーのタッチ</p>
		<p class="explain">メール欄のリンクに対応します</p>

		<p class="title">レスヘッダーのロングタッチ</p>
		<p class="explain">返信レス、IP、IDから<br />抽出メニューが表示されます</p>

		<p class="title">レス本文のロングタッチ</p>
		<p class="explain">レス本文メニューを表示します</p>

		<hr />

		<p class="midashi">抽出ポップアップ</p>

		<p class="title">レスヘッダーのタッチ</p>
		<p class="explain">メール欄のリンクに対応します</p>

		<p class="title">レスヘッダーのロングタッチ</p>
		<p class="explain">返信レス、IP、IDから<br />抽出メニューが表示されます</p>

		<p class="title">レス本文のロングタッチ</p>
		<p class="explain">レス本文メニューを表示します</p>

		<hr />


		<p class="midashi">ツールバー</p>

""",
    """

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAgVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+xZKUEAAAAK3RSTlMAFnDxYvn/9nNQTzv3CrsCCcBmHEtCTj+9B6PmciwrUfU1yxf+5CFUDA/8P7Wq7wAAAb5JREFUeNrt0sdSw0AURFGBrbEJZkgmg8kG/v8DMdlJo2eKRXdxe6HSzOrckqqKMcYYY4wxxhhjjDHGGGOM/Wpr5v71TtfbX6de19ufnAve/cYF/Q+/bcG337Rgym9ZMOM3LJjz2xUs+M0KNhb9VgWby/xGBVvbKTkXDAY7ybkgV9XunnFBfnsUCvYd/MYF+evFtCD/vFoW5OmDYUGePdoV5PkLs4K8eNVc0DmQ8x8Oq3hB3ZfzH9W943CBpH/yY0cLRP3hAll/sEDY31RwYuMPFIj7Wwvk/S0FBv5igYW/UGDibyg4PTu/MPEvL8iXVzb+hm9g5PcoKPknBUNvv37BqMWvXtDu1y6I+JULYn7dgqg/pesbb39Kt3fe/vuHR2t/qkf48ePHjx8/fvz48ePHjx//P/CP8ePHjx//58b48ePHjx8/fvz48ePHjx9/YE/m/urZ3L9KgKR/hQBNfzzgRdMfDlD1RwNk/cEAXX8sQNgfClD2RwKk/YEAbX97gLi/NUDd3xYg728J0PeXAwz8xQAHfynAwl8I8PA3B5j4GwNc/E0BNn7GGGOMMcYYY4wxxhhjjDHGGGOM/cFeASW7fY3bi6UzAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">返信フォームを表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABGlBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9y/ChlAAAAXnRSTlMAGDMMMFR4nMzw/+TDmWxIJC11tPOlYB5LqOrejVLn0odaz645A9jAP3tjxsmiFZMP/CefBgmr7dWxXfm3cr2WZn5pilFvPEIS/vY2TiGBhLrhkbDHl9zvV5BY2yobsNs1HgAABQtJREFUeNrtnGtbGjkUgOMAChlAYUFEqLdKK+V+GRAQpYUqRWxtd9td9/L//8bKXlrJDMzkzJCDz3PeD/qJk7wMmSQnF8YIgiAIgiAIgiAIgiAIgiAIgiAIgiAIwis2tBm+51dxf2BzKxjiP9DDkej2zvOofCz+U4Jboyd3U2te+730Pl9OJqs5iPMiivLDyR5wJxxubthEivIj9dU/PuHOeXm6LFScKxfI7b7icrw+Wxgsz5ULvMlweQoLFI64agF/kcMolS2iVbhigWqlxqGEzC+bCFcssHfA3RCsz7elBlcr0DziLkkcP61/kqsVSBW4e7aM719Hi6sV2G5zLzj/b5DU7HC1At0e94YD/yycccHVCvS5ZyQeuwTfJVcrkLWr1X6wFPmHUtBuhMd7gep8c1q9wNXS30R6oFXnOgttkF76vq0JffnKBd4uG2zWrT9Tjx86/lGtWuDdooLbw9jS2c6wvRYCowWjh/2+7fzX19/HF/Bbvz9D7w0nnzbeh5AFqhnnY0tLyiVcgbTlXPdMJsR1BlEgalVgpyr5FDtoAjGLBtADJBGiPSSBsMVI4AYS6CaBIvDGot/VYKG0AwSBsbkj0sEpw+4H9QLmIVAoBp5OTNQ/gZipC57cQmNNewht4KWppBE0VADjLeQ3FTQEZyJrGP1AUiyn2ARGGqD0xCnxW0vcedmbr17go1hMHxgojjSYE0dgJzlYnDzScPpMLOUaFucT1oRmS1ylgIUZYs3IDHEiBRsCVdDmxHtCGfegKBG8Sb04DDoDxMglEdMqn+eLeAXJxrcQ80J3QhFxQP07mImtrlCE/Jr7XPrZhi8rbwIF+fpfSuSrf/ZeoODyGRthmYT7L94LJFz2wl+lVgy+rboNTwzZAFIPgP/K1o54RIYRIwiCIAiCIIjvjKQGk/H1EyhJDefDbvNFPc8FvkgJfJWOL2yePfdcQGp/ZVh6vmYIC35pVIFL6fqzayFEHlPgQr7+ph1g24gCHci6mTDlr43xBFqQ+qfEfXcMTSAJWnYSF82u0AQisOji+Y89LIEKLLi4+jYxkASg6/Zi2rrFcAQ+AWNrYqDfcATAnY+4hSRkoAiAR6BiL8wfGIYA+PBgznSCLoYhMABHNm38eM3UC9SOwYFTps2L2+oFegFw3KbpHNEhUy7Qm8LjDr3bxAYWmLh45lPz0TimWmByA48aMzWAmqZa4MPv8KA75r27V0z5E4Dv3N3RzUdYxuoFPN073WUIAh7uXr9gKAKenR84KCMJPI7gJa/v8FntHKrdMDQBnpHaPGF9hibPEAU4bzg/xdSwDHCfwxVwfI4san2OrFhlyALuTvL9UWYqBb7VPD5Lqa/67pt5gUK1u3D//0l8QV124otvL2nHmEqBS9/yEwyz88RzPyafNkjrtofSlQn8m36eTmxOyhcjkUil8vinaHf3xOc6Uynwf/r51t2dGE/ePxtMpcCP9HOq6En9GwZTKfA0/dzMuq9+YpcxlQKN+e5y6vZujPM6UypgSp+n7t1Uv1YxmFIBq/R5QIe3Xj9jSgWOrEcHQ9gNQ+0BY2oFFg53/0wCGm92zBQLLEs/1xtyT6GdV1v9mYDNtPGvB+cvpExf/f2L0Rf2ac5px8mFPe2rU7a2jN91lt96oSdHTbbm3G5eWEvorcEdeyaUTwcPnWAwOEv96I//kx93NYMRBEEQBEEQBEEQBEEQBEEQBEEQBEEQ6PwNmUUxQ+QUu1QAAAAASUVORK5CYII=" class="toolbar_button" /> <span class="vb">再読み込みします</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABBVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+fZH5/AAAAV3RSTlMADDM8ZlQkaZnD5//Mq4dICRJgpeTAgQZRsfzhIRuQ7cZaJ5z52AMeot518y1C1Y3bbBh+ljDqKnvSS89FqIpyFZ9OtzkPNvbweG+Trr2EP1e6Y7TJXfhCd459AAAFBElEQVR42u2ce1saOxCHd1cKQhaseEFXBW8FKVbwUrQttl7AS6lt9eD5/h/loKfnUjNZdpPsbPI88/5PMr/NZSaTCY5DEARBEARBEARBEARBEARBEARBEARhL643lfmbV17WMtuzuel8gf2GXyzNvLbC+Nny3DwTsLA4Zbj1laXlgIWysrhqrvlr1RqLwPqGmeZvbm2ziLwp140zv7HD4tAsGzb337ZYTPK7Btn/bo/FJ2i7pnz+EpOj45kx+9eZLNv7Btg/U2MKFFOfRpmAKeEfpGv/IVOlk6Znrh8xdQrv0xOgw37Gusdp2X/C9DC/lo79OaaLD7Np2P9x0v5TWJ9+pvhpcoBaScF/he7/C4u9/+/wm+XT+VAFn/HjhxD/6385A37ROw8L+C6wBXwWmnLZF/1mcCIetVoD1/4p4de/Cs1UHF4LJx3uBOrAVtzkJiYsLkUKcpgCbgWbSZR5sC84ed4g7qVr8HKsRtsMvRVYwRGegDnQgK+R9X+Aj2hoIcUm5MKCGMmSWXgPnsMScA71nonTwgAcgwApsm5Aq/A25ioCHfMWjoAh0PW3uI140FfYRjmevb4B8lSD2M3sQ0NwiCGgDEzevkQ7kEdrYqQc7zTt4FloEiGk38/4PbQm50Mhdz5MZQbdyrXkdoHrgzS8cE02OQXlZJK/TeMDme+yTQ1aKcSkx3yf8v7zPIVwIqfzJPIOSBLhx0EKg14HAoqkc0S+1mUHxOU/EhbQ0rrxlRWj2vgccB3uqDSXRXdlV1yHJ0rt8YHhZbICLrgOX2leUp+wAwm129Iin6pOVsAXrkO1M8gWn13BTgip1dBUufausQWotQdcMWMLqNsuYKDUXht7DeQ0xqJwaJVwNPdTc+xyz9cSJSugpzl2afJlONjnGaXbLZfPEJwmK6Ci94sBFz2lhMNp7ghyreLJvvMCHhIW8AfXY0+htTwv4CphAbznqWpNkgVJFxDx8XRX3hd/ZdjR9Pij6byj9tPILfJ3K3ca9yDF85Fk+CW77u6BRH3yd63AZ5M8xl6lc18PJaM+SjW0ntJ1PXBF1pEp94HqpYIzBAEe0HE7fjNgvdE3B4MR0zCJKnnGNM3F2GxA5T5xzzVtyP49nGcF9TdQ/Wq82QtesjKsRwVlqPORqziISJesz/MXrJcZRR+DB7jg8cHB4hGugo5a9iYomO0g1l7m4cK9SJuIWxTUnC3h2e+8FxS9liYvhL6g3E4+JtSUkPpVrZsJX4hZ0ednLdz3lm5TZMheyEw4OBKXK2O/RvHEr95GF/AoNEohpbt/or/K2g97ETDsvdxRVnMLE14RoCtYDn8ldlfN9L2nlMuqt/R2ZyXCOwhsBXDhnspLDmwFZx3bFWS7tivYrdmu4HjFdgWNPdsVzOZtV1BpK1gbGOHRLm5k7V/ud41Q0FiQMr81jt92zVDgPEp4hOLzAc4UBYNh3Fe4M//4EkMUOLtzMZ5GNzP/RavGKHBWpyP+M8aL/8UwR4FzcDvZr12fckd/gxSMT2rV0Fef92WoOMQoBU//LlQEN6WR+B+GDFMwZu1HZnjp+08nhpbv+1snP73QdIV5CmJvYqSAFJACUkAKSAEpIAUJKjgmBaSAFNivoODarSB4dBybFVhmP6fAOvtfKLDQ/t8UBBuOY7MCS+3/V4G19v9SYLH9zwqstn+soGC3/Y7jOgRBEARBEARBEARBEARBEAnyF9q1QVmN58OiAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">検索入力を開きます</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAflBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+yfIzaAAAAKnRSTlMAgf9+e3hyb2xpY2D8XVdU+VFOS0j2RUI/PPM58DYz7TDqLSrnJ+QkIeHMA/ldAAABYElEQVR42u3YV1ICARgEYRhzzgFRRDHg/S/o/2SVVQZAlt22uk8w3+v0emZmZmZmZmZmZmZmZmZmZma2rPp9+P5kDb4/WYfvTzbg+7GCj/3JJnw/UvBpf7IF359sw/cnO/D9KMGX+5Nd+H6M4Nv9yR58P0Lw4/7sH7D3d1/w2/4SHLL3d1swy/4SHLH3d1cw6/7k+IS9v5uCefaX4JS9vwRn7P0lOGfv75Zgkf3JxSV7f3cEi+4vwRV7fwmu2ftLMGDvb1/w1/3JzYC9vwRD9v7kdsjeX4I79v4SjNj72xEsc39yP2LvL8GYvT95GLP3l+CRvX+Vgmb2l2DC3p88Tdj7S/DM3r8KQbP7k5dX9v6mBc3vL8GUvT95m/ZaaP6dHTt3BQgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAADMzMzMzMzMzMzMzMzMzM7N/1Dsu6DC3Bqap1AAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">スレッドの最上段にスクロールします</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAflBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+yfIzaAAAAKnRSTlMA/4HhIeQk5ycq6i0w7TPwNjnzPPY/QkX5SEtOUfxUV11gY2lsb3J4e34IMnptAAABc0lEQVR42u3YWVKDQBhF4RtMjEEjGlFxwHnc/wZ98cEHNQEa+r9V56zgfpUBuiUiIiIiIiIiIiIiIiIiIiIi6tqscwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEBgQDGbor25t2Ax9/4MFvve36Kx948tWB54/5Kn2D+mYLny/jctV97Pg/LQ+4k25f4xBOWR91vFeuL9qQXrY+83uyrD/pSCPPvTCaqTXKeYNILqNN85LIVgk3F/CsHmLO9ZeKigzrx/qCD//mGC+jzCjUp/QX0R406or6C+jHKrVZjv7ydoriLdLBbm+7sLmutgl7sdBc2N5CxobiVnQcz9uwvaO8lZEHf/boL2XnIWxN6/XdA+SM6C+Pu3CB4la4HF/n8ET5K1wGb/H4JnyVpgtf8XwYtkLXiVrAVvkrXAcv8PwbtkLbDd/y34kKwFn7KuKEREREREREREREREREREREREqfoC4pUb9EQ9QZwAAAAASUVORK5CYII=" class="toolbar_button" /> <span class="vb">新着レスから最下段の順でスクロールします</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAA51BMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////bMJBQAAAATXRSTlMASJPM5P8DVNsMtBXSBsPGjSH57TlmeIfw81GEire6EuH2LTNXY5we6oGoMEX8D95gNpCrKnXVJ+c/e5m9GBs8bMmfCUJyWq6lls8k2I+Wjd8AAALISURBVHja7ZxpV9pAFEDDMqyCgAuLBgQB6YK21VKsVqvWVmv//++pWKoRJoGjM5PMOfd+fcPjXQ5JhpD3HAcAAAAAAAAAAAAAAAAAILrE4omk0EkyEY+ltJWfzggTZNJ6ys/mhClyWQ3151eEOVbyyusvFIVJVguqBUrCLCXF9ZcrhgUqZbUCcWGauNL6U2vGBdaUXg7WhXnWVQpshCCwoVJgMwSBTdsFqggggAACCCCAAAIIIIAAAggggAACCCCAAAIIIIAAAggggAACCCCAAAIIIPAqaum63QINJ221wNZ9qm2LBdzJc3ypprUCmdZDrvyOpQLtzjTZbsVOge5jtp6VAt6HoftRFKil20HhpvdB3EIiggKNwKdjd543BOwNIifw5n7VW/8D+N1MwvfDiAnkJt+Q/QO/8IcXfCRGBTL7D8s++pwgP0lSHkZJoH40XfdZGi7K+kmyowgJNB4XupJoUt7R86UWGYGtp4X58Vy04tcI0IiKgOs9xR/PnV56vlmr0RCYHsD/+ToTPvHPGrwxNSVQ78xUdfosHNgQFrgxNSUw1/j4zXuVHZ8F5g3amBoSkLxNzHMAny9I3AtbwJU1Sz39avy+MHM/XIHpj6wZChfT8OXizIXVMAXaHfny8r9rVOJqidRngxAFfDuXu5PoYG+p3MfD0AS2/V9wIsTwx5LJr8MSaAZ0O16NxPXS2Q/DEThoBb3iqL98dvnGVLeA3wH8EqQbU90CP1Xmb5gXUNwyXjUtcKp4eoVkY6pVYKx8bEIraVSg6CjHtV3gl+0CN7YL3NouUDIq8HtXOZf8T4wAAggggIAXpcNhrB/PY/2AJOtHVFk/JMy5Mz6m7U73blczqgflBdwG18KF8lGFTn5ksP4/6odFOk7WNVa/q2Nc5+Suv6GBqV1HG7F4Yqy1+LHWkbUAAAAAAAAAAAAAAAAAAK/nL+puE5OhwQgwAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">画像一覧を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAElBMVEX///////////////////////+65XQCAAAABnRSTlMA/6WZZkKHwEbIAAAApUlEQVR42u3b2w2AQAhFQXz137IdbDDe1RjnNEAGvqmSJEmSJEmSJEmSJElSryXSCgAAAAAAAAAAkG4bNQPQGRjb7gxAZyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAjw+WfQ21e6CngjAAAAAAAAAIA/A/ZIR0mSJEmSJEmSJEmSJD3TCVJJMPLRjo8EAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">タブ一覧を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAXVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////8/beDHAAAAH3RSTlMASJPM5P8DVNsMtBXSBsPGjSH57TlmeIckydXYt1fnfcKlTAAAATVJREFUeNrt3E1SwkAUhdFOQgMhRP4RFdj/MnXqwIFW90vFOt8CUu+MepSbkiRJkiRJkiRJkiSpXk3bLXLNFl3bLKudv1rniNarOuf3mxzVpq9w/7DNcW2H4vePLzmy3VgasM+x7QvffzgGA46HsoA2R9cWvX95Cgecij4H5xzfuSTgMgHgUhJwnQBwnTvgFQAAAAAAAAAAYDLA9w8AAAAAAAAAAAAAAAAAAPw7wI93AgAAAAAAAAAAAAAAAAAA/FEGAAAAAAAAAAAAAAAAAAAAAAAA4B8aAAAAAAAAAACAX1V0HGb28zyzH0ia/UTV7EfC0i18pu1WFjD7obw07kLvfys+VZiG98D7P8qPRabU38Puv9eY6/zqETSY+kjVatruWfX4Z9XJWkmSJEmSJEmSJEnT9wmy5/ChvXrFgAAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">レスを抽出します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAACW1BMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////H1qrdAAAAyXRSTlMAHjNgZpOZLcz/FVeo6gZUtPwbkO0nnPmfEpYDcvAhyWPzwNXSD7F+RdvhDIcY9glCv4Eke73asothPDEwNkBSeqT0af52Og4rW63pKvGYASNz1zhTovgpDdnn3buK4zVOBDvR38NBFlHOJdgmjRlJdJGrZeS60BoTX6+wfFp9nXUFH8f6PaGuArOpCKVIm5VvMo6ahWJdf17GeUSXxPfv++anxWpubfK+QxC3yHiEKM8gHcrg/dYHF7Y/bEe1VZ5nWIhk5RGsj95s88XWAAAGgElEQVR42u2c+1sTRxSGFwyQCRAIBEOAiIgBDRdRQAtoRfEC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" class="toolbar_button" /> <span class="vb">通信の軽量化を設定します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAz1BMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////LRjS1AAAARXRSTlMAIUVmmQZRn+H/J/AqqAyH+TDbYPZ7inhUA8xpEu2BD28eq978Qk5az5B+Y64tCaXkFTNXouokXdUYxmw/vcm0chvAw9KU+iyZAAACeklEQVR42u3b2VbiQBSF4UCYDkEZJIoBxQlHBEHAERX1/Z/JtrsvXN1Nm0QqnHL93x13e18QUqcOjgMAAAAAAAAAAABgQVJpN/OTm05ZFz6byxfkg0I+l7UofjHjyV+8TNGS+KUVmWOlZEH81XJF5qqUV7Xnr9bkv2pV3fnX6vKJ+prm/L6E4OvNvy6hrGvNvyEhbejM35DQGirfHLzwBTyF7xabgUQQbKor0JRImtryt7aiFdhqKSuwLRFt68rf9qIW8NqqCuxIZDuqCgTRCwSa8u9KDLv2PkP1PUn34hTYU1RgP06BfUUziIM4BQ70zClKEoueI34nXoGOmgKH8QocqilwFK/AkZoCuXgFcnwHeAp9l98B63+J7X8Xsv5t1PrzgHMcPf8xZ2KmEt9pLmT/ZM762aj902mnGOV+QOWdt+03NPbfkTnOSbj8J45ap2HynzqKnX1+U3/mqGb7roT92yrvR/zuvPxdG/aF3p3n/7WxlT937NFr9C8+pr/oN3qObVLp5q+txaaFW4sAAAAA4hoMO67rljO/lX986AwHNiSvXvqjYDzvTDwORv6l2rHE5Cp/HW4yd52/migLf+N2x9HuB8Zd90ZL+pJ/G29X4tZXMGZp3wXyBcHdcu/6hv26fFG9P1xa/PupLMT0fjnxH2RhHpKvMHmUhXpM+MH6VJEFqzwlGH/2LAY8z5LKf+SJEV4ye5i9kRgzSmCE3XoRg16Mr1DMXsWoV8NfhFRBDCsYvUsY1MS4msGDT3YqCZiaW4g9lUSYu8rvJ1OgTwEKUIACFKAABShAAQpQgAIU+NP71WMC9PxNFwAAAAAAAAAAAAAAAPhe3gBR3uplPaS/dQAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">プライバシーモードです</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABX1BMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////8oPrqgAAAAdXRSTlMAGz9mlpnAzJxvSypgqOf/87R1LQ9pw+GBHhWNrjMSk/a37fwMIcnkRVHwA4q9BqLPGN7bcrE2+XhUkPt29fJbIOwJ0c5C90iHVu/xX4To6xDV3aBXjyRjgKkwPCd9WozamuYKuupT0oJdRP2/C0zGDmx+2KXZ6uo0AAADNklEQVR42u3a6VdSQRgG8HtFxA0GyH1BBERJxR3MstRySTPTSsuKNk1LW6z+/9M7nT6UqXDvzJl5Oef5ffTD4/scZZg7dxwHAAAAAAAAAAAAAAAAAAAAAAAAAAAAAOACbk2gNhisC9UHgw2NTdU2fHM4Iv4RjcWvVcv0La1t4kLtHZ1VMH5Xd1RcrqeX+fiJvqS4Wj/rj0MgJcpKd2e4jj+QFRUZHOI5f+66qFTHMMP5R4QHoy3cxs+PCU/GJ5jNHxMeTQ5wmn+4R3g21cWoQK3wIctnOZ0Wvsxwmb9Q9FdATDPZPkz6nF8UC9X7AfizmOYZzN+b9l9AzDJYQccV5hdR13qBG0LJnPUCIbUCSdtbikahaMRygRnVAim/C5HqLxY3++UTcFo5J0cxt+b//omhArfvUEqDcozooZiFRQsFFhcopU29QHKJcu5aKHCPQpaS6gXEMgXNrRgvsCJX8FUN84s1Crq/brzAxgMK2dRRoI2COh8aL7BV0LGI/l5I5TiDxgtEHlHIto4CIkFJWeMFduRGrqilQA1FPTZeIEsZE1rmF08o6qnxAjHK2NVTYI+inhkv8Jwy9vUUkI/GcSsFXugpMHtuT2KuQK+eAvFzJzNmCshDnZd6CrRS1CvjBeT3p6unQImiXhsv8Ebu5fQUWKWot8YLvHtPIREtBeRbswPjBQ7la986HfMX5UPljvEC6x/kNl5HgSP5nW5+Nzp/TCEfdRSop6BPKeMFTk7lNl7XInQ8b/6R8vAzpRxpKCDvH5yemC+w8YVS1tTn/0ox37a8P9RrOppWL3Bm92RO+X+omLBb4Ey1QNjy2WgmpVhg3/bx+nf1BzvLfwK1/RCD13wl1W9h6xQOeFMJDgWa/J/w7vF40x3wO/8Yl7sGY/7m/8HmtsdS1tdukNH9xa4pH29mCg4jbrvnPVCjw4rrcVcXrXGYSXj6OogwvAme76t8/lHX4ajim68s771KE+FKxh/nfIN9qOxbs9SZw1vuyvs3kemMw97yz8t2dwelvFMVMqXwf4850VjcdarJbvNmOBSSt3G2Q7G5QMEBAAAAAAAAAAAAAAAAAAAAAAAAAAAAgDJ+AW/hgzQp/CBtAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">水平スクロールバーを表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAaVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9tAe1xAAAAI3RSTlMAMJk2gf9aq1T5Evxu+/4BDwIDjAQFBgcIYQmpZ4j9VtacnwXXhN0AAAH6SURBVHja7djZbsIwFEXR2wETGhra0nlu//8jOzETX4dGin2kvR4RSGejQAxmAAAAAAAAAAAAAAAAAAAAAAD4jo4TTkovOB35gokXlB+QKBAI8AsUAtwCiQCvQCPAKRAJiBeoBEQLZAJiBToBkQKhABuLB1QT7YD2/ToBkf0yAbH9KgHR/SIB8f0aAc7+DAFnfffX46wB0/Npz/3V1j156IBpM2qmPfdvnSrC4PtHhxW07d8sCMPvP6igff9GQciw/4CC2P51Qcixv3NBfP+qIGTZ37HA278sGC4gNFtrmtBz/6JgsIC9u+mk6rn/ryDk2p8sSO//LQjZ9icKuuz/KQj59rsF3fZ/F4SM+50Cb/9s65kXeU/DkQJv/2VRp/n2Am//Ve799bxOFXj7r7Pvr6xKFHj7bwrYb4kCb/9tEfv9Am//XSH7vQKN/fEC9/qZlbM/VrDzaLHvf6zA239f2P79ggd3/2Nx+/cKzp/E9u8VjNT2uwUS+52Cwj+/yYKivz87FBR2fk78dq/9/Vb4/pYCsf1mzzv/eKntN3sR3282X7/iNfP++n9X81x8/6pAdv+iQHi/2Wmjvd/s7V17f27sZz/7dffbh/h+s0/x/ZsFE8n96wLV/csC3f1/Bcr7fwq095sdi+8HAAAAAAAAAAAAAAAAAACAtC+GHjhk47LHNgAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">更新の確認を起動します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAP1BMVEX///////////////////////////////////////////////////////////////////////////////////81m6ZbAAAAFXRSTlMAIY3zOf88hIf2avj+P2XwQjYV0sM/f1FqAAACqElEQVR42u3ai3LaMBSEYRdbbp3UdUjy/s8aSik1WL7qrLSns/sA1vfPwDAIqkrTNE3TNE3TNE3TNE3TNE3TNE3TNE3T/ut9c/z03zvVDfDpTX1C+0MAFjR1CCe0/1LQAv3YgqsfVvDHjyy4+UEFf/24grsfUvDPjyoY+QEFYz+m4MFvXtDWj4+3L/geArDg2R/CD+uA6RGGBdOHd/ZvMmBBFj+wIJMfVgB9cWY4KqMfclhWP+C4zH7zA7P7jY8s4Dc9tIjf8NhCfrODi/mNji7oNzm8qN/g+ML+ZEBxfyKBwJ+EoPAnMEj8hyE0/oMUIv8hDJX/AIfMvxtE599JIvTvuhSh9O8oIPVvLqD1bywg9m8qoPY//zwRKWg7av9qAb1/pcCBf7HAhX+hwIl/tiDib6rKT4Ejf7TgxZM/VhBc+TcUkPtXC+j9KwUO/IsFLvwLBU78swVu/DMFjvyXgsnHV+g8+av2dRLw2nryd5GXUNf69jsqmPG7KZj1OylY8LsoWPQ7KFjx0xdM/T8nBb0rf/2rdlQQ/f7e+CmYuX9wUzB7f+KkoJ+/P2kGBwX90v2Pg4J++f6KvqBfu38jL+jX7w+pC6b+Yfr9kbhgk5+4YKOftmCzn7Rgh5+yYJefsGCnn65gt5+s4ICfquCQn6jgoJ+m4LCfpCDBT1GQ5CcoSPQXL0j2Fy4w8BctMPEXLDDyFysw8xcqMPQXKTD1RwvePPmzF5j7MxcA/FkLIP6MBW8Yf7YCmL+qzjkKWpw/WmD+a+z7B84fKfh4r8AFg+3/l54KAP6ngsH6/1cPBRD/Q8FwNn/6qADkHxUA/KMCmP9eAPHfC4D+WwHIfyuA+q8FMP+1AOy/FHyegU8/f6L9mqZpmqZpmqZpmqZpmqZpmqZpmqZpafsC2NdWDklI3LIAAAAASUVORK5CYII=" class="toolbar_button" /> <span class="vb">スレッドを閉じます</span></p>

		<hr />

		<p class="midashi">タブ一覧</p>

		<p class="title">シングルタッチ</p>
		<p class="explain">タブの切替または下にスクロール</p>

		<p class="title">ロングタッチ</p>
		<p class="explain">選択メニューまたは割り当てた任意の機能</p>

		<p class="explain">※表示位置は設定→デザインから変更可能です</p>

		<hr />

		<p class="midashi">上部メニュー</p>

		<p class="title">表示オプション</p>
		<p class="explain">各種機能や描画を変更します<br /></p>

		<p class="title">ツールバー編集</p>
		<p class="explain">チェックボックスで表示有無を設定します<br />つまみをドラッグして並び替えることができます</p>

		<hr />

		<p class="midashi">下部メニュー</p>

		<p class="title">ページを保存</p>
		<p class="explain">HTMLや画像を保存します</p>

		<p class="title">NG管理 → NGヘッダー</p>
		<p class="explain">ヘッダーからチェックします<br />題名、おなまえ、ID、IP、NOなど</p>

		<p class="title">NG管理 → NGワード</p>
		<p class="explain">本文からチェックします</p>

		<p class="explain">NG機能は1レス目以降のみ有効です</p>

		<p class="title">URL → コピー</p>
		<p class="explain">スレッドのURLをコピーします</p>

		<p class="title">URL → 外部アプリ</p>
		<p class="explain">スレッドを別のアプリで開きます</p>

		<p class="title">URL → 共有</p>
		<p class="explain">スレッドを共有できるアプリで開きます</p>

		<p class="title">抽出 → 自分の書き込み</p>
		<p class="explain">書き込んだレスを表示します</p>

		<p class="title">抽出 → そうだねが多い</p>
		<p class="explain">そうだねが多いレスを表示します</p>

		<p class="title">抽出 → 返信が多い</p>
		<p class="explain">返信が多いレスを表示します</p>

		<p class="title">抽出 → 削除されたレス</p>
		<p class="explain">削除されたレスを表示します</p>

		<p class="title">抽出 → URLを含んだレス</p>
		<p class="explain">URLがあるレスを表示します</p>

		<p class="title">抽出 → 画像レス</p>
		<p class="explain">画像があるレスを表示します</p>

		<p class="title">抽出 → キーワード</p>
		<p class="explain">指定された単語で探します</p>

		<p class="title">読み上げ</p>
		<p class="explain">表示されている位置からレスを読み上げます<br />最後まで読み上げたら自動でリロードします<br />新着レスがなければ1分後に再度リロードします<br />連続1時間まで利用できます</p>

		<p class="title">過去ログ検索</p>
		<p class="explain">キャッシュサーバーから過去ログを取得します</p>

		<p class="title">更新の確認</p>
		<p class="explain">カタログからレス数を取得して更新分を確認します</p>

	</div>

	<label for="post" class="index">送信画面</label>
	<input type="checkbox" id="post" class="on-off" />
	<div class="explain">

		<p class="midashi">ツールバー</p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAA/FBMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////yKwRjAAAAVHRSTlMAQv/SYwbqgRX5nC29SNVmCe2EnzBL2GmHGPyiM8NR22wM8IobpTZU3m+NHqg5yVcD4XIP85Ahqzxa5HWTJK7PXed4EvaWJ7Rge5kqt8x+xrpOsUXiKcfqAAACyklEQVR42u2c61ITQRSEJ00SEBUTUREJYgAjKHIxUaOogAEBAa/v/y4OJP6xtJLNzs6cLvt7gv6qMqnd6XPWOSGEEEIIIYQQQgghhBBCCCGEEEKI4JTYBTBRrnALANXJKW4B4Nr0dW4Bz42b5ALAzK0atwBQvz3FLeAPw+wdboHLw3CXXAC4N1fjFvCH4f48twDwYKHBLeBZfEguACw9anILAMsrq9wC/jA8bnELXB6GJ+QCwNp6k1vAH4anz7gF/GHYeM4t4Nnc2uYWAHZetLkFgM7LV9wCntddcgF/GN5scwsAb3fb3AL+MLx7zy3g+dAlFwD29ivcAsDBxx63AHB49IlbwHNcIhfwh+Gkwi0AVE973AL+MHw+4xbwnJfIBYCJco1bIHpjhQKI2lihGOI1ViiKWI1Vr1GenL3oFKEQtbEqxiN+YxXeI01jFdQjYWMVyqO+kraxCuBhorHK6WGmsRrfw1ZjNZaHwcYqq4fVxiqLh+XGakQPu41Vs3HyZfqiPrRlWG+TJjf4E8qS/OrxzswMRtbkgwfsedLk/Vecco00eaobl0DJr9qQNBfAAZIP+qgeafL49XjQ5P0BhRZp8sGr2Cpp8v7z/1yMR57CLrZiXUcUk/4w3u1oEfGrpxGv5MLHj9wdh44fvb0Pmn45wTBRwPhrSV60gsVPdXEV6B59IdnmR5C/zZS7N4TFaliB49TLmLnSd47Sj53liL9jYvBv7Phfjcwhj/miZeeSmbHzyikwY2tPLmv8b9bWdglmsoIJJB8RzSewuW9wfWN0gY2uM8pIL1pmV5hGEjC+UTn0fuq7s82Q6T37K/Z25idDC+yRfHPlH/F/0Hz1xvJ+z5gCBtcNswgsbjku/hhtaznHK8D0OYy/CJB+nee3wPlPR0p/E+zMOV6Bg92eI8bSarAQQgghhBBCCCGEEEIIIYQQQgghxH/LL/NTaTpFEi4LAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">内容を送信します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAA51BMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////bMJBQAAAATXRSTlMASJPM5P8DVNsMtBXSBsPGjSH57TlmeIfw81GEire6EuH2LTNXY5we6oGoMEX8D95gNpCrKnXVJ+c/e5m9GBs8bMmfCUJyWq6lls8k2I+Wjd8AAALISURBVHja7ZxpV9pAFEDDMqyCgAuLBgQB6YK21VKsVqvWVmv//++pWKoRJoGjM5PMOfd+fcPjXQ5JhpD3HAcAAAAAAAAAAAAAAAAAILrE4omk0EkyEY+ltJWfzggTZNJ6ys/mhClyWQ3151eEOVbyyusvFIVJVguqBUrCLCXF9ZcrhgUqZbUCcWGauNL6U2vGBdaUXg7WhXnWVQpshCCwoVJgMwSBTdsFqggggAACCCCAAAIIIIAAAggggAACCCCAAAIIIIAAAggggAACCCCAAAIIIPAqaum63QINJ221wNZ9qm2LBdzJc3ypprUCmdZDrvyOpQLtzjTZbsVOge5jtp6VAt6HoftRFKil20HhpvdB3EIiggKNwKdjd543BOwNIifw5n7VW/8D+N1MwvfDiAnkJt+Q/QO/8IcXfCRGBTL7D8s++pwgP0lSHkZJoH40XfdZGi7K+kmyowgJNB4XupJoUt7R86UWGYGtp4X58Vy04tcI0IiKgOs9xR/PnV56vlmr0RCYHsD/+ToTPvHPGrwxNSVQ78xUdfosHNgQFrgxNSUw1/j4zXuVHZ8F5g3amBoSkLxNzHMAny9I3AtbwJU1Sz39avy+MHM/XIHpj6wZChfT8OXizIXVMAXaHfny8r9rVOJqidRngxAFfDuXu5PoYG+p3MfD0AS2/V9wIsTwx5LJr8MSaAZ0O16NxPXS2Q/DEThoBb3iqL98dvnGVLeA3wH8EqQbU90CP1Xmb5gXUNwyXjUtcKp4eoVkY6pVYKx8bEIraVSg6CjHtV3gl+0CN7YL3NouUDIq8HtXOZf8T4wAAggggIAXpcNhrB/PY/2AJOtHVFk/JMy5Mz6m7U73blczqgflBdwG18KF8lGFTn5ksP4/6odFOk7WNVa/q2Nc5+Suv6GBqV1HG7F4Yqy1+LHWkbUAAAAAAAAAAAAAAAAAAK/nL+puE5OhwQgwAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">添付画像を選択します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABAlBMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////7Srm1AAAAVnRSTlMAFa6NCav/2DZX/IQGGO3SsWAtG/l7A7rJJ2P2HvO9SGycJOEPwEVvlt4Mxj91kCrbzDl+1YEzXZkwk+eKaeTqeKhOEnLPQsMhop9m8FS0WkulUbeHPAGNVoYAAAV1SURBVHja7ZxrW9pMEIYFYasxJCiWgwgqinhAJYKUCoIi4gER0fr//0qVviJkZzEkbLL7XnN/6tVsl+fpDHuY3TA3hyAIgiAIgiAIgiAIgiAIgiAIgiAIgiDI/wSffz4gtYEgIT8WJNa/SN5RllRZ9Yc0MkCXNQhh8h+SBmGZfKGvyKc/oo0YIKs/o7IZiJFx4gm59K8RM8llmfSvpygDJC6TgTStn2xIpH8T0L8lkf4MkEDbO/Loj2aBAOxKFIAcoH9PIv2JJK1//0CiBDoEApCXKABHgP5jifSfrNL6CxLty6IGrV85lSgARSCB5iXSvwIkUEmi7YyqAwkk047yDEigJYn0lxVav85KoJVfuUpFrPlBLdH6V3+DTXfPqx/ZdSRWAGpAAhUhoxf1wbOUYDuEUyCBDDqBosv7/55pgn27Az+ABKLLKYnPma5xItg3eB5IoEu6WvS510mKNrrmgQSKm4tBgavh7NAUTP9BAUggcyno+mulfSHaFHAOJFDL1GbnZvjIL5r+NqD/1pRAgdvho1REtATapvWnMqZGW1/PzkULwBYQgJx5/Bl5JlqxehfQnza1CY1Uq+uC6b+uAwl0Z2p0P8Gc1zwAAeiYJ+DRaSIrlv4moD9mbrQ3+lSsFLqu0vofzcOkOnZeI9aX+AoIAHWWsTH+vCKQ/i6g/+m7vaZAhRZfg9av0fOs31xsvxbFQBAIQI9uRtVLs4IU61RgEQ0t1OhyiygOnqkU0nxAM+DIw1gXw8FO3ySsa3WoaohSU3kZC8IV2KYFGCBKTpSRaGSMqcLDS5mA3ItSd+89fkp6YbQowQ5KopQmIq//BD2wGmzCBkhSmM1x56NeUmfOT2qB4YAERTlAvstOvE3wzDJA9j2qEK2XzX+z1pnUvsJ0kOy5rT3UCQ+GzpS/Z30YUW+ZDsgfd3NldOqqrlkfbbfZDp7cOwhX503HYK+WP/t3ku1Av3Nr0qJXNQWf1X+cT7EdVN25khaB0iBu+Riy3PDYQUAHP9t6oe2kPsGBC8tTP/zRqxnrIUyzHdxwXxn1WB99b72PaFFhOghy1h9ljoPaNNda8+w0ynkUAELaU1WSnljdpDJcDUxI3yn/61qsNHriunlUZrcWyFcZHb1xNLDL1k/Opl4KGnBHrxwN5CYYmH5b4oP3aArHJUVtggEbpcIIPBgV+RnYY+vX7Lwd8Aav6vgZYO9ISM1Wh2GwL347zGN2AOyVauFdJr87ykWmgUWbS0NwXN7kZmCZpf/Sbo/67NLRCgnG9N+13WMM6o/j6Q04e6Yd1NZAA31XJ4K4o1tjccjAAz8DGWr97mzlEngkLt/zHT8K6Ds9LIWH0U2OBkZP9Kptx929zmpVYp32sCakhxx3Bi8lknw3xl2FcZvMRoVMd39L87Ep0GY1VDDWhl3OBuYyWQeLhxEYJx5VF+7q97YJWeCkn7hymzr6EnM4gkb/MPQX5HhZIvTKWhc+yyA/mmMWqcMy6D/V2eVdn/jy2372xlR5c1XKS3fqyfjgokRmWptxVqjTSWFv0fod4oNm5XGSfJ4FFUZhZ/DGw37/svl9MWrhZ1ohk/HgdbnI8J2N1GGl0yyDtYlA+VctppHv8OZ9oJB5QCkZlXeOOu8U3//QN4wUsYTm0QvfO1kyE27uvBoV1a1Z6O97eW3oIulUvtLydmpK6M7033p+D1ldUuzLbwjxiysLdoPweCbKbae1bRvyU0vCXEB+n69a2pTyDzuC/VjJzlHBuvr6mZC/l9QOWvo6J/vi/tBK6CL8zbxQ2OsK/js36kbNgAPRSB/3InNykHi5rMQNY3CSWjCMWKXY2ZVFO4IgCIIgCIIgCIIgCIIgCIIgCIIgCIJw5C/rQLF6zvfurgAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">手書き入力を始めます</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAolBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9MEo+OAAAANnRSTlMAP7Hk/3j8w/Dn4TBLWieNzHL2BreQLdj5yc8S0hXbG94eJOrtM/M5PEJIVFddY2lvZnUPgZntBx0yAAACEElEQVR42u3cbTcCQRTA8WG3aVPCotaSQiQij9//qzk4jqco3Lk79/j/X+/tzK99M7M9OEdEREREREREREREREREZKSl5SQVKVleqmL9tVSwWgXvv5cEeP17UE9Fq6sDMllApg5IhQMAAAAAAAAA/CuA+b2Q+d2o+fOA+RPZ45m4IbP6RjVn4qdW/r78lSofTDQlbkCzQkBLAtCqELAqAVgFAAAAgF/UXlsX2w359bW29vo3ctndaL6huvzNrVS8rU1FwHYaoG299Xd8CIDv2L4BmregGwbQVQMUYQCF2Ucq6o9W/hNgZ8c2oNzdLS0D9nrO9fbsArq9x6t7XauA/f7z5f19m4BB/+X6/sAiIDl4HThI7AGSw7cTh4k1QOPDEavdsAU4+rQ17hxZAgxnPO9sDu0AsuNZU8eZFcDJaPbY6MQGID/9au40twDIx18PjvP4AcXZd5NnReyAYs7HFa0iboA/nzd77mMG+Mn84YmPF+AvFpm+8NECLhcbv4wVMF10fhon4GrxF7iKEfCjrzvU4gOU12+bNfXugjLy84DcFAAAAAAAAAAAAAAAAAAAAABAFcD3hQAAAAAAAAAAAAAAAAAAAAAAIcrCrF/v/1XqYQB6/69yEwZwowa4HYZY//BWDeAmIQATp9hU/Ae5fupUG5ey6y/HTrvR3b1YdyNHRERERERERERERERERGSjB9ohqimNyOAaAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">あぷ小アップロード</span></p>

		<p class="explain">ファイルをあぷ小にアップロードしてファイル名を追記します</p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABAlBMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////7Srm1AAAAVnRSTlMAD05mgZkSbLf8/wl48FHkfgarn3I/22kDQpbYDDBIV0s8G/79LSE2hxU5e0Xtxq6TYF1v+ornkKX282MetOHezyTMGKj5dbGE6tLVySpUvVozwCe6oXhfgxYAAAO6SURBVHja7ZxpV9pAFIYDiCTDJoKIgohRUHAD64aCIopU21pb2///V+rpaQ93QiaTfGhm0vM+H7OQ+2Sbmcy9GAYAAAAAAAAAAAAAAAAAAAAAAAAA/g9i8cRCMrmQiMeiGP1iyrTYHywztRix8NOZLOPIZtJRij+XZ3Pkc9GJf4m5shSR8AvLTMByIRICRSakGIX4S8yDkv7xr1heAtaK9g9AmXlS1v0xWGUSVvWOv1KWCZQrWgusMSlrWgusywXWtRaoygWqOsdfYz6oaSyw4UdgQ2OBuh+BusYCm34ENjUWaPgRaGgssOVHYEtjAduPgK3ze9SPgNYN2bY8/m0I/Et25AI7EIAABCAAAQhAAAIQgAAEIAABCEAAAhDAhy3tiPq3UcyRqSYyc2RNwcx74DmyRUUz4C1y3F2yPPAcWYEs3wtRoE2O2yHLA8+RUYH9EAUq5LgHZHngOTJ6JhJhPgQkrZLLpjyUx38ouhfjYQocCTKYjuUCx3T7DlnRDFOAZCZ26fKeXKBHtz8h6XShNnAfZgc2uab4VBb/KRfnmao0HPK6OedWXMgELrjNzdmKy1AFCn3BvWtfecd/xc3TV8jPXIfbFpuiPMSMt0BGdCHZIFwBEueQbyJMr/hNPmdxb7bmRl1vyGpza2IeeXNVRy3HreBlFgYjYUL03bko/vM7fss1QZcqFEiO940jl3Uwdo9/7LzNhypzGe8tQdv0TvrBLf6HtMfwZz90AYOUOVTn0onrI2f4o7rXL0zu1Q4fH+dXn3D30fhkfosV1em8Q1Hz9LenWUqO3y/EaJwstdzaQpInbrVUCOySMzgNvvtH9aUplySEVNCdDyzJBQwB2u+ZdALu+yTsX4TIIx1oBRqPtJ/Jrp+UlaVUPtNKvQC9sRrd0eoYymjRisn8F9/nn8avog2bkeOG6z6Tigdcd89U+42OG4FNHv3sEucKXfNttQKVKddhGEpfiDV+0JltGopJH/FDxpJ3mdULX6jb16CmKeboPFd7YoU6b8v6DUMDal8dHc/bjGvfMnbtHCdkNamsLMx3/4cpx+DLfl3uzw3QmoYu7LmNH81u4vXbO7nE2bFbheJoYOhD/YkFpajXvwTY34OFn9Wvlql3GOT0tw39iCX9hv+sayHTW3HiI/yxzpVwdjcrCf+HzpWIvxuFl6n4bwGq+7YRAdq5rst7PzstvRnRwW5wvc6fvaYRNehsPote+BCAAAQgAAEIQAACEIAABCAQYQEAAAAAAAAAAAAAAAAAAAAA9OQX46zL4ZlVOgEAAAAASUVORK5CYII=" class="toolbar_button" /> <span class="vb">音声入力</span></p>

""",
    """
		<p class="explain">「改行」という単語で改行を実行します</p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAC6FBMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////h9QkCAAAA+HRSTlMAGzdgg5+4x9ji6O3r5d/QwKyRcksmDidcmMjs//rbs3hADwT0plsXI3fM/adNCUGp8tUaTLzpih9Iv4ETKOBlAwV28TMyw3pq8CSgecvKwUf+/D/j5oj5NaP4i7sGUddwFSCMlfuv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" class="toolbar_button" /> <span class="vb">回線情報を追記します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAk1BMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+AVyv3AAAAMXRSTlMABktmDITz/yHVEtuiJ/x4pb3DwDZR4RhdPMzGVBXkfvBOh0UPCbrY57SZk4FI3nv2+iGRswAAAWFJREFUeNrt3MlOwlAUgOFSCwVkLIKKivOAgvj+T+fCcUVSWnNb+f7tzUnOl3TRpLmNIkmSJEmSJEmSJEmSVKsa8cGOxY0KrJ80W+nOtZpJ6P3bnbRQnXbY/buHacEOu0EBvbRwvZD79wfFAYN+QMAwLaFhQMCoDMAoIGBcBmAcEJCVAcgAAAAAAAAA9h4wOco3OZ1VDHCcd/SkYoDco6cAAAAAAP8ckIzmH52d1xPws+Kk7oAMAAAAACA/4GKx7ROGVwkAAAAAAIA/Blx+nl1d1xTwfXoDAAAAAAAAAAAAAAAAAAAAAAAAALA/gK9bZq3bmgK2BgAAAADwG3CXdzSuGKCZc/L+oWqXgB7nuXpyiwkAAAAAAACg9oBlGYBlQMBzGYCXgIC4DEAcENBYFN9/FvRfbdPigGkUtFXR/VdR4NavhZ6fdRS8ZJO97bb9W7ZJIkmSJEmSJEmSJEmSVKfeAfDoloDv3QUSAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">機種情報を追記します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAACH1BMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9O2oTAAAAtXRSTlMAGDMMMFR4nMzw/+TDmWxIJC11tPOlYB5LqOrejVLn0odaz645A9jAP3tjxsmiFZMP/CefBgmr7dWxXfm3cr2WZn6kion9b0XctTyQYShCg+D+Dp72XvUQTvLpUQqhT6OOL8dlwSqE0yx8kdE6IbmaSuHFLpWXsBOqcUxG2iXvXIiAc0N3vBrjBRJTrFf6aG4jul/3CNd6GTjQPvQBpx/i2+h9FAdB2TTKVSlqDWe2F81wmKaBFj3YfgAABeVJREFUeNrtnOlbU0cUh4ewhQkJkBIgQFmqFFBISMhli0AKLVpEtGLRFloVu1itRaW4ULFKoUC1i5XWLrZq933/AwvW1mZmbpKZzJ3JfZ7zfoBPOWd+yZ2Zc86cuQgBAAAAAAAAAAAAAAAAAAAAAAAAAAAAgCyyHBtk22/gObl5+c4CfB9XodtTVGyPwZd4HyjFbFy+svIMH32FvxInpqrakYKdBz1aHpzqGpwKtXlZSSx5cJ364dc/hFNn0+ZEprxYuYCGsocxH41NpsaasXIBW6owP1tNJNRh1QJyWrAYrQGGtSBWLKAtGMKiFNCLjRsrFlBRg9PBGY6fS+1YrYBIHU6T0vr/j9+H1Qoo34rTJ9/47+vowGoFFHViGXTdC5Ii3VitgJ4olkNNzoY5YxtWK6AXS6N0fUvI7sNqBVQnG1Wls9V9l1ZnsggPR3Pb4qeT9QL6Ez4T/pijLW6zcMT8CdfbELGXWy7gkUTBZpj9mbC3NuWHymIBkQEzx52DJQmzncHOjBDwqNlj35s0/83urdQvwPMYO7IZMlL5tDFUoFnA9h2px5ZMAq1aBZQ/zsx1m3hsDFdpFLCT5bC7jTMI79YmYIQRQEQFigieqB4BuxoZkcCoiKXRUi0CdjP2XYeYKUeNBgFFe2hnwiXDnieUC9jro32NCX8Z+9T/Ak+OU64G9gvaOhBVPwcYP0DjLkFbuTpWofqnSEdPT4iaCmnYByafoRx5BU3FtOzEz1Lr3kHB0xePnljoEOlmT5GYIa+eYO7wFOlmt5ihZk3h9BFy3j33vJCdFzQlNJEx0kuekJ1BXRnZ8IuEk8KjImaC2nJiauV4ScSKW19Sf4zwcfxlfhsNPn1llROvED6mG/inUYfGutDJU0Qt7Qj/+Lt1FraGCBenZ3gtxJWfk3BMvoBXCRez3OPv46hXn5E+/rPnCBfnecdfyFNwn7I8mXfNcRqY5Tox2CFdwGsX4j30zXMa4PoB8EXpAl4niiBjEd4I1M3DhOWL0CVkMy4TAhZsLiA6YjcBdXJyMX0QycCFN0AACAAB9uKQ3QVctvsyavuNbNHuoQR5OCk5mCOqLVH5Asjjee5wOjFE62mXfAFkZXdpXqZ1gzgu88sX8CZRElmek2l9mJhhzfIFrOSnmdQnhOyfsmKRzku3rMKRMIdWLRDwFtGoNHVYnu1ysmvNinV05nTapUXzhJ8Q0G+FgIbp9Iu7ZpC3Jyos2coWJJTX2TQRlvcZlgiYW5ZxwMGCLPp2WBNMBK5IOWKicZBluasWhUPn5RzyUWwiux8NiwRQp3yCx6xJdmG806qAlNqMRQ+641c36v5ZiWUh9Za3sfy8jDr8bLQuJ6A7Rn1707ZJtf5Zmay+I63d5l8i1C2cWgvHTx+1ijc83YPuO5iwUgB6V17L2V0O0BfLrM2M37tGeTy4X9xcCTUBQg5rBaD3r1MKZtdEjRXTna/9Fo8frUzLO1UvdtEXQFatFoCaPqAVDK4IhUCMzuMeBQWiDxmHorOT/HZYvd/bVFS4qKB0g3M3uDdgRt9rTUCFAPTRxwwFxz/hspHN6rsJjSI1jIwzvH+6cJMjAGXeQGlGqmBfIl5KtUwRaGd+/lqDMgFnP2OOYLwulT3N8LBvYbW0IXV8btK6dGsoWXRqeg/udgCp5KhZ78mdL75MFDqY3kR0qX5zDJVe3n+QOrZ/xd54vebv/ugsQar5+hvzZp87/rJv49rasx0xvyvplW7FOL5Lcs+8xe12B4Prf1qSvbnhVBjpILwk6UJ6SxbSw40rUsbfbiBdrF2S8D6AMqST72+l+06JMNLLD2lNhFDQQLpZW7woPntzUCYwf0Zs+J0xlCnMdQlM3upVlDlM/rjM+e03Z9LwN/jp59upD7+qNxPfXhg5+cv1lL78/s0oU/nV81uS0bt8ExGU0RT/3vjP70C/O8PVETuB7IAxc3WxeuCPP51O50bpx7X+3/dXmcNA9uImAgAAAAAAAAAAAAAAAAAAAAAAAAAAADKQvwHDVmmRCiAyKAAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">元の状態に戻します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAVFBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////+UMeA9AAAAHHRSTlMAfv8k+bRLBtt4G/OipZNmHrcw/HuWaRWQ6io5if9DFwAAAQBJREFUeNrt1NmRwjAURNGH2DFg9jX/PIkAu8bWlFTFORH0/ekIAAAAAAAAAACGm6QOk/r3T2ddAbNp9QHz1Gle+/5F6rGoe/9y1RewWlYdsE691jXv3zT9Ac2mhqXpHwgQIECAAAECBAgA+CXbHPe5LRiwyxGwEyBAgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgIBv9jkC9gUD2hwBbcGAQ46AQ8GAOI7ffyy5P07nsfvPp6IBcRkbcInCruP2X6O4WzN8fnOLCtwfz2Hzn497VOL1bv/s/QoAAAAAAAAA+DkfgItBY4K2bBcAAAAASUVORK5CYII=" class="toolbar_button" /> <span class="vb">内容を破棄して前の画面に戻ります</span></p>

	</div>

	<label for="drawing" class="index">手書き</label>
	<input type="checkbox" id="drawing" class="on-off" />
	<div class="explain">

		<p class="midashi">操作</p>

		<p class="title">スワイプ</p>
		<p class="explain">線を描きます</p>

		<hr />

		<p class="midashi">ツールバー</p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABAlBMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////7Srm1AAAAVnRSTlMACTNdZpaZzMNpTjASUZDA5P/huoFIDwZLitKNOe2fFX7b8Ccbk/a9IfljAwz82FpXRajzq1TPGOpgPHXVHsYtNkK3sSqHhLR456WiyZwkb95srj97cmggfHcAAASdSURBVHja7ZxtWxJBFIZnCXYFIRlJBY1UVAhXslg1kRQVtSIzK/v/fyWzNF72ZWZ2ztnhus79wQ+evWaes7vz9uwMjBEEQRAEQRAEQRAEQRAEQRAEQRAEQRBEfKzUs3TGtm1n5v6Pnc3NTpH2fOH5XJFPMF96sbBovPilcsXhISyvLJgs/2WmyiOpviovGal+dW2dC1J7vmGc/M2tOpehUTBK/mu7yWWZ33ZNkb+z0uIqvNk1Q//bIldl+V3y8jdKPA7t1YT173o8HrXtRCcM9rAWb28/5bqpwp5/UkHhuc3kXp+DYSGZ94//f5/x0R8cPkyqJSwM30ovNxzKjT+E8PCLRHrUznDn2TwaDR6NDgwRYd7I4+vvjijIjoezMmHOPxxj618bqf9k8oIT8fDDwNzD1T92B3OTV5TFw39nqUeo3f9o5XVr8hK3Khp+bOenePrPxtrgud9FfdHwU3d6gaV/tjZW9YrfVSui4SeKSEPa4sTK5dLvsivR8H/WcXrT9kTFmp4A5x8x9KcF6+2Lhof5hNAAfBZfWnqhB1rgnal74FevhnHgH5+hm0HWt9r4I/ETA2D3oeZfbTq8paQFGtIjZdAEBgG1xpyNjvSlkL7XUWC13siNK3ty4RG6gAmEreAzT+vz1Yx8eHhKsQOm/zS0Ym9QmL3vZgsDTyWMMhj0OQqfoVpBjyPxDCiBL1gJXAN5oHWsBDiM/d4R8qkkCC7iCiSBhoSNJUJIEcsQ+o9bwjaW0O0PLQLCZfmkNjNQm3t0ABKYkfCpooko4qt+/fmW0uw4gKgiABpBQWl9EkRUEU39dm9FZYUYRHQRN9oTOJH1qcKILuJM+1q4ruCSBBJdhPbp0IawjSVCdBHfdCewz3GfgPY1QVfYxhIhuog13QnYHLcX0v4EPnLUcWBypI7LtaJPpTgS6++FblV9Kq7khOm3t2qqPpXabJT3QBOQ8qmUnLCWdm9I3adScsLWGXQCEj6VihPW1p5A3G01knxnsG0AHP07fYuo+qv61zMHqAkAfKZpoCYA8JXGxtTvWfoT6GImsAXgCuUwE4DY9XGBqP8c5AN3c5oHgT8sT3Mf+oevaIMY0K6hDlYCUHvaj5H0Z8C+seI0ghLcJt5LFP0WmP6QfRL6aIMecJqHlt/8zkCBfofaN7D62Q2o/EaKgQO3Jri9QjktCjMjnf+xhnbWVeNQUHQcZ8/upn+iHn3Yj/miOF/s7HYqZbGkcN8oi6/3O0kfGYs1o2sfMyNwHSX5h2fMFGZVzn1WL5g5qFjRRp0dzh9K659jRiHflebMSkDao6tbhiVgSY7HM8w0NuQ+dgyMS2D8GFwEffMSGN7/F01tx8AMtpL2mmMzMMPqUWfpXKoduwZmIPUMfixNezuomNgO7mQySJmYgcyvYpjYkO/H5A/CCawbmcDYT3uE0WKG8lZ0fWCZmsGi2BqtzswlJbLSv2Um04n2i0rMbHajVjmXzHRyJez9J9rZvAt+k6oWmwpOKwGv0h2bGvKFrYnxuXXlsumiV/hlXzvOwyvlNSo9RhAEQRAEQRAEQRAEQRAEQRAEQRAEQRBa+A2RuzgBVn2EuQAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">カラーパレットを表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAA9lBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9IpfwOAAAAUnRSTlMAHhvbGNj/FdXSEs8PzAzGCcPAuga3rgOrqKKflpCNh4F4cm9pY5yKM138V95U+U5R6pNLRfY/PC0584Q28DDtJCpIJ+d+5LThIb2lmVpCZnuxwGmvbgAAAsBJREFUeNrt22tXElEUh/EDyEYuA2OSE4RiqDGgTlzUrBxFLMM0tO//ZQqqZcUAc1uevVf/32vPWvtxgOHMDEoBAAAAAAAAAAAAAAAAAAAAAAAAwP8rIXz+ZEr2/CtpEj1/ZpVEB2SyJDoglyfRAQWDRAcUSyQ6wFwj0QHmMxIdYK6T6IDycxIdUN4g0QHWCxIdYFVIdIBVJdkBL0l2QI1kB2yS7IAtkh1QJ9kB2yQ74BXJDmiQ7IAdkh2wS7ID9mguw3jd3LOLvOdvtWmZdG0/yXb+/eXzTx1UWzmO8x/6nH/C2XhjcZu/41Ag3Z7Nav5+wPmnb+wOn/mPjimMPJeEk3DzszkKb0PP/0PpVPv8795TFM4HU+/8Zy5FlD7ROf/5BUVX03cQTrsUh+xA0/yX8cxP5A61zH/1kWLzScP8g2uK0ecnfyMkRhSrtafeL9zEHEBGTnpBPiO9IJ2UXpAtSC/4UpZecKukF9TFF7SkFxxfSi9YNaUX3CnpBV+lF1wkpRdUlPSCPquC3b5tn092ELY9vt+qGj42otdFTgWze7lWL7ukoKkYFXj+tXnUW7Sldm74FDhzF4w35zdUFZsCd9GScW3Opcn2OZsCd/Gawk7K+zKF4lLgLr/BY3gVnHEpcH0sG5ZYnM28C1xf6zozL6R2gklB19+6csNlcC7wKhj5XZip/HPoijwKRv5X9g/07i69CwIEqOJfT9ytKxYFo0BL+3981WsnWRQEC1Arj4+9U0NxKAj8O7KHx0vuikNBNvDa4e/vR+0ch4LgAeoqpW9nNlsQIkDlfr0RaopBQZgAVf72866NYlAQKkCVp7+hcCwGBSH/i9btpED3wyCTgrCfhdbkdwiHSn9B+A/zO6J7pb8gwtmoRw9Kf0GUu0bNuv4AFely/0ABAAAAAAAAAAAAAAAAAAAAAAAAADD0Hd6ShXKd1xuaAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">一つ前の状態に戻します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAA9lBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9IpfwOAAAAUnRSTlMAHtsb/9gY1RXSzxLMD8YMwwnAurcGrqsDqKKflpCNh4F4cm9pM2OKnFf8Xd5O+VST6lFL9kU/LTyE8znwNu0wJEgqfucntOThIb2lmVpmQnuxhxuR+gAAAsFJREFUeNrt231TElEUx/ErHJKFlYcIXQil0JUEQxCUslXALNcwsff/ZhrKshBkn4Z7zszv8/+d+R6UWXbvXqUAAAAAAAAAAAAAAAAAAAAAAAAAAGatSR8gFhc+ACVeCB+A1pPCByAjKXwASqWFD0DmhvABKJMVPgDl8sIHoJd54QNQIS98AHpVFD4AbRaFD0BblvABqGQJH4DKlvAB6LX0AagifQDalj4A7UgfgKrSB6A30gegt9IHoJr0AWhX+gC0xz08a+/X35kmLbTPOD5+UEnQMo0mz/h0s3xInjQO+NVb7zdb5FnjiFm+3e6QL61jTvnHJvnW6vLJT1EQvRO5n/7DBKcM8vsZCq73QXd+/mOLwjj7pLf/NEEhOec6P/4KhXcx0NY/NCgKnb6m/pFD0ehcaun/TJG5+qLh3/8rReh6uPLf+zmKlLviLf20SREPcLPS/mRKdn88Ibt/w5DdX/wmu1+NhfdXhfc3hfdf9mT359dl96tb4f3fhffHL2T3q5Lw/q7w/uy1h5tDs7xzN7Ht6a/7gW139xj1q/qSeKPdHHreodHQf/PcA6yr9sn8N7L49Kvy4vrtycJVLTb9g8aCR4OVyXPLHC79av5DiNjukndaHS795/PyzeVbLA6T/nnXsMzIwzqHSf/ak29AzNsGl8Oj/8k1wKl5fIeyw6M/O/NBljwfCXBZ9M/cRx762Fx0WfSrwn9vvfl5l95l0R//5yvc8be363LoV7XHiILPQ0kuh371+Cj93u/SGIf+9J//oLOR77UGg/6/d2KxABtBBoN+9bCXWghymspg0K9+78b8CHR+wWDQb/26KdkKdv7C0N+v+tOGsRX8r6e5Xx1N3/sPenIhp79f3RHdhriEaO9X99QOcQ3U36+q9RCLx/r71TDMYrFnKAEAAAAAAAAAAAAAAAAAAAAAAAAA1E/anm1vffCYMgAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">一つ後の状態に直します</span></p>

		<hr />

		<p class="midashi">メニュー</p>

		<p class="title">クリアー</p>
		<p class="explain">キャンバスを最初の状態に戻します</p>

		<p class="title">保存する</p>
		<p class="explain">キャンバスを保存して添付画像として利用します</p>

		<hr />

		<p class="title">カラーパレット</p>
		<p class="explain">二種類のブラシを保存して利用できます</p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAYAAABS3GwHAAAZkUlEQVR42u2debBlV1XGf2uf4fXc/br7dTqdjgmhIBGCDBEwgAzGARVQHJmJERSQIdFS8A8ttawSxUKrtLBUIopSqOUASUCjIWEmTUaCAXqkhzfc++743p2Hc7Z/nH27X3e/4b737r3n3Hv3V3Wqp+Sdc/Za397fWmetvUVrjYXFuELZIbCwBLCwsASwsLAEsLCwBLCwsASwsLAEsLCwBLCwsASwsLAEsLCwBLCwsASwsLAEsLCwBLCwsASwsLAEsLCwBLCwsASwsLAEsLCwBLCwsASwsLAEsLCwBLCwsASwsLAEsLCwBLCwsASwsLAEsLCwBLCwsASwsBgg3Dhv/vb/+NHY7t0MhBv2tnjVdTVaofTjFruBvcBh4CCwD9hvri1a42x12fmUHQCUgACoA1lz5YAUMA3kgYWeP6EI6tg0LFZAxTcXTvzI/HgSYISwF3gm8Czg2cD1wHXAJLBjkz+7DBSAU8BR4BvAN4EnDTEsLAEGjp3Ac4FbgJcY57+iT/faYa6rgZct+fu0IcGXgc8Bj5mVxMISoC/YA7wUeC3wcuDamJ/nCnP9EPC7wGngC8B/AF8EitZklgC9SBK8HHg98Eqj55OKa831VhM3/DfwSeDzQGhNaQmwHkwCvwDcBrxgCJ//MPA2cz0E/B3wLyaWsLAEWBGHgLcDvwRcMyLv9HxzfQD4GPC3wKw19YUl3iJKU/6BCSR/b4ScfymuMe/2mHnXg9bsY04Aga0i3AE8AvwOcGAMXvuAeddHELkD2GoJMIZwlH5Nuup8tdRUH1bCofGzvDpEufZhKvWvotRrLAHGB1cDH3eET+fq6jmPpD1cNY6HhWvUbA7awXOATwMfN2NjCTDCeDNwxPyKrzRPZH1SFWe8SOAoJF+ChQo4atmxsQQYLewF7jSz3JXnYwCBRls4Mud3YoIxCHwE2gEym1vuX680Y3SnGTNLgBHAC4AvEeX0L4PnaE4UPU4WPTxnDFYBJUi6CNUGqBUpf5sZsxdaAgw3biOqk3nGihOi+fXBOZ9GW1bxidFwfmpNJF2gixd9BnDfShOHJUDCF3rgg2YpX7Ma01WaVMXhiayHN8qxgAgyl4NWO5JCa2OHGcMPjqpCHEUCbAc+Abx/fXGh5pG0T6GhcEbR1EohixUkt7g08O0W7zdjut0SINmYBO4iKl5bX2JEoNRUPJzycUZxFdAamc2D3vC7vd6M7aQlQDJxAPgMUXnwhuA5midzHtNlZ7SkkKOQ3EIvOr9+yIzxAUuA5Dn/Z4GbNxs4tALhwdkJQi2jIXpFoNlG5vK9kvE3m7E+YAmQHNnzKeCmXvwwz9GcXnQ5WnBHIy2qJMr61Jr0MMV1kxnzSUuAeLEN+PfNzvyXrwSaI3M+1daQp0WVQKWBzBfpw4vcbMZ+myVAfLgTeEWvf6irIFNzeDzjD3ksIKi5HLSDbtOe68UrjA0sAWLAnwCv69cP95TmsXmfTM0ZzrSoo5BiGfKljaQ914PXGVtYAgwQtwK/2Xf10BIeSvkoGbJVQIAgNPU+A3n23zQ2sQQYAL4f+MtB3Mh3NN/Ju5xZdIdLCimFZIpQrg1yw6u/NLaxBOgj9gD/xIC+SAoQaOHBOZ92OCRpURFotJBUgQFH8NuNbfZYAvQPf0a069rA4CnNuZLLt/Ie/jCkRZUgqTw0Wv0KfFfD9cZGlgB9wBvj0pmO0jyU8llsqmSnRZWCUg3JLBDjg94KvMESoLc4HOfM4gjk64pHk94+qU2bYxDGMfsvxZ+T7E3Eho4AfwpMxfkAnmmfTFcc3CSOmqOQQgkWyv1Oe3aDKWMzS4DVnaq+6uWrOo60fhL4xQRIa+qB8GBqIpmB7/k2x8RotF9E8SqU8bLVrhgR685wD2TfsppVEfSWG3Z+5YNXTpygrX1C7cQ6WL7SnCg4nCy6PG1Pi2aYEGdTgqRMm6OTgOWpMywl54+oqvuIzj1IJGIlwLHy6iU8Gnnn6dqzb7xhx1e5cef97HQLtEIfHfO0cWTO53t2tlEyoM9Ma83+3bc59t/xBagpKLhQVTeieScJzgwlWQJN+ar2gUC7PL7wI9yV/nX+r/QyNApPGsTleq6CuYrDN7MJaaIXiUqdm+14A18FtAUyHsz6UFERGRS/jeJAUiVQkoPg9wAHhBBf1ai09/Dl3Ov47Py7OVu7EVdaONKKKd7UPJz2KcbdPukoKFWjZpe4pI8yc1HBhRkfis6lnjUF3G5XgPXhSuBdF9la2niqTrrxFO7N/Ar3Z2+l0LoSX9VREgzW7wQWm4qH0zG3T4Yhaja7mTbHzcudsoLZCci60QqwvEe9gyX7MVkCrI1fJTpU7nIJIk2UBJyoPp+70+/jSOGnaATb8FUdGaAs8h3Nk1mPmXJMdUKOQnIlWKgO9oA7MV7TEEj50dUwjr/yajhpbGoJ0AV2ER3ssIoNNJ7UaWufRxdeyV3pO/h26cWANvHBYPygFUR1QqEecPJRBFrtaIuTQep+BQQSzfazE9HsL3T78m8ztrUEWANvAK7q7uFDfFWn1N7HF/Jv5L8y72KmfgOeNAcSH3iO5rsLLkcLAw6IlUTFbr1tc1zbS4oOTPuR3tfr9p6rSGCJhErg87xt/Zq8jScNZutP578y7+SB3JtYaB3AVzUU/Y0PBM3XUwNsn1QC1b61OS6v8ysKZiaiDM/KOr/bVUBZAqyMHwCet+EZWRoIIUfLN3N3+nYeLr6Kpt6C18f4wFUwXx1s+6TM9rXN8YLObwqkPZjrSud3g+fR4/7tUSPArZsdYkHjqzrNcCsPFV/NPen3cbz8QkRCXGn2RwopzePzHtmag9vPSdlRUKxEW5v3K+3Z0fk5N5r1S856dH431HqrJcDy2A28undKIcBXNYqtgzyQewv3zr+DVOOpeNLAkXbPVUmlpfh6ykf61T5p2hxVv9ocO56w6ET5/LwbHa7aew95tbG1JcAleDl9OLjNkRauNDlX/14+O/9rfDH/ekrtvSY+6N3xuV6nfbLUp7SoUlGdf6/bHDtyp6qiL7hpD1o9kTsr4aCxtSXAJfjZvmZspAEI3yq9lLvSd/DYwitpa69n8cFF7ZNaeivPz7c55nsX+HYcv2V0/qwf1fD0z/EHZuthJMAu4Ja+B4+mrKIRbOfBwk9zT/q9nKrchJKgJ/GBpzTnFl2+lXPxe7kK9LrNURHJm7wL0xOR7JGBesMtJOSbQFIIcBMM7qTGKD6ok2tdxX3Z2/ifzK8w37gWrwdlFVH75ASlXrVPKkHKnTZHtXlrC1FgOz0RBbphLF5wiB5tZTkqBHhFHDd1TXxwpnYjn5l/N1/J/wLVYDe+qiEbjA/Ot0/O9ygtqoHzbY6blDs1o/NTfdf5ibV5Ugnw4jhv7kkDjcM3F1/B3anbeWLxFkKcDZdde0rzjYxnTp/c1HKCFEpIsbLxtGdH588bnV9VcTt+Bz9oCRBhEnhW3A8hpqyiGuziq/mf5zPp93C69n240sZdZ1mFEqi3hSMpnw2nLEWgHZptzTdo2aVlygtO0tIez2KFgsdxI8CNxNzwfml84Kk6meY1/G/m7dyX/WWyrcNR2nQd8YHnaI4XXE4tuBvbT0hJVO5Qqa8v8yNLdP6MKVMOJIlVX/uAZ1oCwHNIIKKy6zanqs/lM6n38rXCz1IPdpiy67ArPwThwbkJGsE664TOn+a4jrTn+TJlFZUop72olCEZcmclPNsSIKEEiHwqKrsOcHl84Ue5K30HT5ZeuqQtcw0SKc1c2bRPricgXm+b49J2xBl/vWXKY217FbuPwdOTbqXO94Nye5Iv5V/PZ+d/jXP1Z+BKY82y687pk123TyoFi9XuTnM8X6bsRmXKxcTp/LXw9LhpGvdQ7QSuGRZrRWXXddKN67h3/le5P3srxdaVq8YHjsBCQ/FQt6dPah21OYbh2jq/rKIZP5NYnb8WrjE+MLYEuALYP2xWc6WJSMiJygu4O/0+vl58DY1w64ptmb6KTp+M2idXXS6imX+huvzsv7RM+Xw7okq6zl8N+40PjC0BDgFbh9FygsZTdVra55HiT3B36g6+U34Ry7VlikAzvNA+uZLuP9/muFzgu7QdcXpimHT+atjKACsAkroCDDU6bZmL7f18Pvcm/jvzTmbq119Wdu2rqH3yWGGFtOjSNselgW/HQgumTLngDpvOT7QPuDG//MgcuOxIG4c2M/XrSTWeytO2PcSzd93HpD9HO/QIcaLTJ1M+1+5uXxwQL9fm2Pn3itllraYGXbA2FgSIezinRs2anbbMb5dfxF3p9/HIwo/T0hP4qo6ndNQ+Oe9f0kQvUaNLO4gIsLRMec6H+lDr/LWwb5wJsHsULXqhLXMbXy+8hrvTt3Os/AJEArY6DR6b98jVTJ2Qo5CFMhRK4MmFdsTpnrcjJhV7xpkA20fZsp2y66gt863cm3kHmeZ1NIMmD6dUJPWDMAp8tYZFt9/tiElErD4QawwQOGpiLExMgABnGjcynXs6T9t+hAV9Hzd9zzTXtYu083VY2HJB5/uME7aMLQGmsiVvrExNCY2QyT6Pr6incnDm03w/n8IrbyMMZdSlTiJ9MNab33B0drxMLYJWgqq3CdJ1mtmnkPL2c/BQkfZ2Ffl/OH4M2DbGEqg1NlZ2ovp+N1NBpcvsCjQHtwQcLR5gX3oR2QF6L8gWojp+PTYj02ZcCQA0R9685hgZJ1/DTZWQahOthKsmPCa0Jtfexrn6JNdKnnZVIbs1atJYZjxWg/o4E6A8ynIHBarcxE2VUAv1zqrHfgf2OhC0QaE5Wd3HFVtKTEibsCAEZVCTGtnFhR0cRheVWOenmF++OHqOH8kdaQV45xbwj2cj51eR/neBw0sahZVoKm2fU9X9KNGmth/CeSGcFnSZUf8WUBxnAuRGUe646TL+sQxOumS2EY+8NwAOusIOuXhSd1TI2eokxdZWHAnPlzzoBoRzQpgSdINR/RqcG2cCpEfG8ZWgFur4x7K45xaQVhCVNBuHDYFtAofcyxsqFdDSimOVqYs93Mz8ugThjKAzErFotD6QxeoDriXA5nW+VFt4qRKqULuQ8VkGV7mCb1aCywwhIen6TubqOzm0ZZG2VhczJISwAFQEtWek4oOxJsAsUGMYewIchTTbOPMV3GwlKmRboYUxBHYp4YAjax7XcbxygKmJCgp9cSa0Ewe0oviAEqi9GtnOMKdNa8YHxloCZYdO7gDOfDmSO6kShHpF59fGb692Zc3BdkSz0NrCmepenJW2YOmURNchnI3iA5pDGx/k4l4B4iZACTgzVDp/sY5/Iot3toA025HcWcXxQmDKEfYoujqsSYnmVGUflWAiygqtlm0S0IsQTAs6yzAW0J0xPjC2BNDAscTrfEch9Tbe6Tz+yTyq3DQBrqz5cp5E2l93bRBNPXQ5UZ5CdfN/deKDvBCcE/Qiw9Q4czRu8ZaEYfpGcnW+IEGIO7vAxLEMTq5mnKs7rREChxxhm6wvVnUkZLq+m1xze5QWXZOknVQShCmJMkbVoZBFsds+CQR4PJFyR8DJVvGPZXBnFyHQdLexzwXn3y5R3n+9iZrosA3FsfIUoZbufbjz/aBq4oO0QCvRRHjcEgC+BeQTYQ4xOr/UwD+RwztTQOrti/L569F2hz2Ft8E13pWQTHMHs/Xd3a0Cy1hVL5j4IC9JjA/yxvZjT4As8ES8jm/kTqONd6aAfyKHKjXOB77rRQDsVcL+LgPf1eKBE5Up6qG7sWOcVPQwYdYQoUSSyiqeIAEZwKTMCV+KT+crCDXuXCnS+dnqunT+sj8SONyDLyxKNKX2BKere9e/ClwaHzRNfDAr6HoiZNGXE6F2E0KAB+LR+YKTqzJxLIs7sxidwuJszisC4IAr7FLSk4+0jmhOV/ex2N6Cs5kjWDtp00pUVhHOS1SJH58H3G8JcAGPAHMD0/mOoCpN/JM5vNN5pNYy+fzNOX8ITAhc5fTuXHpB0wwdTlSm6EnGsBMfFI0sKpgfO1hPmDM2twQwWAQ+13+dr6Iy5TPFqEx5sb5hnb9S4HvIFbZKb0t0HAmZre0m09iJK2HvLN+GMCMEM6bsWg3MIz5nbG4JsAT/1j+dH3mkmyrhH83gZCoXlSn3AiGwU+BgF/U+G+GuRjhWmaKtFT1bX84fqmHKrmcl6s/qf3zwb0lxuiQR4PNAqi86v1Bn4ngGd3oBaQdrli9sFIc9hUN/Pm06EpJvbmO6tmdzscBq8UE5kkVhpq/xQcrY2hLgEiwA9/RO5ytUtYn/3RzeqRxS7Y3OXynw3ecIezeZ9uwmK3Sisp9a4HVXJrHR+KBg4oNiX7zkHmNrS4Bl8PebmkCX6Hz3nNH5hd7q/OV0f9TmKAMwlqYSLGmf7KdXdNoyZwRdoVffDzTwD0lyuKQR4GvAoxvW+Rrc+VJUvpAuRcLc6a9jhizf5tgvuBJytrrnQvtkP5MGCvSSsmu9+bLrR42NLQFW8aePrlvnK0EV6/jHs7hni5e1I/YLGtgqUcHboBqzBGhph+PlKfQgvmQtbcucFsIsm2nL/Cj9VYlDTwCATwIz3ep8qbXwvpvHP5VDVborU+4lW69yBV8GW9PrSki6sYtUvYdp0W48JQSdlyg+WFi3B80Y22IJsHYw/NHVdb4g7QB3eoGJY1mcfO18IdugEAC7FRwY4Ox/6epzojJFSzu9S4t2KYtoQZg2ZRXdl13fmaTgN8kEAPhrltsuo7PLWqZi2hEXTTuixDJwV7sKRTwdHY6EFFtbOVOdxJUBP8HSsusZ05a5etl1wdgUS4DuMAd85DKdv9gw7YhF046oYinoClhfm2PfjCeaU5X9lAO/v1mh1byn05Z5TtC5Fcuu/5qYm9+TSQBZ9foLROZxBKm38E4X8E9mo3ZEJQPT+ctJD4/1tTn2z3imfbKyvz/fBdYZH4Q58/1gkaVp0wzwZwmdaOMlgLTDVS6dkVbwx+5sKdL5ucrAdf5Ks/8hd/1tjv0MiKdre8g1tw8uIF4tPmguacusA8IHgfmkEiDWfYH872RWH1OtP0I7vA2RZ6607cggEQI7Ntjm2Nfn0orjlSn2eNX4H6aTNq2CbsiTskN/hAQjVgJIe003qqPkA8DdSRmww67gkaxktiMh842offLqrcWLd5WLU1toPqAXpZ5kAiQ5BuhoyHuAf417oAJgj4pqfoJEGjJqn2yEA0yLro5/Be7p0sY2C7QGfsMEU7EFvkvTnomcyUSz2N7C6eq+/pZIdIeMsVniMSwEmAbuiFP7X+EIuxSJnP3PSyFCTlf3UWpviSctegF3GJtZAvQQnyCGSkKNaXN0JfH7zyrRNEKH45UDccqgfzC2whKg97idaDu9gc7+h1xhiwzHTuSuhMzWdpFp7IhDCh01NsISoD8oAm9mQOdKddocr3BkqLbh1wjHK1MEWg0yxqwY2xQtAfqLh4D3DOpmh73oXK9h2n7fkZBcc7tpnxxY1PIeYxssAfqPjwEf6ucNAmCfEvYqSXTgu7JhNScr+6mH/iDKJD5kbIIlwODwfuCf+xX4umb2H1YoidonT1b29Tsj9M/GFlgCDFrqwtvow65yg25z7J8U0pyt7u1n++QDxgbaEiAeVICfo4d9piGDb3PsFwRNSyuOl6PTJ3u8nn3NjH1lmMdoFA7czAM/zUab6ZdZVuJoc+xnQJxq7CRV39XLVeBRM+b5YR+fUTlxdh74ceDBzc7+u1XU7BKOyMB0Zv3jlf2mfXLTeNCM9fwojM8oHbk8D/zEZmICAQ7H2ObYz1ig2NrK2erkZleBB8wYz4/K2IzWmeNR7+mr2cDuAwGw3xEmFSMz+19kaNGcrO6nEmw4LfpJM7aFkRqXEbR1BXgj6/hO0GlzPDwE9T4bN7SmHricrGxoV7kPmTGtjN64jCY08FtEKbpyN9r/ygS1OfYzIJ6u7SHX2t6tFCqbMfytEVOFI0+ADu4Efhj49mrOv03gyhEKfFeLcdomLarXTot+24zdnaM8JqNOAIAjwA+ywqd6baSPJ4ys/FkKV0IyjTVPn/yYGbMjoz4e40AAiDbZug14C0uOYgqASRUFvyHjBM3x8hSNy0+fnAPeasYqNw4joRgv/CPwA5iGDYfoUItRS3uuHQssPX3y/Jt/wozNx8fJIcaNAABngTcF8NoDjnxjtzCU1Z69IMHp6l4W21ueUKJ/BniTGRssAcYDn9queBFR8/bcuL28oOfqofsbpfaWmxX6P8fVCcaZAGioAh8GbgL+kBH6wrkKMuZdbxL4sNnfGUuA8cYc8DvA84DfH1EpcNa823PNu85Zs1sCXIoZ4PeMk7yLIWzxWwYPmXd5rnm3GWvmC3DtECyLPPBXwN8ALyMqA/gx4KohIvK9RJmdLzCecb4lQA8QAPebaxJ4KfBaQ4prE/asp42z/yfwRUasaM0SIH4UgE+ba6eJF24BXgI8Ezgw4OeZB54Evgx8jqhJpWTNZAkwCJTMbPsF8+e9wI3A95nreuAp5u+3b/JeFSPJvku08dQT5vo/RqAjyxJgdGKGL5qrg93APhM3HDS/3w9MAROAD2wz/20VaAINojRllqgUIWX0fI4EHjA3ChCttR0Fi7GFTYNaWAJYWFgCWFhYAlhYWAJYWFgCWFhYAlhYWAJYWFgCWFhYAlhYWAJYWFgCWFhYAlhYWAJYWFgCWFhYAlhYWAJYWFgCWFhYAlhYWAJYWFgCWFhYAlhYWAJYWFgCWFhYAlhYWAJYWFgCWFhYAlhYWAJYWFgCWFgMEv8PeXX+f+rHrAwAAAAASUVORK5CYII=" class="on_button" /> <span class="vb">色見本を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAACH1BMVEWZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZkAAACZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZneqSvDAAAAtHRSTlOBpphwtmdqKVVB2RR94qc+0HrNCPdfuugjbmj6V6xT1xLKBRq8d0NzgIhcRkxxqrCXlcXhSpq5ITqRfCyE7yrBZcdPoVFOXvaeo0Jh9C+QOf48RdMOb4qkfmaWvXK3XbEQ1SjtqwkGnyTpJ/wPk9oVoskX3HsDAcauCs+HDdJa51KNGd6JJeqoE9hL4OMeYBiltI7yLTT52wBIbMODH+Rj8MAw9TN4dZnRDDj9nLVUPwfMLvP/y5wVAAAGUUlEQVR42u2c61+URRTH7X61G6ZdtIggtFBJJTOzpDBRUsMEWkAQYRdiQSBBJQJEAgy5bEbr2cVWLsuuFEEL8wcGZtnOzF5mnpln2M/n/F7Aq33O+e6zM3POmTOzAVJcGxAAARAAARAAARAAARAAARAAARAA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" class="on_button" /> <span class="vb">全てのブラシを元に戻します</span></p>

	</div>

	<label for="gallery" class="index">画像一覧</label>
	<input type="checkbox" id="gallery" class="on-off" />
	<div class="explain">

		<p class="midashi">操作</p>

		<p class="title">タッチ</p>
		<p class="explain">画像ビューアを表示します<br />保存モード時はダウンロードを行います</p>

		<p class="title">ロングタッチ</p>
		<p class="explain">メニューを表示します</p>

		<hr />

		<p class="midashi">ツールバー</p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAkFBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9oY44IAAAAMHRSTlMAmf82Zg/SG9sk6jDwQvlO/HiQpQa0CckS4VRpe5a6FdXkJ/ZagQOovQwt7Uhsh664LcKpAAAB9UlEQVR42u3Z6U7CUBRF4UNLKSJDgSoqQhnK4Pz+b6eJMWq8lZZe7qDr+29yVhTYVBEAAAAAAAAAAAAAAP6rRknOBgQlEUAAAQQQQAABBBBAAAEEEEAAAQQQQAABBBBAAAEEEEAAAYaFTbWyAQU/HhoLiFrBCbQic7+CuK3//nZs8o/orKP7/s6Z2ZfBeVfv/d1z0y/kXl/n/f2e+beiQaLv/mRg4820qS+gaefjYKjr/qGtD7SRnvtH9j6SxzruH1vcFOlF/fsvUpur6HJS9/7Jpd1dd1VzFrWubC/T+LrO/dexWFdnFpkeQAXfDo6eRd1QnHDsLLIxgNRujppFyY07X4anxwRMXfo6f1v9/lu3HkjMqt4/c+2RytifAVQwi+ZV7p+nzgVUmkW2B5Batih7/yJz88livCx3/zIWR61KzaLOytmHuxKuD9+/DsVhh2eROwNILT8wi5JcHDf1aACpbX67fyMemHk0gJS2u6L7d1svAiTdq+/fp+KJO+UsmtyJNzLFv6DamXjk5yxydwCp3T98v//hXjzzfRa5PYDUHr/Mov6jeCj/DMjFS08f9z+Jp57f738Wb838GUAFs+glCF62Hge8zSJ/BpBaFAkAANCqcTKGAoKTIYAAAggggAACCCCANQoAAAAAAAAAAAAA+LteAY6PM8FhxCYiAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">ワンタッチで保存するモードに移行します</span></p>

		<hr />

		<p class="title">元レスに移動する</p>
		<p class="explain">添付されたレスに戻ります</p>

		<p class="title">画像を保存する</p>
		<p class="explain">指定のフォルダに画像を保存します</p>

""",
    """
		<p class="title">URLリンクをコピー</p>
		<p class="explain">画像のアドレスをコピーします</p>

		<p class="title">ブラウザーで開く</p>
		<p class="explain">ブラウザーで画像を開きます</p>

		<p class="title">URLを共有</p>
		<p class="explain">アドレスをアプリに渡します</p>

		<p class="title">画像を共有</p>
		<p class="explain">ファイルをアプリに渡します</p>


		<p class="title">Google画像検索</p>
		<p class="explain">類似画像を検索します</p>

		<p class="title">画像詳細検索</p>
		<p class="explain">アドレスを知っている必要があります</p>
	</div>

	<label for="viewer" class="index">画像ビューア</label>
	<input type="checkbox" id="viewer" class="on-off" />
	<div class="explain">

		<p class="midashi">操作</p>

		<p class="title">ダブルタップ</p>
		<p class="explain">縦または横合わせ → 拡大 → リセット</p>

		<p class="title">ダブルタップしたままスワイプ</p>
		<p class="explain">拡大率を調節します</p>

		<p class="title">ピンチ操作</p>
		<p class="explain">拡大率を調節します</p>

		<p class="title">左右にスワイプ</p>
		<p class="explain">前後の投稿に切り替えます</p>

		<p class="title">下に大きくスワイプ</p>
		<p class="explain">画像ビューアを終了します</p>

		<hr />

		<p class="midashi">ツールバー</p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAkFBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9oY44IAAAAMHRSTlMAmf82Zg/SG9sk6jDwQvlO/HiQpQa0CckS4VRpe5a6FdXkJ/ZagQOovQwt7Uhsh664LcKpAAAB9UlEQVR42u3Z6U7CUBRF4UNLKSJDgSoqQhnK4Pz+b6eJMWq8lZZe7qDr+29yVhTYVBEAAAAAAAAAAAAAAP6rRknOBgQlEUAAAQQQQAABBBBAAAEEEEAAAQQQQAABBBBAAAEEEEAAAYaFTbWyAQU/HhoLiFrBCbQic7+CuK3//nZs8o/orKP7/s6Z2ZfBeVfv/d1z0y/kXl/n/f2e+beiQaLv/mRg4820qS+gaefjYKjr/qGtD7SRnvtH9j6SxzruH1vcFOlF/fsvUpur6HJS9/7Jpd1dd1VzFrWubC/T+LrO/dexWFdnFpkeQAXfDo6eRd1QnHDsLLIxgNRujppFyY07X4anxwRMXfo6f1v9/lu3HkjMqt4/c+2RytifAVQwi+ZV7p+nzgVUmkW2B5Batih7/yJz88livCx3/zIWR61KzaLOytmHuxKuD9+/DsVhh2eROwNILT8wi5JcHDf1aACpbX67fyMemHk0gJS2u6L7d1svAiTdq+/fp+KJO+UsmtyJNzLFv6DamXjk5yxydwCp3T98v//hXjzzfRa5PYDUHr/Mov6jeCj/DMjFS08f9z+Jp57f738Wb838GUAFs+glCF62Hge8zSJ/BpBaFAkAANCqcTKGAoKTIYAAAggggAACCCCANQoAAAAAAAAAAAAA+LteAY6PM8FhxCYiAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">指定のフォルダに画像を保存します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABBVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+fZH5/AAAAV3RSTlMADDM8ZlQkaZnD5//Mq4dICRJgpeTAgQZRsfzhIRuQ7cZaJ5z52AMeot518y1C1Y3bbBh+ljDqKnvSS89FqIpyFZ9OtzkPNvbweG+Trr2EP1e6Y7TJXfhCd459AAAFBElEQVR42u2ce1saOxCHd1cKQhaseEFXBW8FKVbwUrQttl7AS6lt9eD5/h/loKfnUjNZdpPsbPI88/5PMr/NZSaTCY5DEARBEARBEARBEARBEARBEARBEARhL643lfmbV17WMtuzuel8gf2GXyzNvLbC+Nny3DwTsLA4Zbj1laXlgIWysrhqrvlr1RqLwPqGmeZvbm2ziLwp140zv7HD4tAsGzb337ZYTPK7Btn/bo/FJ2i7pnz+EpOj45kx+9eZLNv7Btg/U2MKFFOfRpmAKeEfpGv/IVOlk6Znrh8xdQrv0xOgw37Gusdp2X/C9DC/lo79OaaLD7Np2P9x0v5TWJ9+pvhpcoBaScF/he7/C4u9/+/wm+XT+VAFn/HjhxD/6385A37ROw8L+C6wBXwWmnLZF/1mcCIetVoD1/4p4de/Cs1UHF4LJx3uBOrAVtzkJiYsLkUKcpgCbgWbSZR5sC84ed4g7qVr8HKsRtsMvRVYwRGegDnQgK+R9X+Aj2hoIcUm5MKCGMmSWXgPnsMScA71nonTwgAcgwApsm5Aq/A25ioCHfMWjoAh0PW3uI140FfYRjmevb4B8lSD2M3sQ0NwiCGgDEzevkQ7kEdrYqQc7zTt4FloEiGk38/4PbQm50Mhdz5MZQbdyrXkdoHrgzS8cE02OQXlZJK/TeMDme+yTQ1aKcSkx3yf8v7zPIVwIqfzJPIOSBLhx0EKg14HAoqkc0S+1mUHxOU/EhbQ0rrxlRWj2vgccB3uqDSXRXdlV1yHJ0rt8YHhZbICLrgOX2leUp+wAwm129Iin6pOVsAXrkO1M8gWn13BTgip1dBUufausQWotQdcMWMLqNsuYKDUXht7DeQ0xqJwaJVwNPdTc+xyz9cSJSugpzl2afJlONjnGaXbLZfPEJwmK6Ci94sBFz2lhMNp7ghyreLJvvMCHhIW8AfXY0+htTwv4CphAbznqWpNkgVJFxDx8XRX3hd/ZdjR9Pij6byj9tPILfJ3K3ca9yDF85Fk+CW77u6BRH3yd63AZ5M8xl6lc18PJaM+SjW0ntJ1PXBF1pEp94HqpYIzBAEe0HE7fjNgvdE3B4MR0zCJKnnGNM3F2GxA5T5xzzVtyP49nGcF9TdQ/Wq82QtesjKsRwVlqPORqziISJesz/MXrJcZRR+DB7jg8cHB4hGugo5a9iYomO0g1l7m4cK9SJuIWxTUnC3h2e+8FxS9liYvhL6g3E4+JtSUkPpVrZsJX4hZ0ednLdz3lm5TZMheyEw4OBKXK2O/RvHEr95GF/AoNEohpbt/or/K2g97ETDsvdxRVnMLE14RoCtYDn8ldlfN9L2nlMuqt/R2ZyXCOwhsBXDhnspLDmwFZx3bFWS7tivYrdmu4HjFdgWNPdsVzOZtV1BpK1gbGOHRLm5k7V/ud41Q0FiQMr81jt92zVDgPEp4hOLzAc4UBYNh3Fe4M//4EkMUOLtzMZ5GNzP/RavGKHBWpyP+M8aL/8UwR4FzcDvZr12fckd/gxSMT2rV0Fef92WoOMQoBU//LlQEN6WR+B+GDFMwZu1HZnjp+08nhpbv+1snP73QdIV5CmJvYqSAFJACUkAKSAEpIAUJKjgmBaSAFNivoODarSB4dBybFVhmP6fAOvtfKLDQ/t8UBBuOY7MCS+3/V4G19v9SYLH9zwqstn+soGC3/Y7jOgRBEARBEARBEARBEARBEAnyF9q1QVmN58OiAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">画像検索を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAA6lBMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////E22vdAAAATnRSTlMASJPM5P8DVNsMtBXSBsPGjSH57TlmeIfw81GEire6EuH2LTNXY5we6oGoMEX8D95gNpCrKnXVJ+c/e5m9GBs8bMmfCUJyWq6lls8k2E5VhdrSAAADCklEQVR42u3ceVPaQBjH8YdjOQ33LeG+WgvYqoiKJx6tte//7VQq2gBJYJzNJs/M7/vvhnU/jkAgG4kQQgghhBBCCCGEEEIIIYQQQgghhBBCjuTzB4LCyYIBvy/k2PLDEaGiSNiZ5UdjQlWxqAPrj+8Jde3Fpa9fSwiVJTXZgJRQW0ry+tMZxYBMWi7AL1Tnl7r+UFY5ICv17SAn1JeTCci7AMjLBBRcABS4A4oAAAAAAAAAAAAAAAAAAAAAAAAAAAC8VwqXeQMqFGYN2H+dqsoYoC++cA7V2AIi9beLbQ2mgGZrOVk7wxPQ+ZityxJgvGrX8yKgFG7aDdeMV4y0gAcBFdvLOI3VK9f9gecAX16P+mr9BD5Ym/Db0GOA2OIvZDS2Gj78xK9EKSAy+nfYd4sXyB8mUx55CVA+Xh53YjqcMNv4EJ14CFD5OFA3GQ2abz05LXkGsG/YlDPdGM1YXbGueAWgG1/izzZeXrqWsxa9AVg+gd87Xxu+sNmBUfMCoNxaW9VsZdh255LtiakqwMYOvUvju+z0ynZeuxNTRQCTH+MzPIGvt0zcdRugm+3q+f+p8WbrzD13AcsPWWtpt8vhu+0za0k3Ac2W+eHpt/eowHyHqa8GLgIst9h2FqOD/k5znw1dA1StH3AhxPB+x8kf3ALUbLblzSfiYefZj9wBjOt2jzju7T67+Ymp0wCrJ/BnMj0xdRrwKHP+inqA5L3NRdWAmeTbLExOTB0FTKXv768HlQISJD2dO+CJO+And8Av7oCUUsBzW3p3uE4MAAAAAACAMfZ3MbG/jyzHHCD3XkoXAHLvZlUPyPxmDpD8rYdywK3GG/Ai+1sPxQBd+v+VUAqIdOR/YFUGmDr0v1UKal6tncsKUCXegHPiDTgh3oAb4g3IE2vA8JBYA4aPxBqQ+UOsAU0fsQY074k1oHxNrAGlHLEGZNvEGjA9INaA4CmxBjTSxBow7hNrwMsVsQZMLok14DlOrAHJEeP1U14kopzXT7nZnHjHff0IIYQQQgghhBBCCCG00l+tGupGxZrA1wAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">添付されたレスに戻ります</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAflBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+yfIzaAAAAKnRSTlMAgf9+e3hyb2xpY2D8XVdU+VFOS0j2RUI/PPM58DYz7TDqLSrnJ+QkIeHMA/ldAAABl0lEQVR42u3Z2y4DYRhG4c9rr0PHZlBqU9ri/m9QHAhCT5rOxErWcwXvOvvy/1WSJEmSJEmSJEmSJEnalC36/tD3h74/9P2h7w99f+j7Q98f+v7Q94e+P/T9oe8PfX/o+0PfH/r+0PeHvj/0/aHvD31/6PtD3x/6/tD3h75/LfT9oe8PfX/o+0PfH/r+0PeHvj/0/aHvD31/6PtD3x/6/tD3h74/9P2h7w99f+j7Q98f+v7Q94e+34DVtukBtUMPqF16wCAF/Z4Se/SAAQr6Pqf36QF1QA+oQ3pAzwVDPKsc0QN6LRjmaXFED6hRAw+o4wYe0FvBcF9MJw08oKeCIb9Zxw08oMYtPKBOW3hADwUDB9RZCw+o8xYeUBftPxqzXkEHD6jLDh6wsgATUFcdPKCuO3hATTp4QE1u4AF/FbAC6nYKD/hdQAuouyk8oO6n8IC6f4AH/CwgBtTjDB7wvYAZUE8zeMBXATWgnufwgM8CbkAt5vCAWizhAfWyhAd8FLAD6vWt6AUlSZIkSZIkSZIkSZKkjXkHXUgwt9W5CbMAAAAASUVORK5CYII=" class="toolbar_button" /> <span class="vb">前の画像を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAflBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+yfIzaAAAAKnRSTlMAIeH/5CQn5yrqLTDtM/A2OfM89j9CRUj5S05RVPxXXWBjaWxvcnh7foG0iZ+sAAABmElEQVR42u3WWU4cQQAE0TRpNgPDMmCGfV/6/hcECcm2jPhB0w0hRZwgn1RV3YmZmZmZmZmZmZmZmZmZmZmZLa0fdMAKXdCfq3AAXdBXwRoc0PU1OKDrG3AAWfAG6OYvOIAr6B/BFhxAFfwFdHsHDmAK/gV0tgMHdLYLB3S2BwfwBP8Dun8AB9AE7wGdH8ABnR/CAZ0fwQGd/4YDQIJ+JDiGA7o4/kZjPtXiBA7o4hQO6OIMDphc0OULzuGAXpzDAb24hAMmFXQcwRUcMKGgYwmu4YD2mg7oDR0wjWBMQG/pgCkE4wJ6Rwf0ng7oAx0wtmB8QB/pgHEFUwD6RAf0mQ7oIOBrAQP8CA3wSzzAn9EB/iEb4L8SI+8PfX/o+0PfH/r+0PeHvj/0/aHvD31/6PtD3x/6/tD3h74/9P2h7w99f+j7l4xm7A99f+j7Q98f+v7Q94e+P/T9oe8PfX/o+0PfH/r+0PeHvj/0/aHvD31/6PtD3x/6/tD3h74/9P2h7w99f+j7zczMzMzMzMzMzMzMzMzMzL5TLxqOTHKtYNbyAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">次の画像を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAABlBMVEUAAAD///+l2Z/dAAAAAXRSTlMAQObYZgAAAHpJREFUeNrt2qENAAAIBDHYf2k2IAgQJK3GnHtBBAAAAAAAAAAAAADLsnVxJUCAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAADgLVtIgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgADvNgAAAAAAAAAAAMBUAUJ0GgXKnlttAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">画像一覧を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAA51BMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////bMJBQAAAATXRSTlMAHkJmgZkhYKXM/P+EG1qi4TON2xh40le6A9gG3uQtxgyQUedUCSfP85axEsMPvZOfwDkwbGPqcoc/FSTtijw2bypIe/n+/ft1S7ddqwmyZWgAAASFSURBVHja7Z1rVyIxDIbrjsMgBcRBR1RG8YI4XvGO62WFXV1X/f+/Z1kvxwN6lEnSZno2zyc+ODXvGdomaRqUEgRBEARBEARBEARBEARBEIQXxr55njfu/2O8/+lbzh3Tc8F4fqKghylM5ItB5mWUypMV/RmVyXIps9ZPhVU9CtVwKoPWT89EenSimelsmT9b02mpzWbG+rlgXkOYD+ayYP6CV9dQ6t4Cu/1BrDHEAa/5i0say9Iin/mN5YLGU1huMNm/sqppWF3jML/pazr8pnX711uakta6ZfvLG5qWjbLVrSvR9CT2trXmpjbBpq2JsLWtzbC9ZcfvjLQpIhs+aq6izVExH7KNxdok8ZjpmLGqzVI1G3HuRNo00Y7J9X9Xm2fXYIywp22wZy7y1XYwFS2vb1gSsGHGs1toaVu0jEwDX9vDN2D/mrYJfYzWAO3A9bbvtyFpl5g8Tl4GWLF/8PzswX76Z5eJ7T9Mn38oHL09fpT68cIhrQBACDAQI5bTBwek9h+ntz8cHCFMPcAxZQyZfgafDM3CxknqbBFhhHma/gV0hsfopB7ilG4JPUkv4Gx4kDP0S4RzBFhC30W339OPcUQVBUCi4PfeDCBCJsoUgbzod29gi8+vBoVhBHOgH5yR2H8O8sYIVqE+5xQCQP9ZXwzvAxeQUS4pBAATWUP/+xKW6CKw/4rEpYdGE1eMmQicN0qXoUCkQl/jgR/74CHw36FFXKKwnSRtVDYSfQh7rXlBe3SbzAI2sQIumAVcYLNxmhtkli5gF4CsBgnZBYQ4ATV2ATWcgJhdQIzLSGt+UJnqbgYEdDECeph3v/J2XFf6+eujP7kZZZwe0ypaOBgYaQZqP24dPYULaH2dVbk17w0V4QK2v8qq3PwebaAiRkBiUMCoMU6SUQE3tzYE5A2+gVHJiwCXBSQZEJD81wKKGRBQZNqJyQScMvlCZAICJm+UTADKG+1mQAAqHmhmQADuuDhmF4CLiRFZCSoByKxEyC4AmRcK2AUgM3NddgFdnAB4dppIADY7DT8fIBKAPh+4YxYww3ZGRiQAf1GxwiqA4KQ7YRWQ4AWcsQo4Iyh3ihkFxBQlTyGjgJDAfuhBJYkAmosEu2wC9hlr5kgEENXMgaoWKQRQVS3CCj4IBFwT2Q+q3CUQQFe5C8pv4QXckdkPqV7HC1ilewGg+wNoAZ6iJP0NjlXkdkh7gwNyh2YwGp9M+TT1HRpA9WUh/DP1yuw9gx89tJTarVuhv0fm/E0+9+9SqmZkzf7ITIsM1+8TK/VgScCDMoXv7gR4KaFzvauB830llCrVDdtfN91LMme4t4r57jDrJhXENlpt5cx9i+p2GqqWTM3kyFYv1ca9EftrDWWLBRM7mm+186XjfeaU+53+3O+1qJzvdqnc7zeqnO/4+lQN4nbPXeV+12PlfN/p51Mopzt/P+F47/UnRu1+X89k9/sXT7uc/3xVivMZ/v2B14An6DxOfGD7xGMnOFfOcN7zPK/z9Bscnf6nnkOmC4IgCIIgCIIgCIIgCIIgCMJX/AWPjmI5wDwN6gAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">レス文やExif情報を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAilBMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////6DghSAAAALnRSTlMAFVqBmT/A/xK0LeEb5APJaQnnpdXz+Xt4ETPM7mZEd1Wq3YhyGL0h/E5vnGy3WesjJgAAAshJREFUeNrtnOty4jAMRoG6VS+h9EZaO05CoBfo5f1fr8sWhgLt7NaxHKnznf+e0RmILWks93oAAAAAAAAAAAAAAAAAAAAAACCL/uDAdMTBoN86/MMj6pSjw1bhH59Q55wch8d/ekYCODsNjT8bkgiGWVj85yMSwug8SOCCxHAREv/llRyBq8sAgQEJYhAgcC1J4DpA4EaSwE2AwFiSwDhAgETRViC/zdKmkNltHlXgLn0WfBdVoJ9eoB9VoItCRIaAdcoFCm9VCxiiwioWKJdrjV6B6mNxrVXA+dXqiU4BW6xXe6dSoNksn1qFArPP6wt9AuV2Nmm0CbjdfLjUJWD9XkZfaRLYbEAbfrwVdSlgviqqfppTdChQf10WNmoEqm+wmrbR31LQQAACEIAABCAAAQj8SoG6ihlsVScWsIbiCpCxKQWWJWNkgf2CjVHgb88wtsBe0cwnUIZ1Gf4lsNt6YROYEZcAzRII2Ib4BKix3AKuIE4BKhyvQOWJV4B8xSlQtmkV/p/A5lNmEDCUQmDdyY4usNPw5BNYnWnRBZxPJfBxpMX/C23/BHwCq6SC4RuwJoXAOq1j2UZrfoGa9yCbeF4BP+FOJdyUU2Dq+JO59afMIbBVE/Cl04ZLwKQqaEoegTJdSbnM6WIL+CplUf8nq44sULi0bRXbxBVobPLGlo0pYNGZgwAEIAABCEAAAhCQIqD+ypn6S3/qr13qv/iq/uqx/svf+q/f6x+A0D+Con8ISP0Ylv5BOP2jiPqHQfWP4+ofiJZS0Kh/FED9swz5/UPa8B/u4z6MofFpklxS/HmAwFCSwDBA4FGSwGPrXaBjQnbBp7mc+OdPgYmYFMIS2kzMI1XjwNeZFlIEFqHH4bOM+J/DD/SFgH/ReNEmJcleOt6L5i9tXyfLXt+KrqIv3l6zHgAAAAAAAAAAAAAAAAAAAAAAiOIdiFPTR92yA+oAAAAASUVORK5CYII=" class="toolbar_button" /> <span class="vb">画面モードを変更します</span></p>

		<hr />

		<p class="midashi">上部メニュー</p>

		<p class="title">表示オプション</p>
		<p class="explain">先読み条件などを変更します</p>

		<p class="title">ツールバー編集</p>
		<p class="explain">チェックボックスで表示有無を設定します<br />つまみをドラッグして並び替えることができます</p>

		<hr />

		<p class="midashi">下部メニュー</p>

		<p class="title">URLをコピー</p>
		<p class="explain">画像のアドレスをコピーします</p>

		<p class="title">ブラウザーで開く</p>
		<p class="explain">ブラウザーで画像を開きます</p>

		<p class="title">URLを共有</p>
		<p class="explain">アドレスをアプリに渡します</p>

		<p class="title">画像を共有</p>
		<p class="explain">ファイルをアプリに渡します</p>

	</div>

	<label for="drawer" class="index">ドロワー</label>
	<input type="checkbox" id="drawer" class="on-off" />
	<div class="explain">

		<p class="title">表示方法</p>
		<p class="explain">左上のアイコンまたは画面の左端からスワイプして表示する事ができます</p>

		<hr />

		<p class="midashi">ツールバー</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAElBMVEX///////////////////////+65XQCAAAABnRSTlMA/6WZZkKHwEbIAAAApUlEQVR42u3b2w2AQAhFQXz137IdbDDe1RjnNEAGvqmSJEmSJEmSJEmSJElSryXSCgAAAAAAAAAAkG4bNQPQGRjb7gxAZyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAjw+WfQ21e6CngjAAAAAAAAAIA/A/ZIR0mSJEmSJEmSJEmSJD3TCVJJMPLRjo8EAAAAAElFTkSuQmCC" class="toolbar_button" /> <span class="vb">閲覧中のスレッドを表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAABEVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////+MM7EfAAAAW3RSTlMABkISwPkt3v/wJEjz2xVs/L0Dk5wMt3ge0k425/YwYOQbh8kJqxjtP+EzZjxXe5/MmbohtKJRsep+2A9UroTPOSeKdUWl1ZAqS2/DWsaWco1daYFjqE3rStAC53rtLAAACAhJREFUeNrtXPlXGkkQboJIA3IqhyAognJ4GzQqHnhGkAgxca///w/ZkGw29DFH91QN7tv+fsh7eWpNfT1T1VXV1UWIgYGBgYGBgYGBgYGBgYGBgYHB/wqBd2Ci3gVmoH9wLgTF4F1oLui7/vNhSiNREFHRCKXheZ/1jy3Qb4gnAEQl4hNRCzFf9U+m6HekvX+8gfQPUamkf+pnFulPLHkWtvSvrMWMT/pnc/QX8h6F5adk5bK+6F9YnnomTRU9CSumpoUtF3zQv7RCGZQrHoRVyqywlRK6/qvcIyldq2oLq67xwsqryPqvUxE1XePL1CTS1jHVr29InhjSX7TVkETeRh1N/03ZijWaHiQ2G7I3uokVPbQkT4t7CyeicYnMFk5c8SN64JDzGoUFcxKpKHFFMiV5EsDmObWtT+0u4HFFYEn9MYl2u13c2v72b0B9aZZgU4TgnMqLbu/s7u2z+8Xa3EEyFlX5OEFTBAVTa+4eynzjPxvtxtZ7v9yDjrMLJjsL1Amto2LGBwetvt0Ejj+kqDucLJawt0in6IGe8h/BQYOq4Czf5SScUpy4Qro05XPOai+oMiKXXD56XsaIKwpXMltkv4BejmqhvFi1DdS/48pbiiCVyaYd12mqjdBN1jpVgkgRpG+VSfzq6yHqBa1zy2TV6nvVzFfl0UOxRb0iF3WKK7Tz7p5T9BD8QAFQvnWMK3qaDETP1ribJtiiMOhMRw13DUef7R73drt7PkWh0Nq2i1vuPcSgNcv4KtihgEjlrSPHmpe4NBGekrQ3JakQt9foofNxEkhPHP1ju90fHDyt2P/B3pRvCOxN/SDsrf46H5H6gnk7dfbXY7ISW3V4ZGc0nbrU/0W8ppf9lMQbNyOWehyu2sXx70/Dln/5KSjZgVJ976mkuB8ely10iOedi1y9Zyv2D1UxBoBILZ8nhjQdPfQtNt/loTuB3V2LyJU5bihMHMgzSDE9zcaEJfn6144V6tsjOYVanY2D0zAF9+5g+n+PJ1JPPlSUOZa+RvbAZNBFqK6I9dgJxuqV/esnmaB7goy6LOJt6cUqt7KXcINM4EjyzA+6BZCezKfiHvZ9liQlIw/WJQlnG5inNI+iA1rwdto9Fhk84Z311R/ECol1wjec+jVrmSM/zUBMl1o21TN3BMhn0ZS3kfSvpJSOx1wSIDuC2Cukj0hw3A3bSNEtAfIivIIRiv7Cc8r2BQ/XBMTa30IVQf+gEL44HHG7J0CW/NiQF1WdhQIBcX/vgeu/WVZ11woEyDVflj8EJzBW3jBVCIhbfBvaAvglcg6flQgIFZwLYAJf+IIgASZQ5VfoEVT/LJfHhqLQBMgA1RG9aJycKBLIcCcRoQQkgX0uhMvCEyB3iNtxRaferUqAcCeVZ4AEuL0+kkUhcMwtUwWOAJf6fSEoBMgyVuvTvJZ5qRMYss8JgxHIa+0x6gTqXLx4jZQIHGMR4APGFyD9u2zK1AigEdjmKu5ABGKsWLcFVw0ChD1AaACllpcsgT4igT32UUC9c+xRfLmOSIDzQ19hCLDl6DRBJFDlDtVB9K/q7GKaBAhbOnvCsOEYKgE2vW9ghNJVVAK37MNAGv9uNBdFi0ARIZ57ZQ+BcQlEtTZ9FS/6iksgw5Z6tyAIsKneR1wCXOS+C0FgRbP1RY8Am7yOIQhENCNEPQKHWnGXLdiCzSoyAbaLZwmCgGpFTkbgs6bPu38rBGitNDsCEJ/QRJeCxie0N0sj/u137kDnsj4bI9Z1o3/8qdMRguBGdTey6r2kn8bRFBA2Mt1Qgvw1kvRmOZgCRiihG8x9Q/BZ7C1d2K37HMzdeMkxHnNqzVEY4bRuQvMzoXtQMQWMhEY3pfyJgMwUljb9Syl1k3otU8BI6klDr6yiYwooZRXdwhb3IZ5JWpsq/hS2NEuLrkwh4UdpUbO468YUGkG74m4EqLgb1CuvuzGFW/bnSOV1zQMO+dtk7h3EubVAOuDQPGJyYQqcOfFHTGA309ugZ+i/TKFj74PgDvk429LZy2SmkOIP8dCOWfUOuu3Qj0vcWRHvoFur1cDJFHgXyrcaxAkg+GYPgOEbQf6+Nmazh1a7jSJw2210Gp48rhFw5yXfctaB1h+55Uxs+tsBJoDd9Ce0XZ7AdgfvYLddajS+qsCHxlex9RjQE9WvfLhCIFgBLYLJFpq/OwQBecX2e/cQbo4jeOnJ7i9ktScwru6FUvx9UpIvUU8T2n7hWEg041jjR58FBmHv73ooXgJqIulPsmKVcMWrvx74ehtRdhHO23JJRuc8Yc6v/Sq5ijjQF9e98PkqosVlUN1rv23ZZdAiqv4kK6kQ0nBPS9ZoFtdxSVR6oftZ/SVUPskEveJPfZVfqT9RHIfV/ZhyvpKOhJh8qEFN4eQjOzqRy+gSP3BsMRVm32XN0d1YBkycW011esg7T9osLVoNxjgrEL/QtJ6HlxvafQaF0zPLv6z5Ob68Yjcc5tNlTFY32tzZC9uNqqoTP/HeYTzP8sV4q9RuT2qQhXb77uVgzmEc1JHfA/ATh6ADknaJ/wAcUbW2TWaB7TWoIWEJMhskQMa0hfJkdjj3PqntMEpmieyNt1GFaztk1nic8/D1jLPkDaCpOe+SH9c5Q2gNTL1JkDeE+YOIkvriyNqZI3DuemhwY7FE3iQSg1zZ2e+8FjPk7SLTPE1bv4iTjWSUvH0E2sNTYXT500Gyf03+W3A7PN7AwMDAwMDAwMDAwMDAwMDAwOB/iL8BxNdthX2cIrUAAAAASUVORK5CYII=" class="toolbar_button" /> <span class="vb">履歴を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAw1BMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9ZxT1WAAAAQXRSTlMAGANjt/z/P9hd+UUM6oTnLU5maQ9IgaXM837PtCq9kyTVHgbJ4XLSnwla9odvnMbwJ1E89EJgbHixlqgSFTPkIfLzaHAAAAK3SURBVHja7d2LTupQEIVhBDtIERAQ2yK2YvECKOIdQdH3f6pzzYlHIFGYmd1J1vcE88e26d6lNZcDAAAAAAAAAADgt7VlePh8Ydsj8rYLeZvzF3for52ixflLPv3jl+zNX/boA69sbf7dCv2nsmssoEqfVI1dgGqfA2q2LkV7tGDPVEB9MaBuKqCxGNAwFdBcDGgiAAEIQIC8/dZBEIYRLRWFYXDQ2s/w6qt9SF9w2M7mKq3VoS/rtDI3/lFM3xIfZevuP/Hom7wkQyuE4y6toXucmVvPiNYSZeQGtUFraxifPxMFBdpIwfnulb9ZgO94z+ukQhuqnLicPw1pY2HqMKBNDNoON098jgDf2YZLvkcseq72vErExNHedXrKFXDq5jw+IzZnTv4ANb6AWmr5DHB1FpxzBpzrz18mVvqPoC54Ay605+9HvAFRXzlgQMwGygEd7oCO8hE05A4Y6h5DLWKnu1l3yR9wqRrQ4w/oqZ4CPn+Ar3kSFEmA5v7ElUTAlWLASCJgpBhwLRFwrRgwlggYKz7PIBF6TzxuZAJurC5m9Bc1tzIBtzbX8y5W9k2ZgCYCEIAABCAAAQhAAAIQgAAEIAABCEAAAhCAAAQgAAEIQAACEIAABCx1dy8TcH+nM//DkIQMHzTmr/skxld4u3IgOP/PAvEf8D56JMp7lJ3/icQ9Sc4/IQUTufmrpELsY1AJKUlExs+PSM1I4N2+NCBFAft7WekzqXpmLujHpCzm/S34lNRNWQMC/YAAAQhAAAIAAExa+Y7T2EjAym+edY0EzFYFzIwENN3vn2/mZcXuo/9i5SyeaqyxJL0u/ehE9GrnQrr043mmPjw9tXwA/d5GWriUztKcLRO1LWcp8w97YfHc5D1RMXn7Nf1bYvJ/iPzxPp+/4+4WAAAAAAAAAAAAACBjfgBEt/JJbRCjzwAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">巡回検索の結果を表示します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAAaVBMVEX///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9tAe1xAAAAI3RSTlMAMJk2gf9aq1T5Evxu+/4BDwIDjAQFBgcIYQmpZ4j9VtacnwXXhN0AAAH6SURBVHja7djZbsIwFEXR2wETGhra0nlu//8jOzETX4dGin2kvR4RSGejQAxmAAAAAAAAAAAAAAAAAAAAAAD4jo4TTkovOB35gokXlB+QKBAI8AsUAtwCiQCvQCPAKRAJiBeoBEQLZAJiBToBkQKhABuLB1QT7YD2/ToBkf0yAbH9KgHR/SIB8f0aAc7+DAFnfffX46wB0/Npz/3V1j156IBpM2qmPfdvnSrC4PtHhxW07d8sCMPvP6igff9GQciw/4CC2P51Qcixv3NBfP+qIGTZ37HA278sGC4gNFtrmtBz/6JgsIC9u+mk6rn/ryDk2p8sSO//LQjZ9icKuuz/KQj59rsF3fZ/F4SM+50Cb/9s65kXeU/DkQJv/2VRp/n2Am//Ve799bxOFXj7r7Pvr6xKFHj7bwrYb4kCb/9tEfv9Am//XSH7vQKN/fEC9/qZlbM/VrDzaLHvf6zA239f2P79ggd3/2Nx+/cKzp/E9u8VjNT2uwUS+52Cwj+/yYKivz87FBR2fk78dq/9/Vb4/pYCsf1mzzv/eKntN3sR3282X7/iNfP++n9X81x8/6pAdv+iQHi/2Wmjvd/s7V17f27sZz/7dffbh/h+s0/x/ZsFE8n96wLV/csC3f1/Bcr7fwq095sdi+8HAAAAAAAAAAAAAAAAAACAtC+GHjhk47LHNgAAAABJRU5ErkJggg==" class="toolbar_button" /> <span class="vb">更新の確認を起動します</span></p>

		<p class="explain"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAACjlBMVEX////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////2vGPuAAAA2nRSTlMAA2YnMP+KbMOo9gnhOR51V7GT6s8M/GBFnH7YuhLzTgEOSgYEJCALVc2LVD94gRxcyfnFmgoHBYDI+3kPW8v6xEAlAho9l/7o+NKUfSEXdrXrX8rpURVvtJ+skO2I410qmfAbcM4NqlAYPH9Y98ypLreNu/LnT1LBU/31HWQrwky97kGDGYwR216dFqs45nMTxvGv0d5WoS3crh8mv5YzQjZ7aeSlhoJLSKKEh8By9C+nCHFNhUnlKXpoLImY1G3W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" class="toolbar_button" /> <span class="vb">設定画面を表示します</span></p>

		<hr />

		<p class="midashi">閲覧中のスレッド</p>

		<p class="title">表示順</p>
		<p class="explain">更新日時順に並んでいます</p>

		<p class="title">右にスワイプ</p>
		<p class="explain">スレッドを削除します</p>

		<p class="title">タッチ</p>
		<p class="explain">選択したスレッドを表示します</p>

		<p class="title">ロングタッチ</p>
		<p class="explain">削除メニューを表示します</p>

		<hr />

		<p class="title">お気に入り</p>
		<p class="explain">削除から保護します</p>

		<p class="title">削除する</p>
		<p class="explain">選択したスレッドを削除します</p>

		<p class="title">下のスレを全て削除する</p>
		<p class="explain">下に続くスレッドを削除します</p>

		<p class="title">他のスレを全て削除する</p>
		<p class="explain">選択したスレッド以外を削除します</p>

		<p class="title">落ちたスレを削除</p>
		<p class="explain">落ちたスレッドだけ削除します</p>

		<p class="title">全て削除する</p>
		<p class="explain">全ての履歴を削除します</p>

		<p class="midashi">履歴</p>

		<p class="title">操作</p>
		<p class="explain">閲覧中のスレッドと同じです</p>

		<p class="midashi">巡回結果</p>

		<p class="title">導入方法</p>
		<p class="explain">にじろぐ(仮) バージョン1.0.5以上が必要です</p>

		<p class="title">操作</p>
		<p class="explain">閲覧中のスレッドと同じです</p>

	</div>

	<label for="background" class="index">バックグラウンド</label>
	<input type="checkbox" id="background" class="on-off" />
	<div class="explain">

		<p class="midashi">概要</p>

		<p class="title">スケジュール</p>
		<p class="explain">一定間隔で補助的な機能を実行します<br />常に利用する場合は通信量などに十分注意してください</p>

		<p class="midashi">スレッド関連</p>

		<p class="title">スレッドの生存確認</p>
		<p class="explain">しばらく更新されていないスレッドを確認してスレ一覧に反映させます<br />落ちたスレを明確にしておけば管理や更新の確認に役立ちます</p>

		<p class="title">スレッドの更新確認</p>
		<p class="explain">カタログからレス数を取得して更新分をスレ一覧やツールバーに反映させます<br />ツールバーにタブ一覧ボタンがあれば更新レスに従ってバッジが表示されます</p>

	</div>

	<label for="network" class="index">ネットワーク</label>
	<input type="checkbox" id="network" class="on-off" />
	<div class="explain">

		<p class="midashi">キャッシュサーバー</p>

		<p class="title">概要</p>

		<p class="explain">サーバーからスレッド内容を取得します<br />通信量や読み込み速度が改善されます<br />また落ちたスレッドを取得する事も可能です</p>

		<p class="title">仕様</p>

		<p class="explain">本来のHTMLからタグを削除したり内容をコンパクトにした解析済みのデータを取得します<br />サーバーが事前に取得したデータなので実際のスレッドと少しだけ遅延があります<br />※遅延の間隔は日々変更されます</p>

		<p class="title">過去ログ検索</p>
		<p class="explain">読む前に落ちてしまったカタログ上のスレッドや目を離した隙に落ちてしまったスレッドの続きを後から取得して読む事が可能です<br >ただし落ちたスレッドの過去ログは短い期間しか保持しません</p>

		<p class="title">注意点</p>
		<p class="explain">サーバーが重い場合は通信部分で時間が掛かってしまう可能性があります<br />負荷軽減の為に大きな板のレスの多いスレッドのみ対応しています<br />※判定する閾値は日々変更されます<br /><br />これらの機能は予告無く仕様の変更または終了する場合がありますのでご了承下さい</p>

	</div>


	<label for="setting" class="index">設定画面</label>
	<input type="checkbox" id="setting" class="on-off" />
	<div class="explain">

		<p class="midashi">デザイン</p>

		<p class="title">カラーテーマ</p>
		<p class="explain">見た目を変更します</p>

		<p class="title">ナビゲーションバー背景色</p>
		<p class="explain">カラーテーマを適用します</p>


		<p class="title">ローディング</p>
		<p class="explain">アイコンを変更します</p>

		<p class="title">カスタムフォント</p>
		<p class="explain">フォントを変更します<br />ttfまたはotf形式に対応しています</p>

		<hr />

		<p class="midashi">コントロール</p>

		<p class="title">ボリュームキー</p>
		<p class="explain">スクロールなどに利用する事ができます</p>

		<p class="title">カタログのロングタップ</p>
		<p class="explain">任意の動作または選択メニューを表示します</p>

		<p class="title">レスをタッチしてドロワー</p>
		<p class="explain">手早くドロワーを表示する事ができます</p>

		<p class="title">スレッドを閉じたら前画面に戻る</p>
		<p class="explain">タブを更新せずに前の画面に戻ります</p>

		<p class="title">タブ一覧のロングタップ</p>
		<p class="explain">任意の動作または選択メニューを表示します</p>

		<p class="title">送信時の確認</p>
		<p class="explain">確認ダイアログを表示します</p>

		<p class="title">下にスワイプして閉じる</p>
		<p class="explain">画像ビューアを終了します</p>

		<hr />

		<p class="midashi">ストレージ</p>

		<p class="title">ダウンロード・一時ファイル</p>
		<p class="explain">スレッドや画像の保存先を指定します<br />手書き画像やリサイズ画像にも利用します</p>

		<p class="title">ファイル操作を無効にする</p>
		<p class="explain">保存先の存在確認や重複の確認<br />ファイル移動をスキップして<br />単純なダウンロードを行います</p>

		<p class="title">画像キャッシュ上限</p>
		<p class="explain">指定サイズを超えたら終了時にクリアーします</p>

		<p class="title">画像キャッシュクリア</p>
		<p class="explain">キャッシュを削除します</p>

		<p class="title">スレッドキャッシュ上限</p>
		<p class="explain">指定サイズを超えたら終了時にクリアーします</p>

		<p class="title">スレッドキャッシュクリア</p>
		<p class="explain">キャッシュを削除します</p>

		<p class="title">その他のクリア</p>
		<p class="explain">一時ファイル等を削除します</p>

		<hr />

		<p class="midashi">バックアップ</p>

		<p class="title">基本的な設定</p>
		<p class="explain">カスタマイズ、板一覧、ツールバーを取り扱います</p>

		<p class="title">監視･ＮＧワード</p>
		<p class="explain">各種キーワードを取り扱います<br />登録数が多いほど復元に時間が掛かります</p>

		<hr />

		<p class="midashi">その他</p>

		<p class="title">更新情報</p>
		<p class="explain">これまでのアップデートを表示します</p>

		<p class="title">ライセンス</p>
		<p class="explain">利用中のライブラリを表示します</p>

		<p class="title">Twitter</p>
		<p class="explain">開発者のTwitterを表示します</p>

		<p class="title">バージョン</p>
		<p class="explain">現在のアプリ情報を表示します</p>

	</div>

	<label for="question" class="index">よくある質問</label>
	<input type="checkbox" id="question" class="on-off" />
	<div class="explain">

		<p class="title">画像が薄くなったり白っぽい</p>
		<p class="explain">プライバシーモードが有効になっています<br />メニューからプライバシーを選択して下さい</p>

		<p class="title">新着レスが直ぐに取得できない</p>
		<p class="explain">サーバー機能を有効にしている場合はキャッシュされたデータを表示しています<br />読み上げ機能や回転の速いスレをリアルタイムで閲覧したい場合は無効にして下さい</p>

	</div>

</div>
</body>
</html>
""",
).joinToString("")
