# 保留所有核心类
-keep class com.liumanhanyu.app.** { *; }
# 保留 TranslationData 的 Map 字段不被优化
-keepclassmembers class com.liumanhanyu.app.TranslationData$* {
    <fields>;
}
