#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include "fixtures.hh"
#include "test.hh"
#include "../../main/cpp/globals.hh"
#include "../../main/cpp/thread_perf_ctx_tracker.hh"

/*** a simple test to calculate index of given permutation of chars, will later adapt it for numbers

  int main(int argc, char** argv) {
    if (argc < 2) { return -1; }
    
    const char* s = argv[1];
    auto l = strlen(s);
    auto fac = 1;
    auto val = 0;
    for (auto i = 0; i < l; i++) {
      auto fv = s[i] - '0';

      auto rank = 1;

      for (auto j = 0; j < i; j++) {
        if (s[i] > s[j]) {
          rank++;
        }
      }
      val += fac * (rank - 1);
      fac *= (i + 1);
    }
    
    std::cout << val << "\n";
    
    return 0;
  }

  written haphazardly, basically based on the fact that nth element picked from a set for a certain position while permuting, falls at (n! / n * rank) th place.

  This along with pi(prime-nos) uniquely identifies a permuted instance of set mPk, where mCk is identified by prime-number multiplication + one of k! members of the set identified by the permutation-idx.
 ***/

TEST(ThreadPerfCtxTracker__can_push_and_pops) {
    init_logger();
    PerfCtx::ThreadTracker t_ctx;

    CHECK_EQUAL(0, t_ctx.current());
    t_ctx.enter(10);
    CHECK_EQUAL(10, t_ctx.current());
    t_ctx.enter(20);
    CHECK_EQUAL(20, t_ctx.current());
    t_ctx.exit(20);
    CHECK_EQUAL(10, t_ctx.current());
    t_ctx.exit(10);
    CHECK_EQUAL(0, t_ctx.current());
}

