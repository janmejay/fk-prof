#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include "fixtures.h"
#include "test.h"
#include "../../main/cpp/ftrace_feed_handler.h"

typedef const std::string S;
typedef std::int32_t i32;
typedef std::int64_t i64;

void assert_equal(const FtraceFeedHandler::EventHeader& h, S& proc, i32 ctx_pid, std::int16_t cpu, S& common_flags, double time) {
    CHECK_EQUAL(proc + "-" + std::to_string(ctx_pid), h.ctx_proc_pid);
    CHECK_EQUAL(cpu, h.ctx_cpu);
    CHECK_EQUAL(common_flags, h.common_flags);
    CHECK_EQUAL(time, h.time);
}
void assert_equal(const std::pair<int, FtraceFeedHandler::EventSchedSwitch>& entry, i32 index, S& proc, i32 ctx_pid, std::int16_t cpu, S& common_flags, double time, S& prev_comm, i32 prev_pid, i32 prev_priority, char prev_state, S& next_comm, i32 next_pid, i32 next_priority) {
    auto sw = entry.second;
    CHECK_EQUAL(index, entry.first);
    assert_equal(sw.h, proc, ctx_pid, cpu, common_flags, time);
    CHECK_EQUAL(prev_comm, sw.prev_comm);
    CHECK_EQUAL(prev_pid, sw.prev_pid);
    CHECK_EQUAL(prev_priority, sw.prev_priority);
    CHECK_EQUAL(prev_state, sw.prev_state);
    CHECK_EQUAL(next_comm, sw.next_comm);
    CHECK_EQUAL(next_pid, sw.next_pid);
    CHECK_EQUAL(next_priority, sw.next_priority);
}

void assert_equal(const std::pair<int, FtraceFeedHandler::EventSchedWakeup>& entry, i32 index, S& proc, i32 ctx_pid, std::int16_t cpu, S& common_flags, double time, S& comm, i32 pid, i32 priority, i64 target_cpu) {
    auto w = entry.second;
    CHECK_EQUAL(index, entry.first);
    assert_equal(w.h, proc, ctx_pid, cpu, common_flags, time);
    CHECK_EQUAL(comm, w.comm);
    CHECK_EQUAL(pid, w.pid);
    CHECK_EQUAL(priority, w.priority);
    CHECK_EQUAL(target_cpu, w.target_cpu);
}

TEST(Ftrace_feed_handling_4_5_0) {
    auto data = 
        "          <idle>-0     [002] dNh4 144402.445779: sched_wakeup: comm=slp pid=23941 prio=120 target_cpu=002\n"
        "          <idle>-0     [002] d..3 144402.445787: sched_switch: prev_comm=swapper/2 prev_pid=0 prev_prio=120 prev_state=R ==> next_comm=slp next_pid=23941 next_prio=120\n"
        "             slp-23941 [002] d..3 144402.445812: sched_switch: prev_comm=slp prev_pid=23941 prev_prio=120 prev_state=S ==> next_comm=swapper/2 next_pid=0 next_prio=120\n"
        "          <idle>-0     [003] dNh4 144402.944220: sched_wakeup: comm=slp pid=23939 prio=120 target_cpu=003\n"
        "          <idle>-0     [003] d..3 144402.944224: sched_switch: prev_comm=swapper/3 prev_pid=0 prev_prio=120 prev_state=R ==> next_comm=slp next_pid=23939 next_prio=120\n"
        "             slp-23939 [003] d..3 144402.944247: sched_switch: prev_comm=slp prev_pid=23939 prev_prio=120 prev_state=S ==> next_comm=swapper/3 next_pid=0 next_prio=120\n";
    std::stringstream ss{data};

    std::vector<std::pair<int, FtraceFeedHandler::EventSchedSwitch>> switch_events;
    std::vector<std::pair<int, FtraceFeedHandler::EventSchedWakeup>> wakeup_events;

    auto unknown_type = false;

    auto index = 0;

    FtraceFeedHandler::Parser ffh_parser([&unknown_type, &index, &switch_events, &wakeup_events](const FtraceFeedHandler::Event& e) -> bool {
            if (e.type() == typeid(FtraceFeedHandler::EventSchedSwitch)) {
                switch_events.push_back(std::make_pair(index++, boost::get<FtraceFeedHandler::EventSchedSwitch>(e)));
            } else if (e.type() == typeid(FtraceFeedHandler::EventSchedWakeup)) {
                wakeup_events.push_back(std::make_pair(index++, boost::get<FtraceFeedHandler::EventSchedWakeup>(e)));
            } else {
                unknown_type = true;
            }
            return true;
        });

    auto at_end = ffh_parser.feed(ss);

    CHECK_EQUAL(true, at_end);
    
    CHECK_EQUAL(false, unknown_type);
    CHECK_EQUAL(4, switch_events.size());
    CHECK_EQUAL(2, wakeup_events.size());

    assert_equal(wakeup_events[0], 0, "<idle>", 0, 2, "dNh4", 144402.445779, "slp", 23941, 120, 2);
    assert_equal(switch_events[0], 1, "<idle>", 0, 2, "d..3", 144402.445787, "swapper/2", 0, 120, 'R', "slp", 23941, 120);
    assert_equal(switch_events[1], 2, "slp", 23941, 2, "d..3", 144402.445812, "slp", 23941, 120, 'S', "swapper/2", 0, 120);
    assert_equal(wakeup_events[1], 3, "<idle>", 0, 3, "dNh4", 144402.944220, "slp", 23939, 120, 3);
    assert_equal(switch_events[2], 4, "<idle>", 0, 3, "d..3", 144402.944224, "swapper/3", 0, 120, 'R', "slp", 23939, 120);
    assert_equal(switch_events[3], 5, "slp", 23939, 3, "d..3", 144402.944247, "slp", 23939, 120, 'S', "swapper/3", 0, 120);
}


