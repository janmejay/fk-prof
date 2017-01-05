#include <iostream>
#include <thread>
#include <atomic>
#include <mutex>
#include <chrono>
#include <signal.h>
#include <sys/resource.h>

#include <signal.h>
#include <time.h>
#include <stdlib.h>
#include <sys/time.h>

#include <unistd.h>
#include <sys/syscall.h>

thread_local int sigprof_count;

volatile int x = 0;

std::atomic<bool> keep_running {true};

int total_count {0};

std::mutex print;

void hdlr(int signum, siginfo_t *info, void *context) {
    sigprof_count++;
}

int usec = 1000;

void populate_itimerval(struct itimerval& itvl) {
    itvl.it_interval.tv_sec = 0;
    itvl.it_interval.tv_usec = usec;
    itvl.it_value = itvl.it_interval;
}

int run() {
#ifdef THD
    sigevent_t evt;
    evt.sigev_notify = SIGEV_THREAD_ID;
    evt._sigev_un._tid = syscall(SYS_gettid);
    evt.sigev_signo = SIGPROF;

    timer_t timer;

    if (timer_create(CLOCK_THREAD_CPUTIME_ID, &evt, &timer) != 0) {
        std::cerr << "Timer creation failed: " << errno << "\n";
        return 1;
    }

    struct itimerspec itspec;
    itspec.it_interval.tv_sec = 0;
    itspec.it_interval.tv_nsec = usec * 1000;
    itspec.it_value = itspec.it_interval;

    if (timer_settime(timer, 0, &itspec, nullptr) != 0) {
        std::cerr << "Timer 'enable' operation failed: " << errno << "\n";
        return 1;
    }
#endif

    while (keep_running) {
        x++;
    }

#ifdef THD
    if (timer_delete(timer) != 0) {
        std::cerr << "Timer deletion failed: " << errno << "\n";
        return 1;
    }
#endif
    std::lock_guard<std::mutex> g(print);
    std::cout << "Called: " << sigprof_count << " times\n";
    total_count += sigprof_count;
    return 0;
}

int main(int argc, char** argv) {
    if (argc > 1) {
        usec = atoi(argv[1]);
        std::cout << "Sig itvl (usec) : " << usec << "\n";
    }

    // struct rlimit sig_queue_lim;

    // sig_queue_lim.rlim_cur = 5000;
    // sig_queue_lim.rlim_max = 5000;

    // if (setrlimit(RLIMIT_SIGPENDING, &sig_queue_lim) != 0) {
    //     std::cerr << "Failed to set sigpending len\n";
    //     return 1;
    // }

    struct sigaction sa;
    sa.sa_handler = NULL;
    sa.sa_sigaction = hdlr;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART | SA_SIGINFO;
    sigaction(SIGPROF, &sa, 0);
    sigaction(SIGALRM, &sa, 0);
    sigaction(SIGVTALRM, &sa, 0);

    // struct itimerval timer;

#ifndef THD
    sigevent_t evt;
    evt.sigev_notify = SIGEV_SIGNAL;
    evt.sigev_signo = SIGPROF;

    timer_t timer;

    if (timer_create(CLOCK_PROCESS_CPUTIME_ID, &evt, &timer) != 0) {
        std::cerr << "Timer creation failed: " << errno << "\n";
        return 1;
    }

    struct itimerspec itspec;
    itspec.it_interval.tv_sec = 0;
    itspec.it_interval.tv_nsec = usec * 1000;
    itspec.it_value = itspec.it_interval;

    if (timer_settime(timer, 0, &itspec, nullptr) != 0) {
        std::cerr << "Timer 'enable' operation failed: " << errno << "\n";
        return 1;
    }
#endif

    sigaction(SIGPROF, &sa, 0);
    
    std::thread t(run);
    std::thread t1(run);
    std::thread t2(run);

    //populate_itimerval(timer);
    // if (setitimer(ITIMER_PROF, &timer, 0) == -1) {
    //     std::cerr << "Scheduling profiler interval failed with error: " << errno << "\n";
    //     return 1;
    // }


    int elapsed_ms = 0;
    while (elapsed_ms < 1000) {
        auto start = std::chrono::high_resolution_clock::now();
        std::this_thread::sleep_for(std::chrono::seconds(1));
        auto end = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double, std::milli> x = end - start;
        elapsed_ms += x.count();
    }
    
#ifndef THD
    if (timer_delete(timer) != 0) {
        std::cerr << "Timer deletion failed: " << errno << "\n";
        return 1;
    }
#endif
    keep_running = false;
    
    t.join();
    t1.join();
    t2.join();
        
    std::cout << "Called: " << sigprof_count << " times\n";
    std::cout << "Done...\n";
    std::cout << "Total count: " << total_count + sigprof_count << "\n";
    return 0;
}
