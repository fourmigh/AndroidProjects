# Compose / Kotlin 默认规则即可；@JavascriptInterface 需要保留（见下方规则）。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
