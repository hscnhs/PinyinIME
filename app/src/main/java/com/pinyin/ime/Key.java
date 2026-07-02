package com.pinyin.ime;

/**
 * 单个按键的数据模型。
 * 包含显示标签、按键码、输出文本以及布局权重等信息。
 */
public class Key {

    /** 普通按键码（与字符/Unicode 一致） */
    public int code;
    /** 按键显示文本 */
    public String label = "";
    /** 直接输出的文本（多字符按键使用，例如全角标点） */
    public String outputText = null;
    /** 宽度权重（默认 1.0） */
    public float width = 1.0f;
    /** 是否为功能键（shift / backspace / enter 等） */
    public boolean isModifier = false;
    /** 是否支持长按重复（backspace） */
    public boolean isRepeatable = false;

    // —— 以下为布局时计算出的几何信息 ——
    public int x;
    public int y;
    public int widthPx;
    public int heightPx;
    /** 按键命中区域（含间隙） */
    public int hitLeft;
    public int hitRight;

    public boolean isInside(int px, int py) {
        return py >= y && py < y + heightPx
                && px >= hitLeft && px < hitRight;
    }

    /** 是否为字母键 */
    public boolean isLetter() {
        return code >= 'a' && code <= 'z';
    }

    /** 输出该按键的文本：优先 outputText，否则用 code 的字符 */
    public String getOutputText() {
        if (outputText != null) return outputText;
        return String.valueOf((char) code);
    }
}
