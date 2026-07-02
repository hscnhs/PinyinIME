//! 拼音词典加载器，对应 Java 版 `PinyinDictionary`。
//!
//! 词典文件每行格式：
//! ```text
//! 拼音<TAB>词<TAB>词频
//! ```
//! 以 `#` 开头的行为注释，空行跳过。词频缺省为 1。

use std::collections::HashMap;
use std::io::{self, BufRead};
use std::str::FromStr;

/// 一条词典条目。
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Entry {
    /// 不含空格的完整拼音，例如 `"nihao"`
    pub pinyin: String,
    /// 对应汉字
    pub word: String,
    /// 词频，越大越常用
    pub freq: i32,
}

/// 词典加载错误。
#[derive(Debug)]
pub enum ParseDictError {
    /// 底层 IO 错误
    Io(io::Error),
}

impl From<io::Error> for ParseDictError {
    fn from(e: io::Error) -> Self {
        ParseDictError::Io(e)
    }
}

/// 已加载的拼音词典。
///
/// 内部维护：
/// - `all_entries`：所有条目（按加载顺序，用于补全扫描）
/// - `by_pinyin`：拼音 -> 条目索引列表（用于精确/前缀查询）
pub struct PinyinDictionary {
    all_entries: Vec<Entry>,
    by_pinyin: HashMap<String, Vec<usize>>,
}

impl PinyinDictionary {
    /// 构造空词典。
    pub fn new() -> Self {
        Self {
            all_entries: Vec::new(),
            by_pinyin: HashMap::new(),
        }
    }

    /// 从任意 `BufRead` 加载词典。
    pub fn from_reader<R: BufRead>(reader: R) -> Result<Self, ParseDictError> {
        let mut dict = Self::new();
        for line in reader.lines() {
            let line = line?;
            dict.add_line(&line);
        }
        Ok(dict)
    }

    fn add_line(&mut self, line: &str) {
        if line.is_empty() {
            return;
        }
        // Java: line.charAt(0) == '#' —— 对 ASCII '#' 同义
        if line.starts_with('#') {
            return;
        }
        // Java: line.split("\t")，Java 的 split 不丢弃尾随空串，
        // 但仅对全 tab 的行无影响。这里按 tab 切分。
        let parts: Vec<&str> = line.split('\t').collect();
        if parts.len() < 2 {
            return;
        }
        let pinyin = parts[0].trim();
        let word = parts[1].trim();
        let freq = if parts.len() >= 3 {
            parse_int(parts[2].trim(), 1)
        } else {
            1
        };
        if pinyin.is_empty() || word.is_empty() {
            return;
        }
        let idx = self.all_entries.len();
        self.all_entries.push(Entry {
            pinyin: pinyin.to_string(),
            word: word.to_string(),
            freq,
        });
        self.by_pinyin
            .entry(pinyin.to_string())
            .or_default()
            .push(idx);
    }

    /// 已加载条目总数（含同音多词，与 Java `size()` 一致）。
    pub fn size(&self) -> usize {
        self.all_entries.len()
    }

    /// 全部条目（用于补全扫描）。
    pub fn all_entries(&self) -> &[Entry] {
        &self.all_entries
    }

    /// 精确查询某个拼音对应的全部词条。
    pub fn lookup_exact(&self, pinyin: &str) -> Vec<&Entry> {
        match self.by_pinyin.get(pinyin) {
            Some(indices) => indices.iter().map(|&i| &self.all_entries[i]).collect(),
            None => Vec::new(),
        }
    }
}

impl Default for PinyinDictionary {
    fn default() -> Self {
        Self::new()
    }
}

/// 与 Java `parseInt(s, def)` 行为一致：解析失败返回 `def`。
fn parse_int(s: &str, def: i32) -> i32 {
    s.trim().parse::<i32>().unwrap_or(def)
}

/// 通过 `&str` 解析为词典，等价于 Java 端逐行读取 asset。
///
/// 用法：`let dict: PinyinDictionary = text.parse()?;`
impl FromStr for PinyinDictionary {
    type Err = ParseDictError;

    fn from_str(text: &str) -> Result<Self, ParseDictError> {
        let mut dict = Self::new();
        for line in text.lines() {
            dict.add_line(line);
        }
        Ok(dict)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn loads_basic_entries() {
        let dict: PinyinDictionary = "# comment\nni\t你\t999970\nnihao\t你好\t99999\n\n"
            .parse()
            .unwrap();
        assert_eq!(dict.size(), 2);
        let exact = dict.lookup_exact("ni");
        assert_eq!(exact.len(), 1);
        assert_eq!(exact[0].word, "你");
        assert_eq!(exact[0].freq, 999970);
    }

    #[test]
    fn empty_freq_defaults_to_one() {
        let dict: PinyinDictionary = "a\t啊\n".parse().unwrap();
        assert_eq!(dict.all_entries()[0].freq, 1);
    }

    #[test]
    fn invalid_freq_falls_back_to_one() {
        let dict: PinyinDictionary = "a\t啊\tnotanumber\n".parse().unwrap();
        assert_eq!(dict.all_entries()[0].freq, 1);
    }

    #[test]
    fn unknown_pinyin_returns_empty() {
        let dict: PinyinDictionary = "a\t啊\n".parse().unwrap();
        assert!(dict.lookup_exact("zzz").is_empty());
    }
}
