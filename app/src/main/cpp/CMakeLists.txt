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
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(pianoengine PRIVATE -O3 -ffunction-sections -fdata-sections)
else ()
    target_compile_options(pianoengine PRIVATE -O2 -g)
    target_link_options(pianoengine PRIVATE -Wl,--gc-sections -Wl,--strip-all)
endif ()

target_link_libraries(pianoengine
        oboe::oboe
        android   # AAssetManager para leer el .sf2 desde assets
        log       # __android_log_print
)
