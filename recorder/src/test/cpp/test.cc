#include "test.hh"
#include "../../main/cpp/prob_pct.hh"
#include "../../main/cpp/thread_map.hh"

LoggerP logger(nullptr);
PerfCtx::Registry* GlobalCtx::ctx_reg = nullptr;
ProbPct* GlobalCtx::prob_pct = nullptr;

void init_logger() {
    if (logger == nullptr) {
        logger = spdlog::stdout_color_mt("console");
        logger->set_level(spdlog::level::trace);
        logger->set_pattern("{%t} %+");
    }
}

static ThreadMap thread_map;
ThreadMap& get_thread_map() {
    return thread_map;
}
