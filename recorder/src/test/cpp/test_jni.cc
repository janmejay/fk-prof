#include "test_jni.hh"
#include "../../main/cpp/perf_ctx.hh"
#include "../../main/cpp/globals.hh"
#include "../../main/cpp/profile_writer.hh"
#include "../../main/cpp/thread_map.hh"
#include "test_profile.hh"
#include "test.hh"
#include <jni.h>
#include <iostream>
#include <atomic>
#include "test.hh"

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
std::atomic<bool> ctx_ready(false);
TestEnv* test_env;

JNIEXPORT void JNICALL Java_fk_prof_TestJni_teardownPerfCtx(JNIEnv* jni, jobject self) {
    if (! ctx_ready.load(std::memory_order_acquire)) {
        jni->ThrowNew(jni->FindClass("java/lang/IllegalStateException"), "PerfCtx is not setup yet, so can't tear it down");
        return;
    }
    delete GlobalCtx::ctx_reg;
    delete GlobalCtx::prob_pct;
    delete test_env;
    ctx_ready.store(false, std::memory_order_release);

}

JNIEXPORT void JNICALL Java_fk_prof_TestJni_setupPerfCtx(JNIEnv* jni, jobject self) {
    if (ctx_ready.load(std::memory_order_acquire)) {
        jni->ThrowNew(jni->FindClass("java/lang/IllegalStateException"), "PerfCtx is already setup");
        return;
    }
    test_env = new TestEnv();
    GlobalCtx::ctx_reg = new PerfCtx::Registry();
    GlobalCtx::prob_pct = new ProbPct();
    ctx_ready.store(true, std::memory_order_release);
}

JNIEXPORT void JNICALL Java_fk_prof_TestJni_teardownThdTracker(JNIEnv* jni, jobject self) {
    get_thread_map().remove(jni);
}

JNIEXPORT void JNICALL Java_fk_prof_TestJni_setupThdTracker(JNIEnv* jni, jobject self) {
    get_thread_map().put(jni, "foo", 8, false);
}

JNIEXPORT jint JNICALL Java_fk_prof_TestJni_getCurrentCtx(JNIEnv* jni, jobject self, jlongArray arr) {
    if (arr == nullptr) {
        jni->ThrowNew(jni->FindClass("java/lang/IllegalArgumentException"), "Got NULL array");
        return -1;
    }

    if (jni->GetArrayLength(arr) < PerfCtx::MAX_NESTING) {
        jni->ThrowNew(jni->FindClass("java/lang/IllegalArgumentException"), Util::to_s("Got a very small array, need array with length > ", PerfCtx::MAX_NESTING).c_str());
        return -1;
    }

    ThreadBucket* thread_info = get_thread_map().get(jni);
    if (thread_info == nullptr) {
        jni->ThrowNew(jni->FindClass("java/lang/IllegalStateException"), "Thread tracker not initialized yet, can't get current context");
        return -1;
    }
    
    PerfCtx::ThreadTracker& ctx_tracker = thread_info->ctx_tracker;

    PerfCtx::ThreadTracker::EffectiveCtx eff_ctx;
    auto count = ctx_tracker.current(eff_ctx);
    logger->info("Wrote {} entries to effective_ctx", count);
    if ((count > 0) && (!ctx_tracker.in_ctx())) {
        auto msg = Util::to_s("Thread tracker doesn't seem to be bahaving right, it has ", count, " entries, but doesn't think its in a ctx.");
        jni->ThrowNew(jni->FindClass("java/lang/IllegalStateException"), msg.c_str());
        return -1;
    }

    jlong result[PerfCtx::MAX_NESTING];

    for (auto i = 0; i < count; i++) {
        result[i] = static_cast<jlong>(eff_ctx[i]);
    }

    jni->SetLongArrayRegion(arr, 0, count, result);
    
    return count;
}

#define RESOLVE(tpt, ret)                                               \
    auto pt = static_cast<PerfCtx::TracePt>(tpt);                       \
    std::string name;                                                   \
    bool is_gen;                                                        \
    std::uint8_t cov_pct;                                               \
    PerfCtx::MergeSemantic m_sem;                                       \
    try {                                                               \
        GlobalCtx::ctx_reg->resolve(pt, name, is_gen, cov_pct, m_sem);  \
    } catch (const PerfCtx::UnknownCtx& e) {                            \
        jni->ThrowNew(jni->FindClass("java/lang/IllegalStateException"), e.what()); \
        return ret;                                                     \
    }                                                                   \

JNIEXPORT jstring JNICALL Java_fk_prof_TestJni_getCtxName(JNIEnv* jni, jobject self, jlong tpt) {
    RESOLVE(tpt, nullptr);
    return jni->NewStringUTF(name.c_str());
}

JNIEXPORT jint JNICALL Java_fk_prof_TestJni_getCtxCov(JNIEnv* jni, jobject self, jlong tpt) {
    RESOLVE(tpt, -1);
    return static_cast<jint>(cov_pct);
}

JNIEXPORT jint JNICALL Java_fk_prof_TestJni_getCtxMergeSemantic(JNIEnv* jni, jobject self, jlong tpt) {
    RESOLVE(tpt, -1);
    return static_cast<jint>(m_sem);
}

JNIEXPORT jboolean JNICALL Java_fk_prof_TestJni_isGenerated(JNIEnv* jni, jobject self, jlong tpt) {
    RESOLVE(tpt, false);
    return static_cast<jboolean>(is_gen);
}

JNIEXPORT jstring JNICALL Java_fk_prof_TestJni_getNoCtxName(JNIEnv* jni, jobject self) {
    return jni->NewStringUTF(NOCTX_NAME);
}
