#include "log_writer.hh"
#include <cstdlib>
using std::copy;

bool isLittleEndian() {
    short int number = 0x1;
    char *numPtr = (char *) &number;
    return (numPtr[0] == 1);
}

static bool IS_LITTLE_ENDIAN = isLittleEndian();

template<typename T>
void LogWriter::writeValue(const T &value) {
    if (IS_LITTLE_ENDIAN) {
        const char *data = reinterpret_cast<const char *>(&value);
        for (int i = sizeof(value) - 1; i >= 0; i--) {
            output_.put(data[i]);
        }
    } else {
        output_.write(reinterpret_cast<const char *>(&value), sizeof(value));
    }
}

void LogWriter::record(const JVMPI_CallTrace &trace, ThreadBucket *info, std::uint8_t ctx_len, PerfCtx::ThreadTracker::EffectiveCtx* ctx) {
    map::HashType threadId = (map::HashType) trace.env_id;
    // char *src = (char*)"";

    if (info) {
        threadId = (map::HashType) info->tid;
        // src = info->name;
    }

    // std::cout << "#### thread: " << trace.env_id << ", tid: " << threadId << ", src: " << src << std::endl;

    recordTraceStart(trace.num_frames, threadId);

    for (int i = 0; i < trace.num_frames; i++) {
        JVMPI_CallFrame frame = trace.frames[i];
        method_id methodId = (method_id) frame.method_id;

        // lineno is in fact BCI, needs converting to lineno
        jint bci = frame.lineno;
        if (bci > 0) {
            jint lineno = SiteResolver::line_no(bci, frame.method_id, jvmti_);
            recordFrame(bci, lineno, methodId);
        }
        else {
            recordFrame(bci, methodId);
        }
        inspectMethod(methodId, frame);
    }

    if (info != nullptr)
        info->release();
}

void LogWriter::inspectMethod(const method_id methodId,
        const JVMPI_CallFrame &frame) {
    if (knownMethods.count(methodId) > 0) {
        return;
    }

    knownMethods.insert(methodId);
    frameLookup_(frame.method_id, jvmti_, *this);
}

void LogWriter::recordTraceStart(const jint numFrames, const map::HashType threadId) {
    output_.put(TRACE_START);
    writeValue(numFrames);
    writeValue(threadId);
    output_.flush();
}

void LogWriter::recordFrame(const jint bci, const jint lineNumber, const method_id methodId) {
    output_.put(FRAME_FULL);
    writeValue(bci);
    writeValue(lineNumber);
    writeValue(methodId);
    output_.flush();
}

// kept for old format tests
void LogWriter::recordFrame(const jint bci, const method_id methodId) {
    output_.put(FRAME_BCI_ONLY);
    writeValue(bci);
    writeValue(methodId);
    output_.flush();
}

void LogWriter::writeWithSize(const char *value) {
    jint size = (jint) strlen(value);
    writeValue(size);
    output_.write(value, size);
}

void LogWriter::recordNewMethod(const jmethodID method_id, const char *fileName,
                                const char *className, const char *methodName, const char* method_signature) {
    map::HashType methodId = reinterpret_cast<map::HashType>(method_id);
    output_.put(NEW_METHOD);
    writeValue(methodId);
    writeWithSize(fileName);
    writeWithSize(className);
    writeWithSize(methodName);
    std::cout << fileName << " " << className << " " << methodName << "\n";
    output_.flush();
}
