##########################################################
# CMake Configuration

cmake_minimum_required(VERSION 2.8 FATAL_ERROR)

project(honest_profiler)

enable_testing()

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "build")

include(CheckLibraryExists)
include(CheckSymbolExists)
include(CheckFunctionExists)

##########################################################
# Paths
set(SRC "src/main/cpp")
set(INCLUDE_VENDOR_SPDLOG "vendor/spdlog/include")
set(SRC_TEST "src/test/cpp")
set(BIN "build")
set(OUTPUT "lagent")
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${BIN})
#protobuf idl setup
add_subdirectory(src/idl)
include_directories(src/idl)

##########################################################
# Libraries & Code

find_package(PkgConfig REQUIRED)

find_package(JNI REQUIRED)
include_directories(${JAVA_INCLUDE_PATH})
include_directories(${JAVA_INCLUDE_PATH2})

pkg_check_modules(ZLIB REQUIRED zlib)
pkg_check_modules(CURL REQUIRED libcurl)

if (DEFINED ENV{UNITTEST_INCLUDE_DIRS})
    message("User has configured " $ENV{UNITTEST_INCLUDE_DIRS} " as the unit test include directory")
    include_directories($ENV{UNITTEST_INCLUDE_DIRS})
else()
    if(${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
        pkg_check_modules (unittest++ REQUIRED UnitTest++)
    else()
        pkg_check_modules (unittest++ REQUIRED unittest++)
    endif()
    include_directories(${unittest++_INCLUDE_DIRS})
  endif()

find_package(Boost)
if(Boost_FOUND)
  include_directories(${Boost_INCLUDE_DIRS})
endif()

# include of gcc 4.8 headers specifically to work around
# https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=729933
# equivalent in ubuntu 13.10
include_directories("/usr/include/i386-linux-gnu/c++/4.8/")

set(SOURCE_FILES
    ${SRC}/agent.cc
    ${SRC}/checksum.cc
    ${SRC}/checksum.hh
    ${SRC}/circular_queue.cc
    ${SRC}/circular_queue.hh
    ${SRC}/common.cc
    ${SRC}/common.hh
    ${SRC}/controller.cc
    ${SRC}/controller.hh
    ${SRC}/globals.hh
    ${SRC}/globals.cc
    ${SRC}/log_writer.cc
    ${SRC}/log_writer.hh
    ${SRC}/signal_handler.cc
    ${SRC}/signal_handler.hh
    ${SRC}/processor.cc
    ${SRC}/processor.hh
    ${SRC}/profiler.cc
    ${SRC}/profiler.hh
    ${SRC}/stacktraces.hh
    ${SRC}/trace.hh
    ${SRC}/thread_map.hh
    ${SRC}/thread_map.cc
    ${SRC}/concurrent_map.hh
    ${SRC}/concurrent_map.cc
    ${SRC}/profile_writer.cc
    ${SRC}/profile_writer.hh
    ${SRC}/config.hh
    ${SRC}/config.cc
    ${SRC}/scheduler.hh
    ${SRC}/scheduler.cc)

set(TEST_FILES
    ${SRC_TEST}/fixtures.hh
    ${SRC_TEST}/test_circular_queue.cc
    ${SRC_TEST}/test_log_writer.cc
    ${SRC_TEST}/test_config.cc
    ${SRC_TEST}/test.hh
    ${SRC_TEST}/test_recording_serde.cc
    ${SRC_TEST}/test_checksum.cc
    ${SRC_TEST}/test_maps.cc
    ${SRC_TEST}/test_scheduler.cc)

set(TEST_UTIL_FILES
    ${SRC_TEST}/test_profile.hh
    ${SRC_TEST}/test_profile.cc)

set(TEST_JNI_FILES
    ${SRC_TEST}/test_jni.hh
    ${SRC_TEST}/test_jni.cc)

##########################################################
# Compiler Options

set(GLOBAL_WARNINGS "-Wall -Wformat-security -Wno-char-subscripts")
set(GLOBAL_WARNINGS "${GLOBAL_WARNINGS} -Wno-sign-compare -Wno-strict-overflow -Wwrite-strings")
set(GLOBAL_WARNINGS "${GLOBAL_WARNINGS} -Wnon-virtual-dtor -Woverloaded-virtual")

set(GLOBAL_COPTS "-fdiagnostics-show-option -fno-omit-frame-pointer -fno-strict-aliasing")
set(GLOBAL_COPTS "${GLOBAL_COPTS} -funsigned-char -fno-asynchronous-unwind-tables -msse2 -g -D__STDC_FORMAT_MACROS -O2")

# Platform Specific
if(${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
    set(GLOBAL_COPTS "${GLOBAL_COPTS} -DTARGET_RT_MAC_CFM=0")
    if("${CMAKE_SIZEOF_VOID_P}" EQUAL "8") 
        message("-- Detected 64 bit Mac")
        set(GLOBAL_COPTS "${GLOBAL_COPTS} -D__LP64__=1")
    endif()
endif()

# Compiler Specific
if ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang")
    message("-- using Clang")
    set(GLOBAL_WARNINGS, "${GLOBAL_WARNINGS} -Weverything -Wno-c++98-compat-pedantic -Wno-padded")
    set(global_warNINGS, "${GLOBAL_WARNINGS} -Wno-missing-prototypes -Wno-weak-vtables -Wno-global-constructors")
elseif ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "GNU")
    message("-- using GCC")
    #set(GLOBAL_COPTS "${GLOBAL_COPTS} -mfpmath=sse -fno-tree-loop-distribute-patterns")
    set(GLOBAL_COPTS "${GLOBAL_COPTS} -mfpmath=sse")
    set(GLOBAL_WARNINGS, "${GLOBAL_WARNINGS} -Wframe-larger-than=16384 -Wno-unused-but-set-variable")
    set(GLOBAL_WARNINGS, "${GLOBAL_WARNINGS} -Wunused-but-set-parameter -Wvla -Wno-conversion-null")
    set(GLOBAL_WARNINGS, "${GLOBAL_WARNINGS} -Wno-unknown-pragmas -Wno-builtin-macro-redefined -Wl,-fatal_warnings")
endif()

set(CMAKE_CXX_FLAGS_DEBUG  "-g3 -ggdb")
# to enable tracing add: -DENABLE_TRACING
set(CMAKE_CXX_FLAGS "-I${INCLUDE_VENDOR_SPDLOG} ${CMAKE_CXX_FLAGS} ${GLOBAL_WARNINGS} ${GLOBAL_COPTS} -pthread -std=c++0x")

##########################################################
# Build

add_library(${OUTPUT} SHARED ${SOURCE_FILES})

if ("${CMAKE_SYSTEM_NAME}" STREQUAL "FreeBSD") 
    target_link_libraries(${OUTPUT} ${JNI_LIBRARIES} proto ${PROTOBUF_LIBRARY} ${ZLIB_LDFLAGS} ${CURL_LDFLAGS})
else()
    target_link_libraries(${OUTPUT} ${JNI_LIBRARIES} proto ${PROTOBUF_LIBRARY} ${ZLIB_LDFLAGS} ${CURL_LDFLAGS} dl)
endif()

add_library(testutil SHARED ${TEST_UTIL_FILES})
add_executable(unitTests ${TEST_FILES} ${SRC_TEST}/test.cc)
add_library(testjni SHARED ${TEST_JNI_FILES})

if (DEFINED ENV{UNITTEST_LIBRARIES})
  message("User has configured " $ENV{UNITTEST_LIBRARIES} " as the unit test libraries")
  target_link_libraries(unitTests testutil ${OUTPUT} $ENV{UNITTEST_LIBRARIES})
  target_link_libraries(testjni testutil ${OUTPUT} $ENV{UNITTEST_LIBRARIES})
else()
  target_link_libraries(unitTests testutil ${OUTPUT} ${unittest++_LIBRARIES})
  target_link_libraries(testjni testutil ${OUTPUT} ${unittest++_LIBRARIES})
endif()

# make test
add_test(unitTests ${BIN}/unitTests)
