#include <sstream>

#ifndef UTIL_H
#define UTIL_H

namespace {
    template <typename T> void to_s(std::stringstream& ss, T t) {
        ss << t;
    }

    template <typename T, typename... Args> void to_s(std::stringstream& ss, T t, Args... args) {
        to_s(ss, t);
        to_s(ss, args...);
    }
}

template <typename... Args> std::string to_s(Args... args) {
    std::stringstream ss;
    to_s(ss, args...);
    return ss.str();
}

#endif
