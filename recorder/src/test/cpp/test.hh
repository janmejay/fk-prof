#ifndef HONEST_PROFILER_TEST_H
#define HONEST_PROFILER_TEST_H

#ifdef __APPLE__
#include <UnitTest++/UnitTest++/UnitTest++.h>
#else
#include <UnitTest++.h>
#endif

#include "../../main/cpp/globals.hh"

void init_logger();

#endif //HONEST_PROFILER_TEST_H
