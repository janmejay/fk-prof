#ifndef LOADED_CLASSES_H
#define LOADED_CLASSES_H

#include <cuckoohash_map.hh>
#include <city_hasher.hh>
#include <jni.h>
#include <cstdint>
#include <utility>
#include <string>
#include <memory>
#include <jvmti.h>

class LoadedClasses {
public:
    typedef std::uint32_t ClassId;
    struct ClassSig {
        jclass klass;
        std::string ksig;
        std::string gsig;
    };
    typedef std::shared_ptr<ClassSig> ClassSigPtr;
    typedef std::function<void(ClassSigPtr)> NewSigHandler;
    
private:
    typedef cuckoohash_map<ClassId, ClassSigPtr, CityHasher<ClassId> > IdSignatures;
    
    IdSignatures signatures;
    std::atomic<ClassId> new_class_id{1};
    
public:
    LoadedClasses() {
        signatures.reserve(100000);
    }
    ~LoadedClasses() {}

    const ClassId xlate(jvmtiEnv *jvmti, jclass klass, NewSigHandler new_sig_handler);
    void remove(jvmtiEnv *jvmti, jclass klass);
};

#endif
