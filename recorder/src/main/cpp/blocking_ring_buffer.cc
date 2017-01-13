#include "blocking_ring_buffer.hh"
#include "globals.hh"

std::uint32_t BlockingRingBuffer::write(const std::uint8_t *from, std::uint32_t offset, std::uint32_t sz, bool do_block) {
    std::unique_lock<std::mutex> lock(m);

    std::uint32_t total_written = 0;
    while (sz > 0) {
        auto written = write_noblock(from, offset, sz);
        total_written += written;
        if ((! do_block) || (sz == 0)) {
            if (total_written > 0) {
                readable.notify_all();
            }
            return total_written;
        }
        writable.wait(lock, [&] { return available < capacity; });
    }
    return total_written;
}

std::uint32_t BlockingRingBuffer::read(std::uint8_t *to, std::uint32_t offset, std::uint32_t sz, bool do_block) {
    std::unique_lock<std::mutex> lock(m);

    std::uint32_t total_read = 0;
    while (sz > 0) {
        auto read = read_noblock(to, offset, sz);
        total_read += read;
        if ((! do_block) || (sz == 0)) {
            if (total_read > 0) {
                writable.notify_all();
            }
            return total_read;
        }
        readable.wait(lock, [&] { return available > 0; });
    }
    return total_read;
}

std::uint32_t BlockingRingBuffer::write_noblock(const std::uint8_t *from, std::uint32_t& offset, std::uint32_t& sz) {
    if (available == capacity) return 0;
    
    auto available_before = available;

    if ((read_idx < write_idx) ||
        (read_idx == write_idx &&
         available == 0)) {
        auto copy_bytes = min(sz, capacity - write_idx);
        std::memcpy(buff + write_idx, from + offset, copy_bytes);
        available += copy_bytes;
        offset += copy_bytes;
        sz -= copy_bytes;
        write_idx += copy_bytes;
        if (write_idx == capacity) write_idx = 0;
        if (sz == 0) {
            return copy_bytes;
        }
    }

    auto bytes_copied = available - available_before;
    auto copy_bytes = min(sz, read_idx - write_idx);
    if (copy_bytes > 0) {
        std::memcpy(buff + write_idx, from + offset, copy_bytes);
        write_idx += copy_bytes;
        if (write_idx == capacity) write_idx = 0;
        bytes_copied += copy_bytes;
        available += copy_bytes;
        offset += copy_bytes;
        sz -= copy_bytes;
    }

    return bytes_copied;
}

std::uint32_t BlockingRingBuffer::read_noblock(std::uint8_t *to, std::uint32_t& offset, std::uint32_t& sz) {
    if (available == 0) return 0;

    auto available_before = available;

    if ((write_idx < read_idx) ||
        (read_idx == write_idx &&
         available == capacity)) {
        auto copy_bytes = min(sz, capacity - read_idx);
        std::memcpy(to + offset, buff + read_idx, copy_bytes);
        available -= copy_bytes;
        offset += copy_bytes;
        sz -= copy_bytes;
        read_idx += copy_bytes;
        if (read_idx == capacity) read_idx = 0;
        if (sz == 0) {
            return copy_bytes;
        }
    }

    auto bytes_copied = available_before - available;
    auto copy_bytes = min(sz, write_idx - read_idx);
    if (copy_bytes > 0) {
        std::memcpy(to + offset, buff + read_idx, copy_bytes);
        read_idx += copy_bytes;
        if (read_idx == capacity) read_idx = 0;
        bytes_copied += copy_bytes;
        available -= copy_bytes;
        offset += copy_bytes;
        sz -= copy_bytes;
    }

    return bytes_copied;
}

std::uint32_t BlockingRingBuffer::clear() {
    auto old_available = available;
    read_idx = write_idx = available = 0;
    return old_available;
}
