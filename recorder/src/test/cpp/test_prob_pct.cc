#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include "fixtures.hh"
#include "test.hh"
#include "../../main/cpp/globals.hh"
#include "../../main/cpp/prob_pct.hh"

TEST(ProbPct__should_hit_desired_pct_of_times) {
    init_logger();

    ProbPct pp;
    auto hits = 0;
    for (auto i = 0; i < 2000; i++) {
        if (pp.on(i, 10)) hits++;
    }
    CHECK_CLOSE(200, hits, 40);
}
