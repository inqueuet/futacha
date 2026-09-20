@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.ui.board

import platform.JavaScriptCore.JSContext
import kotlin.test.*

/** Executes the production script in Apple's JavaScriptCore with deterministic media events/time. */
class WebVideoJavaScriptTest {
    @Test fun nativeDiagnosticsAcceptUnicodeUrlsAndFormatCharacters() {
        logVideoPlaybackEnvironment("https://example.test/日本語%20.webm?value=%@%s%d", "WKWebView")
        logVideoPlaybackDiagnostic("web", "decoder 100%: 日本語\nnetworkState=3 readyState=1")
    }
    private fun player(canPlay: String = "probably"): JSContext {
        val context = JSContext()
        context.evaluateScript("""
            var now=0, messages=[], listeners={}, ticks=[], pauseCalls=0, loads=0;
            Date.now=function(){return now;};
            function setInterval(fn){ticks.push(fn);return fn;}
            function clearInterval(fn){ticks=ticks.filter(function(t){return t!==fn;});}
            function advance(ms){now+=ms;ticks.slice().forEach(function(t){t();});}
            var v={paused:true, readyState:0, networkState:0, currentTime:0, duration:5, videoWidth:320, videoHeight:240,
                canPlayType:function(){return '$canPlay';}, addEventListener:function(e,f){listeners[e]=f;},
                pause:function(){pauseCalls++;this.paused=true;if(listeners.pause)listeners.pause();},
                load:function(){loads++;},removeAttribute:function(k){delete this[k];},getAttribute:function(){return 'movie.webm';}};
            var document={hidden:false,querySelector:function(){return v;},addEventListener:function(){}};
            var window={innerWidth:320,addEventListener:function(){},webkit:{messageHandlers:{futachaVideoState:{postMessage:function(m){messages.push(m);}}}}};
            function emit(e){listeners[e]();}
            function errors(){return messages.filter(function(m){return m.indexOf('error:')===0;});}
        """.trimIndent())
        context.evaluateScript(buildEmbeddedVideoHtml("movie.webm", "video/webm; codecs=\"vp9, opus\"").substringAfter("<script>").substringBefore("</script>"))
        assertNull(context.exception?.toString())
        return context
    }
    private fun JSContext.check(expression: String) {
        assertTrue(evaluateScript(expression)?.toBool() == true, "$expression exception=${exception?.toString()}")
    }
    @Test fun unsupportedTypeStopsSourceAndLateEventsCannotReviveIt() {
        player("").apply { check("errors().length===1 && errors()[0].indexOf('unsupported_type')>=0 && !v.src")
            evaluateScript("emit('loadedmetadata');emit('playing');advance(60000);")
            check("errors().length===1 && messages.indexOf('ready')===-1") }
    }
    @Test fun metadataTimeoutIsTerminalEvenWithRepeatedStalls() {
        player().apply { evaluateScript("advance(19000);emit('stalled');advance(1100);emit('loadedmetadata');")
            check("errors().length===1 && errors()[0].indexOf('metadata_timeout')>=0 && messages.indexOf('idle')===-1") }
    }
    @Test fun metadataPreloadSuspendAndWaitingForUserAreNotPlaybackFailures() {
        player().apply { evaluateScript("emit('loadedmetadata');emit('suspend');advance(90000);")
            check("errors().length===0 && v.paused && pauseCalls===0 && messages.indexOf('idle')>=0 && messages.filter(function(m){return m==='buffering';}).length===1") }
    }
    @Test fun earlyUserPlaySurvivesMetadataAndStallsHaveAFiniteDeadline() {
        player().apply { evaluateScript("v.paused=false;emit('play');emit('loadedmetadata');advance(14000);emit('waiting');emit('stalled');advance(1100);")
            check("errors().length===1 && errors()[0].indexOf('playback_timeout')>=0 && pauseCalls===1") }
    }
    @Test fun advancingPlaybackExtendsDeadlineButPauseDoesNotTimeOut() {
        player().apply { evaluateScript("emit('loadedmetadata');v.paused=false;emit('play');emit('playing');advance(14000);v.currentTime=1;advance(500);advance(14000);v.pause();advance(90000);")
            check("errors().length===0 && messages.indexOf('ready')>=0 && v.paused") }
    }
    @Test fun mediaErrorForwardsCodeMessageAndBothNativeStatesOnce() {
        player().apply { evaluateScript("v.error={code:3,message:'decoder failure'};v.networkState=3;v.readyState=1;emit('error');emit('error');emit('canplay');")
            check("errors().length===1 && errors()[0]==='error:3:decoder failure'")
            check("messages.some(function(m){return m.indexOf('\\\"networkState\\\":3')>=0 && m.indexOf('\\\"readyState\\\":1')>=0;})") }
    }
    @Test fun recoveredStallReturnsToPlayingOnlyWhenDecodedFramesAndTimeAdvance() {
        player().apply {
            evaluateScript("emit('loadedmetadata');v.paused=false;emit('play');v.currentTime=0.2;advance(250);emit('waiting');v.currentTime=0.4;advance(250);")
            check("messages.filter(function(m){return m==='ready';}).length===2 && errors().length===0")
        }
        player().apply {
            evaluateScript("emit('loadedmetadata');v.paused=false;emit('play');v.getVideoPlaybackQuality=function(){return {totalVideoFrames:0};};v.currentTime=1;advance(16000);")
            check("messages.indexOf('ready')===-1 && errors().length===1")
        }
    }
    @Test fun backwardsSeekUsesNewPositionAndPausedSeekDoesNotStartPlayback() {
        player().apply {
            evaluateScript("emit('loadedmetadata');v.paused=false;emit('play');v.currentTime=90;advance(250);v.currentTime=10;emit('seeking');emit('seeked');v.currentTime=10.5;advance(250);advance(14000);")
            check("errors().length===0 && messages.filter(function(m){return m==='ready';}).length===2")
            evaluateScript("v.pause();v.currentTime=2;emit('seeking');emit('seeked');advance(90000);")
            check("errors().length===0 && v.paused")
        }
    }
    @Test fun shortDecodedClipReportsProgressEvenWhenItEndsBetweenTimerTicks() {
        player().apply {
            evaluateScript("emit('loadedmetadata');v.paused=false;emit('play');v.currentTime=0.2;v.getVideoPlaybackQuality=function(){return {totalVideoFrames:2};};v.paused=true;v.ended=true;emit('pause');emit('ended');advance(90000);")
            check("errors().length===0 && messages.indexOf('ready')>=0 && messages[messages.length-1]==='idle'")
        }
    }
}
