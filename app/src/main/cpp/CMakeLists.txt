cmake_minimum_required(VERSION 3.22.1)
project(pianoengine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe llega como paquete Prefab desde la dependencia Gradle com.google.oboe:oboe
find_package(oboe REQUIRED CONFIG)

add_library(pianoengine SHARED
        native-lib.cpp
        AudioEngine.cpp
        SoundFontPlayer.cpp
)

target_include_directories(pianoengine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
# TinySoundFont es header-only (tsf.h, tml.h). SYSTEM silencia sus warnings internos
# para que -Wall -Wextra solo reporte problemas de nuestro código.
target_include_directories(pianoengine SYSTEM PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/tinysoundfont
)

# Optimización agresiva siempre en el código de audio, incluso en Debug:
# un callback lento en Debug genera glitches que confunden al depurar.
# Debug conserva los símbolos (-g) para poder depurar; solo Release los elimina.
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(pianoengine PRIVATE -O3 -ffunction-sections -fdata-sections)
    target_link_options(pianoengine PRIVATE -Wl,--gc-sections -Wl,--strip-all)
else ()
    target_compile_options(pianoengine PRIVATE -O2 -g)
endif ()

# Android 15+ admite dispositivos con páginas de 16 KB y Google Play lo exige a las apps
# con targetSdk 35. NDK r27 todavía alinea a 4 KB por defecto (r28 ya lo hace a 16 KB):
# https://developer.android.com/guide/practices/page-sizes#compile-r27-lower
target_link_options(pianoengine PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
)

target_link_libraries(pianoengine
        oboe::oboe
        android   # AAssetManager para leer el .sf2 desde assets
        log       # __android_log_print
)
