#ifndef PROB_PCT_H
#define PROB_PCT_H

#include <cstdint>

class ProbPct { //allows trials by probablity expressed as percentage
    static const std::uint32_t SZ = 1024;
    std::uint8_t random_nos[SZ];
public:
    ProbPct();
    ~ProbPct() {}

    bool on(std::uint32_t counter, std::uint8_t pct);
};

#endif
