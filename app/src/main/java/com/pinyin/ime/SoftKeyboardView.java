package com.pinyin.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

/**
 * 自绘软键盘视图，仿 Gboard 风格。
 *
 * 负责：
 *  - 根据传入的 Keyboard 计算按键几何并绘制
 *  - 处理触摸（按下 / 移动 / 抬起）
 *  - 长按重复（退格键）
 *  - 按键预览弹窗
 *  - Shift 大写状态显示
 */
public class SoftKeyboardView extends View {

    /** 按键回调 */
    public interface OnKeyListener {
        void onKey(Key key);

        /** 长按事件（非重复键）。返回 true 表示已处理，UP 时不再触发 onKey */
        boolean onLongPress(Key key);
    }

    private static final int LONG_PRESS_DELAY = 400;
    private static final int REPEAT_INTERVAL = 50;

    private Keyboard mKeyboard;
    private OnKeyListener mListener;

    // —— 主题颜色 ——
    private int mColorBg;
    private int mColorKey;
    private int mColorKeyPressed;
    private int mColorKeyFunction;
    private int mColorKeyFunctionPressed;
    private int mColorText;
    private int mColorTextFunction;
    private int mColorSpaceText;
    private int mColorAccent;

    // —— 画笔 ——
    private final Paint mKeyBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPreviewTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTmpRect = new RectF();

    // —— 尺寸 ——
    private final float mKeyTextSize;
    private final float mFuncTextSize;
    private final float mPreviewTextSize;
    private final float mKeyHGap;
    private final float mKeyVGap;
    private final float mCornerRadius;
    private final float mSidePadding;
    private final float mTopPadding;
    private final float mBottomPadding;

