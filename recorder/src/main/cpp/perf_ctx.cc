#include "perf_ctx_jni.hh"
#include "thread_perf_ctx_tracker.hh"
#include "perf_ctx.hh"
#include "globals.hh"
#include <sstream>

PerfCtx::MergeSemantic PerfCtx::merge_semantic(PerfCtx::TracePt pt) {
    assert((pt & PerfCtx::TYPE_MASK) == PerfCtx::USER_CREATED_TYPE);
    return static_cast<MergeSemantic>((pt >> PerfCtx::MERGE_SEMANTIIC_SHIFT) & MERGE_SEMANTIIC_MASK);
}

static std::uint8_t effective_tracept_len(const std::vector<PerfCtx::TracePt>& effective_tracepts) {
    return static_cast<std::uint8_t>(effective_tracepts.size());
}

void PerfCtx::ThreadTracker::enter(PerfCtx::TracePt pt) {
    if (actual_stack.size() == PerfCtx::MAX_NESTING) {
        ignore_count++;
        return;
    }
    assert(actual_stack.size() < PerfCtx::MAX_NESTING);
    auto ms = PerfCtx::merge_semantic(pt);
    assert((pt & PerfCtx::TYPE_MASK) == PerfCtx::USER_CREATED_TYPE);
    auto effective_end_before = effective_tracept_len(effective);
    switch (ms) {
    case PerfCtx::MergeSemantic::to_parent:
        if (effective_end_before == 0) effective.push_back(pt);
        actual_stack.emplace_back(pt, effective_end_before, effective_tracept_len(effective));
        break;
    case PerfCtx::MergeSemantic::scoped:
        if (effective_end_before == 0) effective.push_back(pt);
        else {
            effective.push_back(reg.merge_bind(actual_stack, pt));
        }
        actual_stack.emplace_back(pt, effective_end_before, effective_tracept_len(effective));
        break;
    }
}

void PerfCtx::ThreadTracker::exit(PerfCtx::TracePt pt) throw (IncorrectEnterExitPairing) {
    if (ignore_count > 0) {
        ignore_count--;
        return;
    }
    auto top = actual_stack.back();
    if (top.ctx != pt) throw IncorrectEnterExitPairing(top.ctx, pt);
    effective.resize(top.effective.start);
    actual_stack.pop_back();
}

int PerfCtx::ThreadTracker::current(std::array<PerfCtx::TracePt, PerfCtx::MAX_NESTING>& curr) {
    if (effective.size() == 0) return 0;
    curr[0] = effective.back();
    return 1;
}

PerfCtx::TracePt PerfCtx::Registry::find_or_bind(const char* name, std::uint8_t coverage_pct, std::uint8_t merge_type) throw (PerfCtx::ConflictingDefinition) {
    return 0;
}

typedef std::vector<PerfCtx::ThreadCtx>::const_reverse_iterator It;

static It scoped_parent_chain_beginning(const std::vector<PerfCtx::ThreadCtx>& ctx_stack) {
    It it;
    for (it = ctx_stack.crbegin(); it != ctx_stack.crend(); it++) {
        auto ms = PerfCtx::merge_semantic(it->ctx);
        if (ms != PerfCtx::MergeSemantic::scoped &&
            ms != PerfCtx::MergeSemantic::scoped_strict) {
            it++;
            break;
        }
    }
    return it;
}

PerfCtx::TracePt merge(std::array<std::uint64_t, PerfCtx::MAX_NESTING> s, std::uint8_t end_idx) {
    auto fac = 1, permutation_id = 0;
    std::uint64_t combination_id = 1;
    for (auto i = 0; i < end_idx; i++) {
        auto rank = 1;
        for (auto j = 0; j < i; j++) {
            if (s[i] > s[j]) {
                rank++;
            }
        }
        permutation_id += fac * (rank - 1);
        fac *= (i + 1);
        combination_id *= s[i];
    }

    assert(permutation_id <= 120); //depends on MAX_NESTING, 5! = 120
    assert(combination_id < PerfCtx::GENERATED_COMBINATION_MAX_VALUE);

    return PerfCtx::MERGE_GENERATED_TYPE | (combination_id << PerfCtx::GENERATED_COMBINATION_SHIFT) | permutation_id;
}

PerfCtx::TracePt PerfCtx::Registry::merge_bind(const std::vector<ThreadCtx>& ctx_stack, PerfCtx::TracePt child, bool strict) {
    assert(ctx_stack.size() > 0);
    assert(ctx_stack.size() <= PerfCtx::MAX_NESTING);
    auto parent_chain_start = scoped_parent_chain_beginning(ctx_stack);
    auto curr_rev = ctx_stack.crbegin();

    std::array<std::uint64_t, PerfCtx::MAX_NESTING> ctx_ids;
    assert((child & PerfCtx::TYPE_MASK) == PerfCtx::USER_CREATED_TYPE);
    ctx_ids[0] = child & USER_CREATED_CTX_ID_MASK;

    std::uint8_t i;
    for (i = 1; curr_rev != parent_chain_start; curr_rev++, i++) {
        assert(i < PerfCtx::MAX_NESTING);
        ctx_ids[i] = curr_rev->ctx & USER_CREATED_CTX_ID_MASK;
    }

    auto trace_pt = merge(ctx_ids, i);

    //generate the name-scoping here, so we can bind the generated value, if necessary
    
    return trace_pt;
}

JNIEXPORT jlong JNICALL Java_fk_prof_PerfCtx_registerCtx(JNIEnv* env, jobject self, jstring name, jint coverage_pct, jint merge_type) {
    const char* name_str = nullptr;
    try {
        name_str = env->GetStringUTFChars(name, nullptr);
        auto id = GlobalCtx::ctx_reg->find_or_bind(name_str, static_cast<std::uint8_t>(coverage_pct), static_cast<std::uint8_t>(merge_type));
        return static_cast<jlong>(id);
    } catch (PerfCtx::ConflictingDefinition& e) {
        if (name_str != nullptr) env->ReleaseStringUTFChars(name, name_str);
        if (env->ThrowNew(env->FindClass("fk/prof/ConflictingDefinitionException"), e.what()) == 0) return -1;
        logger->warn("Conflicting definition of perf-ctx ignored, details: {}", e.what());
        return -1;
    }
}

JNIEXPORT void JNICALL Java_fk_prof_PerfCtx_end(JNIEnv* env, jobject self, jlong ctx_id) {
    // try {
    //     //GlobalCtx::perf_ctx->exit(env, static_cast<PerfCtx::TracePt>(ctx_id));
    // } catch (PerfCtx::IncorrectCtxScope& e) {
    //     if (env->ThrowNew(env->FindClass("fk/prof/IncorrectContextException"), e.what()) == 0) return;
    //     logger->warn("Incorrect ctx end was requested, details: {}", e.what());
    // }
}

JNIEXPORT void JNICALL Java_fk_prof_PerfCtx_begin(JNIEnv* env, jobject self, jlong ctx_id) {
    //GlobalCtx::perf_ctx->enter(env, static_cast<PerfCtx::TracePt>(ctx_id));
}


