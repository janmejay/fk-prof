#include <cstdint>
#include <stack>
#include <stdexcept>

#ifndef THREAD_PERF_CTX_TRACKER_H
#define THREAD_PERF_CTX_TRACKER_H

namespace PerfCtx {
    class IncorrectCtxScope : public std::runtime_error {
    public:
        IncorrectCtxScope(const std::string& expected, const std::string& got) : runtime_error("Excepted " + expected + " but got " + got) {}
        virtual ~IncorrectCtxScope() {}
    };

    typedef std::uint64_t TracePt;
    
    class ThreadTracker {
        struct ThreadCtx {
            std::uint64_t ctx;
            std::uint32_t push_count;
            std::uint32_t ignore_count;
        };
        
        std::stack<ThreadCtx> stk;

        std::uint64_t effective;
        
    public:
        ThreadTracker() : effective(0) {}
        ~ThreadTracker() {}

        void enter(TracePt pt);
        void exit(TracePt pt) throw (IncorrectCtxScope);
        TracePt current();
    };
}

#endif
