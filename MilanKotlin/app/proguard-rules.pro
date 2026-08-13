# kotlinx.serialization 模型保持规则（Task 2 起按需补充）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.milan.game.**$$serializer { *; }
-keepclassmembers class com.milan.game.** {
    *** Companion;
}
-keepclasseswithmembers class com.milan.game.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager 自动初始化 keep 规则（AGP 9 R8 严格化会裁掉反射实例化的 WorkDatabase_Impl，
# 导致 release 启动崩：Failed to create an instance of androidx.work.impl.WorkDatabase；Google Issue 348590028）
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
