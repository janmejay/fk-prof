#include "test_helpers.hh"
#include <unordered_map>
#include <fstream>

__attribute__ ((noinline)) void some_λ_caller(std::function<void()> fn) {
    fn();
}
