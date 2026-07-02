//! 拼音转换引擎，对应 Java 版 `PinyinEngine`。
//!
//! 算法与 Java 版逐字节对齐：
//! 1. 精确前缀匹配：枚举输入的所有前缀，查找词典中拼音恰好等于该前缀的词条。
//!    其中前缀长度等于输入长度者为"整串精确匹配"（`exact = true`）。
//! 2. 智能补全：词典中拼音以输入开头且更长者。
//! 3. 排序：整串精确 > 消费长度更大 > 词频更高 > 词更短 > 词字典序。
//! 4. 去重：同一中文词只保留排名最高者（排序后的首个）。
//! 5. 截断：最多返回 `max` 条。

use crate::dict::PinyinDictionary;
use std::collections::HashSet;

/// 一个候选词。
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Candidate {
    /// 输出中文
    pub word: String,
    /// 对应拼音
    pub pinyin: String,
    /// 消费的字符数
    pub consumed: usize,
    /// 词频
    pub freq: i32,
    /// 是否为整串精确匹配
    pub exact: bool,
    /// 是否为补全候选
    pub completion: bool,
}

/// 拼音引擎。
pub struct PinyinEngine {
    dict: PinyinDictionary,
}

impl PinyinEngine {
    pub fn new(dict: PinyinDictionary) -> Self {
        Self { dict }
    }

    /// 引用底层的词典（用于 JNI 层读取 size 等）。
    pub fn dictionary(&self) -> &PinyinDictionary {
        &self.dict
    }

    /// 为输入串生成候选词列表。
    ///
    /// `input` 应为小写拼音串；含大写字母会被转小写，非字母字符会被过滤。
    /// 返回排序去重后的候选列表，最多 `max` 条。
    pub fn get_candidates(&self, input: &str, max: usize) -> Vec<Candidate> {
        let mut result: Vec<Candidate> = Vec::new();

        let sanitized = sanitize(input);
        if sanitized.is_empty() {
            return result;
        }

        let input_len = sanitized.len(); // sanitize 后为纯 ASCII，字节数 == 字符数

        // 1) 精确前缀匹配
        for len in 1..=input_len {
            let prefix = &sanitized[..len]; // ASCII 安全
            let exact = len == input_len;
            for e in self.dict.lookup_exact(prefix) {
                result.push(Candidate {
                    word: e.word.clone(),
                    pinyin: e.pinyin.clone(),
                    consumed: len,
                    freq: e.freq,
                    exact,
                    completion: false,
                });
            }
        }

        // 2) 补全匹配
        for e in self.dict.all_entries() {
            if e.pinyin.len() > input_len && e.pinyin.starts_with(&sanitized) {
                result.push(Candidate {
                    word: e.word.clone(),
                    pinyin: e.pinyin.clone(),
                    consumed: input_len,
                    freq: e.freq,
                    exact: false,
                    completion: true,
                });
            }
        }

        sort_and_dedup(&mut result);

        if result.len() > max {
            result.truncate(max);
        }
        result
    }
}

/// 排序 + 去重，与 Java `sortAndDedup` 行为一致。
fn sort_and_dedup(list: &mut Vec<Candidate>) {
    // Rust 的 slice::sort_by 是稳定排序，与 Java Collections.sort 一致。
    list.sort_by(|a, b| {
        // 1) 整串精确匹配优先
        if a.exact != b.exact {
            return if a.exact {
                std::cmp::Ordering::Less
            } else {
                std::cmp::Ordering::Greater
            };
        }
        // 2) 消费长度更大者优先
        if a.consumed != b.consumed {
            return b.consumed.cmp(&a.consumed);
        }
        // 3) 词频更高者优先
        if a.freq != b.freq {
            return b.freq.cmp(&a.freq);
        }
        // 4) 词更短者优先（Java String.length 返回 UTF-16 码元数）
        let la = utf16_len(&a.word);
        let lb = utf16_len(&b.word);
        if la != lb {
            return la.cmp(&lb);
        }
        // 5) 词字典序（Java String.compareTo 按 UTF-16 码元比较；
        //    对合法 UTF-8，str::cmp 按码点比较，与 UTF-16 顺序一致）
        a.word.cmp(&b.word)
    });

    // 去重：同一 word 仅保留排名最高者（首个）
    let mut seen: HashSet<String> = HashSet::new();
    let mut deduped: Vec<Candidate> = Vec::with_capacity(list.len());
    for c in list.drain(..) {
        if seen.insert(c.word.clone()) {
            deduped.push(c);
        }
    }
    *list = deduped;
}

/// 返回字符串的 UTF-16 码元数，等价于 Java `String.length()`。
fn utf16_len(s: &str) -> usize {
    s.chars()
        .map(|c| if (c as u32) >= 0x10000 { 2 } else { 1 })
        .sum()
}

