#include <cstdint>

class Checksum {
private:
    std::uint64_t adler;
    
public:
    Checksum() { reset(); }
    
    ~Checksum() {}
    
    uint32_t chksum(const std::uint8_t *buf, uint32_t len);

    void reset();
};
