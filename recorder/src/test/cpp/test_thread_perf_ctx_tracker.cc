#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include "fixtures.hh"
#include "test.hh"
#include "../../main/cpp/globals.hh"
#include "../../main/cpp/perf_ctx.hh"

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

using namespace PerfCtx;


std::ostream& operator<<(std::ostream& os, MergeSemantic ms) {
    switch (ms) {
    case MergeSemantic::to_parent:
        os << "Merge_To_Parent";
        break;
    case MergeSemantic::scoped:
        os << "Parent_Scoped";
        break;
    case MergeSemantic::scoped_strict:
        os << "Parent_Scoped (Strict)";
        break;
    case MergeSemantic::stack_up:
        os << "Stack_up";
        break;
    case MergeSemantic::duplicate:
        os << "Duplicate";
        break;
    default:
        os << "!!Unknown!!";
    }
    return os;
}

TEST(PerfCtx__should_understand_ctx_merge_semantics) {
    init_logger();
    CHECK_EQUAL(MergeSemantic::to_parent, merge_semantic(0));
    CHECK_EQUAL(MergeSemantic::scoped, merge_semantic((std::uint64_t) 1 << 53));
    CHECK_EQUAL(MergeSemantic::scoped_strict, merge_semantic((std::uint64_t) 2 << 53));
    CHECK_EQUAL(MergeSemantic::stack_up, merge_semantic((std::uint64_t) 3 << 53));
    CHECK_EQUAL(MergeSemantic::duplicate, merge_semantic((std::uint64_t) 4 << 53));
}

TEST(ThreadPerfCtxTracker__should_understand_ctx__when_merging_to_parent) {
    init_logger();
    Registry r;
    ThreadTracker t_ctx(r);

    std::array<TracePt, MAX_NESTING> curr;

    CHECK_EQUAL(0, t_ctx.current(curr));
    t_ctx.enter(10);
    CHECK_EQUAL(1, t_ctx.current(curr));
    std::array<TracePt, 1> expected{{10}};
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    t_ctx.enter(20);
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    t_ctx.enter(30);
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    t_ctx.exit(30);
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    t_ctx.exit(20);
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    t_ctx.exit(10);
    CHECK_EQUAL(0, t_ctx.current(curr));
}

TEST(ThreadPerfCtxTracker__should_not_allow_unpaired_pop__when_merging_to_parent) {
    init_logger();
    Registry r;
    ThreadTracker t_ctx(r);
    std::array<TracePt, MAX_NESTING> curr;

    CHECK_EQUAL(0, t_ctx.current(curr));
    t_ctx.enter(10);
    t_ctx.enter(20);
    try {
        t_ctx.exit(10);
        CHECK(false); //should never reach here
    } catch (const IncorrectEnterExitPairing& e) {
        CHECK_EQUAL("Expected 20 got 10", e.what());
    }
    std::array<TracePt, 1> expected{{10}};
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    t_ctx.exit(20);
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    t_ctx.exit(10);
    CHECK_EQUAL(0, t_ctx.current(curr));
}

