#include "site_resolver.hh"
#include <cxxabi.h>
#include <link.h>

bool SiteResolver::method_info(const jmethodID method_id, jvmtiEnv* jvmti, MethodListener& listener) {
    jint error;
    JvmtiScopedPtr<char> methodName(jvmti);
    JvmtiScopedPtr<char> methodSig(jvmti);

    error = jvmti->GetMethodName(method_id, methodName.GetRef(), methodSig.GetRef(), NULL);
    if (error != JVMTI_ERROR_NONE) {
        methodName.AbandonBecauseOfError();
        methodSig.AbandonBecauseOfError();
        if (error == JVMTI_ERROR_INVALID_METHODID) {
            static int once = 0;
            if (!once) {
                once = 1;
                logger->error("One of your monitoring interfaces "
                         "is having trouble resolving its stack traces.  "
                         "GetMethodName on a jmethodID involved in a stacktrace "
                         "resulted in an INVALID_METHODID error which usually "
                         "indicates its declaring class has been unloaded.");
                logger->error("Unexpected JVMTI error {} in GetMethodName", error);
            }
        }
        return false;
    }

    // Get class name, put it in signature_ptr
    jclass declaring_class;
    JVMTI_ERROR_RET(
        jvmti->GetMethodDeclaringClass(method_id, &declaring_class), false);

    JvmtiScopedPtr<char> signature_ptr2(jvmti);
    JVMTI_ERROR_CLEANUP_RET(
        jvmti->GetClassSignature(declaring_class, signature_ptr2.GetRef(), NULL),
        false, signature_ptr2.AbandonBecauseOfError());

    // Get source file, put it in source_name_ptr
    char *fileName;
    JvmtiScopedPtr<char> source_name_ptr(jvmti);
    static char file_unknown[] = "UnknownFile";
    if (JVMTI_ERROR_NONE !=
        jvmti->GetSourceFileName(declaring_class, source_name_ptr.GetRef())) {
        source_name_ptr.AbandonBecauseOfError();
        fileName = file_unknown;
    } else {
        fileName = source_name_ptr.Get();
    }

    listener.recordNewMethod(method_id, fileName, signature_ptr2.Get(), methodName.Get(), methodSig.Get());

    return true;
}

static jint bci2line(jint bci, jvmtiLineNumberEntry *table, jint entry_count) {
	jint line_number = -101;
	if ( entry_count == 0 ) {
		return line_number;
	}
	line_number = -102;
    // We're looking for a line whose 'start_location' is nearest AND >= BCI
	// We assume the table is sorted by 'start_location'
    // Do a binary search to quickly approximate 'start_index" in table
	int half = entry_count >> 1;
    int start_index = 0;
    while ( half > 0 ) {
        jint start_location = table[start_index + half].start_location;
        if ( bci > start_location ) {
            start_index = start_index + half;
        } else if ( bci == start_location ) {
        	// gotcha
            return table[start_index + half].line_number;
        }
        half = half >> 1;
    }

    /* Now start the table search from approximated start_index */
    for (int i = start_index ; i < entry_count ; i++ ) {
    	// start_location > BCI: means line starts after the BCI, we'll take the previous match
        if ( bci < table[i].start_location ) {
            break;
        }
        else if (bci == table[i].start_location) {
        	// gotcha
        	return table[i].line_number;
        }
        line_number = table[i].line_number;
    }
    return line_number;
}

jint SiteResolver::line_no(jint bci, jmethodID method_id, jvmtiEnv* jvmti_) {
    if(bci <= 0) {
        return bci;
    }

    JvmtiScopedPtr<jvmtiLineNumberEntry> jvmti_table(jvmti_);
    jint entry_count;

    JVMTI_ERROR_CLEANUP_RET_NO_MESSAGE(
        jvmti_->GetLineNumberTable(method_id, &entry_count, jvmti_table.GetRef()),
        -100,
        jvmti_table.AbandonBecauseOfError());

    jint lineno = bci2line(bci, jvmti_table.Get(), entry_count);

    return lineno;
}

typedef NativeFrame Addr;

