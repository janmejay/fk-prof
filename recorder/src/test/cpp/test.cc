#include "test.hh"
#include "../../main/cpp/prob_pct.hh"

LoggerP logger(nullptr);
PerfCtx::Registry* GlobalCtx::ctx_reg;
ProbPct* GlobalCtx::prob_pct;

std::ostream& operator<<(std::ostream& os, PerfCtx::MergeSemantic ms) {
    switch (ms) {
    case PerfCtx::MergeSemantic::to_parent:
        os << "Merge_To_Parent";
        break;
    case PerfCtx::MergeSemantic::scoped:
        os << "Parent_Scoped";
        break;
    case PerfCtx::MergeSemantic::scoped_strict:
        os << "Parent_Scoped (Strict)";
        break;
    case PerfCtx::MergeSemantic::stack_up:
        os << "Stack_up";
        break;
    case PerfCtx::MergeSemantic::duplicate:
        os << "Duplicate";
        break;
    default:
        os << "!!Unknown!!";
    }
    return os;
}

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