TEST(ThreadPerfCtxTracker__should_understand_ctx__when_scoping_under_parent) {
    init_logger();
    Registry r;
    ThreadTracker t_ctx(r);

    constexpr auto SCOPED_MASK = static_cast<std::uint64_t>(MergeSemantic::scoped) << PerfCtx::MERGE_SEMANTIIC_SHIFT;

    std::array<TracePt, MAX_NESTING> curr;

    CHECK_EQUAL(0, t_ctx.current(curr));
    t_ctx.enter(2);
    CHECK_EQUAL(1, t_ctx.current(curr));
    std::array<TracePt, 1> expected{{2}};
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    
    t_ctx.enter(SCOPED_MASK | 5);
    CHECK_EQUAL(1, t_ctx.current(curr));
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5) << GENERATED_COMBINATION_SHIFT);
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.enter(SCOPED_MASK | 4);
    CHECK_EQUAL(1, t_ctx.current(curr));
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4) << GENERATED_COMBINATION_SHIFT) | 0x1;
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.enter(SCOPED_MASK | 3);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4 * 3) << GENERATED_COMBINATION_SHIFT) | 0x5;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    //now we exit 2 nearest scopes and push them in opposite order
    
    t_ctx.exit(SCOPED_MASK | 3);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4) << GENERATED_COMBINATION_SHIFT) | 0x1;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.exit(SCOPED_MASK | 4);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5) << GENERATED_COMBINATION_SHIFT);
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.enter(SCOPED_MASK | 3);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 3) << GENERATED_COMBINATION_SHIFT) | 0x1;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.enter(SCOPED_MASK | 4);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 3 * 4) << GENERATED_COMBINATION_SHIFT) | 0x4;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    //permutation id now is 4 and not 5, while combination-id is the same (this distinguishes 2>5>3>4 from 2>5>4>3

    t_ctx.exit(SCOPED_MASK | 4);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 3) << GENERATED_COMBINATION_SHIFT) | 0x1;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.exit(SCOPED_MASK | 3);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5) << GENERATED_COMBINATION_SHIFT);
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.exit(SCOPED_MASK | 5);
    expected[0] = 2;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.exit(2);
    CHECK_EQUAL(0, t_ctx.current(curr));
}

TEST(ThreadPerfCtxTracker__not_nest_beyond_max_depth__when_scoping_under_parent) {
    init_logger();
    Registry r;
    ThreadTracker t_ctx(r);

    constexpr auto SCOPED_MASK = static_cast<std::uint64_t>(MergeSemantic::scoped) << PerfCtx::MERGE_SEMANTIIC_SHIFT;

    std::array<TracePt, MAX_NESTING> curr;

    CHECK_EQUAL(0, t_ctx.current(curr));
    t_ctx.enter(2);
    CHECK_EQUAL(1, t_ctx.current(curr));
    std::array<TracePt, 1> expected{{2}};
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    
    t_ctx.enter(SCOPED_MASK | 5);
    CHECK_EQUAL(1, t_ctx.current(curr));
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5) << GENERATED_COMBINATION_SHIFT);
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.enter(SCOPED_MASK | 4);
    CHECK_EQUAL(1, t_ctx.current(curr));
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4) << GENERATED_COMBINATION_SHIFT) | 0x1;
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.enter(SCOPED_MASK | 3);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4 * 3) << GENERATED_COMBINATION_SHIFT) | 0x5;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.enter(SCOPED_MASK | 7);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4 * 3 * 7) << GENERATED_COMBINATION_SHIFT) | 0xE;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.enter(SCOPED_MASK | 11);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4 * 3 * 7) << GENERATED_COMBINATION_SHIFT) | 0xE;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.enter(SCOPED_MASK | 13);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4 * 3 * 7) << GENERATED_COMBINATION_SHIFT) | 0xE;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);


    t_ctx.exit(42); //this is gibberish, doesn't matter, because ctx-tracer is ignoring it (its beyond 5 levels)
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);
    t_ctx.exit(42); //gibberish again, same as above
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    try {
        t_ctx.exit(42); //now gibberish won't work, because ctx is tracking it
        CHECK(false); //should never reach here
    } catch (const IncorrectEnterExitPairing& e) {
        CHECK_EQUAL(to_s("Expected ", SCOPED_MASK | 7, " got 42"), e.what());
    }
    
    t_ctx.exit(SCOPED_MASK | 7);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4 * 3) << GENERATED_COMBINATION_SHIFT) | 0x5;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.exit(SCOPED_MASK | 3);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5 * 4) << GENERATED_COMBINATION_SHIFT) | 0x1;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.exit(SCOPED_MASK | 4);
    expected[0] = MERGE_GENERATED_TYPE | ((2 * 5) << GENERATED_COMBINATION_SHIFT);
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.exit(SCOPED_MASK | 5);
    expected[0] = 2;
    CHECK_EQUAL(1, t_ctx.current(curr));
    CHECK_ARRAY_EQUAL(expected, curr, 1);

    t_ctx.exit(2);
    CHECK_EQUAL(0, t_ctx.current(curr));
}
