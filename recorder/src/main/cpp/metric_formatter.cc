#include "metric_formatter.hh"

//

std::string MetricFormatter::hostname() {
    char buff[HOST_NAME_MAX + 1];
    if (gethostname(buff, sizeof(buff)) < 0) {
        return "UNKNOWN";
    }
    return buff;
}

MetricFormatter::SyslogTsdbFormatter::SyslogTsdbFormatter(std::string _tsdb_tags, std::string _tags, std::string _host, std::uint16_t _pri_sev) :
    tsdb_tags(_tsdb_tags), tags(_tags), host(_host), pri_sev(_pri_sev) { }

MetricFormatter::SyslogTsdbFormatter::~SyslogTsdbFormatter() {}

template <typename... SuffixFrags> void append(std::stringstream& ss) {}

template <typename... SuffixFrags> void append(std::stringstream& ss, const std::string& first, const SuffixFrags&... rest) {
    ss << "." << first;
    append(ss, rest...);
}

template <typename V, typename... SuffixFrags> std::string line(std::uint16_t pri_sev, const std::string& host, const std::string& tags, const std::string& tsdb_tags, const std::string& name, const std::string& type, V v, const SuffixFrags&... suffixes) {
    std::time_t t = std::time(nullptr);
    std::tm tm = *std::localtime(&t);
    char buffer[32];
    strftime(buffer, sizeof(buffer),"%b %d %H:%M:%S", &tm);

    std::stringstream ss;
    ss << "<" << pri_sev << ">" << buffer << " " << host << " " << tags << " " << t << " " << name << "." << type;
    append(ss, suffixes...);
    ss << " " << v << " " << tsdb_tags;
    return ss.str();
}

std::string MetricFormatter::SyslogTsdbFormatter::operator()(MetricType type, PropName prop, std::uint64_t val) {
    return line(pri_sev, host, tags, tsdb_tags, *name, type, val, prop);
}

std::string MetricFormatter::SyslogTsdbFormatter::operator()(MetricType type, PropName prop, std::int64_t val) {
    return line(pri_sev, host, tags, tsdb_tags, *name, type, val, prop);
}

std::string MetricFormatter::SyslogTsdbFormatter::operator()(MetricType type, PropName prop, double val) {
    return line(pri_sev, host, tags, tsdb_tags, *name, type, val, prop);
}

std::string MetricFormatter::SyslogTsdbFormatter::operator()(MetricType type, PropName prop, std::uint64_t val, Unit unit) {
    return line(pri_sev, host, tags, tsdb_tags, *name, type, val, prop, unit);
}

std::string MetricFormatter::SyslogTsdbFormatter::operator()(MetricType type, PropName prop, double val, Unit unit) {
    return line(pri_sev, host, tags, tsdb_tags, *name, type, val, prop, unit);
}

std::string MetricFormatter::SyslogTsdbFormatter::operator()(MetricType type, EventType evt_typ, PropName prop, std::uint64_t val, Unit unit) {
    return line(pri_sev, host, tags, tsdb_tags, *name, type, val, evt_typ, prop, unit);
}

std::string MetricFormatter::SyslogTsdbFormatter::operator()(MetricType type, EventType evt_typ, PropName prop, double val, Unit unit) {
    return line(pri_sev, host, tags, tsdb_tags, *name, type, val, evt_typ, prop, unit);
}

