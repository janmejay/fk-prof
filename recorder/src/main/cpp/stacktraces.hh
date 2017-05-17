#include "globals.hh"

#ifndef STACKTRACES_H
#define STACKTRACES_H

// To implement the profiler, we rely on an undocumented function called
// AsyncGetCallTrace in the Java virtual machine, which is used by Sun
// Studio Analyzer, and designed to get stack traces asynchronously.
// It uses the old JVMPI interface, so we must reconstruct the
// neccesary bits of that here.

// For a Java frame, the lineno is the bci of the method, and the
// method_id is the jmethodID.  For a JNI method, the lineno is -3,
// and the method_id is the jmethodID.
typedef struct {
    jint lineno;
    jmethodID method_id;
} JVMPI_CallFrame;

typedef std::uint64_t NativeFrame;

typedef union {
    JVMPI_CallFrame jvmpi_frame;
    NativeFrame native_frame;
} StackFrame;

typedef struct {
    // JNIEnv of the thread from which we grabbed the trace
    JNIEnv* env_id;
    // < 0 if the frame isn't walkable
    jint num_frames;
    // The frames, callee first.
    JVMPI_CallFrame *frames;
} JVMPI_CallTrace;

typedef struct {
    // flags that identify frame-type and error-conditions
    std::uint8_t flags;
    // < 0 if the frame isn't walkable
    jint num_frames;
    // The frames, callee first
    StackFrame* frames;
    // Frame may be one of:
    // - The frames, callee first: JVMPI_CallFrame *frames;
    // - native frames (PC):  std::uint64_t *frames;
} Backtrace;

#define CT_JVMPI 0x1
#define CT_JVMPI_ERROR 0x2
#define CT_NO_JNI_ENV 0x4
#define CT_NATIVE 0x8

typedef void (*ASGCTType)(JVMPI_CallTrace *, jint, void *);
typedef int (*IsGCActiveType)();

const int kNumCallTraceErrors = 10;

enum CallTraceErrors {
    // 0 is reserved for native stack traces.  This includes JIT and GC threads.
            kNativeStackTrace = 0,
    // The JVMTI class load event is disabled (a prereq for AsyncGetCallTrace)
            kNoClassLoad = -1,
    // For traces in GC
            kGcTraceError = -2,
    // We can't figure out what the top (non-Java) frame is
            kUnknownNotJava = -3,
    // The frame is not Java and not walkable
            kNotWalkableFrameNotJava = -4,
    // We can't figure out what the top Java frame is
            kUnknownJava = -5,
    // The frame is Java and not walkable
            kNotWalkableFrameJava = -6,
    // Unknown thread state (not in Java or native or the VM)
            kUnknownState = -7,
    // The JNIEnv is bad - this likely means the thread has exited
            kTicksThreadExit = -8,
    // The thread is being deoptimized, so the stack is borked
            kDeoptHandler = -9,
    // We're in a safepoint, and can't do reporting
            kSafepoint = -10,
};

// Wrapper to hold reference to AsyncGetCallTrace function
class Asgct {
public:
    static void SetAsgct(ASGCTType asgct) {
        asgct_ = asgct;
    }

    static void SetIsGCActive(IsGCActiveType is_gc_active) {
        is_gc_active_ = is_gc_active;
    }

    // AsyncGetCallTrace function, to be dlsym'd.
    static ASGCTType GetAsgct() {
        return asgct_;
    }

    static IsGCActiveType GetIsGCActive() {
        return is_gc_active_;
    }

private:
    static ASGCTType asgct_;
    static IsGCActiveType is_gc_active_;

    DISALLOW_IMPLICIT_CONSTRUCTORS(Asgct);
};

#endif // STACKTRACES_H
