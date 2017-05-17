#ifndef BACKTRACER_H
#define BACKTRACER_H

#include "stacktraces.hh"

namespace Backtracer {
    std::uint32_t fill_backtrace(NativeFrame* buff, std::uint32_t capacity);
}

#endif