    // —— 触摸状态 ——
    private Key mPressedKey;
    private boolean mInLongPressRepeat = false;
    private boolean mLongPressHandled = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPressedKey == null) return;
            if (mPressedKey.isRepeatable) {
                mInLongPressRepeat = true;
                mHandler.postDelayed(this, REPEAT_INTERVAL);
                if (mListener != null) mListener.onKey(mPressedKey);
            } else {
                mLongPressHandled = true;
                if (mListener != null) {
                    boolean handled = mListener.onLongPress(mPressedKey);
                    // 若未处理，UP 时仍照常触发 onKey
                    if (!handled) mLongPressHandled = false;
                }
            }
        }
    };

    // —— 预览弹窗 ——
    private PopupWindow mPreviewPopup;
    private TextView mPreviewText;
    private boolean mPreviewEnabled = true;

    // —— 大写状态 ——
    private boolean mShiftOn = false;

    // —— 空格键标签 ——
    private String mSpaceLabel = "";

    public SoftKeyboardView(Context context) {
        this(context, null);
    }

    public SoftKeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SoftKeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mKeyTextSize = getResources().getDimension(R.dimen.key_text_size);
        mFuncTextSize = getResources().getDimension(R.dimen.key_function_text_size);
        mPreviewTextSize = getResources().getDimension(R.dimen.preview_text_size);
        mKeyHGap = getResources().getDimension(R.dimen.key_horizontal_gap);
        mKeyVGap = getResources().getDimension(R.dimen.key_vertical_gap);
        mCornerRadius = getResources().getDimension(R.dimen.key_corner_radius);
        mSidePadding = mKeyHGap;
        mTopPadding = mKeyVGap / 2f;
        mBottomPadding = mKeyVGap / 2f;

        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mPreviewTextPaint.setTextAlign(Paint.Align.CENTER);

        applyThemeColors();
        setupPreview();
    }

    private void setupPreview() {
        mPreviewText = new TextView(getContext());
        mPreviewText.setGravity(Gravity.CENTER);
        mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mPreviewTextSize);
        mPreviewText.setTextColor(Color.WHITE);
        mPreviewPopup = new PopupWindow(mPreviewText);
        mPreviewPopup.setClippingEnabled(false);
    }

    private void applyThemeColors() {
        mColorBg = resolveAttr(R.attr.kbBackground);
        mColorKey = resolveAttr(R.attr.kbKeyBackground);
        mColorKeyPressed = resolveAttr(R.attr.kbKeyPressed);
        mColorKeyFunction = resolveAttr(R.attr.kbKeyFunctionBackground);
        mColorKeyFunctionPressed = resolveAttr(R.attr.kbKeyFunctionPressed);
        mColorText = resolveAttr(R.attr.kbTextColor);
        mColorTextFunction = resolveAttr(R.attr.kbTextFunctionColor);
        mColorSpaceText = resolveAttr(R.attr.kbSpaceTextColor);
        mColorAccent = resolveAttr(R.attr.kbAccentColor);
        mPreviewText.setBackgroundColor(mColorKey);
        mPreviewText.setTextColor(mColorText);
        invalidate();
    }

    private int resolveAttr(int attr) {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
            if (tv.resourceId != 0) {
                return getContext().getColor(tv.resourceId);
            }
            return tv.data;
        }
        return android.graphics.Color.BLACK;
    }

    public void setOnKeyListener(OnKeyListener listener) {
        mListener = listener;
    }

    public void setKeyboard(Keyboard keyboard) {
        mKeyboard = keyboard;
        requestLayout();
        invalidate();
    }

    public void setShift(boolean on) {
        mShiftOn = on;
        invalidate();
    }

    public void setSpaceLabel(String label) {
        mSpaceLabel = label;
        invalidate();
    }

    public void setPreviewEnabled(boolean enabled) {
        mPreviewEnabled = enabled;
    }

    public void refreshTheme() {
        applyThemeColors();
    }

    // —————— 布局 ——————

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) getResources().getDimension(R.dimen.keyboard_height);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutKeys(w, h);
    }

    /** 计算每个按键的坐标与尺寸 */
    private void layoutKeys(int viewWidth, int viewHeight) {
        if (mKeyboard == null || mKeyboard.rows.isEmpty()) return;
        int rowCount = mKeyboard.rows.size();
        float availableWidth = viewWidth - 2 * mSidePadding;
        float availableHeight = viewHeight - mTopPadding - mBottomPadding - (rowCount - 1) * mKeyVGap;
        float rowHeight = availableHeight / rowCount;

        float y = mTopPadding;
        for (List<Key> row : mKeyboard.rows) {
            float totalWeight = 0;
            for (Key k : row) totalWeight += k.width;
            float x = mSidePadding;
            float rowGapSum = (row.size() - 1) * mKeyHGap;
            float usableWidth = Math.max(0, availableWidth - rowGapSum);
            for (Key k : row) {
                float keyWidth = usableWidth * (k.width / totalWeight);
                k.x = Math.round(x);
                k.y = Math.round(y);
                k.widthPx = Math.round(keyWidth);
                k.heightPx = Math.round(rowHeight);
                k.hitLeft = k.x;
                k.hitRight = k.x + k.widthPx;
                x += keyWidth + mKeyHGap;
            }
            y += rowHeight + mKeyVGap;
        }
    }

    // —————— 绘制 ——————

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(mColorBg);
        if (mKeyboard == null) return;
        for (List<Key> row : mKeyboard.rows) {
            for (Key k : row) {
                if (k.code == 0) continue; // 跳过空白占位
                drawKey(canvas, k);
            }
        }
    }

    private void drawKey(Canvas canvas, Key k) {
        mTmpRect.set(k.x, k.y, k.x + k.widthPx, k.y + k.heightPx);
        mTmpRect.inset(mKeyHGap / 2f, mKeyVGap / 2f);

        boolean pressed = (k == mPressedKey);
        boolean isSpace = (k.code == Keyboard.CODE_SPACE);
        boolean isFunction = k.isModifier || k.code == Keyboard.CODE_DELETE || k.code == Keyboard.CODE_ENTER;

        int bg;
        if (isFunction) {
            bg = pressed ? mColorKeyFunctionPressed : mColorKeyFunction;
        } else {
            bg = pressed ? mColorKeyPressed : mColorKey;
        }
        mKeyBgPaint.setColor(bg);
        canvas.drawRoundRect(mTmpRect, mCornerRadius, mCornerRadius, mKeyBgPaint);

        // 文本
        String label = k.label;
        if (isSpace && mSpaceLabel != null && !mSpaceLabel.isEmpty()) {
            label = mSpaceLabel;
        }
        if (mShiftOn && k.isLetter()) {
            label = label.toUpperCase();
        }
        if (label == null || label.isEmpty()) return;

        mTextPaint.setColor(isFunction ? mColorTextFunction : mColorText);
        mTextPaint.setTextSize(isFunction ? mFuncTextSize : mKeyTextSize);
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        float textY = k.y + k.heightPx / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, k.x + k.widthPx / 2f, textY, mTextPaint);

        // Shift 高亮指示
        if (k.code == Keyboard.CODE_SHIFT && mShiftOn) {
            mKeyBgPaint.setColor(mColorAccent);
            mKeyBgPaint.setStyle(Paint.Style.STROKE);
            mKeyBgPaint.setStrokeWidth(3f);
            canvas.drawRoundRect(mTmpRect, mCornerRadius, mCornerRadius, mKeyBgPaint);
            mKeyBgPaint.setStyle(Paint.Style.FILL);
        }
    }

    // —————— 触摸 ——————

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                Key k = findKey(x, y);
                mPressedKey = k;
                mLongPressHandled = false;
                invalidate();
                cancelLongPress();
                if (k != null) {
                    // 重复键与可长按键均启动长按计时
                    mHandler.postDelayed(mLongPressRunnable, LONG_PRESS_DELAY);
                    showPreview(k);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                Key k = findKey(x, y);
                if (k != mPressedKey) {
                    mPressedKey = k;
                    cancelLongPress();
                    mInLongPressRepeat = false;
                    mLongPressHandled = false;
                    invalidate();
                    if (k != null) {
                        mHandler.postDelayed(mLongPressRunnable, LONG_PRESS_DELAY);
                        showPreview(k);
                    } else {
                        hidePreview();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                cancelLongPress();
                hidePreview();
                Key k = mPressedKey;
                mPressedKey = null;
                boolean wasRepeating = mInLongPressRepeat;
                boolean longHandled = mLongPressHandled;
                mInLongPressRepeat = false;
                mLongPressHandled = false;
                invalidate();
                if (k != null && action == MotionEvent.ACTION_UP && mListener != null
                        && !wasRepeating && !longHandled) {
                    mListener.onKey(k);
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private Key findKey(float x, float y) {
        if (mKeyboard == null) return null;
        for (List<Key> row : mKeyboard.rows) {
            for (Key k : row) {
                if (k.code == 0) continue; // 占位不可命中
                if (k.isInside((int) x, (int) y)) return k;
            }
        }
        return null;
    }

    private void cancelLongPress() {
        mHandler.removeCallbacks(mLongPressRunnable);
    }

    // —————— 预览弹窗 ——————

    private void showPreview(Key k) {
        if (!mPreviewEnabled || k == null || k.label == null || k.label.isEmpty()) return;
        // 功能键不显示预览
        if (k.isModifier || k.code == Keyboard.CODE_DELETE || k.code == Keyboard.CODE_ENTER) {
            return;
        }
        String label = k.label;
        if (mShiftOn && k.isLetter()) label = label.toUpperCase();
        mPreviewText.setText(label);
        mPreviewText.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int pw = Math.max(mPreviewText.getMeasuredWidth(),
                (int) (k.widthPx * 1.3f));
        int ph = (int) getResources().getDimension(R.dimen.preview_height);
        mPreviewText.measure(
                View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY));
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        int px = loc[0] + k.x + k.widthPx / 2 - pw / 2;
        int py = loc[1] + k.y - ph;
        if (px < 0) px = 0;
        mPreviewPopup.setWidth(pw);
        mPreviewPopup.setHeight(ph);
        if (!mPreviewPopup.isShowing()) {
            mPreviewPopup.showAtLocation(this, Gravity.NO_GRAVITY, px, py);
        } else {
            mPreviewPopup.update(px, py, pw, ph);
        }
    }

    private void hidePreview() {
        if (mPreviewPopup.isShowing()) {
            mPreviewPopup.dismiss();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelLongPress();
        hidePreview();
    }
}
