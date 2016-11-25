#include <jni.h>
#include <jvmti.h>
#include <iostream>
#include "hprof_tracker.h"

extern jvmtiEnv *ti_env;

static void print_allocation_trace(const std::string& type, jclass klass, jobject obj) {
    jlong tag;
    auto e = ti_env->GetTag(klass, &tag);
    if (e != JVMTI_ERROR_NONE) {
        std::cerr << type << " class-tag-resolution failed\n";
        return;
    }
    jlong sz;
    e = ti_env->GetObjectSize(obj, &sz);
    if (e != JVMTI_ERROR_NONE) {
        std::cerr << type << " obj-size query failed for instance of class_id: " << (tag * -1) << "\n";
    }
    std::cout << type << " Sz: " << sz << "\n";
}

extern "C" void JNICALL Tracker_nativeNewArray(JNIEnv *env, jclass klass, jobject thread, jobject obj) {
    std::cout << "TRACKER nativeNewArray\n";
}

extern "C" void JNICALL Tracker_nativeObjectInit(JNIEnv *env, jclass klass, jobject thread, jobject obj) {
    print_allocation_trace("Alloc-NewObj", klass, obj);
}

extern "C" void JNICALL Tracker_nativeCallSite(JNIEnv *env, jclass klass, jobject thread, jint cnum, jint mnum) {
    
}

extern "C" void JNICALL Tracker_nativeReturnSite(JNIEnv *env, jclass klass, jobject thread, jint cnum, jint mnum) {
    
}

void set_tracking(JNIEnv *jni, bool on) {
    
}
