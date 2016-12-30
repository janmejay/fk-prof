#ifndef HONEST_PROFILER_CONTROLLER_H
#define HONEST_PROFILER_CONTROLLER_H

#include "globals.h"
#include "common.h"
#include "profiler.h"
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
#include "profile_writer.h"

#define MAX_DATA_SIZE 100

class Controller {
public:
    explicit Controller(JavaVM *_jvm, jvmtiEnv *_jvmti, ThreadMap& _thread_map, ConfigurationOptions& _cfg) :
        jvm(_jvm), jvmti(_jvmti), thread_map(_thread_map), cfg(_cfg), running(false) {
        
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

    void startSampling();

    void stopSampling();

    void run();
    
    void run_with_associate(const Buff& response_buff);
};

#endif
