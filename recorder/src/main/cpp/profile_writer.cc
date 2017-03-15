#include "profile_writer.hh"
#include "buff.hh"
#include "globals.hh"
#include "util.hh"

//for buff, no one uses read-end here, so it is inconsistent

void ProfileWriter::flush() {
    w->write_unbuffered(data.buff, data.write_end, 0); //TODO: err-check me!
    data.write_end = 0;
}

std::uint32_t ProfileWriter::ensure_freebuff(std::uint32_t min_reqired) {
    if ((data.write_end + min_reqired) > data.capacity) {
        flush();
        data.ensure_free(min_reqired);
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

recording::StackSample::Error translate_forte_error(jint num_frames_error) {
    /** copied form forte.cpp, this is error-table we are trying to translate
        enum {
          ticks_no_Java_frame         =  0,
          ticks_no_class_load         = -1,
          ticks_GC_active             = -2,
          ticks_unknown_not_Java      = -3,
          ticks_not_walkable_not_Java = -4,
          ticks_unknown_Java          = -5,
          ticks_not_walkable_Java     = -6,
          ticks_unknown_state         = -7,
          ticks_thread_exit           = -8,
          ticks_deopt                 = -9,
          ticks_safepoint             = -10
        };
    **/
    assert(num_frames_error <= 0);
    return static_cast<recording::StackSample::Error>(-1 * num_frames_error);
}

void ProfileSerializingWriter::record(const JVMPI_CallTrace &trace, ThreadBucket *info, std::uint8_t ctx_len, PerfCtx::ThreadTracker::EffectiveCtx* ctx) {
    if (cpu_samples_flush_ctr >= sft.cpu_samples) flush();
    cpu_samples_flush_ctr++;
    
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
            s_c_new_thd_info.inc();
        } else {
            ss->set_thread_id(known_thd->second);
        }
    } else {
        ss->set_error(translate_forte_error(trace.num_frames));
    }

    SPDLOG_DEBUG(logger, "Ctx-len count: {}", ctx_len);

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
            SPDLOG_DEBUG(logger, "Reporting trace named '{}', cov {}% as ctx-id: {}", name, coverage_pct, ctx_id);
            s_c_new_ctx_info.inc();
        } else {
            ss->add_trace_id(known_ctx->second);
        }
    }

    auto snipped = trace.num_frames > trunc_thresholds.cpu_samples_max_stack_sz;
    if (snipped) s_c_frame_snipped.inc();
    ss->set_snipped(snipped);

    if (trace.num_frames < 0) {
        s_m_stack_sample_err.mark();
        return;
    }

    for (auto i = 0; i < Util::min(static_cast<TruncationCap>(trace.num_frames), trunc_thresholds.cpu_samples_max_stack_sz); i++) {
        auto f = ss->add_frame();
        auto& jvmpi_cf = trace.frames[i];
        //find method
        auto mth_id = jvmpi_cf.method_id;
        if (known_methods.count(reinterpret_cast<MthId>(mth_id)) == 0) {
            if (fir(mth_id, jvmti, *this)) {
                s_c_new_mthd_info.inc();
            } else {
                recordNewMethod(mth_id, "?", "?", "?", "?");
            }
            s_c_total_mthd_info.inc();
        }
        //end find method
        f->set_method_id(reinterpret_cast<std::int64_t>(mth_id));
        f->set_bci(jvmpi_cf.lineno);//turns out its actually BCI
        auto line_no = lnr(jvmpi_cf.lineno, mth_id, jvmti);
        if (line_no < 0) s_c_bad_lineno.inc();
        f->set_line_no(line_no);
    }

    s_m_cpu_sample_add.mark();
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
    cpu_sample_accumulator.Clear();
    cpu_samples_flush_ctr = 0;
    w.flush();
}

#define METRIC_TYPE "profile_serializer"

ProfileSerializingWriter::ProfileSerializingWriter(jvmtiEnv* _jvmti, ProfileWriter& _w, SiteResolver::MethodInfoResolver _fir, SiteResolver::LineNoResolver _lnr,
                                                   PerfCtx::Registry& _reg, const SerializationFlushThresholds& _sft, const TruncationThresholds& _trunc_thresholds) :
    jvmti(_jvmti), w(_w), fir(_fir), lnr(_lnr), reg(_reg), next_mthd_id(10), next_thd_id(3), next_ctx_id(5), sft(_sft), cpu_samples_flush_ctr(0), trunc_thresholds(_trunc_thresholds),

    s_c_new_thd_info(GlobalCtx::metrics_registry->new_counter({METRICS_DOMAIN, METRIC_TYPE, "thd_rpt", "new"})),
    s_c_new_ctx_info(GlobalCtx::metrics_registry->new_counter({METRICS_DOMAIN, METRIC_TYPE, "ctx_rpt", "new"})),
    s_c_total_mthd_info(GlobalCtx::metrics_registry->new_counter({METRICS_DOMAIN, METRIC_TYPE, "mthd_rpt", "total"})),
    s_c_new_mthd_info(GlobalCtx::metrics_registry->new_counter({METRICS_DOMAIN, METRIC_TYPE, "mthd_rpt", "new"})),

    s_c_bad_lineno(GlobalCtx::metrics_registry->new_counter({METRICS_DOMAIN, METRIC_TYPE, "line_rpt", "bad"})),

    s_c_frame_snipped(GlobalCtx::metrics_registry->new_counter({METRICS_DOMAIN, METRIC_TYPE, "backtrace_snipped"})),

    s_m_stack_sample_err(GlobalCtx::metrics_registry->new_meter({METRICS_DOMAIN, METRIC_TYPE, "cpu_sample"}, "err")),
    s_m_cpu_sample_add(GlobalCtx::metrics_registry->new_meter({METRICS_DOMAIN, METRIC_TYPE, "cpu_sample"}, "rpt")) {

    s_c_new_mthd_info.clear();
    s_c_new_ctx_info.clear();
    s_c_total_mthd_info.clear();
    s_c_new_mthd_info.clear();

    s_c_bad_lineno.clear();

    s_c_frame_snipped.clear();
}

ProfileSerializingWriter::~ProfileSerializingWriter() {}

