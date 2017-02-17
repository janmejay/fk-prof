#include <google/protobuf/io/coded_stream.h>
#include "checksum.hh"
#include "recorder.pb.h"
#include "buff.hh"

#ifndef PROFILE_WRITER_H
#define PROFILE_WRITER_H

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

    RawWriter& w;
    Checksum chksum;
    Buff &data;
    bool header_written;

    void flush();

    std::uint32_t ensure_freebuff(std::uint32_t min_reqired);

    template <class T> std::uint32_t ensure_freebuff(const T& value);
    
    void write_unchecked(std::uint32_t value);

    template <class T> void write_unchecked_obj(const T& value);

public:
    ProfileWriter(RawWriter& _w, Buff& _data) : w(_w), data(_data), header_written(false) {
        data.write_end = data.read_end = 0;
    }
    ~ProfileWriter() {
        flush();
    }

    void write_header(const recording::RecordingHeader& rh);

    void append_wse(const recording::Wse& e);
};
#endif
