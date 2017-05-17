#include "error_backtrace_different_compilation_unit_helper.hh"
#include <atomic>

std::atomic<bool> x;

void Foo::quux() {
    x.store(true, std::memory_order_relaxed);
    print_bt();
}

int foo(int x) {
    auto b = bar();
    return x - b;
}

int foo() {
    return foo(10);
}
