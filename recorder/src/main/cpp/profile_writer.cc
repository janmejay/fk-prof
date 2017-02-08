#include "profile_writer.hh"
#include "buff.hh"
#include "globals.hh"

//for buff, no one uses read-end here, so it is inconsistent

void ProfileWriter::flush() {
    w->write_unbuffered(data.buff, data.write_end, 0); //TODO: err-check me!
    data.write_end = 0;
}

std::uint32_t ProfileWriter::ensure_freebuff(std::uint32_t min_reqired) {
    if ((data.write_end + min_reqired) > data.capacity) {
        flush();
        data.ensure_capacity(min_reqired);
    }
    return data.capacity - data.write_end;
}

template <class T> inline std::uint32_t ProfileWriter::ensure_freebuff(const T& value) {
    auto sz = value.ByteSize();
    return ensure_freebuff(sz + MIN_FREE_BUFF);
}
    
void ProfileWriter::write_unchecked(std::uint32_t value) {
    auto _data = google::protobuf::io::CodedOutputStream::WriteVarint32ToArray(value, data.buff + data.write_end);
    data.write_end = _data - data.buff;
}

template <class T> void ProfileWriter::write_unchecked_obj(const T& value) {
    auto sz = value.ByteSize();
    write_unchecked(sz);
    if (! value.SerializeToArray(data.buff + data.write_end, sz)) {
        //TODO: handle this error
    }
    data.write_end += sz;
}

void ProfileWriter::write_header(const recording::RecordingHeader& rh) {
    assert(! header_written);
    header_written = true;
    ensure_freebuff(rh);
    write_unchecked(DATA_ENCODING_VERSION);
    write_unchecked_obj(rh);
    auto csum = chksum.chksum(data.buff, data.write_end);
    write_unchecked(csum);
}

void ProfileWriter::append_wse(const recording::Wse& e) {
    ensure_freebuff(e);
    auto old_offset = data.write_end;
    write_unchecked_obj(e);
    chksum.reset();
    auto data_sz = data.write_end - old_offset;
    auto csum = chksum.chksum(data.buff + old_offset, data_sz);
    write_unchecked(csum);
}

void ProfileSerializingWriter::record(const JVMPI_CallTrace &trace, ThreadBucket *info, std::uint8_t ctx_len, PerfCtx::ThreadTracker::EffectiveCtx* ctx) {
    auto idx_dat = cpu_sample_accumulator.mutable_indexed_data();
    auto cse = cpu_sample_accumulator.mutable_cpu_sample_entry();
    auto ss = cse->add_stack_sample();

    ss->set_start_offset_micros(0);

    if (info != nullptr) {
        auto local_thd_id = reinterpret_cast<ThdId>(info);
        auto known_thd = known_threads.find(local_thd_id);
        if (known_thd == known_threads.end()) {
            ThdId thd_id = next_thd_id++;
            known_threads.insert({local_thd_id, thd_id});
            auto ti = idx_dat->add_thread_info();
            ti->set_thread_id(thd_id);
            ti->set_thread_name(info->name);
            ti->set_priority(info->priority);
            ti->set_is_daemon(info->is_daemon);
            ti->set_tid(info->tid);
            ss->set_thread_id(thd_id);
        } else {
            ss->set_thread_id(known_thd->second);
        }
    }

    for (auto i = 0; i < ctx_len; i++) {
        auto trace_pt = ctx->at(i);
        auto known_ctx = known_ctxs.find(trace_pt);
        if (known_ctx == known_ctxs.end()) {
            CtxId ctx_id = next_ctx_id++;
            known_ctxs.insert({trace_pt, ctx_id});
            auto new_ctx = idx_dat->add_trace_ctx();
            new_ctx->set_trace_id(ctx_id);
            std::string name;
            bool is_generated;
            std::uint8_t coverage_pct;
            PerfCtx::MergeSemantic m_sem;
            reg.resolve(trace_pt, name, is_generated, coverage_pct, m_sem);
            new_ctx->set_is_generated(is_generated);
            if (! is_generated) {
                new_ctx->set_coverage_pct(coverage_pct);
                new_ctx->set_merge(static_cast<recording::TraceContext_MergeSemantics>(static_cast<int>(m_sem)));
            }
            new_ctx->set_trace_name(name);
            ss->add_trace_id(ctx_id);
        } else {
            ss->add_trace_id(known_ctx->second);
        }
    }

    for (auto i = 0; i < trace.num_frames; i++) {
        auto f = ss->add_frame();
        auto& jvmpi_cf = trace.frames[i];
        //find method
        auto mth_id = jvmpi_cf.method_id;
        if (known_methods.count(reinterpret_cast<MthId>(mth_id)) == 0) {
            if (! fir(mth_id, jvmti, *this)) {
                recordNewMethod(mth_id, "?", "?", "?", "?");
            }
        }
        //end find method
        f->set_method_id(reinterpret_cast<std::int64_t>(mth_id));
        f->set_bci(jvmpi_cf.lineno);//turns out its actually BCI
        f->set_line_no(lnr(jvmpi_cf.lineno, mth_id, jvmti));
    }
}

void ProfileSerializingWriter::recordNewMethod(const jmethodID method_id, const char *file_name, const char *class_name, const char *method_name, const char *method_signature) {
    known_methods.insert(reinterpret_cast<MthId>(method_id));
    auto idx_dat = cpu_sample_accumulator.mutable_indexed_data();
    auto mi = idx_dat->add_method_info();
    mi->set_method_id(reinterpret_cast<std::int64_t>(method_id));

    assert(file_name != nullptr);
    mi->set_file_name(file_name);

    assert(class_name != nullptr);
    mi->set_class_fqdn(class_name);

    assert(method_name != nullptr);
    mi->set_method_name(method_name);

    assert(method_signature != nullptr);
    mi->set_signature(method_signature);
}

void ProfileSerializingWriter::flush() {
    cpu_sample_accumulator.set_w_type(recording::WorkType::cpu_sample_work);
    w.append_wse(cpu_sample_accumulator);
    w.flush();
}
