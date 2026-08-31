# Keep shared Kotlin serialization metadata for snapshot payloads.
-keepclassmembers class com.valoser.futacha.shared.watch.** {
    *** Companion;
}

# Firebase Analytics can optionally query the advertising ID. The app deliberately
# excludes that dependency and removes the AD_ID permission, so these classes are absent.
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient$Info
