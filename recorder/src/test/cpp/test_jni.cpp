#include "test_jni.h"
#include "test_profile.h"
#include <jni.h>
#include <iostream>

JNIEXPORT jboolean JNICALL Java_fk_prof_TestJni_generateCpusampleSimpleProfile(JNIEnv *env, jobject self, jstring path) {
    try {
        auto file_path = env->GetStringUTFChars(path, 0);
        if (file_path != NULL) {
            generate_cpusample_simple_profile(file_path);
            env->ReleaseStringUTFChars(path, file_path);
            return JNI_TRUE;
        }
    } catch (...) {
        std::cerr << "Profile creation failed\n";
    }
    return JNI_FALSE;
}
