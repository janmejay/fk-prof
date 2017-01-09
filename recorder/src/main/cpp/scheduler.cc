#include "scheduler.hh"
#include <thread>
#include <iostream>

void Scheduler::schedule(Time::Pt time, Scheduler::Cb task) {
    auto sec_in_future = std::chrono::duration_cast<Time::sec>(time - Time::now());
    logger->debug("Scheduling {} {}s in future", typeid(task).name(), sec_in_future.count());//TODO: handle me better
    q.push({time, task});
}

Time::usec usec_to_expiry(const Time::Pt& tm) {
    return std::chrono::duration_cast<Time::usec>(tm - Time::now());
}

bool is_expired(const Time::Pt& tm) {
    return usec_to_expiry(tm).count() <= 0;
}

void block_for_expiry(const Time::Pt& tm) {
    while (true) {
        auto far_usec = usec_to_expiry(tm);
        if (far_usec.count() > 0) {
            std::this_thread::sleep_for(far_usec);
        } else {
            return;
        }
    }
}

void execute_top(Scheduler::Q& q) {
    Scheduler::Ent sched_for = std::move(q.top());
    q.pop();
    logger->debug("Scheduler is now triggering {}", typeid(sched_for).name());
    sched_for.second();
}

bool Scheduler::poll() {
    if (q.empty()) {
        return false;
    }

    auto top_expiry = q.top().first;
    if (! is_expired(top_expiry)) {
        block_for_expiry(top_expiry);
    }

    execute_top(q);

    while ((! q.empty()) && is_expired(q.top().first)) {
        execute_top(q);
    }

    return true;
}
