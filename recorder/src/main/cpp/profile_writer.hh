#ifndef PROFILE_WRITER_H
#define PROFILE_WRITER_H

#include <google/protobuf/io/coded_stream.h>
#include "checksum.hh"
#include "recorder.pb.h"
#include "buff.hh"
#include <memory>
#include "site_resolver.hh"
#include "circular_queue.hh"
#include <unordered_map>
#include <unordered_set>

#define NOCTX_NAME "~ OTHERS ~"

class RawWriter {
public:
    RawWriter() {}
    virtual ~RawWriter() {}
    virtual void write_unbuffered(const std::uint8_t* data, std::uint32_t sz, std::uint32_t offset) = 0;
};

class ProfileWriter {
private:
    //MIN_FREE_BUFF should accomodate atleast 4 varint32 values
    static const std::uint32_t MIN_FREE_BUFF = 64;

    std::shared_ptr<RawWriter> w;
    Checksum chksum;
    Buff &data;
    bool header_written;

    std::uint32_t ensure_freebuff(std::uint32_t min_reqired);

    template <class T> std::uint32_t ensure_freebuff(const T& value);
    
    void write_unchecked(std::uint32_t value);

    template <class T> void write_unchecked_obj(const T& value);

    void mark_eof();

public:
    ProfileWriter(std::shared_ptr<RawWriter> _w, Buff& _data);

    ~ProfileWriter();

    void write_header(const recording::RecordingHeader& rh);

    void append_wse(const recording::Wse& e);

    void flush();
};

typedef std::uint32_t FlushCtr;

struct SerializationFlushThresholds {
    FlushCtr cpu_samples;

    SerializationFlushThresholds() : cpu_samples(100) {}
    ~SerializationFlushThresholds() {}
};

typedef std::uint32_t TruncationCap;

struct TruncationThresholds {
    TruncationCap cpu_samples_max_stack_sz;

    TruncationThresholds(TruncationCap _cpu_samples_max_stack_sz) : cpu_samples_max_stack_sz(_cpu_samples_max_stack_sz) {}
    TruncationThresholds() : cpu_samples_max_stack_sz(DEFAULT_MAX_FRAMES_TO_CAPTURE) {}
    ~TruncationThresholds() {}
};

class ProfileSerializingWriter : public QueueListener, public SiteResolver::MethodListener {
private:
    jvmtiEnv* jvmti;
    
    typedef std::int64_t MthId;
    typedef std::int64_t ThdId;
    typedef std::uint32_t CtxId;
    
    ProfileWriter& w;
    SiteResolver::MethodInfoResolver fir;
    SiteResolver::LineNoResolver lnr;
    PerfCtx::Registry& reg;

    recording::Wse cpu_sample_accumulator;
    
    std::unordered_set<MthId> known_methods;
    std::unordered_map<ThdId, ThdId> known_threads;
    ThdId next_thd_id;
    std::unordered_map<PerfCtx::TracePt, CtxId> known_ctxs;
    CtxId next_ctx_id;

    const SerializationFlushThresholds& sft;
    FlushCtr cpu_samples_flush_ctr;

    const TruncationThresholds& trunc_thresholds;

    metrics::Ctr& s_c_new_thd_info;
    metrics::Ctr& s_c_new_ctx_info;
    metrics::Ctr& s_c_total_mthd_info;
    metrics::Ctr& s_c_new_mthd_info;

    metrics::Ctr& s_c_bad_lineno;

    metrics::Ctr& s_c_frame_snipped;

    metrics::Mtr& s_m_stack_sample_err;
    metrics::Mtr& s_m_cpu_sample_add;

    CtxId report_ctx(PerfCtx::TracePt trace_pt);

public:
    ProfileSerializingWriter(jvmtiEnv* _jvmti, ProfileWriter& _w, SiteResolver::MethodInfoResolver _fir, SiteResolver::LineNoResolver _lnr,
                             PerfCtx::Registry& _reg, const SerializationFlushThresholds& _sft, const TruncationThresholds& _trunc_thresholds,
                             std::uint8_t _noctx_cov_pct);

    ~ProfileSerializingWriter();

    virtual void record(const Backtrace &trace, ThreadBucket *info = nullptr, std::uint8_t ctx_len = 0, PerfCtx::ThreadTracker::EffectiveCtx* ctx = nullptr);

    virtual void recordNewMethod(const jmethodID method_id, const char *file_name, const char *class_name, const char *method_name, const char *method_signature);

    void flush();
};

#endif
