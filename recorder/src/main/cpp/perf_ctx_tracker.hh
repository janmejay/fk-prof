#ifndef PERF_CTX_TRACKER_H
#define PERF_CTX_TRACKER_H

class PerfCtxTracker {
private:
    std::vector<PerfCtx> ctxs;
    ThreadMap& thread_map;
    
public:
    PerfCtxRegistry(ThreadMap& _thread_map) : thread_map(_thread_map) { }
    
    ~PerfCtxRegistry() {}

    std::uint32_t do_register(const char* name, std::uint32_t cov_pct);

    void push_ctx(JNIEnv* jni);

    void pop_ctx(JNIEnv* jni);

};

#endif
