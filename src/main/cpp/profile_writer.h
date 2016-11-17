#include <google/protobuf/io/coded_stream.h>
#include "checksum.h"
#include "recorder.pb.h"

template <int Version, class Writer> class ProfileWriter {
private:
    //MIN_FREE_BUFF should accomodate atleast 4 varint32 values
    static const std::uint32_t MIN_FREE_BUFF = 64, BUFF_SZ = 0xFFFF;

    Writer& _w;
    Checksum chksum;
    std::uint8_t *data;
    std::uint32_t data_capacity, data_offset;
    bool header_written;

    inline void flush() {
        _w.write_unbuffered(data, data_offset, 0); //TODO: err-check me!
    }

    inline std::uint32_t ensure_freebuff(std::uint32_t min_reqired) {
        if ((data_offset + min_reqired) > data_capacity) {
            flush();
            data_offset = 0;
            if (min_reqired > data_capacity) {
                delete data;
                data_capacity = min_reqired * 2;
                data = new std::uint8_t[data_capacity];
            }
        }
        return data_capacity - data_offset;
    }

    template <class T> inline std::uint32_t ensure_freebuff(const T& value) {
        auto sz = value.ByteSize();
        return ensure_freebuff(sz + MIN_FREE_BUFF);
    }
    
    inline void write_unchecked(std::uint32_t value) {
        auto _data = google::protobuf::io::CodedOutputStream::WriteVarint32ToArray(value, data + data_offset);
        data_offset = _data - data;
    }

    template <class T> inline void write_unchecked_obj(const T& value) {
        auto sz = value.ByteSize();
        write_unchecked(sz);
        if (! value.SerializeToArray(data + data_offset, sz)) {
            //TODO: handle this error
        }
        data_offset += sz;
    }

public:
    ProfileWriter(Writer& w) : _w(w), data(new std::uint8_t[BUFF_SZ]), data_capacity(BUFF_SZ), data_offset(0), header_written(false) {}
    ~ProfileWriter() {
        flush();
        delete data;
    }

    void write_header(const recording::RecordingHeader& rh) {
        assert(! header_written);
        header_written = true;
        ensure_freebuff(rh);
        write_unchecked(Version);
        write_unchecked_obj(rh);
        auto csum = chksum.chksum(data, data_offset);
        write_unchecked(csum);
    }

    void append_wse(const recording::Wse& e) {
        ensure_freebuff(e);
        auto old_offset = data_offset;
        write_unchecked_obj(e);
        chksum.reset();
        auto data_sz = data_offset - old_offset;
        auto csum = chksum.chksum(data + old_offset, data_sz);
        write_unchecked(csum);
    }
};
