#ifndef SITE_RESOLVER_H
#define SITE_RESOLVER_H

#include "globals.hh"

namespace SiteResolver {
    class MethodListener {
    public:
        virtual void recordNewMethod(const jmethodID method_id, const char *file_name, const char *class_name, const char *method_name, const char* method_signature) = 0;
        virtual ~MethodListener() { }
    };

    typedef bool (*MethodInfoResolver)(const jmethodID method_id, jvmtiEnv* jvmti, MethodListener& listener);
    bool method_info(const jmethodID method_id, jvmtiEnv* jvmti, MethodListener& listener);

    typedef jint (*LineNoResolver)(jint bci, jmethodID method_id, jvmtiEnv* jvmti);
    jint line_no(jint bci, jmethodID method_id, jvmtiEnv* jvmti);
}

#endif
