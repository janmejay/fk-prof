#ifndef LOADED_CLASSES_H
#define LOADED_CLASSES_H

#include <cuckoohash_map.hh>
#include <city_hasher.hh>
#include <jni.h>
#include <cstdint>
#include <utility>

class LoadedClasses {
public:
    typedef std::uint32_t ClassId;
    typedef std::pair<char*, char*> ClassSig;
    
private:
    typedef cuckoohash_map<jclass, ClassId, CityHasher<jclass> > LoadedIds;
    typedef cuckoohash_map<ClassId, ClassSig, CityHasher<ClassId> > IdSignatures;

    LoadedIds ids;
    IdSignatures signatures;
    
public:
    LoadedClasses() {
        ids.reserve(100000);
        signatures.reserve(100000);
    }
    ~LoadedClasses() {}

    const ClassId xlate(jclass klass, const ClassSig* new_sig);
    void remove(jclass klass);
};

#endif
