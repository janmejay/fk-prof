#include "ftrace_feed_handler.h"

namespace FtraceFeedHandler {
    void Parser::populate_rules() {
        using qi::int_;
        using qi::lit;
        using qi::double_;
        using qi::lexeme;
        using ascii::char_;

        word_ %= +(char_ - ' ');

        e_header_ %= word_ >> '[' >> int_ >> ']' >> word_ >> double_ >> ':';

        e_wakeup_ %= e_header_ >> lit("sched_wakeup") >> ':'
                               >> lit("comm") >> '=' >> word_
                               >> lit("pid") >> '=' >> int_
                               >> lit("prio") >> '=' >> int_
                               >> lit("target_cpu") >> '=' >> int_;

        e_switch_ %= e_header_ >> lit("sched_switch") >> ':'
                               >> lit("prev_comm") >> '=' >> word_
                               >> lit("prev_pid") >> '=' >> int_
                               >> lit("prev_prio") >> '=' >> int_
                               >> lit("prev_state") >> '=' >> char_
                               >> lit("==>")
                               >> lit("next_comm") >> '=' >> word_
                               >> lit("next_pid") >> '=' >> int_
                               >> lit("next_prio") >> '=' >> int_;

        event_ %= (e_switch_ | e_wakeup_);
    }

    bool Parser::feed(std::istream& input) {
        std::string line;
        auto handler_ok = true;
        while (handler_ok && input.good()) {
            getline(input, line);
            if (line.empty()) continue;

            Event evt;
            Iter current = line.begin();
            Iter end = line.end();
            bool r = boost::spirit::qi::phrase_parse(current, end, *this, boost::spirit::ascii::space, evt);

            if (r && (current == end)) {
                handler_ok = handler(evt);
            }
        }
        return input.eof();
    }
}
