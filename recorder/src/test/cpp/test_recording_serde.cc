#include <thread>
#include <vector>
#include <iostream>
#include <fstream>
#include "fixtures.hh"
#include "test.hh"
#include "recorder.pb.h"
#include <initializer_list>
#include <unordered_map>
#include "test_profile.hh"
#include "../../main/cpp/checksum.hh"

const std::string PROFILE_DATA_FILE = "/tmp/profile.data";

TEST(WriteAndReadBack_CPUSampleRecording) {
    generate_cpusample_simple_profile(PROFILE_DATA_FILE);

    std::ifstream profile_data_input(PROFILE_DATA_FILE, std::ios_base::in | std::ios_base::binary);

    const uint32_t buff_sz = 2048;

    std::uint8_t buff[buff_sz];

    profile_data_input.read(reinterpret_cast<char*>(buff), buff_sz);

    int len = profile_data_input.gcount();

    Checksum csum;
    auto actual_chksum = csum.chksum(buff, len);

    CHECK_EQUAL(2686278808, actual_chksum);
}

