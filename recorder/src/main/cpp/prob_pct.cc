#include "prob_pct.hh"
#include <fstream>

ProbPct::ProbPct() {
    std::ifstream in("/dev/urandom", std::ios_base::in | std::ios_base::binary);
    std::uint8_t b;
    for (auto i = 0; i < SZ;) {
        in >> b;
        if (b >= 200) continue;
        random_nos[i++] = b % 100;
    }
}

bool ProbPct::on(std::uint32_t counter, std::uint8_t pct) {
    return random_nos[counter % SZ] < pct;
}

