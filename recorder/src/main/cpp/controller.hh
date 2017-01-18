#ifndef HONEST_PROFILER_CONTROLLER_H
#define HONEST_PROFILER_CONTROLLER_H

#include "globals.hh"
#include "common.hh"
#include "profiler.hh"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <jvmti.h>
#include <atomic>
#include "profile_writer.hh"
#include "config.hh"
#include <functional>
#include <queue>
#include <memory>
#include "scheduler.hh"
#include "blocking_ring_buffer.hh"
#include "ti_thd.hh"

#define MAX_DATA_SIZE 100

class Controller {
public:
    explicit Controller(JavaVM *_jvm, jvmtiEnv *_jvmti, ThreadMap& _thread_map, ConfigurationOptions& _cfg) :
        jvm(_jvm), jvmti(_jvmti), thread_map(_thread_map), cfg(_cfg), keep_running(false), raw_writer(nullptr), writer(nullptr), profiler(nullptr) {
        current_work.set_work_id(0);
        current_work_state = recording::WorkResponse::complete;
        current_work_result = recording::WorkResponse::success;
    }

    virtual ~Controller() {}

    void start();

    void stop();

    bool is_running() const;

    friend void controllerRunnable(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg);

private:
    JavaVM *jvm;
    jvmtiEnv *jvmti;
    ThreadMap& thread_map;
    ConfigurationOptions& cfg;
    std::atomic_bool keep_running;
    ThdProcP thd_proc;
    Buff buff;
    std::unique_ptr<RawWriter> raw_writer;
    std::unique_ptr<ProfileWriter> writer;
    std::unique_ptr<Profiler> profiler;
    BlockingRingBuffer raw_writer_ring;

    Scheduler scheduler;
    
    std::mutex current_work_mtx;
    typedef recording::WorkAssignment W;
    typedef recording::WorkResponse::WorkState WSt;
    typedef recording::WorkResponse::WorkResult WRes;
    W current_work;
    WSt current_work_state;
    WRes current_work_result;
    Time::Pt work_start, work_end;

    void startSampling();

    void stopSampling();

    void run();
    
    void run_with_associate(const Buff& associate_response_buff, const Time::Pt& start_time);

    void accept_work(Buff& poll_response_buff, const std::string& host, const std::uint32_t port);

    void with_current_work(std::function<void(W&, WSt&, WRes&, Time::Pt&, Time::Pt&)> proc);

    void issueWork(const std::string& host, const std::uint32_t port);
    void retireWork(const std::uint64_t work_id);

    void issue(const recording::Work& w);
    void retire(const recording::Work& w);

    void issue(const recording::CpuSampleWork& csw);
    void retire(const recording::CpuSampleWork& csw);
};

#endif