SiteResolver::SymInfoError::SymInfoError(const char* msg) : std::runtime_error(msg) {}
SiteResolver::SymInfoError::SymInfoError(const std::string& msg) : std::runtime_error(msg) {}
SiteResolver::SymInfoError::~SymInfoError() {}

SiteResolver::SymInfoError SiteResolver::SymInfoError::err(int err_num) {
    int sz = 1024;
    std::unique_ptr<char[]> buff(new char[sz]);
    if (strerror_r(err_num, buff.get(), sz) != 0) {
        std::string unknown_error = "Unknown error: " + std::to_string(err_num);
        throw SiteResolver::SymInfoError(unknown_error.c_str());
    }
    return SymInfoError(buff.get());
}

class ElfFile {
private:
    int fd_;
    Elf *elf_;
    std::string path_;

public:
    ElfFile(const std::string& path) : path_(path) {
        fd_ = open(path.c_str(), O_RDONLY);
        if (fd_ == -1) throw SiteResolver::SymInfoError::err(errno);
        elf_ = elf_begin(fd_, ELF_C_READ_MMAP, NULL);
        if (elf_ == nullptr) {
            throw SiteResolver::SymInfoError("File format not recognizable for: " + path_);
        }
        if (elf_kind(elf_) != ELF_K_ELF) {
            throw SiteResolver::SymInfoError("File " + path_ + " is not an elf.");
        }
    }

    ~ElfFile() {
        if (elf_ != nullptr) {
            if (elf_end(elf_) != 0) {
                std::cout << "Couldn't close elf\n";//log me
            }
        }
        if (fd_ > -1) {
            close(fd_);
        }
    }

    Elf* get() {
        return elf_;
    }
};

SiteResolver::MappedRegion::MappedRegion(Addr start, std::string file): start_(start), file_(file) {
    ElfFile elf_file(file_);
    load_elf(elf_file.get());
}

SiteResolver::MappedRegion::~MappedRegion() {}

const void SiteResolver::MappedRegion::site_for(Addr addr, std::string& fn_name, Addr& pc_offset) const {
    auto unrelocated_address = addr - start_;
    auto it = symbols_.lower_bound(unrelocated_address);
    it--;
    fn_name = it->second;
    pc_offset = unrelocated_address - it->first;
}

SiteResolver::SymInfoError SiteResolver::MappedRegion::section_error(Elf* elf, size_t shstrndx, Elf_Scn *scn, GElf_Shdr *shdr, const std::string& msg) {
    std::stringstream ss;
    ss << file_ << ": " << elf_ndxscn(scn) << " " <<
        elf_strptr(elf, shstrndx, shdr->sh_name)<< ": " << msg;
    std::string err = ss.str();
    throw SymInfoError(err.c_str());
}

void SiteResolver::MappedRegion::load_symbols(Elf* elf, GElf_Ehdr* ehdr, Elf_Scn* scn, Elf_Scn* xndxscn, GElf_Shdr* shdr) {
    size_t shstrndx;
    if (elf_getshdrstrndx (elf, &shstrndx) < 0) {
        throw SymInfoError("Cannot get section header str-table index");
    }
    size_t size = shdr->sh_size;
    size_t entsize = shdr->sh_entsize;

    if (entsize == 0
        || entsize != gelf_fsize (elf, ELF_T_SYM, 1, EV_CURRENT)) {
        throw section_error(elf, shstrndx, scn, shdr, "Entry size in section not as expected");
    } else if (size % entsize != 0) {
        throw section_error(elf, shstrndx, scn, shdr, "Size of section not multiple of entry sz");
    }

    size_t nentries = size / (entsize ?: 1);

    Elf_Data *data = elf_getdata (scn, NULL);
    Elf_Data *xndxdata = elf_getdata (xndxscn, NULL);
    if (data == NULL || (xndxscn != NULL && xndxdata == NULL))
        throw SymInfoError("Couldn't get section/extended-section data");

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

        bool sym_loaded = false;
        if ((strlen(symstr) > 1) && (symstr[0] == '_') && (symstr[1] == 'Z')) {
            int status = -1;
            char* dmsymstr = abi::__cxa_demangle(symstr, nullptr, 0, &status);
            if (status == 0) {
                symbols_[sym->st_value] = dmsymstr;
                sym_loaded = true;
                free(dmsymstr);
            }
        }
        if (! sym_loaded) symbols_[sym->st_value] = symstr;
    }
}

