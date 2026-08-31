# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Firebase Analytics can optionally query the advertising ID. The app deliberately
# excludes that dependency and removes the AD_ID permission, so these classes are absent.
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient$Info

# These facades live in the KMP shared Android artifact but are invoked from
# the application module during Application.onCreate(). Keep their binary
# names and implementations stable across the shared artifact and R8. This
# also prevents a partially cached/incremental artifact from leaving the app
# with a caller that references a facade absent from the final DEX.
-keep class com.valoser.futacha.shared.analytics.AnalyticsTracker { *; }
-keep class com.valoser.futacha.shared.analytics.AnalyticsTrackerKt { *; }
-keep class com.valoser.futacha.shared.analytics.PerformanceTracker { *; }
-keep class com.valoser.futacha.shared.analytics.CrashReporter { *; }
-keep class com.valoser.futacha.shared.analytics.PlatformAnalytics { *; }
-keep class com.valoser.futacha.shared.analytics.PlatformPerformance { *; }
-keep class com.valoser.futacha.shared.analytics.PlatformPerformanceTrace { *; }
-keep class com.valoser.futacha.shared.analytics.PlatformCrashReporter { *; }
