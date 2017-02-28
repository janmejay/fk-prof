#include "checksum.hh"
#include <cassert>
#include <zlib.h>

void Checksum::reset() {
    adler = ::adler32(0, Z_NULL, 0);
}

uint32_t Checksum::chksum(const std::uint8_t *buf, uint32_t len) {
    adler = ::adler32(adler, buf, len);
    assert(!(adler >> 32));
    return adler;
}
