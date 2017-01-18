#include "ti_thd.hh"
#include <mutex>
#include <condition_variable>
#include <atomic>
#include "globals.hh"
#include "common.hh"

static void prep_new_thread(jvmtiEnv *jvmti_env, JNIEnv *jni_env) {
    IMPLICITLY_USE(jvmti_env);
    IMPLICITLY_USE(jni_env);
    sigset_t mask;

    sigemptyset(&mask);
    sigaddset(&mask, SIGPROF);

    if (pthread_sigmask(SIG_BLOCK, &mask, NULL) < 0) {
        logger->error("Unable to set controller thread signal mask");
    }
}

struct ThreadTargetProc {
    void* arg;
    jvmtiStartFunction run_fn;
    std::string name;
    std::atomic_bool running;
    std::mutex m;
    std::condition_variable v;

    ThreadTargetProc(void* _arg, jvmtiStartFunction _run_fn, const char* _name) : arg(_arg), run_fn(_run_fn), name(_name) {}

    void await_stop() {
        std::unique_lock<std::mutex> l(m);
        v.wait(l, [&] { return ! running.load(std::memory_order_relaxed); });
    }

    void mark_stopped() {
        bool lval_true = true;
        assert(running.compare_exchange_strong(lval_true, false, std::memory_order_relaxed));
        v.notify_all();
    }

    void mark_started() {
        bool lval_false = false;
        assert(running.compare_exchange_strong(lval_false, true, std::memory_order_relaxed));
    }
};

typedef std::shared_ptr<ThreadTargetProc> ThdProcP;

static void thread_target_proc_wrapper(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg) {
    prep_new_thread(jvmti_env, jni_env);
    auto ttp = *static_cast<ThdProcP*>(arg);
    ttp->mark_started();
    ttp->run_fn(jvmti_env, jni_env, ttp->arg);
    ttp->mark_stopped();
}

ThdProcP start_new_thd(JavaVM *jvm, jvmtiEnv *jvmti, const char* thd_name, jvmtiStartFunction run_fn, void *arg) {
    JNIEnv *env = getJNIEnv(jvm);
    jvmtiError result;

    if (env == NULL) {
        logError("ERROR: Failed to obtain JNIEnv\n");
        return ThdProcP(nullptr);
    }

    jthread thread = newThread(env, thd_name);
    ThdProcP ttp = std::shared_ptr<ThreadTargetProc>(new ThreadTargetProc(arg, run_fn, thd_name));
    result = jvmti->RunAgentThread(thread, thread_target_proc_wrapper, &ttp, JVMTI_THREAD_NORM_PRIORITY);

    if (result == JVMTI_ERROR_NONE) {
        logger->info("Started thread named '{}'", thd_name);
    } else {
        logger->error("Failed to start thread named '{}' failed with: {}", thd_name, result);
    }
    return ttp;
}

void await_thd_death(ThdProcP ttp) {
    auto name = ttp->name.c_str();
    logger->info("Awaiting death of thread named '{}'", name);
    ttp->await_stop();
    logger->info("Thread named '{}' reaped", name);
}
