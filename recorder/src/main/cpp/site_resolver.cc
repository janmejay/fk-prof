#include "site_resolver.hh"

bool SiteResolver::method_info(const jmethodID method_id, jvmtiEnv* jvmti, MethodListener& listener) {
    jint error;
    JvmtiScopedPtr<char> methodName(jvmti);

    error = jvmti->GetMethodName(method_id, methodName.GetRef(), NULL, NULL);
    if (error != JVMTI_ERROR_NONE) {
        methodName.AbandonBecauseOfError();
        if (error == JVMTI_ERROR_INVALID_METHODID) {
            static int once = 0;
            if (!once) {
                once = 1;
                logError("One of your monitoring interfaces "
                         "is having trouble resolving its stack traces.  "
                         "GetMethodName on a jmethodID involved in a stacktrace "
                         "resulted in an INVALID_METHODID error which usually "
                         "indicates its declaring class has been unloaded.\n");
                logError("Unexpected JVMTI error %d in GetMethodName\n", error);
            }
        }
        return false;
    }

    // Get class name, put it in signature_ptr
    jclass declaring_class;
    JVMTI_ERROR_RET(
        jvmti->GetMethodDeclaringClass(method_id, &declaring_class), false);

    JvmtiScopedPtr<char> signature_ptr2(jvmti);
    JVMTI_ERROR_CLEANUP_RET(
        jvmti->GetClassSignature(declaring_class, signature_ptr2.GetRef(), NULL),
        false, signature_ptr2.AbandonBecauseOfError());

    // Get source file, put it in source_name_ptr
    char *fileName;
    JvmtiScopedPtr<char> source_name_ptr(jvmti);
    static char file_unknown[] = "UnknownFile";
    if (JVMTI_ERROR_NONE !=
        jvmti->GetSourceFileName(declaring_class, source_name_ptr.GetRef())) {
        source_name_ptr.AbandonBecauseOfError();
        fileName = file_unknown;
    } else {
        fileName = source_name_ptr.Get();
    }

    listener.recordNewMethod(method_id, fileName, signature_ptr2.Get(), methodName.Get(), NULL);

    return true;
}

static jint bci2line(jint bci, jvmtiLineNumberEntry *table, jint entry_count) {
	jint line_number = -101;
	if ( entry_count == 0 ) {
		return line_number;
	}
	line_number = -102;
    // We're looking for a line whose 'start_location' is nearest AND >= BCI
	// We assume the table is sorted by 'start_location'
    // Do a binary search to quickly approximate 'start_index" in table
	int half = entry_count >> 1;
    int start_index = 0;
    while ( half > 0 ) {
        jint start_location = table[start_index + half].start_location;
        if ( bci > start_location ) {
            start_index = start_index + half;
        } else if ( bci == start_location ) {
        	// gotcha
            return table[start_index + half].line_number;
        }
        half = half >> 1;
    }

    /* Now start the table search from approximated start_index */
    for (int i = start_index ; i < entry_count ; i++ ) {
    	// start_location > BCI: means line starts after the BCI, we'll take the previous match
        if ( bci < table[i].start_location ) {
            break;
        }
        else if (bci == table[i].start_location) {
        	// gotcha
        	return table[i].line_number;
        }
        line_number = table[i].line_number;
    }
    return line_number;
}

jint SiteResolver::line_no(jint bci, jmethodID method_id, jvmtiEnv* jvmti_) {
    if(bci <= 0) {
        return bci;
    }

    JvmtiScopedPtr<jvmtiLineNumberEntry> jvmti_table(jvmti_);
    jint entry_count;

    JVMTI_ERROR_CLEANUP_RET_NO_MESSAGE(
        jvmti_->GetLineNumberTable(method_id, &entry_count, jvmti_table.GetRef()),
        -100,
        jvmti_table.AbandonBecauseOfError());

    jint lineno = bci2line(bci, jvmti_table.Get(), entry_count);

    return lineno;
}
