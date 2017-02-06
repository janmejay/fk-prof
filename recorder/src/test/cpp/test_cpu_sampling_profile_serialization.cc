#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include "fixtures.hh"
#include "test.hh"
#include "../../main/cpp/profiler.hh"
#include <unordered_map>
#include "../../main/cpp/blocking_ring_buffer.hh"
#include "../../main/cpp/circular_queue.hh"
#include <memory>
#include <tuple>
#include "../../main/cpp/checksum.hh"
#include <google/protobuf/io/coded_stream.h>

bool test_frame_info_resolver(const jmethodID method_id, jvmtiEnv* jvmti, SiteResolver::MethodListener& listener) {
    return true;
}

jint test_line_no_resolver(jint bci, jmethodID methodId) {
    return 0;
}

typedef std::unordered_map<std::uint64_t, std::tuple<std::uint64_t, const char*, const char*, const char*, const char*>> FnStub;
typedef std::unordered_map<std::tuple<std::uint64_t, jint>, jint> LineNoStub;

namespace std {
    template <> class hash<std::tuple<std::uint64_t, jint>> {
        std::hash<std::uint64_t> u64_h;
        std::hash<jint> jint_h;

    public:
        std::size_t operator()(const std::tuple<std::uint64_t, jint>& v) const {
            return 13 * u64_h(std::get<0>(v)) + std::get<1>(v);
        }
    };
}

void stub(FnStub& method_lookup_stub, LineNoStub& line_no_lookup_stub, std::uint64_t method_id, const char* file, const char* fqdn, const char* fn_name, const char* fn_sig) {
    method_lookup_stub.insert({method_id, std::make_tuple(method_id, file, fqdn, fn_name, fn_sig)});
    line_no_lookup_stub.insert({std::make_tuple(method_id, 10), 1});
    line_no_lookup_stub.insert({std::make_tuple(method_id, 20), 2});
    line_no_lookup_stub.insert({std::make_tuple(method_id, 30), 3});
}

struct AccumulatingRawWriter : public RawWriter {
    BlockingRingBuffer& buff;
    
    AccumulatingRawWriter(BlockingRingBuffer& _buff) : RawWriter(), buff(_buff) {}
    virtual ~AccumulatingRawWriter() {}
    
    void write_unbuffered(const std::uint8_t* data, std::uint32_t sz, std::uint32_t offset) {
        auto wrote = buff.write(data, offset, sz, false);
        if (wrote < sz) {
            throw std::runtime_error(to_s("Wrote too little to byte-buff, wanted to write ", sz, " but wrote only ", wrote));
        }
    }
};

jmethodID mid(std::uint64_t id) {
    return reinterpret_cast<jmethodID>(id);
}


#define ASSERT_THREAD_INFO_IS(thd_info, thd_id, thd_name, thd_prio, thd_is_daemon) \
    {                                                                   \
        CHECK_EQUAL(thd_id, thd_info.thread_id());                      \
        CHECK_EQUAL(thd_name, thd_info.thread_name());                  \
        CHECK_EQUAL(thd_prio, thd_info.priority());                     \
        CHECK_EQUAL(thd_is_daemon, thd_info.is_daemon());               \
    }

#define ASSERT_METHOD_INFO_IS(mthd_info, mthd_id, kls_file, kls_fqdn, fn_name, fn_sig) \
    {                                                                   \
        CHECK_EQUAL(mthd_id, mthd_info.method_id());                    \
        CHECK_EQUAL(kls_file, mthd_info.file_name());                   \
        CHECK_EQUAL(kls_fqdn, mthd_info.class_fqdn());                  \
        CHECK_EQUAL(fn_name, mthd_info.method_name());                  \
        CHECK_EQUAL(fn_sig, mthd_info.signature());                     \
    }

#define ASSERT_TRACE_CTX_INFO_IS(trace_ctx, ctx_id, ctx_name, cov_pct, merge_semantics) \
    {                                                                   \
        CHECK_EQUAL(ctx_id, trace_ctx.trace_id());                      \
        CHECK_EQUAL(ctx_name, trace_ctx.trace_name());                  \
        CHECK_EQUAL(cov_pct, trace_ctx.coverage_pct());                 \
        CHECK_EQUAL(merge_semantics, trace_ctx.merge());                \
    }

typedef std::int64_t F_mid;
typedef std::int32_t F_bci;
typedef std::int32_t F_line;
std::tuple<F_mid, F_bci, F_line> fr(F_mid mid, F_bci bci, F_line line) {
    return std::make_tuple(mid, bci, line);
}

#define ASSERT_STACK_SAMPLE_IS(ss, time_offset, thd_id, frames, ctx_ids) \
    {                                                                   \
        CHECK_EQUAL(time_offset, ss.start_offset_micros());             \
        CHECK_EQUAL(thd_id, ss.thread_id());                            \
        CHECK_EQUAL(frames.size(), ss.frame_size());                    \
        auto i = 0;                                                     \
        for (auto it = frames.begin(); it != frames.end(); it++, i++) { \
            auto f = ss.frame(i);                                       \
            CHECK_EQUAL(std::get<0>(*it), f.method_id());               \
            CHECK_EQUAL(std::get<1>(*it), f.bci());                     \
            CHECK_EQUAL(std::get<2>(*it), f.line_no());                 \
        }                                                               \
        CHECK_EQUAL(frames.size(), i);                                  \
        CHECK_EQUAL(ctx_ids.size(), ss.trace_id_size());                \
        i = 0;                                                          \
        for (auto it = ctx_ids.begin(); it != ctx_ids.end(); it++, i++) { \
            CHECK_EQUAL(*it, ss.trace_id(i));                           \
        }                                                               \
    }


TEST(ProfileSerializer__should_write_cpu_samples) {
    init_logger();
    BlockingRingBuffer buff(1024 * 1024);
    std::shared_ptr<RawWriter> raw_w_ptr(new AccumulatingRawWriter(buff));
    Buff pw_buff;
    ProfileWriter pw(raw_w_ptr, pw_buff);
    FnStub method_lookup_stub;
    LineNoStub line_no_lookup_stub;

    std::uint64_t y = 1, c = 2, d = 3, e = 4, f = 5;
    stub(method_lookup_stub, line_no_lookup_stub, y, "x/Y.class", "x.Y", "fn_y", "(I)J");
    stub(method_lookup_stub, line_no_lookup_stub, c, "x/C.class", "x.C", "fn_c", "(F)I");
    stub(method_lookup_stub, line_no_lookup_stub, d, "x/D.class", "x.D", "fn_d", "(J)I");
    stub(method_lookup_stub, line_no_lookup_stub, e, "x/E.class", "x.E", "fn_e", "(J)I");
    stub(method_lookup_stub, line_no_lookup_stub, f, "x/F.class", "x.F", "fn_f", "(J)I");

    PerfCtx::Registry reg;
    auto ctx_foo = reg.find_or_bind("foo", 20, 0);
    auto ctx_bar = reg.find_or_bind("bar", 30, 0);
    auto ctx_baz = reg.find_or_bind("baz", 40, 4);
    
    ProbPct prob_pct;
    GlobalCtx::prob_pct = &prob_pct;
    GlobalCtx::ctx_reg = &reg;

    ProfileSerializingWriter ps(pw, test_frame_info_resolver, test_line_no_resolver, reg);

    CircularQueue q(ps, 10);
    
    //const JVMPI_CallTrace item, ThreadBucket *info = nullptr, std::uint8_t ctx_len = 0, PerfCtx::ThreadTracker::EffectiveCtx* ctx = nullptr

    STATIC_ARRAY(frames, JVMPI_CallFrame, 7, 7);
    JVMPI_CallTrace ct;
    ct.frames = frames;

    frames[0].method_id = mid(d);
    frames[0].lineno = 10;
    frames[1].method_id = mid(c);
    frames[1].lineno = 10;
    frames[2].method_id = mid(d);
    frames[2].lineno = 20;
    frames[3].method_id = mid(c);
    frames[3].lineno = 20;
    frames[3].method_id = mid(y);
    frames[3].lineno = 30;

    ThreadBucket t25(25, "Thread No. 25", 5, true);
    t25.ctx_tracker.enter(ctx_foo);
    t25.ctx_tracker.enter(ctx_bar);
    q.push(ct, &t25);
    t25.ctx_tracker.exit(ctx_bar);
    t25.ctx_tracker.exit(ctx_foo);

    frames[0].method_id = mid(d);
    frames[0].lineno = 10;
    frames[1].method_id = mid(c);
    frames[1].lineno = 10;
    frames[2].method_id = mid(e);
    frames[2].lineno = 20;
    frames[3].method_id = mid(d);
    frames[3].lineno = 20;
    frames[4].method_id = mid(c);
    frames[4].lineno = 30;
    frames[5].method_id = mid(y);
    frames[5].lineno = 30;

    ThreadBucket tmain(42, "main thread", 10, false);
    tmain.ctx_tracker.enter(ctx_bar);
    tmain.ctx_tracker.enter(ctx_baz);
    q.push(ct, &tmain);
    tmain.ctx_tracker.exit(ctx_baz);

    frames[0].method_id = mid(c);
    frames[0].lineno = 10;
    frames[1].method_id = mid(f);
    frames[1].lineno = 10;
    frames[2].method_id = mid(e);
    frames[2].lineno = 20;
    frames[3].method_id = mid(d);
    frames[3].lineno = 20;
    frames[4].method_id = mid(c);
    frames[4].lineno = 30;
    frames[5].method_id = mid(y);
    frames[5].lineno = 30;
    q.push(ct, &tmain);
    
    tmain.ctx_tracker.exit(ctx_bar);

    CHECK(q.pop());
    CHECK(q.pop());
    CHECK(q.pop());
    CHECK(! q.pop());//because only 3 samples were pushed

    ps.flush();

    buff.readonly();

    std::shared_ptr<std::uint8_t> tmp_buff(new std::uint8_t[1024 * 1024]);
    auto bytes_sz = buff.read(tmp_buff.get(), 0, 1024 * 1024);
    CHECK(bytes_sz > 0);

    google::protobuf::io::CodedInputStream cis(tmp_buff.get(), bytes_sz);

    std::uint32_t len;
    CHECK(cis.ReadVarint32(&len));

    auto lim = cis.PushLimit(len);

    recording::Wse wse;
    CHECK(wse.ParseFromCodedStream(&cis));

    cis.PopLimit(lim);

    auto pos = cis.CurrentPosition();

    std::uint32_t csum;
    CHECK(cis.ReadVarint32(&csum));

    Checksum c_calc;
    auto computed_csum = c_calc.chksum(tmp_buff.get(), pos);

    CHECK_EQUAL(computed_csum, csum);

    CHECK_EQUAL(recording::WorkType::cpu_sample_work, wse.w_type());
    auto idx_data = wse.indexed_data();
    CHECK_EQUAL(0, idx_data.monitor_info_size());
    
    CHECK_EQUAL(2, idx_data.thread_info_size());
    ASSERT_THREAD_INFO_IS(idx_data.thread_info(0), 25, "Thread No. 25", 5, true);
    ASSERT_THREAD_INFO_IS(idx_data.thread_info(1), 42, "main thread", 10, false);

    CHECK_EQUAL(5, idx_data.method_info_size());
    ASSERT_METHOD_INFO_IS(idx_data.method_info(0), d, "x/D.class", "x.D", "fn_d", "(J)I");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(1), c, "x/C.class", "x.C", "fn_c", "(F)I");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(2), y, "x/Y.class", "x.Y", "fn_y", "(I)J");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(3), y, "x/E.class", "x.E", "fn_e", "(J)I");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(4), y, "x/F.class", "x.F", "fn_f", "(J)I");

    CHECK_EQUAL(3, idx_data.trace_ctx_size());
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(0), 2, "foo", 20, 0);
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(1), 3, "bar", 30, 0);
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(2), 5, "baz", 40, 4);

    auto cse = wse.cpu_sample_entry();

    CHECK_EQUAL(3, cse.stack_sample_size());
    auto s1 = {fr(d, 10, 1), fr(c, 10, 1), fr(d, 20, 2), fr(c, 20, 2), fr(y, 30, 3)};
    auto s1_ctxs = {ctx_foo};
    ASSERT_STACK_SAMPLE_IS(cse.stack_sample(0), 0, 25, s1, s1_ctxs); //TODO: fix this to actually record time-offset, right now we are using zero
    auto s2 = {fr(d, 10, 1), fr(c, 10, 1), fr(e, 20, 2), fr(d, 20, 2), fr(c, 30, 3), fr(y, 30, 3)};
    auto s2_ctxs = {ctx_bar, ctx_baz};
    ASSERT_STACK_SAMPLE_IS(cse.stack_sample(1), 0, 42, s2, s2_ctxs);
    auto s3 = {fr(c, 10, 1), fr(f, 10, 1), fr(e, 20, 2), fr(d, 20, 2), fr(c, 30, 3), fr(y, 30, 3)};
    auto s3_ctxs = {ctx_bar};
    ASSERT_STACK_SAMPLE_IS(cse.stack_sample(1), 0, 42, s3, s3_ctxs);
}

//test: should auto flush (when crosses buffering threshold)

//test: incrementally streams out method-info, thread-info, ctx-info etc

//test: when new ctx is created post first flush and used by some stack-sample in the second flush
