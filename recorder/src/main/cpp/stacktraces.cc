#include "stacktraces.hh"

std::uint32_t Stacktraces::fill_backtrace(NativeFrame* buff, std::uint32_t capacity) {//TODO: write me 3 tests { (capacity > stack), (stack > capacity) and (stack == capacity) }
    std::uint64_t rbp, rpc;
    asm("movq %%rbp, %%rax;"
        "movq %%rax, %0;"
        "lea (%%rip), %%rax;"
        "movq %%rax, %1;"
        : "=r"(rbp), "=r"(rpc)
        :
        : "rax");

    //not adding current PC, because we are anyway not interested in showing ourselves on the backtrace
    std::uint32_t i = 0;
    while ((capacity - i) > 0) {
        rpc = *reinterpret_cast<std::uint64_t*>(rbp + 8);
        //if (rpc == 0) break;
        buff[i] = rpc;
        rbp = *reinterpret_cast<std::uint64_t*>(rbp);
        if (rbp == 0) break;
        i++;
    }
    return i;
}
