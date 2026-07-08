# Retrofit/OkHttp/kotlinx-serialization ship consumer rules; project-specific keeps only.

# Keep generic signatures for Retrofit suspend functions (R8 full mode).
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
