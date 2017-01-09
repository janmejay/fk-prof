#ifndef SCHEDULER_H
#define SCHEDULER_H

#include <functional>
#include <chrono>
#include <queue>
#include <exception>

class Scheduler {
public:
    typedef std::chrono::steady_clock Clk;
    typedef std::chrono::time_point<Clk> Tm;
    typedef std::function<void()> Cb;
    typedef std::pair<Tm, Cb> Ent;
    struct Cmp {
        bool operator() (const Ent& left, const Ent& right) {
            return left.first > right.first;
        }
    };
    typedef std::priority_queue<Ent, std::vector<Ent>, Cmp> Q;

    Scheduler() {}

    ~Scheduler() {}

    void schedule(Tm time, Cb task);

    bool poll();

private:
    Q q;
};

#endif
