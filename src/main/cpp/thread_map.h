#ifndef THREAD_MAP_H
#define THREAD_MAP_H

#include <jvmti.h>
#include <jni.h>
#include <string.h>
#include "concurrent_map.h"
#include "sched_tracer.h"

extern jvmtiEnv *ti_env;

int gettid();


template <typename PType>
struct PointerHasher {
	/* Numerical Recipes, 3rd Edition */
	static int64_t hash(void *p) {
		int64_t v = (int64_t)p / sizeof(PType);
		v = v * 3935559000370003845 + 2691343689449507681;

  		v ^= v >> 21;
  		v ^= v << 37;
  		v ^= v >>  4;

  		v *= 4768777513237032717;

  		v ^= v << 20;
  		v ^= v >> 41;
  		v ^= v <<  5;

  		return v;
	}
};

const int kInitialMapSize = 256;

class GCHelper {
public:
	static map::GC::EpochType attach() {
		return map::DefaultGC.attachThread();
	}

	static void detach(map::GC::EpochType &localEpoch) {
		if (localEpoch != map::GC::kEpochInitial)
			map::DefaultGC.detachThread(localEpoch);
	}

	static void safepoint(map::GC::EpochType &localEpoch) {
		map::DefaultGC.safepoint(localEpoch);
	}

	static void signalSafepoint(map::GC::EpochType &localEpoch) {
		map::DefaultGC.ss_safepoint(localEpoch);
	}
};

struct ThreadBucket {
	const int tid;
	char *name;
    jthread jthd;
    JNIEnv *jni;
	std::atomic_int refs;
	map::GC::EpochType localEpoch;

    ThreadBucket(int id, const char *n, jthread _jthd, JNIEnv *_jni) : tid(id), jthd(_jthd), jni(_jni), refs(1), localEpoch(GCHelper::attach()) {
		int len = strlen(n) + 1;
		name = new char[len];
		std::copy(n, n + len, name);
	}

	void release() {
		int prev = refs.fetch_sub(1, std::memory_order_acquire);
		if (prev == 1)
			delete this;
	}

	~ThreadBucket() {
		delete[] name;
        jni->DeleteGlobalRef(jthd);
	}
};


template <typename MapProvider>
class ThreadMapBase {
private:
	MapProvider map;
    SchedTracer& sched_tracer;

	static ThreadBucket *acq_bucket(ThreadBucket *tb) {
		if (tb != nullptr) {
			int prev = tb->refs.fetch_add(1, std::memory_order_relaxed);
			if (prev > 0) {
				return tb;
			}
			tb->refs.fetch_sub(1, std::memory_order_relaxed);
		}
		return nullptr;
	}

public:

	ThreadMapBase(SchedTracer& _sched_tracer, int capacity = kInitialMapSize) : map(capacity), sched_tracer(_sched_tracer) {}

	void put(JNIEnv *jni_env, const char *name, jthread jthd) {
		put(jni_env, name, jthd, gettid());
	}

	void put(JNIEnv *jni_env, const char *name, jthread jthd, int tid) {
		// constructor == call to acquire
		ThreadBucket *info = new ThreadBucket(tid, name, jthd, jni_env);
		ThreadBucket *old = (ThreadBucket*)map.put((map::KeyType)jni_env, (map::ValueType)info);
		if (old != nullptr)
			old->release();
		GCHelper::safepoint(info->localEpoch); // each thread inserts once
        sched_tracer.start(tid, jthd);
	}

	ThreadBucket *get(JNIEnv *jni_env) {
		ThreadBucket *info = acq_bucket((ThreadBucket*)map.get((map::KeyType)jni_env));
		if (info != nullptr)
			GCHelper::signalSafepoint(info->localEpoch);
		return info;
	}

	void remove(JNIEnv *jni_env) {
		ThreadBucket *info = (ThreadBucket*)map.remove((map::KeyType)jni_env);
        sched_tracer.stop(info->tid);
		if (info != nullptr) {
			GCHelper::detach(info->localEpoch);
			info->release();
		}
	}
};

typedef ThreadMapBase<map::ConcurrentMapProvider<PointerHasher<JNIEnv>, true> > ThreadMap;

#endif
