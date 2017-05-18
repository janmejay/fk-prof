#ifndef BACKTRACER_H
#define BACKTRACER_H

#include "stacktraces.hh"
#include <map>
#include <memory>
#include <gelf.h>

namespace Backtracer {
    std::uint32_t fill_backtrace(NativeFrame* buff, std::uint32_t capacity);

    class SymInfoError : std::runtime_error {
    public:
        SymInfoError(const char* msg);
        SymInfoError(const std::string& msg);
        virtual ~SymInfoError();
        static SymInfoError err(int err_num);
    };

    typedef NativeFrame Addr;

    struct MappedRegion {
        Addr start_;
        const std::string file_;
        std::map<Addr, std::string> symbols_;

        MappedRegion(Addr start, std::string file);

        ~MappedRegion();

        const std::string site_for(Addr addr);

    private:

        SymInfoError section_error(Elf* elf, size_t shstrndx, Elf_Scn *scn, GElf_Shdr *shdr, const std::string& msg);

        void load_symbols(Elf* elf, GElf_Ehdr* ehdr, Elf_Scn* scn, Elf_Scn* xndxscn, GElf_Shdr* shdr);
        void load_elf(Elf* elf);
    };


    class SymInfo {
    private:
        typedef std::map<Addr, std::unique_ptr<MappedRegion>> Mapped;
        Mapped mapped; //start -> region
        typedef Mapped::const_iterator It;

        It region_for(Addr addr) const;

    public:
        SymInfo();

        void index(const char* path, Addr start);

        const std::string& file_for(Addr addr) const;

        std::string site_for(Addr addr) const;

        void print_frame(Addr pc) const;
    };
}

#endif
