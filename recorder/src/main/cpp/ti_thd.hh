#ifndef TI_THD_H
#define TI_THD_H

#include <jvmti.h>
#include <memory>

struct ThreadTargetProc;
typedef std::shared_ptr<ThreadTargetProc> ThdProcP;

ThdProcP start_new_thd(JavaVM *jvm, jvmtiEnv *jvmti, const char* thd_name, jvmtiStartFunction run_fn, void *arg);
ThdProcP start_new_thd(JNIEnv *jni_env, jvmtiEnv *jvmti, const char* thd_name, jvmtiStartFunction run_fn, void *arg);

void await_thd_death(ThdProcP ttp);

void quiesce_sigprof(const char* thd_name);

#endif
