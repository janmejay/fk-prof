#include <thread>
#include <iostream>
#include "processor.hh"

#ifdef WINDOWS
#include <windows.h>
#else

#include <unistd.h>

#endif

const uint MILLIS_IN_MICRO = 1000;

void sleep_for_millis(uint period) {
#ifdef WINDOWS
    Sleep(period);
#else
    usleep(period * MILLIS_IN_MICRO);
#endif
}

TRACE_DEFINE_BEGIN(Processor, kTraceProcessorTotal)
    TRACE_DEFINE("start processor")
    TRACE_DEFINE("stop processor")
    TRACE_DEFINE("chech that processor is running")
TRACE_DEFINE_END(Processor, kTraceProcessorTotal);

void Processor::run() {
    int popped = 0;

    while (true) {
        while (buffer_.pop()) {
            ++popped;
        }

        if (popped > 200) {
            if (!handler_.updateSigprofInterval()) {
                break;
            }
            popped = 0;
        }

        if (!isRunning_.load(std::memory_order_relaxed)) {
            break;
        }

        sched_yield();
    }

    handler_.stopSigprof();
    // no shared data access after this point, can be safely deleted
}

void callbackToRunProcessor(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg) {
    Processor* processor = static_cast<Processor*>(arg);
    processor->run();
}

void Processor::start(JNIEnv *jniEnv) {
    TRACE(Processor, kTraceProcessorStart);
    isRunning_.store(true, std::memory_order_relaxed);
    thd_proc = start_new_thd(jniEnv, jvmti_, "Fk-Prof Processing Thread", callbackToRunProcessor, this);
}

void Processor::stop() {
    TRACE(Processor, kTraceProcessorStop);
    isRunning_.store(false, std::memory_order_relaxed);
    await_thd_death(thd_proc);
    thd_proc.reset();
}

bool Processor::isRunning() const {
    TRACE(Processor, kTraceProcessorRunning);
    return isRunning_.load(std::memory_order_relaxed);
}
