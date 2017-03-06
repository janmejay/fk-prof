#ifndef SCHEDULER_H
#define SCHEDULER_H

#include <functional>
#include <chrono>
#include <queue>
#include <exception>
#include <mutex>
#include <condition_variable>
#include "globals.hh"

class Scheduler {
public:
    typedef std::function<void()> Cb;
    typedef std::pair<Time::Pt, Cb> Ent;
    struct Cmp {
        bool operator() (const Ent& left, const Ent& right) {
            return left.first > right.first;
        }
    };
    typedef std::priority_queue<Ent, std::vector<Ent>, Cmp> Q;

    Scheduler();

    ~Scheduler();

    void schedule(Time::Pt time, Cb task);

    bool poll();

private:
    Q q;
    std::mutex m;
    std::condition_variable nearest_entry_changed;

    metrics::Mtr& s_m_runout;
    metrics::Timer& s_t_wait;
    metrics::Timer& s_t_exec;
    metrics::Hist& s_h_exec_spree_len;
};

#endif
