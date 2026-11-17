cmake_minimum_required(VERSION 3.21)
project(FutachaTracking LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Only optical flow/robust geometry. No second media decoder, GUI or inference runtime.
set(BUILD_LIST "core,imgproc,features,geometry,video,flann" CACHE STRING "" FORCE)
foreach(option BUILD_SHARED_LIBS BUILD_TESTS BUILD_PERF_TESTS BUILD_EXAMPLES BUILD_ANDROID_EXAMPLES
        INSTALL_ANDROID_EXAMPLES BUILD_opencv_apps
        BUILD_JAVA BUILD_OBJC BUILD_opencv_python3 BUILD_opencv_dnn BUILD_opencv_objc
        WITH_OPENCL WITH_IPP WITH_LAPACK WITH_EIGEN WITH_ITT WITH_PROTOBUF WITH_FLATBUFFERS)
    set(${option} OFF CACHE BOOL "" FORCE)
endforeach()
add_subdirectory("${OPENCV_SOURCE}" opencv)
if(WIN32)
    # OpenCV geometry uses M_PI; the Windows UCRT exposes it only with this define.
    target_compile_definitions(opencv_geometry PRIVATE _USE_MATH_DEFINES)
endif()
if(ANDROID)
    add_library(futacha_tracking_bridge SHARED FutachaTracking.cpp FutachaTrackingJni.cpp)
    # Keep JNI exports and let the linker discard unused static OpenCV functions.
    target_compile_options(futacha_tracking_bridge PRIVATE -fvisibility=hidden)
    target_link_options(futacha_tracking_bridge PRIVATE
        "-Wl,--exclude-libs,ALL" "-Wl,--gc-sections" "-Wl,-z,max-page-size=16384")
elseif(FUTACHA_DESKTOP)
    add_library(futacha_tracking_bridge SHARED FutachaTracking.cpp)
    if(WIN32)
        set_target_properties(futacha_tracking_bridge PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON PREFIX "")
        if(MINGW)
            target_link_options(futacha_tracking_bridge PRIVATE -static-libgcc -static-libstdc++)
        endif()
    endif()
else()
    add_library(futacha_tracking_bridge STATIC FutachaTracking.cpp)
endif()
target_link_libraries(futacha_tracking_bridge PRIVATE opencv_video opencv_geometry opencv_features)
target_include_directories(futacha_tracking_bridge PRIVATE
    "${OPENCV_SOURCE}/modules/core/include" "${OPENCV_SOURCE}/modules/imgproc/include"
    "${OPENCV_SOURCE}/modules/features/include" "${OPENCV_SOURCE}/modules/geometry/include"
    "${OPENCV_SOURCE}/modules/video/include" "${OPENCV_SOURCE}/modules/flann/include"
    "${CMAKE_CURRENT_BINARY_DIR}")
