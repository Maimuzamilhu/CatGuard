# TensorFlow Lite / Task Library use reflection + JNI. Keep their entry points.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.task.** { *; }
-dontwarn org.tensorflow.lite.**
