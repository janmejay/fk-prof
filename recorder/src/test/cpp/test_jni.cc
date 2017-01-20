#include "test_jni.hh"
#include "../../main/cpp/perf_ctx.hh"
#include "test_profile.hh"
#include <jni.h>
#include <iostream>
#include <atomic>

JNIEXPORT jboolean JNICALL Java_fk_prof_TestJni_generateCpusampleSimpleProfile(JNIEnv* jni, jobject self, jstring path) {
    try {
        auto file_path = jni->GetStringUTFChars(path, 0);
        if (file_path != NULL) {
            generate_cpusample_simple_profile(file_path);
            jni->ReleaseStringUTFChars(path, file_path);
            return JNI_TRUE;
        }
    } catch (...) {
        std::cerr << "Profile creation failed\n";
    }
    return JNI_FALSE;
}

int perf_ctx_idx = 0, in_ctx = -1;

std::string last_registered_ctx_name("");
int last_registered_coverage_pct = 0;

JNIEXPORT jint JNICALL Java_fk_prof_TestJni_getAndStubCtxIdStart(JNIEnv* jni, jobject self, jint val) {
    int old_val = perf_ctx_idx;
    perf_ctx_idx = val;
    return old_val;
}

JNIEXPORT jint JNICALL Java_fk_prof_PerfCtx_registerCtx(JNIEnv* jni, jobject self, jstring name, jint coverage_pct) {
    auto name_cstr = jni->GetStringUTFChars(name, nullptr);
    last_registered_ctx_name = name_cstr;
    jni->ReleaseStringUTFChars(name, name_cstr);
    last_registered_coverage_pct = coverage_pct;
    return perf_ctx_idx++;
}

JNIEXPORT void JNICALL Java_fk_prof_PerfCtx_end(JNIEnv* jni, jobject self, jint ctx_id) {
    in_ctx = -1;
}

JNIEXPORT void JNICALL Java_fk_prof_PerfCtx_start(JNIEnv* jni, jobject self, jint ctx_id) {
    in_ctx = ctx_id;
}

JNIEXPORT jint JNICALL Java_fk_prof_TestJni_getCurrentCtx(JNIEnv* jni, jobject self) {
    return in_ctx;
}

JNIEXPORT jstring JNICALL Java_fk_prof_TestJni_getLastRegisteredCtxName(JNIEnv* jni, jobject self) {
    return jni->NewStringUTF(last_registered_ctx_name.c_str());
}

JNIEXPORT jint JNICALL Java_fk_prof_TestJni_getLastRegisteredCtxCoveragePct(JNIEnv* jni, jobject self) {
    return last_registered_coverage_pct;
}
