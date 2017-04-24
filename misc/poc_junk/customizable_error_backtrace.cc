#include <cstdint>
#include <iostream>

void print_bt() {
    // asm("movq $1729, %rax");
    std::uint64_t rbp, rpc, rax;
    asm("movq %%rax, %2;"
        "movq %%rbp, %%rax;"
        "movq %%rax, %0;"
        "lea (%%rip), %%rax;"
        "movq %%rax, %1;"
        "movq %3, %%rax;"
        : "=r"(rbp), "=r"(rpc), "=r"(rax)
        : "r"(rax));

    // std::uint64_t rax_rr;
    // asm("movq %%rax, %0" : "=r" (rax_rr));
    // std::cout << "RAX_rr: " << rax_rr << " " << std::hex << rax_rr << '\n';
    std::cout << "base: 0x" << std::hex << rbp << "    PC: 0x" << std::hex << rpc << '\n';

    while (true) {
        std::cout << "0x" << std::hex << rpc << '\n';
        rbp = *reinterpret_cast<std::uint64_t*>(rbp);
        if (rbp == 0) break;
        rpc = *reinterpret_cast<std::uint64_t*>(rbp + 8);
        if (rpc == 0) break;
    }
}

int baz() {
    print_bt();
    return 5;
}

int bar() {
    return 5 + baz();
}

int foo(int x) {
    auto b = bar();
    return x - b;
}

int foo() {
    return foo(10);
}

int main() {
    return foo();
}
