#include "config.hh"

char *safe_copy_string(const char *value, const char *next) {
    size_t size = (next == 0) ? strlen(value) : (size_t) (next - value);
    char *dest = (char *) malloc((size + 1) * sizeof(char));

    strncpy(dest, value, size);
    dest[size] = '\0';

    return dest;
}

void safe_free_string(char *&value) {
    if (value != NULL) {
        free(value);
        value = NULL;
    }
}

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
            logger->warn("WARN: No value for key {}", key);
            continue;
        } else {
            value++;
            if (strstr(key, "service_endpoint") == key) {
                service_endpoint = safe_copy_string(value, next);
            } else if (strstr(key, "ip") == key) {
                ip = safe_copy_string(value, next);
            } else if (strstr(key, "host") == key) {
                host = safe_copy_string(value, next);
            } else if (strstr(key, "app_id") == key) {
                app_id = safe_copy_string(value, next);
            } else if (strstr(key, "inst_grp") == key) {
                inst_grp = safe_copy_string(value, next);
            } else if (strstr(key, "cluster") == key) {
                cluster = safe_copy_string(value, next);
            } else if (strstr(key, "inst_id") == key) {
                inst_id = safe_copy_string(value, next);
            } else if (strstr(key, "proc") == key) {
                proc = safe_copy_string(value, next);
            } else if (strstr(key, "vm_id") == key) {
                vm_id = safe_copy_string(value, next);
            } else if (strstr(key, "zone") == key) {
                zone = safe_copy_string(value, next);
            } else if (strstr(key, "inst_typ") == key) {
                inst_typ = safe_copy_string(value, next);
            } else if (strstr(key, "backoff_start") == key) {
                backoff_start = static_cast<std::uint32_t>(atoi(value));
                if (backoff_start == 0) backoff_start = MIN_BACKOFF_START;
            } else if (strstr(key, "backoff_multiplier") == key) {
                backoff_multiplier = static_cast<std::uint32_t>(atoi(value));
                if (backoff_multiplier == 0) backoff_multiplier = DEFAULT_BACKOFF_MULTIPLIER;
            } else if (strstr(key, "max_retries") == key) {
                max_retries = static_cast<std::uint32_t>(atoi(value));
            } else if (strstr(key, "backoff_max") == key) {
                backoff_max = static_cast<std::uint32_t>(atoi(value));
                if (backoff_max == 0) backoff_max = DEFAULT_BACKOFF_MAX;
            } else if (strstr(key, "log_lvl") == key) {
                ConfArg val(safe_copy_string(value, next), safe_free_string);
                log_level = resolv_log_level(val);
                logger->warn("Log-level set to: {}", log_level);
            } else if (strstr(key, "poll_itvl") == key) {
                poll_itvl = static_cast<std::uint32_t>(atoi(value));
                if (poll_itvl == 0) poll_itvl = DEFAULT_POLLING_INTERVAL;
            } else if (strstr(key, "metrics_dst_port") == key) {
                metrics_dst_port = static_cast<std::uint16_t>(atoi(value));
                if (metrics_dst_port == 0) metrics_dst_port = DEFAULT_METRICS_DEST_PORT;
            } else if (strstr(key, "noctx_cov_pct") == key) {
                noctx_cov_pct = static_cast<std::uint8_t>(atoi(value));
                if (noctx_cov_pct > 100) noctx_cov_pct = 100;
            } else {
                logger->warn("Unknown configuration option: {}", key);
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
