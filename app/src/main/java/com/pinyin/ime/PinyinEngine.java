package com.pinyin.ime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 拼音转换引擎。
 *
 * 给定用户已输入的拼音串 S（例如 "nihao"），生成中文候选词列表。
 *
 * 匹配策略：
 *  1) 精确前缀：词典中存在拼音恰好等于 S 的某段前缀（例如 S="nihao"，
 *     命中 "ni"→你、"nihao"→你好）。选中后消费对应长度，剩余部分继续查询。
 *  2) 智能补全：词典中拼音以 S 开头且更长（例如 S="ni" 命中 "nihao"→你好、
 *     "nian"→年）。选中后视为补全，清空已输入。
 *
 * 排序：整串精确匹配 > 消费长度更长 > 词频更高。
 */
public class PinyinEngine {

    public static final class Candidate {
        public final String word;     // 输出中文
        public final String pinyin;   // 对应拼音
        public final int consumed;    // 消费的字符数
        public final int freq;        // 词频
        public final boolean exact;   // 是否为整串精确匹配
        public final boolean completion; // 是否为补全候选

        Candidate(String word, String pinyin, int consumed, int freq, boolean exact, boolean completion) {
            this.word = word;
            this.pinyin = pinyin;
            this.consumed = consumed;
            this.freq = freq;
            this.exact = exact;
            this.completion = completion;
        }
    }

    private final PinyinDictionary dict;

    public PinyinEngine(PinyinDictionary dict) {
        this.dict = dict;
    }

    /**
     * 为输入串生成候选词列表。
     *
     * @param input 已输入的拼音（小写字母），可为空
     * @return 排序后的候选列表，最多 max 个
     */
    public List<Candidate> getCandidates(String input, int max) {
        List<Candidate> result = new ArrayList<>();
        if (input == null) input = "";
        input = sanitize(input);
        if (input.isEmpty()) return result;

        // 1) 精确前缀匹配：枚举输入的所有前缀
        for (int len = 1; len <= input.length(); len++) {
            String prefix = input.substring(0, len);
            List<PinyinDictionary.Entry> entries = dict.lookupExact(prefix);
            if (entries.isEmpty()) continue;
            boolean exact = (len == input.length());
            for (PinyinDictionary.Entry e : entries) {
                result.add(new Candidate(e.word, e.pinyin, len, e.freq, exact, false));
            }
        }

        // 2) 补全匹配：词典中拼音以 input 开头且更长
        for (PinyinDictionary.Entry e : dict.allEntries()) {
            if (e.pinyin.length() > input.length() && e.pinyin.startsWith(input)) {
                result.add(new Candidate(e.word, e.pinyin, input.length(), e.freq, false, true));
            }
        }

        // 去重（同一个词保留排名最高者）+ 排序
        sortAndDedup(result);
        if (result.size() > max) {
            return new ArrayList<>(result.subList(0, max));
        }
        return result;
    }

    private void sortAndDedup(List<Candidate> list) {
        Collections.sort(list, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                // 1) 整串精确匹配优先
                if (a.exact != b.exact) return a.exact ? -1 : 1;
                // 2) 消费长度更大者优先（更贴近已输入）
                if (a.consumed != b.consumed) return Integer.compare(b.consumed, a.consumed);
                // 3) 词频更高者优先
                if (a.freq != b.freq) return Integer.compare(b.freq, a.freq);
                // 4) 词更短者优先（单字短语优先级提升）
                if (a.word.length() != b.word.length())
                    return Integer.compare(a.word.length(), b.word.length());
                return a.word.compareTo(b.word);
            }
        });

        Set<String> seen = new HashSet<>();
        List<Candidate> deduped = new ArrayList<>(list.size());
        for (Candidate c : list) {
            if (seen.add(c.word)) deduped.add(c);
        }
        list.clear();
        list.addAll(deduped);
    }

    /** 仅保留小写字母，过滤掉声调/空格等 */
    private String sanitize(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch >= 'a' && ch <= 'z') sb.append(ch);
            else if (ch >= 'A' && ch <= 'Z') sb.append((char) (ch + 32));
        }
        return sb.toString();
    }
}
