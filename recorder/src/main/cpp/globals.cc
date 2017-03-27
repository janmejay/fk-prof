#include "globals.hh"

#define STR(X) #X
#define EXP_STR(y) STR(y)

const char* fkprec_commit = "Commit: " EXP_STR(GIT_COMMIT);
const char* fkprec_branch = "Branch: " EXP_STR(GIT_BRANCH);
const char* fkprec_version = "Version: " EXP_STR(FKP_VERSION);
const char* fkprec_version_verbose = "Version: " EXP_STR(FKP_VERSION) " (commit: '" EXP_STR(GIT_COMMIT) "', branch: '" EXP_STR(GIT_BRANCH) "')";
const char* fkprec_build_env = "Build Env(base64 encoded): " EXP_STR(BUILD_ENV);

Time::Pt Time::now() {
    return std::chrono::steady_clock::now();
}

std::uint32_t Time::elapsed_seconds(const Pt& later, const Pt& earlier) {
    auto diff = std::chrono::duration_cast<sec>(later - earlier);
    return diff.count();
}
