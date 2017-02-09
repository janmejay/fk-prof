#include "test.hh"

int main() {
    auto ret = UnitTest::RunAllTests();
    logger.reset();
    return ret;
}
