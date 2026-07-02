package com.pinyin.ime;

import android.content.Context;
import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 键盘模型：一组 Row，每行包含若干 Key。
 * 同时负责从 res/xml 中的自定义键盘布局文件解析出键盘。
 *
 * 布局 XML 格式：
 * <Keyboard>
 *   <Row>
 *     <Key label="q" code="113" [width="1.0"] [isModifier="true"] [isRepeatable="true"] [keyOutputText="，"] />
 *     <Spacer width="0.05" />
 *   </Row>
 * </Keyboard>
 */
public class Keyboard {

    public static final int CODE_SHIFT = -1;
    public static final int CODE_MODE_CHANGE = -2;   // ?123 / ABC 切换（保留）
    public static final int CODE_MODE_SYMBOLS = -100; // 进入符号键盘
    public static final int CODE_MODE_BACK = -101;    // 符号键盘内翻页/返回
    public static final int CODE_DELETE = -5;
    public static final int CODE_ENTER = -4;
    public static final int CODE_SPACE = 32;

    public static final int KEYBOARD_QWERTY = 0;
    public static final int KEYBOARD_SYMBOLS = 1;
    public static final int KEYBOARD_SYMBOLS_SHIFT = 2;

    public final List<List<Key>> rows = new ArrayList<>();
    public final List<Key> keys = new ArrayList<>();

    public int totalWidth;
    public int totalHeight;

    private final int mKeyboardId;

    public Keyboard(int keyboardId) {
        mKeyboardId = keyboardId;
    }

    public int getKeyboardId() {
        return mKeyboardId;
    }

    /** 从资源加载键盘布局 */
    public static Keyboard load(Context context, int xmlResId, int keyboardId) {
        Keyboard keyboard = new Keyboard(keyboardId);
        XmlResourceParser parser = context.getResources().getXml(xmlResId);
        try {
            parseKeyboard(context, parser, keyboard);
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException("Failed to load keyboard layout", e);
        } finally {
            parser.close();
        }
        return keyboard;
    }

    private static void parseKeyboard(Context context, XmlResourceParser parser, Keyboard keyboard)
            throws XmlPullParserException, IOException {
        int event = parser.getEventType();
        List<Key> currentRow = null;
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("Row".equals(name)) {
                    currentRow = new ArrayList<>();
                } else if ("Key".equals(name)) {
                    Key key = new Key();
                    applyKeyAttributes(parser, key);
                    if (currentRow != null) currentRow.add(key);
                    keyboard.keys.add(key);
                } else if ("Spacer".equals(name)) {
                    Key spacer = new Key();
                    spacer.code = 0;
                    spacer.label = "";
                    spacer.isModifier = true;
                    float w = parseFloat(parser.getAttributeValue(null, "width"), 0f);
                    spacer.width = w <= 0 ? 0f : w;
                    if (currentRow != null) currentRow.add(spacer);
                }
            } else if (event == XmlPullParser.END_TAG) {
                if ("Row".equals(parser.getName()) && currentRow != null) {
                    keyboard.rows.add(currentRow);
                    currentRow = null;
                }
            }
            event = parser.next();
        }
    }

    private static void applyKeyAttributes(XmlResourceParser parser, Key key) {
        String label = parser.getAttributeValue(null, "label");
        if (label != null) key.label = label;
        key.code = (int) parseLong(parser.getAttributeValue(null, "code"), 0);
        key.outputText = parser.getAttributeValue(null, "keyOutputText");
        key.width = parseFloat(parser.getAttributeValue(null, "width"), 1.0f);
        key.isModifier = parseBool(parser.getAttributeValue(null, "isModifier"), false);
        key.isRepeatable = parseBool(parser.getAttributeValue(null, "isRepeatable"), false);
    }

    private static float parseFloat(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return def; }
    }

    private static long parseLong(String s, long def) {
        if (s == null || s.isEmpty()) return def;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
    }

    private static boolean parseBool(String s, boolean def) {
        if (s == null) return def;
        return "true".equalsIgnoreCase(s);
    }
}
