#include <cstdint>
#include <stack>
#include <stdexcept>
#include <array>
#include <vector>
#include "util.hh"
#include <mutex>

#ifndef PERF_CTX_H
#define PERF_CTX_H

namespace PerfCtx {
    typedef std::uint64_t TracePt;
        
    class ConflictingDefinition : public std::runtime_error {
    public:
        ConflictingDefinition(const std::string& existing_def, const std::string& new_def) : runtime_error("Found conflicting perf-ctx defenition " + new_def + " which conflicts with " + existing_def) {}
        virtual ~ConflictingDefinition() {}
    };

    struct ThreadCtx {
        TracePt ctx;
        std::uint32_t push_count;
        struct {
            std::uint8_t start, end;
        } effective;

        ThreadCtx(TracePt _ctx, std::uint8_t start_idx, std::uint8_t end_idx) : ctx(_ctx), push_count(0) {
            effective.start = start_idx;
            effective.end = end_idx;
        }
    };
    
    class Registry {
    public:
        Registry() {}
        ~Registry() {}
        TracePt find_or_bind(const char* name, std::uint8_t coverage_pct, std::uint8_t merge_type) throw (ConflictingDefinition);
        TracePt merge_bind(const std::vector<ThreadCtx>& parent, TracePt child, bool strict = false);
    };

    class IncorrectEnterExitPairing : public std::runtime_error {
    public:
        IncorrectEnterExitPairing(const TracePt expected, const TracePt got) : runtime_error(to_s("Expected ", expected, " got ", got)) {};
        virtual ~IncorrectEnterExitPairing() {}
    };

    /*
      Ctx bit layout: it is composed of 3 parts, coverage %, merge-semantic and ctx-id and merge-semantic is not relevant for merge-generated ctx
      because they can never get pushed over some other context (other contexts may get pushed over them, but then the parent's merge-semantic
      doesn't matter).
          
      | bits [0 - 63] = 64 | [63 - 63] = 1 | [62 - 56] = 7 | [55 - 53] = 3  | [52 - 13] = 40 | [12 - 0] = 13                   |
      |--------------------+---------------+---------------+----------------+----------------+---------------------------------|
      | user-created       |             0 | coverage %    | merge-semantic | **unused**     | ctx-id (always a prime number)  |
      |--------------------+---------------+---------------+----------------+----------------+---------------------------------|

      13 bits can house 100th prime number (7919, which is less than 8192), so definitely sufficient for forseeable future.

      | bits [0 - 63] = 64 | [63 - 63] = 1 | [62 - 7] = 56               | [6 - 0] = 7                 |
      |--------------------+---------------+-----------------------------+-----------------------------|
      | merge-generated    |             1 | ctx-id part-0 (combination) | ctx-id part-1 (permutation) |
      |--------------------+---------------+-----------------------------+-----------------------------|

      This scheme allows us to pack all ctx-ids in jlong, hence allowing 0-lookup impl on perf-data processing path (sigprof handler, allocator-callback etc) while keeping things simple and fast. Obviously, it is possible to bump this limit by using 2 or 3 or more jlongs, but because this the final aggregated data cardinality needs to be such that its easy for humans to consume, such enhancements shoudldn't be necessary.

      This allows us to offer upto 349 user defined ctx, because 2351 is the 349th prime number and value of 2357^5 (next prime, 350th) is greater than 2^56. Since we support 5-depth nesting (as 2351^5 < 2^56) we need 7 bits for storing permutation-id (5! = 120, the next power of 2 is 128, ie 2^7).
          
    */

    const std::uint8_t MAX_NESTING = 5;

    constexpr std::uint64_t TYPE_MASK = static_cast<std::uint64_t>(0x80) << (7 * 8);
    constexpr std::uint64_t USER_CREATED_TYPE = 0x0;
    constexpr std::uint64_t MERGE_GENERATED_TYPE = static_cast<std::uint64_t>(0x80) << (7 * 8);

    constexpr std::uint64_t USER_CREATED_CTX_ID_MASK = 0x1FFF;

    constexpr std::uint8_t MERGE_SEMANTIIC_SHIFT = 53;
    constexpr std::uint8_t MERGE_SEMANTIIC_MASK = 7;

    constexpr std::uint8_t GENERATED_COMBINATION_SHIFT = 7;
    constexpr std::uint64_t GENERATED_COMBINATION_MAX_VALUE = 0xFFFFFFFFFFFFFF;
    constexpr std::uint8_t GENERATED_PERMUTATION_MAX_VALUE = 120;
    

    enum class MergeSemantic { to_parent = 0, scoped = 1, scoped_strict = 2, stack_up = 3, duplicate = 4 };

    MergeSemantic merge_semantic(TracePt pt);
    
    class ThreadTracker {
        Registry& reg;
        
        std::vector<ThreadCtx> actual_stack;
        std::vector<TracePt> effective;

        std::uint32_t ignore_count;

    public:
        ThreadTracker(Registry& _reg) : reg(_reg) {
            effective.reserve((MAX_NESTING * (MAX_NESTING + 1)) / 2);
        }
        ~ThreadTracker() {}

        void enter(TracePt pt);
        void exit(TracePt pt) throw (IncorrectEnterExitPairing);
        int current(std::array<TracePt, MAX_NESTING>& curr);
    };
};

#endif
