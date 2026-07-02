package com.pinyin.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;

import java.util.ArrayList;
import java.util.List;

/**
 * 候选词栏：横向滚动的中文候选词列表。
 * 点击某个候选词即提交。
 *
 * 由 HorizontalScrollView + 内部内容视图实现，滚动/惯性由系统负责。
 */
public class CandidateView extends HorizontalScrollView {

    /** 候选词选中回调 */
    public interface OnCandidateSelectedListener {
        void onCandidateSelected(int index, String word);
    }

    private final ContentView mContent;
    private OnCandidateSelectedListener mListener;
    private int mSelectedIndex = 0;

    public CandidateView(Context context) {
        this(context, null);
    }

    public CandidateView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CandidateView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setFillViewport(true);
        setHorizontalScrollBarEnabled(false);
        mContent = new ContentView(context);
        addView(mContent);
    }

    public void setOnCandidateSelectedListener(OnCandidateSelectedListener l) {
        mListener = l;
    }

    /** 设置候选词列表，并滚动到起始位置 */
    public void setCandidates(List<String> candidates, int selectedIndex) {
        mContent.setCandidates(candidates, selectedIndex);
        scrollTo(0, 0);
    }

    public void clear() {
        mContent.setCandidates(new ArrayList<>(), 0);
    }

    /** 实际绘制候选词的内部视图 */
    private final class ContentView extends View {

        private final List<String> mCandidates = new ArrayList<>();
        private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mTextSize;
        private final float mPadding;
        private final int mHeight;

        ContentView(Context context) {
            super(context);
            mTextSize = getResources().getDimension(R.dimen.candidate_text_size);
            mPadding = getResources().getDimension(R.dimen.candidate_padding);
            mHeight = (int) getResources().getDimension(R.dimen.candidate_height);

            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            mSelectedPaint.setTextAlign(Paint.Align.CENTER);
            mSelectedPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            applyThemeColors();
        }

        void applyThemeColors() {
            mTextPaint.setColor(resolveAttr(R.attr.kbCandidateTextColor));
            mSelectedPaint.setColor(resolveAttr(R.attr.kbCandidateSelectedColor));
            mDividerPaint.setColor(resolveAttr(R.attr.kbDividerColor));
            mDividerPaint.setStrokeWidth(1f);
            invalidate();
        }

        private int resolveAttr(int attr) {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                        && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
                if (tv.resourceId != 0) {
                    return getContext().getColor(tv.resourceId);
                }
                return tv.data;
            }
            return android.graphics.Color.BLACK;
        }

        void setCandidates(List<String> candidates, int selectedIndex) {
            mCandidates.clear();
            mCandidates.addAll(candidates);
            CandidateView.this.mSelectedIndex = Math.max(0, Math.min(selectedIndex, candidates.size() - 1));
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = computeTotalWidth();
            int height = mHeight;
            setMeasuredDimension(Math.max(width, MeasureSpec.getSize(widthMeasureSpec)), height);
        }

        private int computeTotalWidth() {
            int width = 0;
            for (String c : mCandidates) {
                width += Math.ceil(mTextPaint.measureText(c)) + 2 * mPadding;
            }
            return width;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mCandidates.isEmpty()) return;
            float x = 0;
            float centerY = mHeight / 2f - (mTextPaint.descent() + mTextPaint.ascent()) / 2f;
            for (int i = 0; i < mCandidates.size(); i++) {
                String word = mCandidates.get(i);
                float w = mTextPaint.measureText(word) + 2 * mPadding;
                boolean selected = (i == CandidateView.this.mSelectedIndex);
                Paint p = selected ? mSelectedPaint : mTextPaint;
                canvas.drawText(word, x + w / 2f, centerY, p);

                // 候选词分隔线
                float lineX = x + w;
                canvas.drawLine(lineX, mHeight * 0.2f, lineX, mHeight * 0.8f, mDividerPaint);
                x += w;
            }
        }

        /** 根据触摸 X 计算命中索引 */
        int indexAt(float x) {
            float cur = 0;
            for (int i = 0; i < mCandidates.size(); i++) {
                float w = mTextPaint.measureText(mCandidates.get(i)) + 2 * mPadding;
                if (x >= cur && x < cur + w) return i;
                cur += w;
            }
            return -1;
        }
    }

    private float mDownX, mDownY;
    private long mDownTime;
    private static final float TAP_SLOP = 24f;
    private static final long TAP_TIMEOUT = 500;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mDownX = event.getX();
            mDownY = event.getY();
            mDownTime = event.getEventTime();
        }
        if (action == MotionEvent.ACTION_UP) {
            float dx = Math.abs(event.getX() - mDownX);
            float dy = Math.abs(event.getY() - mDownY);
            long dt = event.getEventTime() - mDownTime;
            // 视为点击（非滚动）时才提交候选
            if (dx < TAP_SLOP && dy < TAP_SLOP && dt < TAP_TIMEOUT) {
                float x = event.getX() + getScrollX();
                int index = mContent.indexAt(x);
                if (index >= 0 && mListener != null) {
                    mListener.onCandidateSelected(index, mContent.mCandidates.get(index));
                }
            }
        }
        return super.onTouchEvent(event);
    }

    /** 主题切换时刷新颜色 */
    public void refreshTheme() {
        mContent.applyThemeColors();
    }
}
