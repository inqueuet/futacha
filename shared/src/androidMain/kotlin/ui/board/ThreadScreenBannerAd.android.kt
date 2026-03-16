package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val THREAD_SCREEN_BANNER_AD_UNIT_ID = "ca-app-pub-6403856201304924/8151063815"

@Composable
internal actual fun ThreadScreenBannerAd(
    modifier: Modifier
) {
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("AdMob Banner")
        }
        return
    }

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(320)
    val adSize = remember(context, widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
    var isBannerVisible by remember(context, adSize) { mutableStateOf(false) }
    val adView = remember(context, adSize) {
        AdView(context).apply {
            adUnitId = THREAD_SCREEN_BANNER_AD_UNIT_ID
            setAdSize(adSize)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    isBannerVisible = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isBannerVisible = false
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    if (isBannerVisible) {
        AndroidView(
            factory = { adView },
            modifier = modifier.fillMaxWidth()
        )
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }
}
