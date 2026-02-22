# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# SLF4J (used by Ktor/OkHttp, no binding needed at runtime on Android)
-dontwarn org.slf4j.**

# Google Tink / ErrorProne annotations (used by androidx.security:security-crypto)
-dontwarn com.google.errorprone.annotations.**

# WebView (prevent R8 from stripping WebViewClient subclasses)
-keepclassmembers class * extends android.webkit.WebViewClient { *; }

# Keep shared module data models
-keep class com.claudeusage.shared.model.** { *; }
-keep class com.claudeusage.widget.data.model.** { *; }

# Crash handler (must survive R8 for crash reporting to work)
-keep class com.claudeusage.widget.CrashActivity { *; }
-keep class com.claudeusage.widget.CrashHandler { *; }

# Glance widget
-keep class com.claudeusage.widget.widget.** { *; }

# Ktor
-dontwarn io.ktor.**
