package com.pinyin.ime;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拼音词典加载器。
 *
 * 词典文件位于 assets/pinyin_dict.txt，每行格式：
 *   拼音<TAB>词<TAB>词频
 * 例如：
 *   ni	你	9999
 *   nihao	你好	99999
 *
 * 同时把单字按拼音分组，方便前缀/补全查询。
 */
public class PinyinDictionary {

    public static final class Entry {
        public final String pinyin;   // 不含空格的完整拼音，例如 "nihao"
        public final String word;     // 对应汉字
        public final int freq;        // 词频，越大越常用

        Entry(String pinyin, String word, int freq) {
            this.pinyin = pinyin;
            this.word = word;
            this.freq = freq;
        }
    }

    private final List<Entry> allEntries = new ArrayList<>();
    /** 拼音 -> 词条列表，用于精确/前缀查询 */
    private final Map<String, List<Entry>> byPinyin = new HashMap<>();
    /** 所有不重复的拼音键（用于补全扫描） */
    private final List<String> pinyinKeys = new ArrayList<>();

    private boolean loaded = false;

    /** 从 assets 加载词典，仅加载一次 */
    public synchronized void load(Context context) {
        if (loaded) return;
        AssetManager am = context.getAssets();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(am.open("pinyin_dict.txt"), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                String pinyin = parts[0].trim();
                String word = parts[1].trim();
                int freq = parts.length >= 3 ? parseInt(parts[2].trim(), 1) : 1;
                if (pinyin.isEmpty() || word.isEmpty()) continue;
                Entry e = new Entry(pinyin, word, freq);
                allEntries.add(e);
                List<Entry> list = byPinyin.get(pinyin);
                if (list == null) {
                    list = new ArrayList<>();
                    byPinyin.put(pinyin, list);
                    pinyinKeys.add(pinyin);
                }
                list.add(e);
            }
        } catch (IOException e) {
            // 词典加载失败时退化为空词典，输入法仍可工作（仅无法出中文候选）
            android.util.Log.w("PinyinDict", "load dict failed", e);
        }
        loaded = true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int size() {
        return allEntries.size();
    }

    /** 精确查询某个拼音对应的全部词条 */
    public List<Entry> lookupExact(String pinyin) {
        List<Entry> list = byPinyin.get(pinyin);
        return list != null ? list : new ArrayList<>();
    }

    /** 全部词条，用于补全扫描 */
    public List<Entry> allEntries() {
        return allEntries;
    }

    /** 所有拼音键 */
    public List<String> pinyinKeys() {
        return pinyinKeys;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
