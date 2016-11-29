#ifndef HPROF_TRACKER_H
#define HPROF_TRACKER_H

#include <jni.h>
#include <fstream>
#include <atomic>
#include "data_writer.h"

#define TRACKER_CLASS_NAME "com/sun/demo/jvmti/hprof/Tracker"
#define TRACKER_CLASS_SIG "L" TRACKER_CLASS_NAME ";"

#define TRACKER_ENGAGED_NAME               "engaged"
#define TRACKER_ENGAGED_SIG                "I"

#define TRACKER_NEWARRAY_NAME        "NewArray"
#define TRACKER_NEWARRAY_SIG         "(Ljava/lang/Object;)V"
#define TRACKER_NEWARRAY_NATIVE_NAME "nativeNewArray"
#define TRACKER_NEWARRAY_NATIVE_SIG  "(Ljava/lang/Object;Ljava/lang/Object;)V"

#define TRACKER_OBJECT_INIT_NAME        "ObjectInit"
#define TRACKER_OBJECT_INIT_SIG         "(Ljava/lang/Object;)V"
#define TRACKER_OBJECT_INIT_NATIVE_NAME "nativeObjectInit"
#define TRACKER_OBJECT_INIT_NATIVE_SIG  "(Ljava/lang/Object;Ljava/lang/Object;)V"

void set_tracking(JNIEnv *jni, bool on);

struct Alloc {
    std::uint32_t sz;
    std::uint64_t cid;
};

std::ostream& operator<<(std::ostream& os, const Alloc& a);

struct AllocRecorder {
    std::ofstream out;
    const char* file;
    DataWriter<Alloc> *dw;
    JavaVM *vm;
    jvmtiEnv *ti;
    
    AllocRecorder(JavaVM *_vm, jvmtiEnv *jvm_ti, const char* _file) : file(_file), vm(_vm) {
        assert(vm != nullptr);
        ti = jvm_ti;
        assert(ti != nullptr);
        out.open(file, std::ios_base::out | std::ios_base::trunc);
        out << "sz\tcid\n";
        out.close();
    }
    ~AllocRecorder() {}
    void open();
    void close();
};

#endif
