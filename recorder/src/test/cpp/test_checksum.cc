#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include "fixtures.hh"
#include "test.hh"
#include "../../main/cpp/checksum.hh"

TEST(Adler32_calculation_simple) {
    Checksum c;
    std::uint8_t hello[] = {'H', 'e', 'l', 'l', 'o'};
    std::uint32_t adler = c.chksum(hello, 5);
    CHECK_EQUAL(0x58c01f5, adler);
    std::uint8_t world[] = {'W', 'o', 'r', 'l', 'd'};
    adler = c.chksum(world, 5);
    CHECK_EQUAL(0x155603fd, adler);
    c.reset();
    adler = c.chksum(world, 5);
    CHECK_EQUAL(0x6060209, adler);
}


