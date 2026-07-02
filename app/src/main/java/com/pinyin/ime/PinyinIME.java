package com.pinyin.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.view.ContextThemeWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 输入法主服务。
 *
 * 支持：
 *  - 中文（拼音）：边输入边出候选词，点击/空格选词
 *  - 英文（QWERTY）：直接上屏，支持 Shift 大小写 / 双击 Shift 大写锁定
 *  - 符号键盘（?123）两页
 *  - 长按空格切换中英文
 *  - 双击空格输出句号
 *  - 深色 / 浅色主题
 */
public class PinyinIME extends InputMethodService
        implements SoftKeyboardView.OnKeyListener,
        CandidateView.OnCandidateSelectedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "PinyinIME";

    // —— 视图 ——
    private View mInputView;
    private SoftKeyboardView mKeyboardView;
    private CandidateView mCandidateView;

    // —— 引擎 / 词典 ——
    private final PinyinDictionary mDict = new PinyinDictionary();
    private PinyinEngine mEngine;

    // —— 键盘 ——
    private Keyboard mQwertyKeyboard;
    private Keyboard mSymbolsKeyboard;
    private Keyboard mSymbolsShiftKeyboard;
    private int mKeyboardMode = Keyboard.KEYBOARD_QWERTY;

    // —— 输入状态 ——
    private final StringBuilder mPinyinBuffer = new StringBuilder();
    private List<PinyinEngine.Candidate> mCandidates = new ArrayList<>();

    // —— 中英文模式 ——
    private boolean mChineseMode = true;

    // —— Shift / 大写 ——
    private boolean mShiftOn = false;
    private boolean mCapsLock = false;
    private long mLastShiftTapTime = 0;
    private static final long DOUBLE_TAP_TIMEOUT = 350;

    // —— 设置 ——
    private SharedPreferences mPrefs;
    private boolean mPrefSound;
    private boolean mPrefVibrate;
    private boolean mPrefPopup;
    private boolean mPrefDoubleSpacePeriod;
    private boolean mDarkTheme;

    // —— 双击空格检测 ——
    private long mLastSpaceTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        loadPrefs();
        mEngine = new PinyinEngine(mDict);
    }

    private void loadPrefs() {
        mPrefSound = mPrefs.getBoolean("pref_key_sound", false);
        mPrefVibrate = mPrefs.getBoolean("pref_key_vibrate", true);
        mPrefPopup = mPrefs.getBoolean("pref_key_popup", true);
        mPrefDoubleSpacePeriod = mPrefs.getBoolean("pref_double_space_period", true);
        mDarkTheme = mPrefs.getBoolean("pref_dark_theme", false);
        mChineseMode = mPrefs.getBoolean("pref_chinese_default", true);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        loadPrefs();
        if (mKeyboardView != null) {
            mKeyboardView.setPreviewEnabled(mPrefPopup);
            mKeyboardView.setShift(mShiftOn);
            updateSpaceLabel();
        }
        // 主题切换：重建输入视图以套用新主题
        if ("pref_dark_theme".equals(key)) {
            mInputView = null;
            // 强制框架重建输入视图
            setInputView(onCreateInputView());
        }
    }

    /** 根据设置构造带键盘主题的 Context */
    private Context themedContext() {
        int themeRes = mDarkTheme ? R.style.KeyboardTheme_Dark : R.style.KeyboardTheme_Light;
        return new ContextThemeWrapper(this, themeRes);
    }

    @Override
    public View onCreateInputView() {
        return buildInputView();
    }

    private View buildInputView() {
        Context ctx = themedContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        mInputView = inflater.inflate(R.layout.input, null);

        mKeyboardView = mInputView.findViewById(R.id.keyboard);
        mCandidateView = mInputView.findViewById(R.id.candidates);

        mKeyboardView.setOnKeyListener(this);
        mCandidateView.setOnCandidateSelectedListener(this);
        mKeyboardView.setPreviewEnabled(mPrefPopup);

        // 懒加载词典
        mDict.load(this);

        // 懒加载键盘
        if (mQwertyKeyboard == null) mQwertyKeyboard = Keyboard.load(this, R.xml.qwerty, Keyboard.KEYBOARD_QWERTY);
        if (mSymbolsKeyboard == null) mSymbolsKeyboard = Keyboard.load(this, R.xml.symbols, Keyboard.KEYBOARD_SYMBOLS);
        if (mSymbolsShiftKeyboard == null) mSymbolsShiftKeyboard = Keyboard.load(this, R.xml.symbols_shift, Keyboard.KEYBOARD_SYMBOLS_SHIFT);

        mKeyboardMode = Keyboard.KEYBOARD_QWERTY;
        attachKeyboardForMode();
        updateSpaceLabel();
        return mInputView;
    }

    private void attachKeyboardForMode() {
        Keyboard kb;
        switch (mKeyboardMode) {
            case Keyboard.KEYBOARD_SYMBOLS: kb = mSymbolsKeyboard; break;
            case Keyboard.KEYBOARD_SYMBOLS_SHIFT: kb = mSymbolsShiftKeyboard; break;
            default: kb = mQwertyKeyboard; break;
        }
        mKeyboardView.setKeyboard(kb);
        mKeyboardView.setShift(mShiftOn);
        updateSpaceLabel();
    }

    private void updateSpaceLabel() {
        if (mKeyboardView == null) return;
        String label = mChineseMode ? getString(R.string.space_label_zh) : getString(R.string.space_label_en);
        mKeyboardView.setSpaceLabel(label);
    }

    @Override
    public void onStartInput(android.view.inputmethod.EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        // 切换输入框时清空拼音缓冲
        resetComposingState();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        resetComposingState();
    }

    private void resetComposingState() {
        mPinyinBuffer.setLength(0);
        mCandidates.clear();
        if (mCandidateView != null) mCandidateView.clear();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
    }

    // —————— 按键处理 ——————

    @Override
    public void onKey(Key key) {
        feedback();
        switch (key.code) {
            case Keyboard.CODE_SHIFT:
                handleShift();
                break;
            case Keyboard.CODE_DELETE:
                handleDelete();
                break;
            case Keyboard.CODE_ENTER:
                handleEnter();
                break;
            case Keyboard.CODE_SPACE:
                handleSpace();
                break;
            case Keyboard.CODE_MODE_SYMBOLS:
                handleModeChange();
                break;
            case Keyboard.CODE_MODE_BACK:
                handleSymbolPageToggle();
                break;
            default:
                handleCharacter(key);
                break;
        }
    }

    @Override
    public boolean onLongPress(Key key) {
        switch (key.code) {
            case Keyboard.CODE_SPACE:
                // 长按空格：切换中英文
                toggleLanguage();
                return true;
            case Keyboard.CODE_SHIFT:
                // 长按 Shift：直接大写锁定
                mCapsLock = true;
                mShiftOn = true;
                mKeyboardView.setShift(true);
                return true;
            case Keyboard.CODE_ENTER:
                // 长按回车：打开输入法选择器
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
                return true;
            default:
                return false;
        }
    }

    private void handleCharacter(Key key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        if (mKeyboardMode != Keyboard.KEYBOARD_QWERTY) {
            // 符号键盘：直接输出
            ic.commitText(key.getOutputText(), 1);
            // 输出符号后回到主键盘
            mKeyboardMode = Keyboard.KEYBOARD_QWERTY;
            attachKeyboardForMode();
            return;
        }

        char code = (char) key.code;
        boolean isLetter = (code >= 'a' && code <= 'z') || (code >= 'A' && code <= 'Z');
        // 非字母（如标点）直接输出
        if (!isLetter) {
            ic.commitText(key.getOutputText(), 1);
            return;
        }

        if (mChineseMode) {
            // 中文模式：拼接到拼音缓冲
            char lower = Character.toLowerCase(code);
            mPinyinBuffer.append(lower);
            updateCandidates();
        } else {
            // 英文模式：考虑大小写
            char out = mShiftOn ? Character.toUpperCase(code) : code;
            ic.commitText(String.valueOf(out), 1);
            // 单字母后关闭 Shift（非大写锁定时）
            if (mShiftOn && !mCapsLock) {
                mShiftOn = false;
                mKeyboardView.setShift(false);
            }
        }
    }

    private void handleShift() {
        long now = SystemClock.uptimeMillis();
        if (mShiftOn && !mCapsLock) {
            // 已开启单次大写：再点关闭
            mShiftOn = false;
            mCapsLock = false;
        } else if (mCapsLock) {
            // 大写锁定状态：点一下关闭
            mCapsLock = false;
            mShiftOn = false;
        } else if (now - mLastShiftTapTime < DOUBLE_TAP_TIMEOUT) {
            // 双击 Shift：大写锁定
            mCapsLock = true;
            mShiftOn = true;
        } else {
            // 单次大写
            mShiftOn = true;
        }
        mLastShiftTapTime = now;
        mKeyboardView.setShift(mShiftOn);
    }

    private void handleDelete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (mPinyinBuffer.length() > 0) {
            // 删除拼音缓冲最后一个字符
            mPinyinBuffer.deleteCharAt(mPinyinBuffer.length() - 1);
            updateCandidates();
        } else {
            // 删除编辑框中光标前一个字符
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void handleSpace() {
        long now = SystemClock.uptimeMillis();
        if (mPinyinBuffer.length() > 0) {
            if (!mCandidates.isEmpty()) {
                // 有候选：选择第一个
                selectCandidate(0);
                // 选词后若缓冲已空，再补一个空格（更像 Gboard）
                if (mPinyinBuffer.length() == 0) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.commitText(" ", 1);
                }
                return;
            }
            // 缓冲非空但无候选：把原始拼音上屏并加空格
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            mPinyinBuffer.setLength(0);
            mCandidates.clear();
            if (mCandidateView != null) mCandidateView.clear();
            if (ic != null) ic.commitText(" ", 1);
            return;
        }
        // 缓冲为空：输出空格（或双击空格转句号）
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (mPrefDoubleSpacePeriod && now - mLastSpaceTime < DOUBLE_TAP_TIMEOUT * 2) {
            // 双击空格：删除上一空格并输出句号
            ic.deleteSurroundingText(1, 0);
            ic.commitText(mChineseMode ? "。" : ".", 1);
            mLastSpaceTime = 0;
        } else {
            ic.commitText(" ", 1);
            mLastSpaceTime = now;
        }
    }

    private void handleEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (mPinyinBuffer.length() > 0 && !mCandidates.isEmpty()) {
            // 有候选：选第一个，不换行
            selectCandidate(0);
            return;
        }
        // 清空缓冲
        if (mPinyinBuffer.length() > 0) {
            resetComposingState();
        }
        // 默认回车动作，否则输出换行
        if (!sendDefaultEditorAction(true)) {
            ic.commitText("\n", 1);
        }
    }

    private void handleModeChange() {
        if (mKeyboardMode == Keyboard.KEYBOARD_QWERTY) {
            mKeyboardMode = Keyboard.KEYBOARD_SYMBOLS;
        } else {
            mKeyboardMode = Keyboard.KEYBOARD_QWERTY;
        }
        attachKeyboardForMode();
    }

    private void handleSymbolPageToggle() {
        if (mKeyboardMode == Keyboard.KEYBOARD_SYMBOLS) {
            mKeyboardMode = Keyboard.KEYBOARD_SYMBOLS_SHIFT;
        } else if (mKeyboardMode == Keyboard.KEYBOARD_SYMBOLS_SHIFT) {
            mKeyboardMode = Keyboard.KEYBOARD_SYMBOLS;
        } else {
            mKeyboardMode = Keyboard.KEYBOARD_SYMBOLS;
        }
        attachKeyboardForMode();
    }

    private void toggleLanguage() {
        mChineseMode = !mChineseMode;
        // 切换语言时清空拼音缓冲
        resetComposingState();
        updateSpaceLabel();
        // 切到英文时关闭 Shift
        if (!mChineseMode) {
            mShiftOn = false;
            mCapsLock = false;
            mKeyboardView.setShift(false);
        }
    }

    // —————— 候选词 ——————

    private void updateCandidates() {
        List<PinyinEngine.Candidate> cands = mEngine.getCandidates(mPinyinBuffer.toString(), 30);
        mCandidates = cands;
        List<String> words = new ArrayList<>(cands.size());
        for (PinyinEngine.Candidate c : cands) words.add(c.word);

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (mPinyinBuffer.length() == 0) {
                ic.finishComposingText();
            } else {
                // 把首个候选作为预览文本上屏（若无候选则显示原始拼音）
                String composing;
                if (!cands.isEmpty()) {
                    composing = cands.get(0).word;
                } else {
                    composing = mPinyinBuffer.toString();
                }
                ic.setComposingText(composing, 1);
            }
        }
        if (mCandidateView != null) {
            mCandidateView.setCandidates(words, 0);
        }
    }

    @Override
    public void onCandidateSelected(int index, String word) {
        feedback();
        if (index < 0 || index >= mCandidates.size()) return;
        selectCandidate(index);
    }

    private void selectCandidate(int index) {
        if (index < 0 || index >= mCandidates.size()) return;
        PinyinEngine.Candidate c = mCandidates.get(index);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            mPinyinBuffer.setLength(0);
            mCandidates.clear();
            return;
        }
        // 提交候选词（替换正在组合的文本）
        ic.commitText(c.word, 1);
        // 消费对应拼音
        int consume = Math.min(c.consumed, mPinyinBuffer.length());
        mPinyinBuffer.delete(0, consume);
        updateCandidates();
    }

    // —————— 反馈 ——————

    private void feedback() {
        if (mPrefVibrate) {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(android.os.VibrationEffect.createOneShot(
                        8, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
        if (mPrefSound) {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD);
            }
        }
    }
}
