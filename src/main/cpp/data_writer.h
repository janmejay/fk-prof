#ifndef DATA_WRITER_H
#define DATA_WRITER_H

#include <cstdint>
#include <cstring>
#include <concurrentqueue.h>
#include <iostream>
#include "common.h"

template <typename T> class DataWriter {
private:
    moodycamel::ConcurrentQueue<T> *q;

    std::atomic<bool> stop {false};
    std::atomic_flag awaiting_stop = ATOMIC_FLAG_INIT;

    sigset_t oldset;

    std::ofstream& out;

    static void run(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg) {
        auto dw = reinterpret_cast<DataWriter<T> *>(arg);
        dw->consume_and_write();
    }

    void consume_and_write() {
        sigset_t consumer_oldset;
        memcpy(&consumer_oldset, &oldset, sizeof(oldset));
        sigaddset(&consumer_oldset, SIGPROF);
        pthread_sigmask(SIG_SETMASK, &consumer_oldset, NULL);
        if (pthread_sigmask(SIG_BLOCK, &consumer_oldset, NULL) < 0) {
            logError("ERROR: failed to set data-writer thread signal-mask\n");
        }
        T data[256];
        while (! stop.load(std::memory_order_seq_cst)) {
            auto data_len = q->try_dequeue_bulk(data, sizeof(data)/sizeof(T));
            for (auto i = 0; i < data_len; i++) {
                out << data[i];
            }
        }
        awaiting_stop.clear(std::memory_order_seq_cst);
    }

public:
    DataWriter(jvmtiEnv *ti, JNIEnv *jni, size_t sz, const std::string name, std::ofstream& _out) : out(_out) {
        q = new moodycamel::ConcurrentQueue<T>(sz);
        sigset_t newset;
        sigemptyset(&oldset);
        sigfillset(&newset);
        pthread_sigmask(SIG_SETMASK, &newset, &oldset);
        std::string thd_name = "Honest Profiler Data Writer - " + name;
        jthread thread = newThread(jni, thd_name.c_str());
        jvmtiStartFunction callback = DataWriter::run;
        auto result = ti->RunAgentThread(thread, callback, this, JVMTI_THREAD_NORM_PRIORITY);
        if (result != JVMTI_ERROR_NONE) {
            logError("ERROR: Running agent thread failed with: %d\n", result);
        }
        pthread_sigmask(SIG_SETMASK, &oldset, NULL);
    }

    ~DataWriter() {
        assert(! awaiting_stop.test_and_set(std::memory_order_seq_cst));
        stop.store(true, std::memory_order_seq_cst);
        while (awaiting_stop.test_and_set(std::memory_order_seq_cst));
        delete q;
    }

    void enq(const T& val) {
        while(! q->try_enqueue(val));
    }
};

#endif
