#ifndef SCHED_TRACER_H
#define SCHED_TRACER_H

#include <cuckoohash_map.hh>
#include <city_hasher.hh>
#include <jvmti.h>
#include <mutex>
#include <set>

class SchedTracer {
public:
    typedef int Tid;
    typedef jthread JThd;
    
private:
    bool do_trace;
    bool should_purge_dir_tracing_root = false;
    std::string dir_tracing_root;
    std::string file_tids_to_be_traced;
    std::string file_tracing_on;
    std::string file_traced_events;
    
    std::mutex tids_mutex;
    std::set<Tid> tids;

    void start_tracing();
    void stop_tracing();

    void write_to(const std::string& path, const std::string& content);
    void append_to(const std::string& path, const std::string& content);

public:
    SchedTracer(const std::string& _trace_dir = "/sys/kernel/debug/tracing", bool _do_trace = true);
    ~SchedTracer();

    void start(const Tid tid, const JThd jthd);
    void stop(const Tid tid);
    
};

#endif

