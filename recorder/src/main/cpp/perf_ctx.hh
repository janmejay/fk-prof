#include <mutex>
#include <cstdint>
#include "thread_map.hh"
#include "thread_perf_ctx_tracker.hh"

#ifndef PERF_CTX_H
#define PERF_CTX_H

namespace PerfCtx {
    class ConflictingDefinition : public std::runtime_error {
    public:
        ConflictingDefinition(const std::string& existing_def, const std::string& new_def) : runtime_error("Found conflicting perf-ctx defenition " + new_def + " which conflicts with " + existing_def) {}
        virtual ~ConflictingDefinition() {}
    };
    
    class Ctx {
    public:
        Ctx() {}
        ~Ctx() {}
        TracePt find_or_create(JNIEnv* env, const char* name, std::uint8_t coverage_pct, std::uint8_t merge_type) throw (ConflictingDefinition);
        void exit(JNIEnv* env, TracePt pt) throw (IncorrectCtxScope);
        void enter(JNIEnv* env, TracePt pt);
    };
};

#endif
