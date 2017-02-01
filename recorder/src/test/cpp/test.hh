#ifndef HONEST_PROFILER_TEST_H
#define HONEST_PROFILER_TEST_H

#ifdef __APPLE__
#include <UnitTest++/UnitTest++/UnitTest++.h>
#else
#include <UnitTest++.h>
#endif

#include <iostream>
#include "../../main/cpp/globals.hh"
#include "../../main/cpp/perf_ctx.hh"

std::ostream& operator<<(std::ostream& os, PerfCtx::MergeSemantic ms);

void init_logger();

#endif //HONEST_PROFILER_TEST_H
