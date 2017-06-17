#include "test_helpers.hh"
#include <unordered_map>
#include <fstream>
#include <libgen.h>

__attribute__ ((noinline)) void some_lambda_caller(std::function<void()> fn) {
    fn();
}

std::atomic<int> test_sink {0};

extern "C" __attribute__ ((noinline)) void fn_c_foo() {
    test_sink++;
    bt_pusher();
    test_sink++;
}

__attribute__ ((noinline)) void Bar::fn_bar(int bt_capture_depth) {
    test_sink += 10;
    if (bt_capture_depth == 0) {
        bt_pusher();
    } else {
        fn_c_foo();
    }
    test_sink += 10;
}

__attribute__ ((noinline)) void fn_baz(int bt_capture_depth) {
    test_sink += 100;
    if (bt_capture_depth == 0) {
        bt_pusher();
    } else {
        Bar::fn_bar(--bt_capture_depth);
    }
    test_sink += 100;
}

__attribute__ ((noinline)) void fn_quux(int r, int bt_capture_depth) {
    test_sink += r;
    if (bt_capture_depth == 0) {
        bt_pusher();
    } else {
        fn_baz(--bt_capture_depth);
    }
    test_sink += r;
}

__attribute__ ((noinline)) void fn_corge(int p, int q, int bt_capture_depth) {
    test_sink += (p - q);
    if (bt_capture_depth == 0) {
        bt_pusher();
    } else {
        fn_quux(q, --bt_capture_depth);
    }
    test_sink += (p - q);
}

std::string my_executable() {
    char link_path[PATH_MAX];
    auto path_len = readlink("/proc/self/exe", link_path, PATH_MAX);
    link_path[path_len] = '\0';
    return {link_path};
}

std::string my_test_helper_lib() {
    auto exec_path = my_executable();
    auto exec_path_cstr = strdup(exec_path.c_str());
    auto dir_name = dirname(exec_path_cstr);
    std::string dir_name_str {dir_name};
    dir_name_str += LIB_TEST_UTIL;
    return dir_name_str;
}
