#include "test.hh"

LoggerP logger(nullptr);
PerfCtx::Ctx* GlobalCtx::perf_ctx;

void init_logger() {
    if (logger == nullptr) {
        logger = spdlog::stdout_color_mt("console");
        logger->set_level(spdlog::level::trace);
        logger->set_pattern("{%t} %+");
    }
}

int main() {
    auto ret = UnitTest::RunAllTests();
    logger.reset();
    return ret;
}
