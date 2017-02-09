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

public:
    ProfileWriter(std::shared_ptr<RawWriter> _w, Buff& _data) : w(_w), data(_data), header_written(false) {
        data.write_end = data.read_end = 0;
    }
    ~ProfileWriter() {
        flush();
    }

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
    MthId next_mthd_id;
    std::unordered_map<ThdId, ThdId> known_threads;
    ThdId next_thd_id;
    std::unordered_map<PerfCtx::TracePt, CtxId> known_ctxs;
    CtxId next_ctx_id;

    const SerializationFlushThresholds& sft;
    FlushCtr cpu_samples_flush_ctr;
    
public:
    ProfileSerializingWriter(jvmtiEnv* _jvmti, ProfileWriter& _w, SiteResolver::MethodInfoResolver _fir, SiteResolver::LineNoResolver _lnr, PerfCtx::Registry& _reg, const SerializationFlushThresholds& _sft) : jvmti(_jvmti), w(_w), fir(_fir), lnr(_lnr), reg(_reg), next_mthd_id(10), next_thd_id(3), next_ctx_id(5), sft(_sft), cpu_samples_flush_ctr(0) {}

    ~ProfileSerializingWriter() {}

    virtual void record(const JVMPI_CallTrace &trace, ThreadBucket *info = nullptr, std::uint8_t ctx_len = 0, PerfCtx::ThreadTracker::EffectiveCtx* ctx = nullptr);

    virtual void recordNewMethod(const jmethodID method_id, const char *file_name, const char *class_name, const char *method_name, const char *method_signature);

    void flush();
};

#endif
