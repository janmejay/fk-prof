#ifndef CONFIG_H
#define CONFIG_H

#include <cstdint>
#include "globals.h"

static const std::uint32_t DEFAULT_BACKOFF_MULTIPLIER = 2;
static const std::uint32_t DEFAULT_MAX_RETRIES = 3;

// all of these are seconds
static const std::uint32_t MIN_BACKOFF_START = 5;
static const std::uint32_t DEFAULT_BACKOFF_MAX = 10 * 60;
static const std::uint32_t DEFAULT_POLLING_INTERVAL = 60;
    
struct ConfigurationOptions {
    char* service_endpoint;
    
    /*self-info*/
    char* ip;
    char* host;
    char* app_id;
    char* inst_grp;
    char* inst_id;
    char* cluster;
    char* proc;
    char* vm_id;
    char* zone;
    char* inst_typ;

    std::uint32_t backoff_start;
    std::uint32_t backoff_multiplier;
    std::uint32_t backoff_max;
    std::uint32_t max_retries;
    std::uint32_t poll_itvl;
    
    spdlog::level::level_enum log_level;

    ConfigurationOptions(const char* options) :
        service_endpoint(nullptr),
        ip(nullptr),
        app_id(nullptr),
        inst_grp(nullptr),
        inst_id(nullptr),
        cluster(nullptr),
        proc(nullptr),
        vm_id(nullptr),
        zone(nullptr),
        inst_typ(nullptr),
        backoff_start(MIN_BACKOFF_START), backoff_multiplier(DEFAULT_BACKOFF_MULTIPLIER), backoff_max(DEFAULT_BACKOFF_MAX), max_retries(DEFAULT_MAX_RETRIES),
        poll_itvl(DEFAULT_POLLING_INTERVAL),
        log_level(spdlog::level::info) {
        load(options);
    }

    virtual ~ConfigurationOptions();

    bool valid() {//TODO: ensure we check for valid config (implement defaulting before validity-check)
        return true;
    }

private:
    void load(const char* options);
};

#endif
