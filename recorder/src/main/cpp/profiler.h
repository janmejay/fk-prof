#ifndef PROFILER_H
#define PROFILER_H

#include <signal.h>
#include <unistd.h>
#include <chrono>
#include <sstream>
#include <string>

#include "globals.h"
#include "thread_map.h"
#include "signal_handler.h"
#include "stacktraces.h"
#include "processor.h"
#include "log_writer.h"
#include "profile_writer.h"

using namespace std::chrono;
using std::ofstream;
using std::ostringstream;
using std::string;

#include "trace.h"

const int kTraceProfilerTotal = 10;

const int kTraceProfilerStartFailed = 0;
const int kTraceProfilerStartOk = 1;
const int kTraceProfilerSetIntervalFailed = 2;
const int kTraceProfilerSetIntervalOk = 3;
const int kTraceProfilerSetFramesFailed = 4;
const int kTraceProfilerSetFramesOk = 5;
const int kTraceProfilerSetFileFailed = 6;
const int kTraceProfilerSetFileOk = 7;
const int kTraceProfilerStopFailed = 8;
const int kTraceProfilerStopOk = 9;

TRACE_DECLARE(Profiler, kTraceProfilerTotal);

template <bool blocking = true>
class SimpleSpinLockGuard {
private:
    std::atomic_bool& f;
    bool rel;

public:
    SimpleSpinLockGuard(std::atomic_bool& field, bool relaxed = false) : f(field), rel(relaxed) {
        bool expectedState = false;
        while (!f.compare_exchange_weak(expectedState, true, std::memory_order_acquire)) {
            expectedState = false;
            sched_yield();
        }
    }

    ~SimpleSpinLockGuard() {
        f.store(false, rel ? std::memory_order_relaxed : std::memory_order_release);
    }
};

template <>
class SimpleSpinLockGuard<false> {
public:
    SimpleSpinLockGuard(std::atomic_bool& field) {
        field.load(std::memory_order_acquire);
    }

    ~SimpleSpinLockGuard() {}
};

class ProfileSerializer : public QueueListener {
private:
    ProfileWriter& w;
    
public:
    ProfileSerializer(ProfileWriter& _w) : w(_w) {}

    ~ProfileSerializer() {}

    virtual void record(const JVMPI_CallTrace &item, ThreadBucket *info = nullptr) {}
};

class Profiler {
public:
    explicit Profiler(JavaVM *_jvm, jvmtiEnv *_jvmti, ThreadMap &_thread_map, ProfileWriter& _writer, std::uint16_t _max_stack_depth, std::uint16_t _sampling_freq)
        : jvm(_jvm), jvmti(_jvmti), thread_map(_thread_map), writer(_writer), ongoing_conf(false) {
        set_max_stack_depth(_max_stack_depth);
        set_sampling_freq(_sampling_freq);
        configure();
    }

    bool start(JNIEnv *jniEnv);

    void stop();

    void handle(int signum, siginfo_t *info, void *context);

    ~Profiler();

private:
    JavaVM *jvm;

    jvmtiEnv *jvmti;

    ThreadMap &thread_map;

    std::uint32_t max_stack_depth;

    std::uint32_t itvl_min, itvl_max;

    ProfileWriter& writer;

    CircularQueue *buffer;

    Processor *processor;

    SignalHandler* handler;

    ProfileSerializer* serializer;

    // indicates change of internal state
    std::atomic<bool> ongoing_conf;

    static bool lookup_frame_information(const JVMPI_CallFrame &frame,
                                       jvmtiEnv *jvmti,
                                       MethodListener &logWriter);

    void set_sampling_freq(std::uint32_t sampling_freq);

    void set_max_stack_depth(int maxFramesToCapture);

    void configure();

    inline std::uint32_t capture_stack_depth() {
        return max_stack_depth + 1;
    }

    bool __is_running();

    DISALLOW_COPY_AND_ASSIGN(Profiler);
};

#endif // PROFILER_H
