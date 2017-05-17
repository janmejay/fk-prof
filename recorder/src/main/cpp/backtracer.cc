#include "backtracer.hh"

std::uint32_t Backtracer::fill_backtrace(NativeFrame* buff, std::uint32_t capacity) {
    std::uint64_t rbp, rpc, rax;
    asm("movq %%rbp, %%rax;"
        "movq %%rax, %0;"
        "lea (%%rip), %%rax;"
        "movq %%rax, %1;"
        : "=r"(rbp), "=r"(rpc)
        :);

    syms.print_frame(rpc);
    std::uint32_t i = 0;
    while ((capacity - i) > 0) {
        rpc = *reinterpret_cast<std::uint64_t*>(rbp + 8);
        if (rpc == 0) break;
        buff[i] = rpc;
        rbp = *reinterpret_cast<std::uint64_t*>(rbp);
        if (rbp == 0) break;
        i++;
    }
    return i;
}


