#ifndef PROCESSOR_H
#define PROCESSOR_H

#include <jvmti.h>
#include "common.hh"
#include "signal_handler.hh"
#include "circular_queue.hh"
#include "ti_thd.hh"
#include "trace.hh"

const int kTraceProcessorTotal = 3;

const int kTraceProcessorStart = 0;
const int kTraceProcessorStop = 1;
const int kTraceProcessorRunning = 2;

TRACE_DECLARE(Processor, kTraceProcessorTotal);


class Processor {

public:
    explicit Processor(jvmtiEnv* jvmti, CircularQueue& buffer, SignalHandler& handler, int interval);

    void start(JNIEnv *jniEnv);

    void run();

    void stop();

    bool isRunning() const;

private:
    jvmtiEnv* jvmti_;

    CircularQueue& buffer_;

    std::atomic_bool isRunning_;

    SignalHandler& handler_;

    int interval_;

    ThdProcP thd_proc;

    metrics::Hist& s_h_pop_spree_len;
    metrics::Timer& s_t_pop_spree_tm;
    metrics::Timer& s_t_yield_tm;

    void startCallback(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg);

    DISALLOW_COPY_AND_ASSIGN(Processor);
};

#endif // PROCESSOR_H
