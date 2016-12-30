#include <assert.h>
#include <dlfcn.h>
#include <jvmti.h>
#include <jni.h>
#include <stdint.h>
#include <signal.h>

#define SPDLOG_ENABLE_SYSLOG
#include <spdlog/spdlog.h>

#ifndef GLOBALS_H
#define GLOBALS_H

#define RECORDER_VERION 1
#define DATA_ENCODING_VERSION 1

#include "profile_writer.h"

typedef std::shared_ptr<spdlog::logger> LoggerP;

extern LoggerP logger;//TODO: stick me in GlobalCtx???

class Profiler;

namespace GlobalCtx {
    struct {
        std::atomic<bool> on;
        Profiler* profiler;
    } recording;

    struct {
        std::atomic<bool> known;
        struct {
            std::string host;
            std::uint32_t port;
        } remote;
    } associate;
    
}

void logError(const char *__restrict format, ...);

Profiler *getProfiler();
void setProfiler(Profiler *p);

const int DEFAULT_SAMPLING_INTERVAL = 1;
const int DEFAULT_MAX_FRAMES_TO_CAPTURE = 128;
const int MAX_FRAMES_TO_CAPTURE = 2048;

#if defined(STATIC_ALLOCATION_ALLOCA)
  #define STATIC_ARRAY(NAME, TYPE, SIZE, MAXSZ) TYPE *NAME = (TYPE*)alloca((SIZE) * sizeof(TYPE))
#elif defined(STATIC_ALLOCATION_PEDANTIC)
  #define STATIC_ARRAY(NAME, TYPE, SIZE, MAXSZ) TYPE NAME[MAXSZ]
#else
  #define STATIC_ARRAY(NAME, TYPE, SIZE, MAXSZ) TYPE NAME[SIZE]
#endif

char *safe_copy_string(const char *value, const char *next);
void safe_free_string(char *&value);

struct ConfigurationOptions {
    char* service_endpoint;
    
    /*self-info*/
    char* ip;
    char* host;
    char* app_id;
    char* inst_grp;
    char* inst_id;
    char* cluster;
    char* proc;
    char* vm_id;
    char* zone;
    char* inst_typ;

    ConfigurationOptions() :
            service_endpoint(nullptr),
            ip(nullptr),
            app_id(nullptr),
            inst_grp(nullptr),
            inst_id(nullptr),
            cluster(nullptr),
            proc(nullptr),
            vm_id(nullptr),
            zone(nullptr),
            inst_typ(nullptr) {
    }

    ConfigurationOptions(const char* options) {
        const char* next = options;
        for (const char *key = options; next != NULL; key = next + 1) {
            const char *value = strchr(key, '=');
            next = strchr(key, ',');
            if (value == NULL) {
                logError("WARN: No value for key %s\n", key);
                continue;
            } else {
                value++;
                if (strstr(key, "service_endpoint") == key) {
                    service_endpoint = safe_copy_string(value, next);
                } else if (strstr(key, "ip") == key) {
                    ip = safe_copy_string(value, next);
                } else if (strstr(key, "host") == key) {
                    host = safe_copy_string(value, next);
                } else if (strstr(key, "appid") == key) {
                    app_id = safe_copy_string(value, next);
                } else if (strstr(key, "igrp") == key) {
                    inst_grp = safe_copy_string(value, next);
                } else if (strstr(key, "cluster") == key) {
                    cluster = safe_copy_string(value, next);
                } else if (strstr(key, "instid") == key) {
                    inst_id = safe_copy_string(value, next);
                } else if (strstr(key, "proc") == key) {
                    proc = safe_copy_string(value, next);
                } else if (strstr(key, "vmid") == key) {
                    vm_id = safe_copy_string(value, next);
                } else if (strstr(key, "zone") == key) {
                    zone = safe_copy_string(value, next);
                } else if (strstr(key, "ityp") == key) {
                    inst_typ = safe_copy_string(value, next);
                } else {
                    logError("WARN: Unknown configuration option: %s\n", key);
                }
            }
        }
    }

    virtual ~ConfigurationOptions() {
        safe_free_string(service_endpoint);
        safe_free_string(ip);
        safe_free_string(app_id);
        safe_free_string(inst_grp);
        safe_free_string(cluster);
        safe_free_string(proc);
        safe_free_string(vm_id);
        safe_free_string(zone);
        safe_free_string(inst_typ);
    }
};

