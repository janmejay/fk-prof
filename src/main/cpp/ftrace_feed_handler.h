#ifndef FTRACE_PARSER_H
#define FTRACE_PARSER_H

#include <boost/config/warning_disable.hpp>
#include <boost/spirit/include/qi.hpp>
#include <boost/spirit/include/phoenix_core.hpp>
#include <boost/spirit/include/phoenix_operator.hpp>
#include <boost/spirit/include/phoenix_object.hpp>
#include <boost/fusion/include/adapt_struct.hpp>
#include <boost/fusion/include/io.hpp>
#include <boost/variant.hpp>

#include <iostream>
#include <functional>
#include <string>
#include <complex>
#include <cstdint>

namespace FtraceFeedHandler {
    namespace qi = boost::spirit::qi;
    namespace ascii = boost::spirit::ascii;

    struct EventHeader {
        std::string ctx_proc_pid;
        std::int32_t ctx_cpu;
        std::string common_flags;
        double time;
    };

    struct EventSchedSwitch {
        EventHeader h;
        
        std::string prev_comm;
        std::int32_t prev_pid;
        std::int32_t prev_priority;
        char prev_state;

        std::string next_comm;
        std::int32_t next_pid;
        std::int32_t next_priority;
    };

    struct EventSchedWakeup {
        EventHeader h;
        
        std::string comm;
        std::int32_t pid;
        std::int32_t priority;
        std::int32_t target_cpu;
    };
}

BOOST_FUSION_ADAPT_STRUCT(
    FtraceFeedHandler::EventHeader,
    (std::string, ctx_proc_pid)
    (std::int32_t, ctx_cpu)
    (std::string, common_flags)
    (double, time))

BOOST_FUSION_ADAPT_STRUCT(
    FtraceFeedHandler::EventSchedSwitch,
    (FtraceFeedHandler::EventHeader, h)
    (std::string, prev_comm)
    (std::int32_t, prev_pid)
    (std::int32_t, prev_priority)
    (char, prev_state)
    (std::string, next_comm)
    (std::int32_t, next_pid)
    (std::int32_t, next_priority))

BOOST_FUSION_ADAPT_STRUCT(
    FtraceFeedHandler::EventSchedWakeup,
    (FtraceFeedHandler::EventHeader, h)
    (std::string, comm)
    (std::int32_t, pid)
    (std::int32_t, priority)
    (std::int32_t, target_cpu))

namespace FtraceFeedHandler {
    typedef boost::variant<EventSchedSwitch, EventSchedWakeup> Event;
    typedef std::string::const_iterator Iter;

    struct Parser : qi::grammar<Iter, Event(), ascii::space_type> {
    private:
        const std::function<bool(const Event&)> handler;

        qi::rule<Iter, std::string()> word_;
        qi::rule<Iter, EventHeader(), ascii::space_type> e_header_;
        qi::rule<Iter, EventSchedWakeup(), ascii::space_type> e_wakeup_;
        qi::rule<Iter, EventSchedSwitch(), ascii::space_type> e_switch_;
        qi::rule<Iter, Event(), ascii::space_type> event_;
        void populate_rules();
        
    public:
        Parser(std::function<bool(const Event&)> _handler) : Parser::base_type(event_), handler(_handler) {
            populate_rules();
        }
        ~Parser() {}

        bool feed(std::istream& input);
    };
}

#endif
