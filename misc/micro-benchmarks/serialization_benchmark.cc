#include <benchmark.h>
#include <stacktraces.h>
#include <cstdlib>
#include <recorder.pb.h>

static void SerializeInto_JVMPI_struct(benchmark::State& state) {
    time_t t;
    srand((unsigned) time(&t));
    JVMPI_CallFrame *src = new JVMPI_CallFrame[state.range(0)];
    for (auto i = 0; i < state.range(0); i++) {
        src[i].lineno = rand();
        src[i].method_id = (jmethodID) rand();
    }
    JVMPI_CallFrame *fb = new JVMPI_CallFrame[state.range(0)];
    while (state.KeepRunning()) {
        for (auto i = 0; i < state.range(0); i++) {
            fb[i].lineno = src[i].lineno;
            fb[i].method_id = src[i].method_id;
        }
    }
}
BENCHMARK(SerializeInto_JVMPI_struct)->RangeMultiplier(2)->Range(32, 8<<10);

static void SerializeInto_FkProf_DTO(benchmark::State& state) {
    time_t t;
    srand((unsigned) time(&t));
    JVMPI_CallFrame *src = new JVMPI_CallFrame[state.range(0)];
    recording::StackSample ss;
    for (auto i = 0; i < state.range(0); i++) {
        src[i].lineno = rand();
        src[i].method_id = (jmethodID) rand();
        ss.add_frame();
    }
    
    while (state.KeepRunning()) {
        for (auto i = 0; i < state.range(0); i++) {
            recording::Frame *f = ss.mutable_frame(i);
            f->set_method_id(10 * (long) src[i].method_id);
            f->set_line_no(src[i].lineno);
        }
    }
        
}
BENCHMARK(SerializeInto_FkProf_DTO)->RangeMultiplier(2)->Range(32, 8<<10);

BENCHMARK_MAIN();
