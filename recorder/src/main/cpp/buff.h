#ifndef BUFF_H
#define BUFF_H

#include "globals.h"
#include <algorithm>

struct Buff { //TODO: test me
    static const std::uint32_t BUFF_SZ = 0xFFFF;
    std::uint8_t *buff;
    std::uint32_t capacity, write_end, read_end;

    Buff(std::uint32_t initial_sz = BUFF_SZ) {
        buff = new std::uint8_t[initial_sz];
        write_end = read_end = 0;
        capacity = initial_sz;
    }

    ~Buff() {
        delete buff;
    }

    inline void ensure_free(std::uint32_t min_available) {
        assert(write_end >= read_end);
        if ((capacity - write_end) < min_available) {
            std::uint32_t new_capacity = std::max(capacity, min_available) * 2;
            std::uint8_t* bigger_buff = new std::uint8_t[new_capacity];
            assert(bigger_buff != nullptr);
            if ((write_end - read_end) > 0) {
                memcpy(bigger_buff, buff + read_end, write_end - read_end);
                write_end = write_end - read_end;
                read_end = 0;
            } else {
                read_end = write_end = 0;
            }
            delete buff;
            buff = bigger_buff;
        }
    }
};

#endif
