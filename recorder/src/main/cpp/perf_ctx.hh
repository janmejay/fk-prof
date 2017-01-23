#include <mutex>
#include <cstdint>
#include "thread_map.hh"

#ifndef PERF_CTX_H
#define PERF_CTX_H

struct PerfCtx {
    std::string name;
    std::uint32_t cov_pct;
};

#endif
