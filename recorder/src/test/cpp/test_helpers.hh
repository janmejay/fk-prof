#ifndef TEST_HELPERS_H
#define TEST_HELPERS_H

#include <string>
#include <stacktraces.hh>

__attribute__ ((noinline)) void some_λ_caller(std::function<void()> fn);

#endif /* TEST_PROFILE_H */
