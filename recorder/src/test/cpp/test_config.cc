#include <thread>
#include <vector>
#include <iostream>
#include "fixtures.hh"
#include "test.hh"
#define IN_TEST
#include "../../main/cpp/config.hh"

TEST(ParsesAllOptions) {
    TestEnv _;
    std::string str("service_endpoint=http://10.20.30.40:9070,"
                    "ip=50.60.70.80,"
                    "host=foo.host,"
                    "app_id=bar_app,"
                    "inst_grp=baz_grp,"
                    "cluster=quux_cluster,"
                    "inst_id=corge_iid,"
                    "proc=grault_proc,"
                    "vm_id=garply_vm_id,"
                    "zone=waldo_zone,"
                    "inst_type=c0.medium,"
                    "backoff_start=2,"
                    "backoff_multiplier=3,"
                    "max_retries=7,"
                    "backoff_max=15,"
                    "log_lvl=warn,"
                    "poll_itvl=30,"
                    "metrics_dst_port=10203");
    
    ConfigurationOptions options(str.c_str());
    CHECK_EQUAL("http://10.20.30.40:9070", options.service_endpoint);
    CHECK_EQUAL("50.60.70.80", options.ip);
    CHECK_EQUAL("foo.host", options.host);
    CHECK_EQUAL("bar_app", options.app_id);
    CHECK_EQUAL("baz_grp", options.inst_grp);
    CHECK_EQUAL("quux_cluster", options.cluster);
    CHECK_EQUAL("corge_iid", options.inst_id);
    CHECK_EQUAL("grault_proc", options.proc);
    CHECK_EQUAL("garply_vm_id", options.vm_id);
    CHECK_EQUAL("waldo_zone", options.zone);
    CHECK_EQUAL("c0.medium", options.inst_typ);
    CHECK_EQUAL(2, options.backoff_start);
    CHECK_EQUAL(3, options.backoff_multiplier);
    CHECK_EQUAL(7, options.max_retries);
    CHECK_EQUAL(15, options.backoff_max);
    CHECK_EQUAL(spdlog::level::warn, options.log_level);
    CHECK_EQUAL(30, options.poll_itvl);
    CHECK_EQUAL(10203, options.metrics_dst_port);
}

TEST(DefaultAppropriately) {
    TestEnv _;
    std::string str("service_endpoint=http://10.20.30.40:9070,"
                    "ip=50.60.70.80,"
                    "host=foo.host,"
                    "app_id=bar_app,"
                    "inst_grp=baz_grp,"
                    "cluster=quux_cluster,"
                    "inst_id=corge_iid,"
                    "proc=grault_proc,"
                    "vm_id=garply_vm_id,"
                    "zone=waldo_zone,"
                    "inst_type=c0.medium");
    
    ConfigurationOptions options(str.c_str());
    CHECK_EQUAL("http://10.20.30.40:9070", options.service_endpoint);
    CHECK_EQUAL("50.60.70.80", options.ip);
    CHECK_EQUAL("foo.host", options.host);
    CHECK_EQUAL("bar_app", options.app_id);
    CHECK_EQUAL("baz_grp", options.inst_grp);
    CHECK_EQUAL("quux_cluster", options.cluster);
    CHECK_EQUAL("corge_iid", options.inst_id);
    CHECK_EQUAL("grault_proc", options.proc);
    CHECK_EQUAL("garply_vm_id", options.vm_id);
    CHECK_EQUAL("waldo_zone", options.zone);
    CHECK_EQUAL("c0.medium", options.inst_typ);
    CHECK_EQUAL(5, options.backoff_start);
    CHECK_EQUAL(2, options.backoff_multiplier);
    CHECK_EQUAL(3, options.max_retries);
    CHECK_EQUAL(10 * 60, options.backoff_max);
    CHECK_EQUAL(spdlog::level::info, options.log_level);
    CHECK_EQUAL(60, options.poll_itvl);
    CHECK_EQUAL(11514, options.metrics_dst_port);
}

TEST(SafelyTerminatesStrings) {
    char* string = (char *) "/home/richard/log.hpl";
    char* result = safe_copy_string(string, NULL);

    CHECK_EQUAL(std::string("/home/richard/log.hpl"), result);
    CHECK_EQUAL('\0', result[21]);

    free(result);

    string = (char *) "/home/richard/log.hpl,interval=10";
    char* next = string + 21;
    result = safe_copy_string(string, next);

    CHECK_EQUAL(std::string("/home/richard/log.hpl"), result);
    CHECK_EQUAL('\0', result[21]);

    free(result);
}
