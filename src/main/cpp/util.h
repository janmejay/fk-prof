#ifndef UTIL_H
#define UTIL_H

#include <string>
#include <jvmti.h>

void* /*lib*/ load_sun_boot_lib(jvmtiEnv* jvmti, const std::string& name);

extern "C" void unload_lib(void* lib);

void* lib_symbol(void* lib, const std::string& sym_name);

#endif
