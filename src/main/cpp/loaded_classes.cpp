#include "loaded_classes.h"
#include <memory>
#include "globals.h"
#include <iostream>

const LoadedClasses::ClassId LoadedClasses::xlate(jvmtiEnv *jvmti, jclass klass, NewSigHandler new_sig_handler) {
    ClassId class_id;
    if (ids.find(klass, class_id)) {
        return class_id;
    }
    if (ids.insert(klass, class_id = new_class_id.fetch_add(1, std::memory_order_relaxed))) {
        ClassSigPtr sig(new ClassSig);
        JvmtiScopedPtr<char> ksig(jvmti);
        JvmtiScopedPtr<char> gsig(jvmti);
        jvmtiError e = jvmti->GetClassSignature(klass, ksig.GetRef(), gsig.GetRef());
        if (e != JVMTI_ERROR_NONE && e != JVMTI_ERROR_CLASS_NOT_PREPARED) {
            std::cerr << "Failed to resolve class-signature, error: " << e << "\n";
        } else {
            if (ksig.Get() != NULL) sig->first = ksig.Get();
            if (gsig.Get() != NULL) sig->second = gsig.Get();
        }
        if (signatures.insert(class_id, sig)) {
            if (new_sig_handler != nullptr) {
                new_sig_handler(sig);
            }
        }
    }
    //take care of data-race possible with concurrent remove, either followed by another add or no-add
    ClassId class_id_later;
    auto still_exists = ids.find(klass, class_id_later);
    if (! (still_exists && (class_id_later == class_id))) {
        signatures.erase(class_id);
    }
    return class_id;
}

void LoadedClasses::remove(jclass klass) {
    ClassId class_id;
    if (ids.find(klass, class_id)) {
        signatures.erase(class_id);
        ids.erase(klass);
    }
}
