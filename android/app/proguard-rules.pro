# kotlinx.serialization ships its own keep rules; nothing extra needed.
-dontwarn org.slf4j.**

# LiteRT (food classifier): the native runtime calls back into these classes by name.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
