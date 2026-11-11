# Los métodos llamados desde C++ (y las clases con `external fun`) no deben renombrarse
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.tuapp.piano.data.audio.** { *; }
