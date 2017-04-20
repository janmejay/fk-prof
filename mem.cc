#include <fstream>
#include <iostream>
#include <unordered_map>

struct SzCnt {
    std::string class_name;
    std::int64_t sz;
    std::int32_t count;
    std::uint32_t cid;
};

int main(int argc, char** argv) {
    if (argc < 3) {
        std::cerr << "Please provide class-file and alloc-file as args.\n";
        return -1;
    }

    std::fstream class_input(argv[1], std::ios_base::in);
    std::fstream alloc_input(argv[2], std::ios_base::in);

    std::unordered_map<std::uint32_t, SzCnt> alloc;

    auto ignore_line = true;
    SzCnt e{"", 0, 0, 0};
    while(! class_input.eof()) {
        if (ignore_line) {
            class_input.ignore(1000, '\n');
            ignore_line = false;
            continue;
        }
        class_input >> e.cid >> e.class_name;
        alloc[e.cid] = e;
    }

    std::cerr << "Loaded classes, count: " << alloc.size() << "\n";

    std::uint64_t handled = 0, allocing = 0, freeing = 0;
    ignore_line = true;
    while (! alloc_input.eof()) {
        if (ignore_line) {
            alloc_input.ignore(1000, '\n');
            ignore_line = false;
            continue;
        }
        SzCnt tmp_e;
        alloc_input >> tmp_e.sz >> tmp_e.cid;

        auto& e = alloc[tmp_e.cid];
        e.sz += tmp_e.sz;
        e.count += (tmp_e.sz > 0) ? 1 : -1;

        if (tmp_e.sz > 0) {
            allocing++;
        } else {
            freeing++;
        }
        
        if ((handled++ % 100000) == 0) {
            std::cerr << "Handled alloc-line: " << handled - 1 << "{ A: " << allocing << ", F: " << freeing << " }\n";
        }
    }

    std::cout << "Class\tSize\tCount\n";
    for (const auto& e : alloc) {
        const auto& val = e.second;
        std::cout << val.class_name << "\t" << val.sz << "\t" << val.count << "\n";
    }
    
    return 0;
}
