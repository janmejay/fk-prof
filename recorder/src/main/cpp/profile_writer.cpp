#include "profile_writer.h"
#include "buff.h"
#include "globals.h"

//for buff, no one uses read-end here, so it is inconsistent

void ProfileWriter::flush() {
    w.write_unbuffered(data.buff, data.write_end, 0); //TODO: err-check me!
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