template <typename T> const T& min(const T& first, const T& second) {
    return first > second ? second : first;
}

#define AGENTEXPORT __attribute__((visibility("default"))) JNIEXPORT

// Gets us around -Wunused-parameter
#define IMPLICITLY_USE(x) (void) x;

// Wrap JVMTI functions in this in functions that expect a return
// value and require cleanup but no error message
#define JVMTI_ERROR_CLEANUP_RET_NO_MESSAGE(error, retval, cleanup)             \
  {                                                                            \
    int err;                                                                   \
    if ((err = (error)) != JVMTI_ERROR_NONE) {                                 \
      cleanup;                                                                 \
      return (retval);                                                         \
    }                                                                          \
  }
// Wrap JVMTI functions in this in functions that expect a return
// value and require cleanup.
#define JVMTI_ERROR_MESSAGE_CLEANUP_RET(error, message, retval, cleanup)       \
  {                                                                            \
    int err;                                                                   \
    if ((err = (error)) != JVMTI_ERROR_NONE) {                                 \
      logError(message, err);                                                  \
      cleanup;                                                                 \
      return (retval);                                                         \
    }                                                                          \
  }

#define JVMTI_ERROR_CLEANUP_RET(error, retval, cleanup)                        \
    JVMTI_ERROR_MESSAGE_CLEANUP_RET(error, "JVMTI error %d\n", retval, cleanup)

// Wrap JVMTI functions in this in functions that expect a return value.
#define JVMTI_ERROR_RET(error, retval)                                           \
  JVMTI_ERROR_CLEANUP_RET(error, retval, /* nothing */)

// Wrap JVMTI functions in this in void functions.
#define JVMTI_ERROR(error) JVMTI_ERROR_CLEANUP(error, /* nothing */)

// Wrap JVMTI functions in this in void functions that require cleanup.
#define JVMTI_ERROR_CLEANUP(error, cleanup)                                    \
  {                                                                            \
    int err;                                                                   \
    if ((err = (error)) != JVMTI_ERROR_NONE) {                                 \
      logError("JVMTI error %d\n", err);                                       \
      cleanup;                                                                 \
      return;                                                                  \
    }                                                                          \
  }

#define DISALLOW_COPY_AND_ASSIGN(TypeName)                                     \
  TypeName(const TypeName &);                                                  \
  void operator=(const TypeName &)

#define DISALLOW_IMPLICIT_CONSTRUCTORS(TypeName)                               \
  TypeName();                                                                  \
  DISALLOW_COPY_AND_ASSIGN(TypeName)

// Short version: reinterpret_cast produces undefined behavior in many
// cases where memcpy doesn't.
template<class Dest, class Source>
inline Dest bit_cast(const Source &source) {
    // Compile time assertion: sizeof(Dest) == sizeof(Source)
    // A compile error here means your Dest and Source have different sizes.
    typedef char VerifySizesAreEqual[sizeof(Dest) == sizeof(Source) ? 1 : -1]
            __attribute__((unused));

    Dest dest;
    memcpy(&dest, &source, sizeof(dest));
    return dest;
}

template<class T>
class JvmtiScopedPtr {
public:
    explicit JvmtiScopedPtr(jvmtiEnv *jvmti) : jvmti_(jvmti), ref_(NULL) {
    }

    JvmtiScopedPtr(jvmtiEnv *jvmti, T *ref) : jvmti_(jvmti), ref_(ref) {
    }

    ~JvmtiScopedPtr() {
        if (NULL != ref_) {
            JVMTI_ERROR(jvmti_->Deallocate((unsigned char *) ref_));
        }
    }

    T **GetRef() {
        assert(ref_ == NULL);
        return &ref_;
    }

    T *Get() {
        return ref_;
    }

    void AbandonBecauseOfError() {
        ref_ = NULL;
    }

private:
    jvmtiEnv *jvmti_;
    T *ref_;

    DISALLOW_IMPLICIT_CONSTRUCTORS(JvmtiScopedPtr);
};

// Accessors for getting the Jvm function for AsyncGetCallTrace.
class Accessors {
public:
    template<class FunctionType>
    static inline FunctionType GetJvmFunction(const char *function_name) {
        // get address of function, return null if not found
        return bit_cast<FunctionType>(dlsym(RTLD_DEFAULT, function_name));
    }
};

void bootstrapHandle(int signum, siginfo_t *info, void *context);

#endif // GLOBALS_H
