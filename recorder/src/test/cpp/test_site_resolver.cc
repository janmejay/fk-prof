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

    auto max_path_sz = 1024;
    std::unique_ptr<char[]> link_path(new char[max_path_sz]);
    auto path_len = readlink("/proc/self/exe", link_path.get(), max_path_sz);
    CHECK((path_len > 0) && (path_len < max_path_sz));//ensure we read link correctly
    link_path.get()[path_len] = '\0';
    std::string path = link_path.get();

    SiteResolver::SymInfo s_info;
    std::string fn_name;
    std::string file_name;
    SiteResolver::Addr pc_offset;
    s_info.site_for(buff[0], file_name, fn_name, pc_offset);
    CHECK_EQUAL("foo(unsigned long*, unsigned int)", fn_name);
    CHECK_EQUAL(path, file_name);
    s_info.site_for(buff[1], file_name, fn_name, pc_offset);
    CHECK_EQUAL("caller_of_foo(unsigned long*, unsigned int)", fn_name);
    CHECK_EQUAL(path, file_name);

    std::map<std::string, std::string> fn_files;
    for (auto i = bt_len; i > 0; i--) {
        s_info.site_for(buff[i - 1], file_name, fn_name, pc_offset);
        fn_files[fn_name] = file_name;
    }

    auto it = fn_files.find("some_λ_caller(std::function<void ()>)");
    CHECK(it != std::end(fn_files));//this symbol comes from a shared-lib (aim is to ensure it works well with relocatable symbols)

    auto dir = path.substr(0, path.rfind("/"));
    CHECK_EQUAL(0, it->second.find(dir));
    CHECK_EQUAL("/libtestutil.so", it->second.substr(dir.length()));
}

