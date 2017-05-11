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
#include <fstream>

#include <map>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <gelf.h>
#include <elfutils/libebl.h>
#include <cxxabi.h>

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
    typedef Region Event;
    typedef std::string::const_iterator Iter;

    struct Parser : qi::grammar<Iter, Event(), ascii::space_type> {
    private:
        const std::function<bool(const Event&)> handler;

        qi::rule<Iter, std::string()> hex_;
        qi::rule<Iter, Range(), ascii::space_type> range_;
        qi::rule<Iter, std::string()> perms_;
        qi::rule<Iter, Dev(), ascii::space_type> dev_;
        qi::rule<Iter, Region(), ascii::space_type> region_;

        void populate_rules() {
            using qi::int_;
            using qi::hex;
            using qi::ulong_;
            using qi::lit;
            using qi::double_;
            using qi::lexeme;
            using ascii::char_;

            hex_ %= +char_("a-fA-F0-9");

            range_ %= hex_ >> lit("-") >> hex_;

            perms_ %= +char_("-rwxps");

            dev_ %= hex >> lit(":") >> hex;

            region_ %= range_ >> perms_ >> ulong_ >> dev_ >> ulong_ >> +(char_);
        }
        
    public:
        Parser(std::function<bool(const Event&)> _handler) : Parser::base_type(region_), handler(_handler) {
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

class SymInfoError : std::runtime_error {
public:
    SymInfoError(const char* msg) : std::runtime_error(msg) {}
    SymInfoError(const std::string& msg) : std::runtime_error(msg) {}
    virtual ~SymInfoError() {}

    static SymInfoError err(int err_num) {
        int sz = 1024;
        std::unique_ptr<char[]> buff(new char[sz]);
        if (strerror_r(err_num, buff.get(), sz) != 0) {
            std::string unknown_error = "Unknown error: " + std::to_string(err_num);
            throw SymInfoError(unknown_error.c_str());
        }
        return SymInfoError(buff.get());
    }
};

struct MappedRegion {
    Addr start;
    Addr end;
    Addr offset;
    const std::string file;
    std::map<Addr, std::string> symbols;

    MappedRegion(Addr start_, Addr end_, Addr offset_, std::string file_): start(start_), end(end_), offset(offset_), file(file_) {
        int fd = open(file.c_str(), O_RDONLY);
        if (fd == -1) throw SymInfoError::err(errno);
        Elf *elf = elf_begin(fd, ELF_C_READ_MMAP, NULL);
        if (elf == nullptr) {
            close(fd);
            throw SymInfoError("File format not recognizable for: " + file);
        }
        if (elf_kind (elf) != ELF_K_ELF) {
            close(fd);
            throw SymInfoError("File " + file + " is not an elf.");
        }
        load_elf(elf);
        if (elf_end(elf) != 0) {
            close(fd);
            throw SymInfoError("Elf " + file + " couldn't be closed.");
        }
        close(fd);
    }
    ~MappedRegion() {}

    const std::string site_for(Addr addr) {
        auto it = symbols.lower_bound(addr);
        if (it == std::end(symbols)) return "Unknown";
        it--;
        return it->second + " +" + std::to_string(addr - it->first);
    }

private:

    SymInfoError section_error(Ebl* ebl, Elf* elf, size_t shstrndx, Elf_Scn *scn, GElf_Shdr *shdr, const std::string& msg) {
        std::stringstream ss;
        ss << file << ": " << elf_ndxscn(scn) << " " <<
            elf_strptr(elf, shstrndx, shdr->sh_name)<< ": " << msg;
        std::string err = ss.str();
        throw SymInfoError(err.c_str());
    }

    void load_symbols(Ebl* ebl, Elf* elf, GElf_Ehdr* ehdr,
                      Elf_Scn* scn, Elf_Scn* xndxscn,
                      GElf_Shdr* shdr) {
        size_t shstrndx;
        if (elf_getshdrstrndx (elf, &shstrndx) < 0) {
            throw SymInfoError("Cannot get section header str-table index");
        }
        size_t size = shdr->sh_size;
        size_t entsize = shdr->sh_entsize;

        if (entsize == 0
            || entsize != gelf_fsize (elf, ELF_T_SYM, 1, EV_CURRENT)) {
            throw section_error(ebl, elf, shstrndx, scn, shdr, "Entry size in section not as expected");
        } else if (size % entsize != 0) {
            throw section_error(ebl, elf, shstrndx, scn, shdr, "Size of section not multiple of entry sz");
        }

        size_t nentries = size / (entsize ?: 1);

        Elf_Data *data = elf_getdata (scn, NULL);
        Elf_Data *xndxdata = elf_getdata (xndxscn, NULL);
        if (data == NULL || (xndxscn != NULL && xndxdata == NULL))
            throw SymInfoError("Couldn't get section/extended-section data");

        size_t demangle_buffer_len = 0;
        char *demangle_buffer = nullptr;

        GElf_Sym sym_buff;
        Elf32_Word xndx;
        for (size_t cnt = 0; cnt < nentries; cnt++) {
            GElf_Sym* sym = gelf_getsymshndx(data, xndxdata, cnt, &sym_buff, &xndx);
            if (sym == nullptr) {
                throw SymInfoError("Symbol was null");
            }
            if (sym->st_shndx == SHN_UNDEF) continue;
            const char* symstr = elf_strptr(elf, shdr->sh_link, sym->st_name);
            if (symstr == nullptr) continue;
            if ((strlen(symstr) > 1) && (symstr[0] == '_') && (symstr[1] == 'Z')) {
                int status = -1;
                char* dmsymstr = abi::__cxa_demangle (symstr, demangle_buffer, &demangle_buffer_len, &status);
                if (status == 0) symstr = dmsymstr;
            }
            symbols[sym->st_value] = symstr;
        }
        if (demangle_buffer != nullptr) free(demangle_buffer);
    }
    
    void load_elf(Elf* elf) {
        Ebl* ebl = ebl_openbackend(elf);
        GElf_Ehdr ehdr_mem;
        GElf_Ehdr *ehdr = gelf_getehdr (elf, &ehdr_mem);
        if (ehdr->e_type != ET_EXEC && ehdr->e_type != ET_DYN) {
            ebl_closebackend(ebl);
            throw SymInfoError("Elf " + file + " wasn't an executable or a shared-lib");
        }
        Elf_Scn* scn = nullptr;
        while ((scn = elf_nextscn(elf, scn)) != nullptr) {
            GElf_Shdr shdr_mem;
            GElf_Shdr *shdr = gelf_getshdr (scn, &shdr_mem);
            if (shdr == nullptr) {
                //report this error
                continue;
            }
            if (shdr->sh_type == SHT_SYMTAB) {
                Elf_Scn* xndxscn = NULL;
                size_t scnndx = elf_ndxscn(scn);
                while ((xndxscn = elf_nextscn(elf, xndxscn)) != NULL) {
                    GElf_Shdr xndxshdr_mem;
                    GElf_Shdr *xndxshdr = gelf_getshdr(xndxscn, &xndxshdr_mem);

                    if (xndxshdr == NULL) { /*report error*/ }

                    if (xndxshdr->sh_type == SHT_SYMTAB_SHNDX
                        && xndxshdr->sh_link == scnndx)
                        break;
                }
                load_symbols(ebl, elf, ehdr, scn, xndxscn, shdr);
            }
        }
        ebl_closebackend(ebl);
    }

};

class SymInfo {
private:
    typedef std::map<Addr, std::unique_ptr<MappedRegion>> Mapped;
    Mapped mapped; //start -> region
    typedef Mapped::const_iterator It;

    It region_for(Addr addr) const {
        auto it = mapped.lower_bound(addr);
        it--;
        return it;
    }

public:
    SymInfo() {
        elf_version(EV_CURRENT);
        auto pid = getpid();
        MRegion::Parser parser([&](const MRegion::Event& e) {
                if (e.perms.find('x') != std::string::npos) {
                    std::stringstream ss;
                    std::uint64_t start, end;
                
                    ss << e.range.start;
                    ss >> std::hex >> start;
                
                    ss << e.range.end;
                    ss >> std::hex >> end;

                    if (e.path == "[vdso]" || e.path == "[vsyscall]") return true;
                    mapped[start] = std::unique_ptr<MappedRegion>(new MappedRegion(start, end, e.offset, e.path));
                }
                return true;
            });
        std::fstream f_maps("/proc/" + std::to_string(pid) + "/maps", std::ios::in);
        parser.feed(f_maps);
    }

    const std::string& file_for(Addr addr) const {
        auto it = region_for(addr);
        if (it == std::end(mapped)) throw std::runtime_error("can't resolve addresss " + std::to_string(addr));
        return it->second->file;
    }

    std::string site_for(Addr addr) const {
        auto it = region_for(addr);
        if (it == std::end(mapped)) throw std::runtime_error("can't resolve addresss " + std::to_string(addr));
        return it->second->site_for(addr);
    }
};

void print_bt() {
    SymInfo syms;
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
        auto site = syms.site_for(rpc);
        auto file = syms.file_for(rpc);
        std::cout << "0x" << std::hex << rpc << " > " << site << " @ " << file <<'\n';
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