/// 仅保留小写字母，大写字母转小写，其余过滤。等价于 Java `sanitize`。
fn sanitize(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    for ch in input.chars() {
        match ch {
            'a'..='z' => out.push(ch),
            'A'..='Z' => out.push((ch as u8 - b'A' + b'a') as char),
            _ => {}
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::PinyinDictionary;

    fn make_dict() -> PinyinDictionary {
        // 与项目内 pinyin_dict.txt 风格一致的精简样例
        "de\t的\t999999\n\
         shi\t是\t999990\n\
         wo\t我\t999980\n\
         ni\t你\t999970\n\
         ta\t他\t999960\n\
         zhe\t这\t999950\n\
         hao\t好\t500000\n\
         nihao\t你好\t99999\n\
         nihaoma\t你好吗\t5000\n\
         nian\t年\t80000\n\
         nan\t南\t30000\n\
         sh\t是\t100\n"
            .parse()
            .unwrap()
    }

    #[test]
    fn empty_input_returns_empty() {
        let engine = PinyinEngine::new(make_dict());
        assert!(engine.get_candidates("", 10).is_empty());
        assert!(engine.get_candidates("!!!", 10).is_empty());
    }

    #[test]
    fn exact_full_match_ranks_first() {
        // "nihao" 整串精确匹配 "你好"，应排第一
        let engine = PinyinEngine::new(make_dict());
        let cands = engine.get_candidates("nihao", 10);
        assert!(!cands.is_empty());
        assert_eq!(cands[0].word, "你好");
        assert!(cands[0].exact);
        assert_eq!(cands[0].consumed, 5);
    }

    #[test]
    fn prefix_match_and_completion_coexist() {
        // 输入 "ni"：
        //   - 精确前缀匹配 "ni"->你 (exact=true, consumed=2)
        //   - 补全: nihao->你好, nihaoma->你好吗, nian->年 (consumed=2, completion=true)
        // 排序：exact 优先，故 "你" 第一；其后按 consumed(均2) -> freq -> 词长
        let engine = PinyinEngine::new(make_dict());
        let cands = engine.get_candidates("ni", 10);
        assert_eq!(cands[0].word, "你");
        assert!(cands[0].exact);

        let words: Vec<&str> = cands.iter().map(|c| c.word.as_str()).collect();
        // 补全候选中 freq 高者在前
        assert!(words.contains(&"你好"));
        assert!(words.contains(&"年"));
        assert!(words.contains(&"你好吗"));
    }

    #[test]
    fn dedup_keeps_best() {
        // "shi" 既精确匹配 "是"(freq=999990)，也精确匹配 "是"(freq=100, 来自 sh)。
        // 同一词 "是" 仅保留 freq 高者。
        let engine = PinyinEngine::new(make_dict());
        let cands = engine.get_candidates("shi", 10);
        let shi_count = cands.iter().filter(|c| c.word == "是").count();
        assert_eq!(shi_count, 1, "duplicate word should be deduped");
        assert_eq!(cands[0].word, "是");
        assert_eq!(cands[0].freq, 999990);
    }

    #[test]
    fn longer_consumed_beats_higher_freq() {
        // 输入 "nihaoma"：整串精确匹配 "你好吗"(freq=5000)。
        // 前缀 "ni"->你(freq=999970) 因 exact=false 且 consumed=2 < 7，应排在后面。
        let engine = PinyinEngine::new(make_dict());
        let cands = engine.get_candidates("nihaoma", 10);
        assert_eq!(cands[0].word, "你好吗");
        assert!(cands[0].exact);
    }

    #[test]
    fn uppercase_is_lowercased() {
        let engine = PinyinEngine::new(make_dict());
        let lower = engine.get_candidates("ni", 5);
        let upper = engine.get_candidates("NI", 5);
        assert_eq!(lower, upper);
    }

    #[test]
    fn max_truncates() {
        let engine = PinyinEngine::new(make_dict());
        let all = engine.get_candidates("ni", 100);
        let limited = engine.get_candidates("ni", 1);
        assert_eq!(limited.len(), 1);
        assert_eq!(limited[0].word, all[0].word);
    }

    #[test]
    fn no_match_returns_empty() {
        let engine = PinyinEngine::new(make_dict());
        assert!(engine.get_candidates("zzzzz", 10).is_empty());
    }

    #[test]
    fn single_char_pinyin_works() {
        // 单字符前缀 "n" 不会命中任何完整拼音（dict 中无 "n" 项），
        // 但会作为补全前缀命中 ni/nihao 等。
        let engine = PinyinEngine::new(make_dict());
        let cands = engine.get_candidates("n", 10);
        assert!(!cands.is_empty());
        // 全部为补全
        assert!(cands.iter().all(|c| c.completion));
    }

    #[test]
    fn dict_loaded_from_real_file() {
        // 验证可以从 assets/pinyin_dict.txt 加载并正常查询
        let path = concat!(env!("CARGO_MANIFEST_DIR"), "/tests/assets/pinyin_dict.txt");
        let file = std::fs::File::open(path).expect("pinyin_dict.txt should exist in tests/assets");
        let dict = PinyinDictionary::from_reader(std::io::BufReader::new(file)).unwrap();
        assert!(dict.size() > 100, "real dict should have many entries");

        let engine = PinyinEngine::new(dict);
        let cands = engine.get_candidates("nihao", 5);
        assert!(!cands.is_empty());
        assert_eq!(cands[0].word, "你好");
    }
}