void SiteResolver::MappedRegion::load_elf(Elf* elf) {
    GElf_Ehdr ehdr_mem;
    GElf_Ehdr *ehdr = gelf_getehdr(elf, &ehdr_mem);
    if (ehdr->e_type != ET_EXEC && ehdr->e_type != ET_DYN) {
        throw SymInfoError("Elf " + file_ + " wasn't an executable or a shared-lib");
    }
    Elf_Scn* scn = nullptr;
    while ((scn = elf_nextscn(elf, scn)) != nullptr) {
        GElf_Shdr shdr_mem;
        GElf_Shdr *shdr = gelf_getshdr (scn, &shdr_mem);
        if (shdr == nullptr) {
            //report this error
            continue;
        }
        if ((shdr->sh_type == SHT_SYMTAB) || (shdr->sh_type == SHT_DYNSYM)) {
            Elf_Scn* xndxscn = nullptr;
            if (shdr->sh_type == SHT_SYMTAB) {
                size_t scnndx = elf_ndxscn(scn);
                while ((xndxscn = elf_nextscn(elf, xndxscn)) != nullptr) {
                    GElf_Shdr xndxshdr_mem;
                    GElf_Shdr *xndxshdr = gelf_getshdr(xndxscn, &xndxshdr_mem);

                    if (xndxshdr == NULL) { /*report error*/ }

                    if (xndxshdr->sh_type == SHT_SYMTAB_SHNDX
                        && xndxshdr->sh_link == scnndx)
                        break;
                }
            }
            load_symbols(elf, ehdr, scn, xndxscn, shdr);
        }
    }
}

static int dyn_link_handler(struct dl_phdr_info* info, size_t size, void* data);

SiteResolver::SymInfo::It SiteResolver::SymInfo::region_for(Addr addr) const {
    auto it = mapped.lower_bound(addr);
    it--;
    return it;
}

SiteResolver::SymInfo::SymInfo() {
    elf_version(EV_CURRENT);
    dl_iterate_phdr(dyn_link_handler, this);
}

void SiteResolver::SymInfo::index(const char* path, Addr start) {
    mapped[start] = std::unique_ptr<MappedRegion>(new MappedRegion(start, path));
}

const std::string& SiteResolver::SymInfo::file_for(Addr addr) const {
    auto it = region_for(addr);
    if (it == std::end(mapped)) throw std::runtime_error("can't resolve addresss " + std::to_string(addr));
    return it->second->file_;
}

void SiteResolver::SymInfo::site_for(Addr addr, std::string& fn_name, Addr& pc_offset) const {
    auto it = region_for(addr);
    if (it == std::end(mapped)) throw std::runtime_error("can't resolve addresss " + std::to_string(addr));
    it->second->site_for(addr, fn_name, pc_offset);
}

void SiteResolver::SymInfo::print_frame(Addr pc) const {
    std::string fn_name;
    Addr pc_offset;
    site_for(pc, fn_name, pc_offset);
    auto file = file_for(pc);
    std::cout << "0x" << std::hex << pc << " > " << fn_name << " +" << pc_offset << " @ " << file <<'\n';
}

static int dyn_link_handler(struct dl_phdr_info* info, size_t size, void* data) {
    auto si = reinterpret_cast<SiteResolver::SymInfo*>(data);

    if (strlen(info->dlpi_name) == 0) {
        auto max_path_sz = 1024;
        std::unique_ptr<char[]> buff(new char[max_path_sz]);
        auto path_len = readlink("/proc/self/exe", buff.get(), max_path_sz);
        if ((path_len > 0) && (path_len < max_path_sz)) {
            buff.get()[path_len] = '\0';
            si->index(buff.get(), info->dlpi_addr);
        } else {
            si->index("/proc/self/exe", info->dlpi_addr);
        }
    } else if (strstr(info->dlpi_name, "linux-vdso.so") != info->dlpi_name) {
        si->index(info->dlpi_name, info->dlpi_addr);
    }
    return 0;
}
