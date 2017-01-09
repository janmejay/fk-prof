#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include "fixtures.hh"
#include "test.hh"
#include "../../main/cpp/globals.hh"
#include "../../main/cpp/scheduler.hh"

LoggerP logger(nullptr);

void init_logger() {
    if (logger == nullptr) {
        logger = spdlog::stdout_color_mt("console");
        logger->set_level(spdlog::level::trace);
    }
}

std::uint32_t elapsed_time(std::function<void()> proc) {
    auto start = Time::now();
    proc();
    auto end = Time::now();
    return std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
}

std::uint32_t time_poll(Scheduler& s) {
    return elapsed_time([&s]() { CHECK(s.poll()); });
}

TEST(Scheduler___one_at_a_time___just_expired) {
    init_logger();
    int order_tracker = 0;
    Scheduler s;

    auto start = Time::now();
    
    s.schedule(start, [&]() { order_tracker = 1; });
    CHECK_EQUAL(0, order_tracker);
    CHECK_CLOSE(1, time_poll(s), 1);
    CHECK_EQUAL(1, order_tracker);
    
    s.schedule(start, [&]() { order_tracker = 2; });
    CHECK_EQUAL(1, order_tracker);
    CHECK_CLOSE(1, time_poll(s), 1);
    CHECK_EQUAL(2, order_tracker);
}

TEST(Scheduler___one_at_a_time___pre_expired) {
    init_logger();
    int order_tracker = 0;
    Scheduler s;

    s.schedule(Time::now() - Time::sec(10), [&]() { order_tracker = 1; });
    CHECK_EQUAL(0, order_tracker);
    CHECK_CLOSE(1, time_poll(s), 1);
    CHECK_EQUAL(1, order_tracker);

    s.schedule(Time::now() - Time::sec(5), [&]() { order_tracker = 2; });
    CHECK_EQUAL(1, order_tracker);
    CHECK_CLOSE(1, time_poll(s), 1);
    CHECK_EQUAL(2, order_tracker);
}

TEST(Scheduler___several___pre_expired) {
    init_logger();
    int order_tracker[] = {0, 0};
    Scheduler s;

    s.schedule(Time::now() - Time::sec(5), [&]() { order_tracker[1] = 5; });
    s.schedule(Time::now() - Time::sec(10), [&]() { order_tracker[0] = 10; });

    CHECK_EQUAL(0, order_tracker[0]);
    CHECK_EQUAL(0, order_tracker[1]);
    CHECK_CLOSE(1, time_poll(s), 1);
    CHECK_EQUAL(10, order_tracker[0]);
    CHECK_EQUAL(5, order_tracker[1]);
    auto elapsed = elapsed_time([&s]() {
            CHECK(! s.poll());
        });
    CHECK_CLOSE(1, elapsed, 1);
}

TEST(Scheduler___several___future) {
    init_logger();
    int order_tracker = 0;
    Scheduler s;

    auto now = Time::now();
    s.schedule(now + Time::sec(1), [&]() { order_tracker = 1000; });
    s.schedule(now + std::chrono::milliseconds(200), [&]() { order_tracker = 200; });
    s.schedule(now + std::chrono::milliseconds(70), [&]() { order_tracker = 70; });
    s.schedule(now + std::chrono::milliseconds(900), [&]() { order_tracker = 900; });
    s.schedule(now + std::chrono::milliseconds(500), [&]() { order_tracker = 500; });

    CHECK_EQUAL(0, order_tracker);
    
    CHECK_CLOSE(70 - 0, time_poll(s), 50);
    CHECK_EQUAL(70, order_tracker);
    
    CHECK_CLOSE(200 - 70, time_poll(s), 50);
    CHECK_EQUAL(200, order_tracker);
    
    CHECK_CLOSE(500 - 200, time_poll(s), 50);
    CHECK_EQUAL(500, order_tracker);
    
    CHECK_CLOSE(900 - 500, time_poll(s), 50);
    CHECK_EQUAL(900, order_tracker);
    
    CHECK_CLOSE(1000 - 900, time_poll(s), 50);
    CHECK_EQUAL(1000, order_tracker);
}

TEST(Scheduler___too_many_pops) {
    init_logger();
    int order_tracker = 0;
    Scheduler s;

    s.schedule(Time::now() - Time::sec(5), [&]() { order_tracker = 1; });
    CHECK(s.poll());
    CHECK(! s.poll());
}
