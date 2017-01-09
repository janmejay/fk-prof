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
#include "scheduler.hh"

#define MAX_DATA_SIZE 100

class Controller {
public:
    explicit Controller(JavaVM *_jvm, jvmtiEnv *_jvmti, ThreadMap& _thread_map, ConfigurationOptions& _cfg) :
        jvm(_jvm), jvmti(_jvmti), thread_map(_thread_map), cfg(_cfg), running(false) {
        current_work.set_work_id(0);
        current_work_state = recording::WorkResponse::complete;
        current_work_result = recording::WorkResponse::success;
    }

    void start();

    void stop();

    bool is_running() const;

    friend void controllerRunnable(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg);

private:
    JavaVM *jvm;
    jvmtiEnv *jvmti;
    ThreadMap& thread_map;
    ConfigurationOptions& cfg;
    Profiler *profiler;
    std::atomic_bool running;
    Buff buff;
    ProfileWriter *writer;

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
    
    void run_with_associate(const Buff& associate_response_buff, const std::chrono::time_point<std::chrono::steady_clock>& start_time);

    void accept_work(Buff& poll_response_buff);

    void with_current_work(std::function<void(W&, WSt&, WRes&, Time::Pt&, Time::Pt&)> proc);

    void issueWork();

    void retireWork();
};

#endif
