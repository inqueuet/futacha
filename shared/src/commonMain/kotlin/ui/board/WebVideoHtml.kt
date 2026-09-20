package com.valoser.futacha.shared.ui.board

import kotlinx.serialization.json.JsonPrimitive

internal fun buildEmbeddedVideoHtml(videoUrl: String, mimeType: String? = null): String {
    val sanitizedUrl = sanitizeVideoHtmlUrl(videoUrl).replace("&", "&amp;")
    val type = mimeType ?: if (videoUrl.substringBefore('?').substringBefore('#').endsWith(".webm", true)) "video/webm" else ""
    val typeJson = JsonPrimitive(type).toString().replace("<", "\\u003c")
    return """
        <html><head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <style>body,html{margin:0;padding:0;background:black;height:100%}video{width:100%;height:100%;object-fit:contain;background:black}</style>
        </head><body>
        <video controls playsinline preload="metadata" src="$sanitizedUrl"></video>
        <script>
        (function(){
            var v = document.querySelector('video');
            var terminal = false, metadata = false, attempted = false, reportedPlaying = false;
            var deadline = Date.now() + $WEB_VIDEO_LOAD_TIMEOUT_MS, lastTime = 0;
            function post(value){
                try { window.webkit.messageHandlers.futachaVideoState.postMessage(value); } catch(e) {}
            }
            if (!v) { post('error:missing_video:video element was not created'); return; }
            function diagnostic(event, extra){
                var mediaError = v.error;
                post('diagnostic:' + JSON.stringify({event:event, engine:'WKWebView',
                    errorCode:mediaError ? mediaError.code : null,
                    errorMessage:mediaError ? mediaError.message : null,
                    networkState:v.networkState, readyState:v.readyState,
                    currentTime:v.currentTime, paused:v.paused, details:extra || null}));
            }
            function fail(code, message){
                if (terminal) return;
                diagnostic('failed', {code:code, message:message});
                terminal = true; clearInterval(timer);
                v.pause(); v.removeAttribute('src'); v.load();
                post('controls_visible'); post('error:' + code + ':' + message);
            }
            window.addEventListener('error', function(event){ fail('javascript', String(event.message || '動画処理に失敗しました')); });
            function showControls(){ if (!terminal) { v.controls = true; post('controls_visible'); } }
            function hideControls(){ if (!terminal) { v.controls = false; post('controls_hidden'); } }
            function buffering(){
                if (terminal || !attempted || v.paused) return;
                if (!deadline) deadline = Date.now() + $WEB_VIDEO_STALL_TIMEOUT_MS;
                reportedPlaying = false;
                post('buffering');
            }
            function observeProgress(completed){
                if (terminal || document.hidden) return;
                // Only decoded time advancement resets a running playback deadline.
                // Repeated waiting/stalled/canplay events cannot keep a black screen alive.
                var quality = v.getVideoPlaybackQuality ? v.getVideoPlaybackQuality() : null;
                if (attempted && (!v.paused || completed) && (!quality || quality.totalVideoFrames > 0) && v.currentTime > lastTime + 0.01) {
                    lastTime = v.currentTime; deadline = Date.now() + $WEB_VIDEO_STALL_TIMEOUT_MS;
                    if (!reportedPlaying) {
                        reportedPlaying = true; hideControls(); post('ready');
                        diagnostic('playback-advanced', {frames:v.getVideoPlaybackQuality ? v.getVideoPlaybackQuality().totalVideoFrames : null});
                    }
                }
            }
            var timer = setInterval(function(){
                if (terminal || document.hidden) return;
                observeProgress(false);
                if (deadline && Date.now() >= deadline) fail(metadata ? 'playback_timeout' : 'metadata_timeout',
                    '動画の読み込みまたは再生が停止しました');
            }, 250);
            v.addEventListener('loadedmetadata', function(){
                if (terminal) return;
                metadata = true;
                // A tap before metadata arrives must not be cancelled by v.pause().
                deadline = attempted ? Date.now() + $WEB_VIDEO_STALL_TIMEOUT_MS : 0;
                post('size:' + (v.videoWidth || 0) + ',' + (v.videoHeight || 0));
                post('media:' + (v.videoWidth || 0) + ',' + (v.videoHeight || 0) + ',' + (Number.isFinite(v.duration) ? v.duration : -1));
                if (!attempted || v.paused) post('idle');
                diagnostic('loadedmetadata');
            });
            v.addEventListener('canplay', function(){
                if (terminal) return;
                diagnostic('canplay');
                if (v.paused) post('idle');
            });
            v.addEventListener('play', function(){
                if (terminal) return;
                attempted = true; reportedPlaying = false; lastTime = v.currentTime;
                deadline = Date.now() + $WEB_VIDEO_STALL_TIMEOUT_MS;
                post('buffering'); diagnostic('play');
            });
            v.addEventListener('playing', function(){
                if (terminal) return;
                diagnostic('playing');
            });
            v.addEventListener('seeking', function(){
                if (terminal) return;
                // After a backwards seek, compare progress with the new position, not
                // the old playback high-water mark. Paused seeking stays paused.
                lastTime = v.currentTime; reportedPlaying = false;
                deadline = v.paused ? 0 : Date.now() + $WEB_VIDEO_STALL_TIMEOUT_MS;
                if (!v.paused) post('buffering');
                diagnostic('seeking');
            });
            v.addEventListener('seeked', function(){
                if (terminal) return;
                lastTime = v.currentTime;
                if (v.paused) post('idle');
                diagnostic('seeked');
            });
            ['waiting', 'stalled', 'suspend'].forEach(function(event){
                v.addEventListener(event, function(){
                    if (terminal) return;
                    diagnostic(event);
                    // suspend is normal after preload=metadata; it is not itself a failure.
                    if (event !== 'suspend') buffering();
                });
            });
            v.addEventListener('pause', function(){
                if (terminal) return;
                deadline = metadata ? 0 : Date.now() + $WEB_VIDEO_LOAD_TIMEOUT_MS;
                showControls(); post('idle'); diagnostic('pause');
            });
            v.addEventListener('timeupdate', function(){ observeProgress(v.ended === true); });
            v.addEventListener('ended', function(){
                if (terminal) return;
                // Short clips can finish between timer ticks. Count actual decoded
                // progress before publishing the final idle state.
                observeProgress(true); deadline = 0; showControls(); post('idle');
            });
            v.addEventListener('error', function(){
                var mediaError = v.error;
                fail(mediaError ? String(mediaError.code || 'unknown') : 'unknown',
                    mediaError && mediaError.message ? String(mediaError.message) : '動画を読み込めませんでした');
            });
            document.addEventListener('visibilitychange', function(){
                if (terminal) return;
                if (document.hidden) { v.pause(); }
                else deadline = metadata ? 0 : Date.now() + $WEB_VIDEO_LOAD_TIMEOUT_MS;
            });
            window.addEventListener('pagehide', function(){ terminal = true; clearInterval(timer); v.pause(); });
            v.addEventListener('touchstart', showControls, {passive:true});
            v.addEventListener('click', showControls);
            var lastTapAt = 0;
            function seekFromDoubleTap(clientX){
                if (terminal || !metadata) return;
                var right = clientX >= (window.innerWidth / 2);
                var next = v.currentTime + (right ? 10 : -10);
                var end = Number.isFinite(v.duration) ? v.duration : next;
                v.currentTime = Math.max(0, Math.min(end, next));
                showControls();
            }
            v.addEventListener('touchend', function(event){
                var now = Date.now();
                if (now - lastTapAt <= 350 && event.changedTouches && event.changedTouches.length === 1) {
                    seekFromDoubleTap(event.changedTouches[0].clientX); lastTapAt = 0; event.preventDefault();
                } else lastTapAt = now;
            }, {passive:false});
            v.addEventListener('dblclick', function(event){ seekFromDoubleTap(event.clientX); event.preventDefault(); });
            var actualType = $typeJson;
            var types = ['video/webm', 'video/webm; codecs="vp8"', 'video/webm; codecs="vp8, vorbis"',
                'video/webm; codecs="vp9, opus"', 'video/webm; codecs="vp9, vorbis"'];
            var capabilities = {};
            types.forEach(function(type){ capabilities[type] = v.canPlayType(type); });
            capabilities.actualType = actualType;
            capabilities.actual = actualType ? v.canPlayType(actualType) : 'unknown';
            diagnostic('capabilities', capabilities);
            if (actualType && capabilities.actual === '') {
                fail('unsupported_type', 'この端末では動画の形式を再生できません'); return;
            }
            // Keep the established static source/preload path. Calling load() here can
            // reset WebKit's preparation while WKWebView is finishing file navigation.
            post('buffering');
        })();
        </script></body></html>
    """.trimIndent()
}
