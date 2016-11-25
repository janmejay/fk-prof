#include "loaded_classes.h"
#include <memory>
#include "globals.h"
#include <iostream>

const LoadedClasses::ClassId LoadedClasses::xlate(jvmtiEnv *jvmti, jclass klass, NewSigHandler new_sig_handler) {
    jlong tag;
    auto e = jvmti->GetTag(klass, &tag);
    if (e != JVMTI_ERROR_NONE) {
        std::cerr << "Couldn't get tag of a class (error: " << e << ")\n";
        tag = 0;
    }

    if (tag < 0) {
        return tag * -1;
    }
    
    ClassId class_id = new_class_id.fetch_add(1, std::memory_order_relaxed);
    ClassSigPtr sig(new ClassSig);
    sig->klass = klass;
    sig->class_id = class_id;
    JvmtiScopedPtr<char> ksig(jvmti);
    JvmtiScopedPtr<char> gsig(jvmti);
    e = jvmti->GetClassSignature(klass, ksig.GetRef(), gsig.GetRef());
    if (e != JVMTI_ERROR_NONE && e != JVMTI_ERROR_CLASS_NOT_PREPARED) {
        std::cerr << "Failed to resolve class-signature, error: " << e << "\n";
        sig->class_id = 0;
    } else {
        if (ksig.Get() != NULL) sig->ksig = ksig.Get();
        if (gsig.Get() != NULL) sig->gsig = gsig.Get();
    }
    if (signatures.insert(class_id, sig)) {
        if (new_sig_handler != nullptr) {
            new_sig_handler(sig);
        }
        tag = -1 * class_id;
        e = jvmti->SetTag(klass, tag);
        if (e != JVMTI_ERROR_NONE) {
            std::cerr << "Couldn't tag class (error: " << e << ")\n";
        } else {
            return tag;
        }
    }
    return 0;
}

void LoadedClasses::remove(jvmtiEnv *jvmti, const jclass klass) { //implement me over unload hook
    jlong tag;
    auto e = jvmti->GetTag(klass, &tag);
    if (e != JVMTI_ERROR_NONE) {
        std::cerr << "Couldn't get tag of a class (error: " << e << ")\n";
        return;
    }
    assert(tag < 0);
    ClassId class_id = static_cast<ClassId>(-1 * tag);
    signatures.erase(class_id);
}
