#include "util.h"
#include <string>
#include <cstring>
#include <dlfcn.h>
#include <iostream>

extern "C" {
    static void* load_lib(const char* lib_path, char* err_msg, int err_msg_buff_len) {
        void* lib = dlopen(lib_path, RTLD_LAZY);
        if (lib == NULL) {
            strncpy(err_msg, dlerror(), err_msg_buff_len - 2);
            err_msg[err_msg_buff_len - 1] = '\0';
        }
        return lib;
    }

    void unload_lib(void* lib) {
        dlclose(lib);
    }

    void* _lib_symbol(void* lib, const char* sym_name,  char* err_msg, int err_msg_buff_len) {
        void* sym = dlsym(lib, sym_name);
        if (sym == NULL) {
            strncpy(err_msg, dlerror(), err_msg_buff_len - 2);
            err_msg[err_msg_buff_len - 1] = '\0';
        }
        return sym;
    }
}

void* load_sun_boot_lib(jvmtiEnv* jvmti, const std::string& lib_name) {
    char  err_msg[1024];
    char* lib_parent_path;

    auto e = jvmti->GetSystemProperty("sun.boot.library.path", &lib_parent_path);
    if (e != JVMTI_ERROR_NONE) {
        std::cerr << "Couldn't get boot-lib-path, hence couldn't load lib: " << lib_name << "\n";
        return nullptr;
    }

    std::string lib_path(lib_parent_path);
    lib_path += "/";
    lib_path += lib_name;
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(lib_parent_path));

    void* lib = load_lib(lib_path.c_str(), err_msg, sizeof(err_msg));
    if (lib == nullptr) {
        std::cerr << "Couldn't load library: " << lib_name << " (error: '" << err_msg << "' )\n";
    }
    return lib;
}

void* lib_symbol(void* lib, const std::string& sym_name) {
    char  err_msg[1024];
    void* sym = _lib_symbol(lib, sym_name.c_str(), err_msg, sizeof(err_msg));
    if (sym == nullptr) {
        std::cerr << "Couldn't find symbol: " << sym_name << " (error: '" << err_msg << "' )\n";
    }
    return sym;
}
