//! 拼音转换引擎核心。
//!
//! 本 crate 提供 Android 输入法核心逻辑的纯 Rust 实现，不依赖任何
//! Android/JNI 类型，便于在桌面 CI 上编译与测试，再通过薄 JNI 层接入
//! 输入法 Service。
//!
//! 等价于 Java 版 `com.pinyin.ime.PinyinDictionary` + `PinyinEngine`，
//! 行为（包括排序与去重）保持一致，可使用同一份 `pinyin_dict.txt`。
//!
//! # 示例
//!
//! ```no_run
//! use pinyin_engine::{PinyinDictionary, PinyinEngine};
//!
//! let dict: PinyinDictionary = "ni\t你\t999970\nnihao\t你好\t99999\n".parse().unwrap();
//! let engine = PinyinEngine::new(dict);
//! let cands = engine.get_candidates("ni", 10);
//! assert_eq!(cands[0].word, "你");
//! ```

pub mod dict;
pub mod engine;

pub use dict::{Entry, ParseDictError, PinyinDictionary};
pub use engine::{Candidate, PinyinEngine};
