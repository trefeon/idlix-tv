# Keep Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer();
}
-keepclassmembers class **$companion {
    kotlinx.serialization.KSerializer serializer();
}

# Preserve Data Models for JSON serialization
-keep class com.idlix.data.model.** { *; }
-keep class com.idlix.tv.data.model.** { *; }

# Debug symbols & stack trace line numbers deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp (shared via CoreHttp)
# (Retrofit service rules removed: direct-source uses OkHttp + kotlinx.serialization only.)
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Exceptions

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase
