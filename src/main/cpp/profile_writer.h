#include <string>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>
#include <google/protobuf/io/coded_stream.h>
#include <memory>
#include "checksum.h"
#include "recorder.pb.h"

template <int Version, class Writer> class ProfileWriter {
private:
    Writer& _w;
    std::string data;
    google::protobuf::io::StringOutputStream* sos;
    google::protobuf::io::CodedOutputStream* os;
    Checksum chksum;
    bool header_written;
    
public:
    ProfileWriter(Writer& w) : _w(w) {
        std::unique_ptr<google::protobuf::io::StringOutputStream> _sos(new google::protobuf::io::StringOutputStream(&data));
        sos = _sos.get();
        os = new google::protobuf::io::CodedOutputStream(sos);
        _sos.release();
        header_written = 0;
    }
    ~ProfileWriter() {
        delete os;
        delete sos;
    }

    void write_header(const recording::RecordingHeader& rh) {
        assert(! header_written);
        header_written = true;
        os->WriteVarint32(Version);
        auto sz = rh.ByteSize();
        os->WriteVarint32(sz);
        rh.SerializeToCodedStream(os);
        auto csum = chksum.chksum(reinterpret_cast<const uint8_t*>(data.c_str()), os->ByteCount());
        os->WriteVarint32(csum);
        _w.write(data, os->ByteCount(), 0);
    }
    
    void append_wse(const recording::Wse& e) {
        auto old_byte_count = os->ByteCount();
        auto sz = e.ByteSize();
        os->WriteVarint32(sz);
        e.SerializeToCodedStream(os);
        chksum.reset();
        auto data_sz = os->ByteCount() - old_byte_count;
        auto csum = chksum.chksum(reinterpret_cast<const uint8_t*>(data.c_str()), data_sz);
        os->WriteVarint32(csum);
        _w.write(data, os->ByteCount() - old_byte_count, old_byte_count);
    }
};
