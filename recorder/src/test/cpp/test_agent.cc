#include <thread>
#include <vector>
#include <iostream>
#include "fixtures.hh"
#include "test.hh"
#include "../../main/cpp/agent.cc"

TEST(ParsesAllOptions) {
    std::string str("service_endpoint=http://10.20.30.40:9070,"
                    "ip=50.60.70.80,"
                    "host=foo.host,"
                    "appid=bar_app,"
                    "igrp=baz_grp,"
                    "cluster=quux_cluster,"
                    "instid=corge_iid,"
                    "proc=grault_proc,"
                    "vmid=garply_vmid,"
                    "zone=waldo_zone,"
                    "ityp=c0.medium,"
                    "backoffStart=2,"
                    "backoffMultiplier=3,"
                    "maxRetries=7,"
                    "backoffMax=15,"
                    "logLvl=warn,"
                    "pollItvl=30");
    
    ConfigurationOptions options(str.c_str());
    CHECK_EQUAL("http://10.20.30.40:9070", options.service_endpoint);
    CHECK_EQUAL("50.60.70.80", options.ip);
    CHECK_EQUAL("foo.host", options.host);
    CHECK_EQUAL("bar_app", options.app_id);
    CHECK_EQUAL("baz_grp", options.inst_grp);
    CHECK_EQUAL("quux_cluster", options.cluster);
    CHECK_EQUAL("corge_iid", options.inst_id);
    CHECK_EQUAL("grault_proc", options.proc);
    CHECK_EQUAL("garply_vmid", options.vm_id);
    CHECK_EQUAL("waldo_zone", options.zone);
    CHECK_EQUAL("c0.medium", options.inst_typ);
    CHECK_EQUAL(2, options.backoff_start);
    CHECK_EQUAL(3, options.backoff_multiplier);
    CHECK_EQUAL(7, options.max_retries);
    CHECK_EQUAL(15, options.backoff_max);
    CHECK_EQUAL(spdlog::level::warn, options.log_level);
    CHECK_EQUAL(30, options.poll_itvl);

}

TEST(DefaultAppropriately) {
    std::string str("service_endpoint=http://10.20.30.40:9070,"
                    "ip=50.60.70.80,"
                    "host=foo.host,"
                    "appid=bar_app,"
                    "igrp=baz_grp,"
                    "cluster=quux_cluster,"
                    "instid=corge_iid,"
                    "proc=grault_proc,"
                    "vmid=garply_vmid,"
                    "zone=waldo_zone,"
                    "ityp=c0.medium");
    
    ConfigurationOptions options(str.c_str());
    CHECK_EQUAL("http://10.20.30.40:9070", options.service_endpoint);
    CHECK_EQUAL("50.60.70.80", options.ip);
    CHECK_EQUAL("foo.host", options.host);
    CHECK_EQUAL("bar_app", options.app_id);
    CHECK_EQUAL("baz_grp", options.inst_grp);
    CHECK_EQUAL("quux_cluster", options.cluster);
    CHECK_EQUAL("corge_iid", options.inst_id);
    CHECK_EQUAL("grault_proc", options.proc);
    CHECK_EQUAL("garply_vmid", options.vm_id);
    CHECK_EQUAL("waldo_zone", options.zone);
    CHECK_EQUAL("c0.medium", options.inst_typ);
    CHECK_EQUAL(5, options.backoff_start);
    CHECK_EQUAL(2, options.backoff_multiplier);
    CHECK_EQUAL(3, options.max_retries);
    CHECK_EQUAL(10 * 60, options.backoff_max);
    CHECK_EQUAL(spdlog::level::info, options.log_level);
    CHECK_EQUAL(60, options.poll_itvl);
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
