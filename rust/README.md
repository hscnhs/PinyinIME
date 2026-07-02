# Rust

拼音输入法核心的纯 Rust 实现，与 Android Java 版 [`PinyinEngine`](../app/src/main/java/com/pinyin/ime/PinyinEngine.java) 算法等价，便于在桌面 CI 上编译与单元测试，再通过薄 JNI 层接入输入法 Service。

## 目录结构

```
rust/
├── Cargo.toml
├── src/
│   ├── lib.rs       # crate 入口，re-export 公共类型
│   ├── dict.rs      # 词典加载器（对应 Java PinyinDictionary）
│   └── engine.rs    # 候选生成 + 排序/去重（对应 Java PinyinEngine）
└── tests/
    ├── integration.rs          # 端到端集成测试
    └── assets/pinyin_dict.txt  # 与 app/src/main/assets 同源词典
```

## 构建 / 测试

```bash
cd rust
cargo build              # 编译
cargo test               # 运行单元测试 + 集成测试
cargo test -- --nocapture  # 显示 println 输出
```

无需网络，无需 Android SDK；仅需稳定版 Rust（edition 2021）。

## 公共 API

```rust
use pinyin_engine::{PinyinEngine, PinyinDictionary};

let dict: PinyinDictionary = "ni\t你\t999970\nnihao\t你好\t99999\n".parse()?;
let engine = PinyinEngine::new(dict);

let cands = engine.get_candidates("ni", 10);
assert_eq!(cands[0].word, "你");
```

## 与 Java 版的等价性

| Java | Rust | 说明 |
|------|------|------|
| `PinyinDictionary.load(Context)` | `PinyinDictionary::from_reader` / `from_str` | 同一 `pinyin_dict.txt`，同样的注释/空行/词频缺省规则 |
| `PinyinDictionary.lookupExact` | `PinyinDictionary::lookup_exact` | 返回同序 |
| `PinyinEngine.getCandidates` | `PinyinEngine::get_candidates` | 同排序、同去重、同截断 |
| `String.length()` | `utf16_len()` | UTF-16 码元数，保证对 emoji 也一致 |
| `String.compareTo` | `str::cmp` | 按码点比较，与 UTF-16 顺序一致 |
| `Collections.sort`（稳定） | `slice::sort_by`（稳定） | 排序稳定，等值元素相对次序保持 |
