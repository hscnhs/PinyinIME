//! 集成测试：从真实词典文件加载，验证端到端查询。
//!
//! 这个文件位于 `tests/` 目录，Cargo 把它当作单独的 crate 编译，
//! 只能访问 `pinyin_engine` 的公共 API，确保 API 完整可用。

use pinyin_engine::{PinyinDictionary, PinyinEngine};
use std::io::BufReader;

const DICT_PATH: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/tests/assets/pinyin_dict.txt");

fn load() -> PinyinEngine {
    let file = std::fs::File::open(DICT_PATH).expect("pinyin_dict.txt present");
    let dict = PinyinDictionary::from_reader(BufReader::new(file)).expect("dict parses");
    PinyinEngine::new(dict)
}

#[test]
fn loads_real_dictionary() {
    let engine = load();
    assert!(engine.dictionary().size() > 100);
}

#[test]
fn nihao_returns_nihao_first() {
    let engine = load();
    let cands = engine.get_candidates("nihao", 5);
    assert!(!cands.is_empty(), "expected candidates for 'nihao'");
    assert_eq!(cands[0].word, "你好");
    assert!(cands[0].exact);
}

#[test]
fn short_prefix_yields_completions() {
    let engine = load();
    let cands = engine.get_candidates("n", 20);
    assert!(!cands.is_empty(), "n should yield completions");
    for c in &cands {
        assert!(c.completion, "all 'n' candidates are completions");
    }
}

#[test]
fn empty_and_garbage_inputs_are_safe() {
    let engine = load();
    assert!(engine.get_candidates("", 10).is_empty());
    assert!(engine.get_candidates("123!@#", 10).is_empty());
}

#[test]
fn determinism_same_input_same_output() {
    let engine = load();
    let a = engine.get_candidates("woaibeijing", 30);
    let b = engine.get_candidates("woaibeijing", 30);
    assert_eq!(a, b);
}

#[test]
fn max_limit_is_respected() {
    let engine = load();
    let n = engine.get_candidates("n", 3);
    assert!(n.len() <= 3);
}
