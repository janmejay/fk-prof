#include "sched_tracer.h"
#include <boost/filesystem.hpp>
#include <unistd.h>
#include <jvmti.h>
#include <fstream>
#include <sstream>
#include <iostream>
#include <unistd.h>

extern jvmtiEnv *ti_env;

void SchedTracer::write_to(const std::string& path, const std::string& content) {
    std::ofstream os(path, std::ios_base::trunc);
    os << content;
    os.close();
}

void SchedTracer::append_to(const std::string& path, const std::string& content) {
    std::ofstream os(path, std::ios_base::app);
    os << content;
    os.close();
}

void SchedTracer::start_tracing() {
    write_to(file_traced_events,
             "sched:sched_switch\n"
             "sched:sched_wakeup\n");
    write_to(file_tracing_on, "1");
    //in real impl, ensure trace_options has necessary options -jj
}


void SchedTracer::stop_tracing() {
    write_to(file_tracing_on, "0");
    write_to(file_traced_events, "");
    write_to(file_tids_to_be_traced, "");
}

SchedTracer::SchedTracer(const std::string& _trace_dir, bool _do_trace) {
    do_trace = _do_trace;
    if (do_trace) {
        auto instances_dir = _trace_dir + "/instances";
        bool has_instances = boost::filesystem::exists(instances_dir);
        dir_tracing_root = has_instances ? instances_dir + "/fkjp_" + std::to_string(getpid()) : _trace_dir;
        if (has_instances) {
            boost::system::error_code err_code;
            if (boost::filesystem::create_directories(dir_tracing_root, err_code)) {
                std::cerr << "Created trace-instance (for event tracing) at " << dir_tracing_root << "\n";
            } else {
                std::cerr << "Couldn't create trace-instance (for event tracing) at " << dir_tracing_root << ". May be one already exists.\n";
            }
            assert(!err_code.value());
            should_purge_dir_tracing_root = true;
        } else {
            std::cerr << "Env doesn't support ftrace-instances, will modify root tracing-instance at " << dir_tracing_root << "\n";
        }

        //watch out for following across linux versions -jj
        file_tids_to_be_traced = dir_tracing_root + "/set_event_pid";
        assert(boost::filesystem::exists(file_tids_to_be_traced));
        
        file_tracing_on = dir_tracing_root + "/tracing_on";
        assert(boost::filesystem::exists(file_tracing_on));
        
        file_traced_events = dir_tracing_root + "/set_event";
        assert(boost::filesystem::exists(file_traced_events));

        start_tracing();
    }
}

SchedTracer::~SchedTracer() {
    if (do_trace) {
        stop_tracing();
        if (rmdir(dir_tracing_root.c_str()) != 0) {
            std::cerr << "Failed to remove trace-instance (for event tracing) at: " << dir_tracing_root << "\n";
        }
    }
}

void SchedTracer::start(const SchedTracer::Tid tid, const SchedTracer::JThd corresponding_jthd) {
    if (do_trace) {
        std::lock_guard<std::mutex> g(tids_mutex);
        tids.insert(tid);
        append_to(file_tids_to_be_traced, std::to_string(tid) + "\n");
    }
}

void SchedTracer::stop(const SchedTracer::Tid tid) {
    if (do_trace) {
        std::stringstream ss;
        std::lock_guard<std::mutex> g(tids_mutex);
        tids.erase(tid);
        for (auto t_id : tids) {
            ss << std::to_string(t_id) << "\n";
        }
        write_to(file_tids_to_be_traced, ss.str());
    }
}
