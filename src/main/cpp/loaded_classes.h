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
#include <fstream>

class LoadedClasses {
public:
    typedef std::uint32_t ClassId;
    struct ClassSig {
        ClassId class_id;
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
    
    std::ofstream out;
    std::atomic<bool> do_report;
    
public:
    LoadedClasses() {
        signatures.reserve(100000);
        out.open("/tmp/CLASSES.out", std::ios_base::out | std::ios_base::trunc);
        out << "cid\tsig\n";
        do_report.store(true, std::memory_order_release);
    }
    ~LoadedClasses() {}

    void stop_reporting();

    const ClassId xlate(jvmtiEnv *jvmti, jclass klass, NewSigHandler new_sig_handler);
    void remove(jvmtiEnv *jvmti, jclass klass);
};

#endif
