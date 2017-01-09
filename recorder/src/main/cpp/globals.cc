#include "globals.hh"

Time::Pt Time::now() {
    return std::chrono::steady_clock::now();
}

std::uint32_t Time::elapsed_seconds(const Pt& later, const Pt& earlier) {
    auto diff = std::chrono::duration_cast<sec>(later - earlier);
    return diff.count();
}
