#include <jni.h>
#include <jvmti.h>
#include <iostream>
#include <functional>
#include <cassert>
#include "hprof_tracker.h"
#include "hprof_tracker_jni.h"

extern jvmtiEnv *ti_env;

static void print_allocation_trace(const std::string& type, JNIEnv* env, jobject obj) {
    jlong tag;
    if (obj != NULL) {
        auto obj_klass = env->GetObjectClass(obj);
        auto e = ti_env->GetTag(obj_klass, &tag);
        if (e != JVMTI_ERROR_NONE) {
            std::cerr << type << " class-tag-resolution failed\n";
            tag = 0;
        }
    } else {
        tag = 0;
    }
    jlong sz;
    auto e = ti_env->GetObjectSize(obj, &sz);
    if (e != JVMTI_ERROR_NONE) {
        std::cerr << type << " obj-size query failed for instance of class_id: " << tag << "\n";
    }
    std::cout << type << " Sz: " << sz << " ClassId: " << tag << "\n";
}

extern "C" void JNICALL Java_com_sun_demo_jvmti_hprof_Tracker_nativeNewArray(JNIEnv *env, jclass klass, jobject thread, jobject obj) {
    print_allocation_trace("Alloc-NewArr", env, obj);
}

extern "C" void JNICALL Java_com_sun_demo_jvmti_hprof_Tracker_nativeObjectInit(JNIEnv *env, jclass klass, jobject thread, jobject obj) {
    print_allocation_trace("Alloc-NewObj", env, obj);
}

extern "C" void JNICALL Java_com_sun_demo_jvmti_hprof_Tracker_nativeCallSite(JNIEnv *env, jclass klass, jobject thread, jint cnum, jint mnum) {
    
}

extern "C" void JNICALL Java_com_sun_demo_jvmti_hprof_Tracker_nativeReturnSite(JNIEnv *env, jclass klass, jobject thread, jint cnum, jint mnum) {
    
}

template <typename V> struct Releasable {
    typedef std::function<V()> Acq;
    typedef std::function<void(V)> Rel;
    
    V v;
    Rel release;

    Releasable(Acq acquire, Rel _release) : release(_release) {
        v = acquire();
    }
    virtual ~Releasable() {
        release(v);
    }
};

struct LocalFrameTracker : Releasable<int> {
    
    LocalFrameTracker(JNIEnv *jni, int num) : Releasable([jni, num]() {
            if (jni->PushLocalFrame(num) != 0) {
                std::cerr << "Couldn't push local-frame " << num << "\n";
            }
            return num;
        },
        [jni](int _) { jni->PopLocalFrame(nullptr); }) {}
    ~LocalFrameTracker() {}
};

bool check_for_exception(JNIEnv* jni, const char* msg, bool do_clear = false) {
    auto throwable = jni->ExceptionOccurred();
    if (throwable == nullptr) {
        return false;
    }
    std::cerr << "Exception found: " << msg << "\n";
    jni->ExceptionDescribe();
    if (do_clear) jni->ExceptionClear();
    return false;
}

void set_tracking(JNIEnv *jni, bool on) {
    std::cout << "CALLED set_tracking\n";
    LocalFrameTracker lft(jni, 10);
    check_for_exception(jni, "Won't try to enable tracking");

    auto tracker_klass = jni->FindClass(TRACKER_CLASS_SIG);
    check_for_exception(jni, "Something failed while load class " TRACKER_CLASS_SIG, true);
    if (tracker_klass == NULL) {
        return;
    }

    auto field_id = jni->GetStaticFieldID(tracker_klass, TRACKER_ENGAGED_NAME, TRACKER_ENGAGED_SIG);
    check_for_exception(jni, "Couldn't find tracker field " TRACKER_ENGAGED_NAME, true);
    if (field_id == NULL) {
        return;
    }

    jni->SetStaticIntField(tracker_klass, field_id, on ? 1 : 0);
    check_for_exception(jni, "Couldn't set tracker field " TRACKER_ENGAGED_NAME, true);

    std::cout << "TRACKING ENABLED\n";
}
