#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include <chrono>
#include "fixtures.hh"
#include "test.hh"
#include <site_resolver.hh>
#include "test_helpers.hh"
#include <set>

__attribute__ ((noinline)) int foo(NativeFrame* buff, std::uint32_t sz) {
    return Stacktraces::fill_backtrace(buff, sz);
}

__attribute__ ((noinline)) int caller_of_foo(NativeFrame* buff, std::uint32_t sz) {
    return foo(buff, sz);
}

TEST(SiteResolver__should_resolve_backtrace) {
    TestEnv _;
    const std::uint32_t buff_sz = 100;
    NativeFrame buff[buff_sz];
    std::uint32_t bt_len;
    some_λ_caller([&]() {
            bt_len = caller_of_foo(buff, buff_sz);
        });
    CHECK(bt_len > 4);

    SiteResolver::SymInfo s_info;
    std::string fn_name;
    SiteResolver::Addr pc_offset;
    s_info.site_for(buff[0], fn_name, pc_offset);
    CHECK_EQUAL("foo(unsigned long*, unsigned int)", fn_name);
    s_info.site_for(buff[1], fn_name, pc_offset);
    CHECK_EQUAL("caller_of_foo(unsigned long*, unsigned int)", fn_name);

    std::set<std::string> some_recent_frames;
    for (auto i = bt_len; i > 0; i--) {
        s_info.site_for(buff[i - 1], fn_name, pc_offset);
        some_recent_frames.insert(fn_name);
    }

    CHECK(some_recent_frames.find("some_λ_caller(std::function<void ()>)") != std::end(some_recent_frames));//this symbol comes from a shared-lib (aim is to ensure it works well with relocatable symbols)
}

