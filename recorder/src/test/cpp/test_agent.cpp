#include <thread>
#include <vector>
#include <iostream>
#include "fixtures.h"
#include "test.h"
#include "../../main/cpp/agent.cpp"

TEST(ParsesAllOptions) {
    std::string str("service_endpoint=http://10.20.30.40:9070,ip=50.60.70.80,host=foo.host,appid=bar_app,igrp=baz_grp,cluster=quux_cluster,instid=corge_iid,proc=grault_proc,vmid=garply_vmid,zone=waldo_zone,ityp=c0.medium");
    
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
