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
    typedef std::pair<std::string, std::string> ClassSig;
    typedef std::shared_ptr<ClassSig> ClassSigPtr;
    typedef std::function<void(ClassSigPtr)> NewSigHandler;
    
private:
    typedef cuckoohash_map<jclass, ClassId, CityHasher<jclass> > LoadedIds;
    typedef cuckoohash_map<ClassId, ClassSigPtr, CityHasher<ClassId> > IdSignatures;
    
    LoadedIds ids;
    IdSignatures signatures;
    std::atomic<ClassId> new_class_id;
    
public:
    LoadedClasses() {
        ids.reserve(100000);
        signatures.reserve(100000);
    }
    ~LoadedClasses() {}

    const ClassId xlate(jvmtiEnv *jvmti, jclass klass, NewSigHandler new_sig_handler);
    void remove(jclass klass);
};

#endif
