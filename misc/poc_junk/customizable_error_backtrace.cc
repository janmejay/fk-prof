#include <cstdint>
#include <iostream>
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

typedef std::uint64_t Addr;

namespace MRegion {
    namespace qi = boost::spirit::qi;
    namespace ascii = boost::spirit::ascii;

    struct Range {
        std::string start;
        std::string end;
    };

    struct Dev {
        std::uint32_t maj;
        std::uint32_t min;
    };

    struct Region {
        Range range;
        std::string perms;
        std::uint64_t offset;
        Dev dev;
        std::uint64_t inode;
        std::string path;
    };
}

BOOST_FUSION_ADAPT_STRUCT(
    MRegion::Range,
    (std::string, start)
    (std::string, end))

BOOST_FUSION_ADAPT_STRUCT(
    MRegion::Dev,
    (std::uint32_t, maj)
    (std::uint32_t, min))

BOOST_FUSION_ADAPT_STRUCT(
    MRegion::Region,
    (MRegion::Range, range)
    (std::string, perms)
    (std::uint64_t, offset)
    (MRegion::Dev, dev)
    (std::uint64_t, inode)
    (std::string, path))

namespace MRegion {
    typedef boost::variant<MapLine> Event;
    typedef std::string::const_iterator Iter;

    struct Parser : qi::grammar<Iter, Event(), ascii::space_type> {
    private:
        const std::function<bool(const Event&)> handler;

        qi::rule<Iter, std::string()> hex_num_;
        qi::rule<Iter, Range(), ascii::space_type> range_;
        qi::rule<Iter, std::string()> perms_;
        qi::rule<Iter, Dev(), ascii::space_type> dev_;
        qi::rule<Iter, Region(), ascii::space_type> region_;
        qi::rule<Iter, Event(), ascii::space_type> line_;

        void populate_rules() {
            using qi::int_;
            using qi::lit;
            using qi::double_;
            using qi::lexeme;
            using ascii::char_;

            hex_num_ %= +char_("a-fA-F0-9");

            range_ %= hex_num_ >> lit("-") >> hex_num_;

            perms_ %= +char_("-rwxps");

            dev_ %= hex_ >> lit(":") >> hex_;

            region_ %= range_ >> perms_ >> ulong_ >> dev_ >> ulong_ >> +(char_)
                                
            event_ %= (range_);
        }
        
    public:
        Parser(std::function<bool(const Event&)> _handler) : Parser::base_type(event_), handler(_handler) {
            populate_rules();
        }
        ~Parser() {}

        bool feed(std::istream& input) {
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
    };
}

class MappedRegion {
private:
    Addr start;
    Addr end;
    std::map<Addr, std::string> symbols;
    
};

class SymInfo {
private:
    std::map<Addr, MappedRegion> mapped; //start -> region

public:
    SymInfo() {
        
    }
};

void print_bt() {
    // asm("movq $1729, %rax");
    std::uint64_t rbp, rpc, rax;
    asm("movq %%rax, %2;"
        "movq %%rbp, %%rax;"
        "movq %%rax, %0;"
        "lea (%%rip), %%rax;"
        "movq %%rax, %1;"
        "movq %3, %%rax;"
        : "=r"(rbp), "=r"(rpc), "=r"(rax)
        : "r"(rax));

    // std::uint64_t rax_rr;
    // asm("movq %%rax, %0" : "=r" (rax_rr));
    // std::cout << "RAX_rr: " << rax_rr << " " << std::hex << rax_rr << '\n';
    std::cout << "base: 0x" << std::hex << rbp << "    PC: 0x" << std::hex << rpc << '\n';

    while (true) {
        std::cout << "0x" << std::hex << rpc << name_for(rpc, ) << '\n';
        rbp = *reinterpret_cast<std::uint64_t*>(rbp);
        if (rbp == 0) break;
        rpc = *reinterpret_cast<std::uint64_t*>(rbp + 8);
        if (rpc == 0) break;
    }
}

int baz() {
    print_bt();
    return 5;
}

int bar() {
    return 5 + baz();
}

int foo(int x) {
    auto b = bar();
    return x - b;
}

int foo() {
    return foo(10);
}

int main() {
    return foo();
}
