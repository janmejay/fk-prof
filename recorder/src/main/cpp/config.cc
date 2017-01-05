#include "config.hh"

char *safe_copy_string(const char *value, const char *next);
void safe_free_string(char *&value);

typedef std::unique_ptr<char, void(*)(char*&)> ConfArg;

static bool matches(const char* expected, const ConfArg& val) {
    return std::strcmp(val.get(), expected) == 0;
}

static spdlog::level::level_enum resolv_log_level(ConfArg& level) {
    if (matches("off", level)) {
        return spdlog::level::off;
    } else if (matches("critical", level)) {
        return spdlog::level::critical;
    } else if (matches("err", level)) {
        return spdlog::level::err;
    } else if (matches("warn", level)) {
        return spdlog::level::warn;
    } else if (matches("debug", level)) {
        return spdlog::level::debug;
    } else if (matches("trace", level)) {
        return spdlog::level::trace;
    } else {
        return spdlog::level::info;
    }
}

void ConfigurationOptions::load(const char* options) {
    const char* next = options;
    for (const char *key = options; next != NULL; key = next + 1) {
        const char *value = strchr(key, '=');
        next = strchr(key, ',');
        if (value == NULL) {
            logError("WARN: No value for key %s\n", key);
            continue;
        } else {
            value++;
            if (strstr(key, "service_endpoint") == key) {
                service_endpoint = safe_copy_string(value, next);
            } else if (strstr(key, "ip") == key) {
                ip = safe_copy_string(value, next);
            } else if (strstr(key, "host") == key) {
                host = safe_copy_string(value, next);
            } else if (strstr(key, "appid") == key) {
                app_id = safe_copy_string(value, next);
            } else if (strstr(key, "igrp") == key) {
                inst_grp = safe_copy_string(value, next);
            } else if (strstr(key, "cluster") == key) {
                cluster = safe_copy_string(value, next);
            } else if (strstr(key, "instid") == key) {
                inst_id = safe_copy_string(value, next);
            } else if (strstr(key, "proc") == key) {
                proc = safe_copy_string(value, next);
            } else if (strstr(key, "vmid") == key) {
                vm_id = safe_copy_string(value, next);
            } else if (strstr(key, "zone") == key) {
                zone = safe_copy_string(value, next);
            } else if (strstr(key, "ityp") == key) {
                inst_typ = safe_copy_string(value, next);
            } else if (strstr(key, "backoffStart") == key) {
                backoff_start = (std::uint32_t) atoi(value);
                if (backoff_start == 0) backoff_start = MIN_BACKOFF_START;
            } else if (strstr(key, "backoffMultiplier") == key) {
                backoff_multiplier = (std::uint32_t) atoi(value);
                if (backoff_multiplier == 0) backoff_multiplier = DEFAULT_BACKOFF_MULTIPLIER;
            } else if (strstr(key, "maxRetries") == key) {
                max_retries = (std::uint32_t) atoi(value);
            } else if (strstr(key, "backoffMax") == key) {
                backoff_max = (std::uint32_t) atoi(value);
                if (backoff_max == 0) backoff_max = DEFAULT_BACKOFF_MAX;
            } else if (strstr(key, "logLvl") == key) {
                ConfArg val(safe_copy_string(value, next), safe_free_string);
                log_level = resolv_log_level(val);
            } else if (strstr(key, "pollItvl") == key) {
                poll_itvl = (std::uint32_t) atoi(value);
                if (poll_itvl == 0) poll_itvl = DEFAULT_POLLING_INTERVAL;
            } else {
                logger->warn("Unknown configuration option: {}\n", key);
            }
        }
    }
}

ConfigurationOptions::~ConfigurationOptions()  {
    safe_free_string(service_endpoint);
    safe_free_string(ip);
    safe_free_string(app_id);
    safe_free_string(inst_grp);
    safe_free_string(cluster);
    safe_free_string(proc);
    safe_free_string(vm_id);
    safe_free_string(zone);
    safe_free_string(inst_typ);
}
