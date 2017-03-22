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

#define METRIC_TYPE "cpu_samp_proc"

Processor::Processor(jvmtiEnv* jvmti, CircularQueue& buffer, SignalHandler& handler, int interval)
    : jvmti_(jvmti), buffer_(buffer), isRunning_(false), handler_(handler), interval_(interval),

      s_h_pop_spree_len(GlobalCtx::metrics_registry->new_histogram({METRICS_DOMAIN, METRIC_TYPE, "pop_spree", "length"})),
      s_t_pop_spree_tm(GlobalCtx::metrics_registry->new_timer({METRICS_DOMAIN, METRIC_TYPE, "pop_spree", "time"})),

      s_t_yield_tm(GlobalCtx::metrics_registry->new_timer({METRICS_DOMAIN, METRIC_TYPE, "sched_yield", "time"})) {}


void Processor::run() {
    int popped = 0;

    while (true) {
        {
            auto _ = s_t_pop_spree_tm.time_scope();

            int poppped_before = popped;
            while (buffer_.pop()) ++popped;

            s_h_pop_spree_len.update(popped - poppped_before);
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

        {
            auto _ = s_t_yield_tm.time_scope();
            sched_yield();
        }
    }

    handler_.stopSigprof();
    // no shared data access after this point, can be safely deleted
}

void callbackToRunProcessor(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg) {
    Processor* processor = static_cast<Processor*>(arg);
    processor->run();
}

void Processor::start(JNIEnv *jniEnv) {
    isRunning_.store(true, std::memory_order_relaxed);
    thd_proc = start_new_thd(jniEnv, jvmti_, "Fk-Prof Processing Thread", callbackToRunProcessor, this);
}

void Processor::stop() {
    isRunning_.store(false, std::memory_order_relaxed);
    await_thd_death(thd_proc);
    thd_proc.reset();
}

bool Processor::isRunning() const {
    return isRunning_.load(std::memory_order_relaxed);
}
