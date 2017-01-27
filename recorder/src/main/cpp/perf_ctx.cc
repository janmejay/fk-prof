#include "perf_ctx_jni.hh"
#include "thread_perf_ctx_tracker.hh"
#include "perf_ctx.hh"
#include "globals.hh"

void PerfCtx::ThreadTracker::enter(PerfCtx::TracePt pt) {
    stk.push({pt, 0, 0});
}

void PerfCtx::ThreadTracker::exit(PerfCtx::TracePt pt) throw (IncorrectCtxScope) {
    stk.pop();
}

PerfCtx::TracePt PerfCtx::ThreadTracker::current() {
    return stk.size() > 0 ? stk.top().ctx : 0;
}

PerfCtx::TracePt PerfCtx::Ctx::find_or_create(JNIEnv* env, const char* name, std::uint8_t coverage_pct, std::uint8_t merge_type) throw (PerfCtx::ConflictingDefinition) {
    return 0;
}

void PerfCtx::Ctx::enter(JNIEnv* env, PerfCtx::TracePt pt) {
    
}

void PerfCtx::Ctx::exit(JNIEnv* env, PerfCtx::TracePt pt) throw (IncorrectCtxScope) {
    
}

JNIEXPORT jlong JNICALL Java_fk_prof_PerfCtx_registerCtx(JNIEnv* env, jobject self, jstring name, jint coverage_pct, jint merge_type) {
    const char* name_str = nullptr;
    try {
        name_str = env->GetStringUTFChars(name, nullptr);
        auto id = GlobalCtx::perf_ctx->find_or_create(env, name_str, static_cast<std::uint8_t>(coverage_pct), static_cast<std::uint8_t>(merge_type));
        return static_cast<jlong>(id);
    } catch (PerfCtx::ConflictingDefinition& e) {
        if (name_str != nullptr) env->ReleaseStringUTFChars(name, name_str);
        if (env->ThrowNew(env->FindClass("fk/prof/ConflictingDefinitionException"), e.what()) == 0) return -1;
        logger->warn("Conflicting definition of perf-ctx ignored, details: {}", e.what());
        return -1;
    }
}

JNIEXPORT void JNICALL Java_fk_prof_PerfCtx_end(JNIEnv* env, jobject self, jlong ctx_id) {
    try {
        GlobalCtx::perf_ctx->exit(env, static_cast<PerfCtx::TracePt>(ctx_id));
    } catch (PerfCtx::IncorrectCtxScope& e) {
        if (env->ThrowNew(env->FindClass("fk/prof/IncorrectContextException"), e.what()) == 0) return;
        logger->warn("Incorrect ctx end was requested, details: {}", e.what());
    }
}

JNIEXPORT void JNICALL Java_fk_prof_PerfCtx_begin(JNIEnv* env, jobject self, jlong ctx_id) {
    GlobalCtx::perf_ctx->enter(env, static_cast<PerfCtx::TracePt>(ctx_id));
}

