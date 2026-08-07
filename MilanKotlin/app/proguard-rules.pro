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
