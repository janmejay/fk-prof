#include <thread>
#include <vector>
#include <iostream>
#include <fstream>
#include "fixtures.h"
#include "test.h"
#include "recorder.pb.h"
#include <initializer_list>
#include <unordered_map>
#include "../../main/cpp/profile_writer.h"

void add_frames_for(const std::initializer_list<const std::string>& frame_fn_names,
                    std::unordered_map<std::string, int>& method_ids,
                    recording::StackSampleWse& ss_wse,
                    recording::StackSample* ss) {
    for (auto fn_name : frame_fn_names) {
        recording::Frame* f = ss->add_frame();
        auto itr = method_ids.find(fn_name);
        std::uint64_t method_id;
        if (itr == method_ids.end()) {
            method_id = method_ids[fn_name] = method_ids.size();
            recording::MethodInfo* mi = ss_wse.add_method_info();
            mi->set_method_id(method_id);
            mi->set_file_name("foo/Bar.java");
            mi->set_class_fqdn("foo.Bar");
            mi->set_method_name(fn_name);
            mi->set_signature("([I)I");
        } else {
            method_id = itr->second;
        }
        f->set_method_id(method_id);
        f->set_bci(17 * method_id);
        f->set_line_no((int32_t) 2 * method_id);
    }
}

class TestRecordingWriter {
private:
    std::ofstream ofs;
public:
    TestRecordingWriter(const std::string& file_path) {
        ofs.open(file_path, std::ofstream::out | std::ofstream::binary);
    }
    ~TestRecordingWriter() {}

    void write(const std::string& data, std::uint32_t sz, std::uint32_t offset) {
        ofs.write(data.c_str() + offset, sz);
    }
};

void write_to_file(const recording::RecordingHeader& rh, const std::initializer_list<const recording::Wse*> entries) {
    TestRecordingWriter w("/tmp/profile.data");
    ProfileWriter<1, TestRecordingWriter> rec_w(w);
    rec_w.write_header(rh);
    for (auto e : entries) {
        rec_w.append_wse(*e);
    }
}

TEST(WriteAndReadBack_CPUSampleRecording) {
    recording::RecordingHeader rh;
    rh.set_recorder_version(1);
    rh.set_controller_version(2);
    rh.set_controller_id(3);
    rh.set_work_description("Test cpu-sampling work");
    recording::WorkAssignment* wa = rh.mutable_work_assignment();
    wa->set_work_id(10);
    wa->set_issue_time("2016-11-10T14:35:09.372");
    wa->set_duration(60);
    wa->set_delay(17);
    recording::Work* w = wa->mutable_work();
    w->Clear();
    w->set_w_type(recording::WorkType::cpu_sample_work);
    recording::CpuSampleWork* csw = w->mutable_cpu_sample();
    csw->set_frequency(49);
    csw->set_max_frames(200);

    recording::Wse e1;
    e1.set_w_type(recording::WorkType::cpu_sample_work);
    recording::StackSampleWse* wse_1 = e1.mutable_cpu_sample_entry();
    recording::StackSample* ss = wse_1->add_stack_sample();
    ss->set_start_offset_micros(15000);
    ss->set_thread_id(200);
    std::unordered_map<std::string, int> method_tracker;
    add_frames_for({"Y", "C", "D", "C", "D"}, method_tracker, *wse_1, ss);
    ss = wse_1->add_stack_sample();
    ss->set_start_offset_micros(15050);
    ss->set_thread_id(200);
    add_frames_for({"Y", "C", "D", "E", "C", "D"}, method_tracker, *wse_1, ss);

    recording::Wse e2;
    e2.set_w_type(recording::WorkType::cpu_sample_work);
    recording::StackSampleWse* wse_2 = e2.mutable_cpu_sample_entry();
    ss = wse_2->add_stack_sample();
    ss->set_start_offset_micros(25002);
    ss->set_thread_id(201);
    add_frames_for({"Y", "C", "D", "E", "F", "C"}, method_tracker, *wse_2, ss);

    write_to_file(rh, {&e1, &e2});

    //CHECK_EQUAL(10, 20);
}

