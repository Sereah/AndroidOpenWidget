package com.lunacattus.numberpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextUtils
import android.text.method.NumberKeyListener
import android.util.AttributeSet
import android.util.SparseArray
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Scroller
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OpenNumberPicker @JvmOverloads constructor(
    private val context: Context,
    private val attributeSet: AttributeSet? = null,
    defStyleAttr: Int = R.style.OpenNumberPicker
) : LinearLayout(context, attributeSet, defStyleAttr) {

    init {
        initAttributeSet()
        setWillNotDraw(false)
        initView()
    }

    /**
     * If true then the selector wheel is hidden until the picker has focus.
     */
    private var mHideWheelUntilFocused = false

    /**
     * The background color used to optimize scroller fading.
     */
    @ColorInt
    private var mSolidColor = Color.TRANSPARENT

    /**
     * Divider for showing item to be selected while scrolling
     */
    private var mSelectionDivider: Drawable? = null

    /**
     * The height of the selection divider.
     */
    private var mSelectionDividerHeight = 0

    /**
     * The distance between the two selection dividers.
     */
    private var mSelectionDividersDistance = 0

    /**
     * The min height of this widget.
     */
    private var mMinHeight = 0

    /**
     * The max height of this widget.
     */
    private var mMaxHeight = 0

    /**
     * The max width of this widget.
     */
    private var mMinWidth = 0

    /**
     * The max width of this widget.
     */
    private var mMaxWidth = 0

    /**
     * The [Drawable] for pressed virtual (increment/decrement) buttons.
     */
    private var mVirtualButtonPressedDrawable: Drawable? = null

    /**
     * Flag whether to compute the max width.
     */
    private var mComputeMaxWidth = false

    /**
     * Helper class for managing pressed state of the virtual buttons.
     */
    private val mPressedStateHelper: PressedStateHelper by lazy { PressedStateHelper() }

    /**
     * Whether the increment virtual button is pressed.
     */
    private var mIncrementVirtualButtonPressed = false

    /**
     * Whether the decrement virtual button is pressed.
     */
    private var mDecrementVirtualButtonPressed = false

    /**
     * The top of the top selection divider.
     */
    private var mTopSelectionDividerTop = 0

    /**
     * The bottom of the bottom selection divider.
     */
    private var mBottomSelectionDividerBottom = 0

    /**
     * Cache for the string representation of selector indices.
     */
    private val mSelectorIndexToStringCache = SparseArray<String>()

    /**
     * The selector indices whose value are show by the selector.
     */
    private val mSelectorIndices = IntArray(SELECTOR_WHEEL_ITEM_COUNT)

    /**
     * @see ViewConfiguration.getScaledTouchSlop
     */
    private var mTouchSlop = 0

    /**
     * @see ViewConfiguration.getScaledMinimumFlingVelocity
     */
    private var mMinimumFlingVelocity = 0

    /**
     * @see ViewConfiguration.getScaledMaximumFlingVelocity
     */
    private var mMaximumFlingVelocity = 0

    /**
     * The [Paint] for drawing the selector.
     */
    private lateinit var mSelectorWheelPaint: Paint

    /**
     * The [Scroller] responsible for flinging the selector.
     */
    private lateinit var mFlingScroller: Scroller

    /**
     * The keycode of the last handled DPAD down event.
     */
    private var mLastHandledDownDpadKeyCode = -1

    /**
     * The [Scroller] responsible for adjusting the selector.
     */
    private lateinit var mAdjustScroller: Scroller

    /**
     * The height of a selector element (text + gap).
     */
    private var mSelectorElementHeight = 0

    /**
     * The initial offset of the scroll selector.
     */
    private var mInitialScrollOffset = 0

    /**
     * The current offset of the scroll selector.
     */
    private var mCurrentScrollOffset = 0

    /**
     * Handle to the reusable command for changing the current value from long
     * press by one.
     */
    private var mChangeCurrentByOneFromLongPressCommand: ChangeCurrentByOneFromLongPressCommand? =
        null

    /**
     * Command for beginning an edit of the current value via IME on long press.
     */
    private var mBeginSoftInputOnLongPressCommand: BeginSoftInputOnLongPressCommand? = null

    /**
     * Handle to the reusable command for setting the input text selection.
     */
    private var mSetSelectionCommand: SetSelectionCommand? = null

    /**
     * The Y position of the last down event.
     */
    private var mLastDownEventY = 0f

    /**
     * The time of the last down event.
     */
    private var mLastDownEventTime: Long = 0

    /**
     * The Y position of the last down or move event.
     */
    private var mLastDownOrMoveEventY = 0f

    /**
     * Flag whether to ignore move events - we ignore such when we show in IME
     * to prevent the content from scrolling.
     */
    private var mIgnoreMoveEvents = false

    /**
     * Determines speed during touch scrolling.
     */
    private var mVelocityTracker: VelocityTracker? = null

    /**
     * Flag whether to perform a click on tap.
     */
    private var mPerformClickOnTap = false

    /**
     * The current scroll state of the number picker.
     */
    private var mScrollState = OnScrollListener.SCROLL_STATE_IDLE

    /**
     * Listener to be notified upon current value change.
     */
    private var mOnValueChangeListener: OnValueChangeListener? = null

    /**
     * Listener to be notified upon scroll state change.
     */
    private var mOnScrollListener: OnScrollListener? = null

    /**
     * The previous Y coordinate while scrolling the selector.
     */
    private var mPreviousScrollerY = 0

    /**
     * The height of the gap between text elements if the selector wheel.
     */
    private var mSelectorTextGapHeight = 0

    /**
     * Formatter for for displaying the current value.
     */
    private var mFormatter: Formatter? = null

    /**
     * User choice on whether the selector wheel should be wrapped.
     */
    private var mWrapSelectorWheelPreferred = true

    /**
     * The speed for updating the value form long press.
     */
    private var mLongPressUpdateInterval: Long = DEFAULT_LONG_PRESS_UPDATE_INTERVAL

    /**
     * The text for showing the current value.
     */
    private lateinit var mInputText: EditText

    /**
     * The values to be displayed instead the indices.
     */
    private var mDisplayedValues: Array<String>? = null

    /**
     * Current value of this NumberPicker
     */
    private var mValue = 0

    /**
     * Lower value of the range of numbers allowed for the NumberPicker
     */
    private var mMinValue = 0

    /**
     * Upper value of the range of numbers allowed for the NumberPicker
     */
    private var mMaxValue = 0

    /**
     * Flag whether the selector should wrap around.
     */
    private var mWrapSelectorWheel = false

    /**
     * The height of the text.
     */
    private var mTextSize = 0f

    /**
     * Returns the value of the picker.
     *
     * @return The value.
     */
    fun getValue(): Int {
        return mValue
    }

    fun getMinValue(): Int {
        return mMinValue
    }

    /**
     * Sets the min value of the picker.
     *
     * @param minValue The min value inclusive.
     *
     * **Note:** The length of the displayed values array
     * set via [.setDisplayedValues] must be equal to the
     * range of selectable numbers which is equal to
     * [.getMaxValue] - [.getMinValue] + 1.
     */
    fun setMinValue(minValue: Int) {
        if (mMinValue == minValue) {
            return
        }
        require(minValue >= 0) { "minValue must be >= 0" }
        mMinValue = minValue
        if (mMinValue > mValue) {
            mValue = mMinValue
        }
        updateWrapSelectorWheel()
        initializeSelectorWheelIndices()
        updateInputTextView()
        tryComputeMaxWidth()
        invalidate()
    }

    /**
     * Returns the max value of the picker.
     *
     * @return The max value.
     */
    fun getMaxValue(): Int {
        return mMaxValue
    }

    /**
     * Sets the max value of the picker.
     *
     * @param maxValue The max value inclusive.
     *
     * **Note:** The length of the displayed values array
     * set via [.setDisplayedValues] must be equal to the
     * range of selectable numbers which is equal to
     * [.getMaxValue] - [.getMinValue] + 1.
     */
    fun setMaxValue(maxValue: Int) {
        if (mMaxValue == maxValue) {
            return
        }
        require(maxValue >= 0) { "maxValue must be >= 0" }
        mMaxValue = maxValue
        if (mMaxValue < mValue) {
            mValue = mMaxValue
        }
        updateWrapSelectorWheel()
        initializeSelectorWheelIndices()
        updateInputTextView()
        tryComputeMaxWidth()
        invalidate()
    }

    fun getDisplayedValues(): Array<String>? {
        return mDisplayedValues
    }

    /**
     * Sets the values to be displayed.
     *
     * @param displayedValues The displayed values.
     *
     * <strong>Note:</strong> The length of the displayed values array
     * must be equal to the range of selectable numbers which is equal to
     * {@link #getMaxValue()} - {@link #getMinValue()} + 1.
     */
    fun setDisplayedValues(displayedValues: Array<String>?) {
        if (mDisplayedValues === displayedValues) {
            return
        }
        mDisplayedValues = displayedValues
        if (mDisplayedValues != null) {
            // Allow text entry rather than strictly numeric entry.
            mInputText.setRawInputType(
                InputType.TYPE_CLASS_TEXT
                        or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            )
        } else {
            mInputText.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        }
        updateInputTextView()
        initializeSelectorWheelIndices()
        tryComputeMaxWidth()
    }

    /**
     * Set the height for the divider that separates the currently selected value from the others.
     * @param height The height to be set
     */
    fun setSelectionDividerHeight(height: Int) {
        mSelectionDividerHeight = height
        invalidate()
    }

    fun getSelectionDividerHeight(): Int {
        return mSelectionDividerHeight
    }

    /**
     * Sets the listener to be notified on change of the current value.
     *
     * @param onValueChangedListener The listener.
     */
    fun setOnValueChangedListener(onValueChangedListener: OnValueChangeListener) {
        mOnValueChangeListener = onValueChangedListener
    }

    /**
     * Set listener to be notified for scroll state changes.
     *
     * @param onScrollListener The listener.
     */
    fun setOnScrollListener(onScrollListener: OnScrollListener) {
        mOnScrollListener = onScrollListener
    }

    /**
     * Set the formatter to be used for formatting the current value.
     * Note: If you have provided alternative values for the values this
     * formatter is never invoked.
     * @param formatter The formatter object. If formatter is `null`,
     * [String.valueOf] will be used.
     * @see .setDisplayedValues
     */
    fun setFormatter(formatter: Formatter) {
        if (formatter === mFormatter) {
            return
        }
        mFormatter = formatter
        initializeSelectorWheelIndices()
        updateInputTextView()
    }

    fun setValue(value: Int) {
        setValueInternal(value, false)
    }

    /**
     * Gets whether the selector wheel wraps when reaching the min/max value.
     *
     * @return True if the selector wheel wraps.
     *
     * @see .getMinValue
     * @see .getMaxValue
     */
    fun getWrapSelectorWheel(): Boolean {
        return mWrapSelectorWheel
    }

    fun setWrapSelectorWheel(wrapSelectorWheel: Boolean) {
        mWrapSelectorWheelPreferred = wrapSelectorWheel
        updateWrapSelectorWheel()
    }

    /**
     * Sets the speed at which the numbers be incremented and decremented when
     * the up and down buttons are long pressed respectively.
     *
     *
     * The default value is 300 ms.
     *
     *
     * @param intervalMillis The speed (in milliseconds) at which the numbers
     * will be incremented and decremented.
     */
    fun setOnLongPressUpdateInterval(intervalMillis: Long) {
        mLongPressUpdateInterval = intervalMillis
    }

    fun setTextColor(@ColorInt color: Int) {
        mInputText.setTextColor(color)
        invalidate()
    }

    fun setTextSize(@FloatRange(from = 0.0, fromInclusive = false) size: Float) {
        mInputText.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        invalidate()
    }


    override fun getTopFadingEdgeStrength(): Float {
        return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH
    }

    override fun getBottomFadingEdgeStrength(): Float {
        return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeAllCallbacks()
    }

    @CallSuper
    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val selectionDivider = mSelectionDivider
        if (selectionDivider != null && selectionDivider.isStateful
            && selectionDivider.setState(drawableState)
        ) {
            invalidateDrawable(selectionDivider)
        }
    }

    @CallSuper
    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        if (mSelectionDivider != null) {
            mSelectionDivider!!.jumpToCurrentState()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val showSelectorWheel = if (mHideWheelUntilFocused) hasFocus() else true
        val x: Float = ((right - left) / 2).toFloat()
        var y = mCurrentScrollOffset.toFloat()

        // draw the virtual buttons pressed state if needed
        if (showSelectorWheel && mVirtualButtonPressedDrawable != null && mScrollState === OnScrollListener.SCROLL_STATE_IDLE) {
            if (mDecrementVirtualButtonPressed) {
                mVirtualButtonPressedDrawable!!.setState(PRESSED_STATE_SET)
                mVirtualButtonPressedDrawable!!.setBounds(0, 0, right, mTopSelectionDividerTop)
                mVirtualButtonPressedDrawable!!.draw(canvas)
            }
            if (mIncrementVirtualButtonPressed) {
                mVirtualButtonPressedDrawable!!.setState(PRESSED_STATE_SET)
                mVirtualButtonPressedDrawable!!.setBounds(
                    0, mBottomSelectionDividerBottom, right,
                    bottom
                )
                mVirtualButtonPressedDrawable!!.draw(canvas)
            }
        }

        // draw the selector wheel
        val selectorIndices = mSelectorIndices
        for (i in selectorIndices.indices) {
            val selectorIndex = selectorIndices[i]
            val scrollSelectorValue = mSelectorIndexToStringCache[selectorIndex]
            // Do not draw the middle item if input is visible since the input
            // is shown only if the wheel is static and it covers the middle
            // item. Otherwise, if the user starts editing the text via the
            // IME they may see a dimmed version of the old value intermixed
            // with the new one.
            if ((showSelectorWheel && i != SELECTOR_MIDDLE_ITEM_INDEX) ||
                (i == SELECTOR_MIDDLE_ITEM_INDEX && mInputText.visibility != VISIBLE)
            ) {
                canvas.drawText(scrollSelectorValue, x, y, mSelectorWheelPaint)
            }
            y += mSelectorElementHeight.toFloat()
        }

        // draw the selection dividers
        if (showSelectorWheel && mSelectionDivider != null) {
            // draw the top divider
            val topOfTopDivider = mTopSelectionDividerTop
            val bottomOfTopDivider = topOfTopDivider + mSelectionDividerHeight
            mSelectionDivider!!.setBounds(0, topOfTopDivider, right, bottomOfTopDivider)
            mSelectionDivider!!.draw(canvas)

            // draw the bottom divider
            val bottomOfBottomDivider = mBottomSelectionDividerBottom
            val topOfBottomDivider = bottomOfBottomDivider - mSelectionDividerHeight
            mSelectionDivider!!.setBounds(0, topOfBottomDivider, right, bottomOfBottomDivider)
            mSelectionDivider!!.draw(canvas)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val inputLeft = (measuredWidth - mInputText.measuredWidth) / 2
        val inputTop = (measuredHeight - mInputText.measuredHeight) / 2
        val inputRight = inputLeft + mInputText.measuredWidth
        val inputBottom = inputTop + mInputText.measuredHeight
        mInputText.layout(inputLeft, inputTop, inputRight, inputBottom)
        if (changed) {
            initializeSelectorWheel()
            initializeFadingEdges()
            mTopSelectionDividerTop =
                (height - mSelectionDividersDistance) / 2 - mSelectionDividerHeight
            mBottomSelectionDividerBottom =
                mTopSelectionDividerTop + 2 * mSelectionDividerHeight + mSelectionDividersDistance
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Try greedily to fit the max width and height.
        val newWidthMeasureSpec = makeMeasureSpec(widthMeasureSpec, mMaxWidth)
        val newHeightMeasureSpec = makeMeasureSpec(heightMeasureSpec, mMaxHeight)
        super.onMeasure(newWidthMeasureSpec, newHeightMeasureSpec)
        // Flag if we are measured with width or height less than the respective min.
        val widthSize: Int = resolveSizeAndStateRespectingMinSize(
            mMinWidth, measuredWidth,
            widthMeasureSpec
        )
        val heightSize: Int = resolveSizeAndStateRespectingMinSize(
            mMinHeight, measuredHeight,
            heightMeasureSpec
        )
        setMeasuredDimension(widthSize, heightSize)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                removeAllCallbacks()
                hideSoftInput()
                mLastDownEventY = event.y
                mLastDownOrMoveEventY = mLastDownEventY
                mLastDownEventTime = event.eventTime
                mIgnoreMoveEvents = false
                mPerformClickOnTap = false
                // Handle pressed state before any state change.
                if (mLastDownEventY < mTopSelectionDividerTop) {
                    if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                        mPressedStateHelper.buttonPressDelayed(BUTTON_DECREMENT)
                    }
                } else if (mLastDownEventY > mBottomSelectionDividerBottom) {
                    if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                        mPressedStateHelper.buttonPressDelayed(BUTTON_INCREMENT)
                    }
                }
                // Make sure we support flinging inside scrollables.
                parent.requestDisallowInterceptTouchEvent(true)
                if (!mFlingScroller.isFinished) {
                    mFlingScroller.forceFinished(true)
                    mAdjustScroller.forceFinished(true)
                    onScrollerFinished(mFlingScroller)
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE)
                } else if (!mAdjustScroller.isFinished) {
                    mFlingScroller.forceFinished(true)
                    mAdjustScroller.forceFinished(true)
                    onScrollerFinished(mAdjustScroller)
                } else if (mLastDownEventY < mTopSelectionDividerTop) {
                    postChangeCurrentByOneFromLongPress(
                        false, ViewConfiguration.getLongPressTimeout().toLong()
                    )
                } else if (mLastDownEventY > mBottomSelectionDividerBottom) {
                    postChangeCurrentByOneFromLongPress(
                        true, ViewConfiguration.getLongPressTimeout().toLong()
                    )
                } else {
                    mPerformClickOnTap = true
                    postBeginSoftInputOnLongPressCommand()
                }
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker?.let { mVelocityTracker ->
            mVelocityTracker.addMovement(event)
            val action = event.actionMasked
            when (action) {
                MotionEvent.ACTION_MOVE -> {
                    if (mIgnoreMoveEvents) {
                        return true
                    }
                    val currentMoveY = event.y
                    if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                        val deltaDownY = abs((currentMoveY - mLastDownEventY).toDouble()).toInt()
                        if (deltaDownY > mTouchSlop) {
                            removeAllCallbacks()
                            onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
                        }
                    } else {
                        val deltaMoveY = ((currentMoveY - mLastDownOrMoveEventY)).toInt()
                        scrollBy(0, deltaMoveY)
                        invalidate()
                    }
                    mLastDownOrMoveEventY = currentMoveY
                }

                MotionEvent.ACTION_UP -> {
                    removeBeginSoftInputCommand()
                    removeChangeCurrentByOneFromLongPress()
                    mPressedStateHelper.cancel()
                    val velocityTracker: VelocityTracker = mVelocityTracker
                    velocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity.toFloat())
                    val initialVelocity = velocityTracker.yVelocity.toInt()
                    if (abs(initialVelocity.toDouble()) > mMinimumFlingVelocity) {
                        fling(initialVelocity)
                        onScrollStateChange(OnScrollListener.SCROLL_STATE_FLING)
                    } else {
                        val eventY = event.y.toInt()
                        val deltaMoveY = abs((eventY - mLastDownEventY).toDouble()).toInt()
                        val deltaTime = event.eventTime - mLastDownEventTime
                        if (deltaMoveY <= mTouchSlop && deltaTime < ViewConfiguration.getTapTimeout()) {
                            if (mPerformClickOnTap) {
                                mPerformClickOnTap = false
                                performClick()
                            } else {
                                val selectorIndexOffset: Int = ((eventY / mSelectorElementHeight)
                                        - SELECTOR_MIDDLE_ITEM_INDEX)
                                if (selectorIndexOffset > 0) {
                                    changeValueByOne(true)
                                    mPressedStateHelper.buttonTapped(BUTTON_INCREMENT)
                                } else if (selectorIndexOffset < 0) {
                                    changeValueByOne(false)
                                    mPressedStateHelper.buttonTapped(BUTTON_DECREMENT)
                                }
                            }
                        } else {
                            ensureScrollWheelAdjusted()
                        }
                        onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE)
                    }
                    mVelocityTracker.recycle()
                    this.mVelocityTracker = null
                }
            }
        }
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> removeAllCallbacks()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (val keyCode = event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> removeAllCallbacks()
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> if (mWrapSelectorWheel || (if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) getValue() < getMaxValue() else getValue() > getMinValue())) {
                        requestFocus()
                        mLastHandledDownDpadKeyCode = keyCode
                        removeAllCallbacks()
                        if (mFlingScroller.isFinished) {
                            changeValueByOne(keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                        }
                        return true
                    }

                    KeyEvent.ACTION_UP -> if (mLastHandledDownDpadKeyCode == keyCode) {
                        mLastHandledDownDpadKeyCode = -1
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTrackballEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> removeAllCallbacks()
        }
        return super.dispatchTrackballEvent(event)
    }

    override fun computeScroll() {
        var scroller = mFlingScroller
        if (scroller.isFinished) {
            scroller = mAdjustScroller
            if (scroller.isFinished) {
                return
            }
        }
        scroller.computeScrollOffset()
        val currentScrollerY = scroller.currY
        if (mPreviousScrollerY == 0) {
            mPreviousScrollerY = scroller.startY
        }
        scrollBy(0, currentScrollerY - mPreviousScrollerY)
        mPreviousScrollerY = currentScrollerY
        if (scroller.isFinished) {
            onScrollerFinished(scroller)
        } else {
            invalidate()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        mInputText.isEnabled = enabled
    }

    override fun scrollBy(x: Int, y: Int) {
        val selectorIndices = mSelectorIndices
        val startScrollOffset = mCurrentScrollOffset
        if (!mWrapSelectorWheel && y > 0 && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] <= mMinValue) {
            mCurrentScrollOffset = mInitialScrollOffset
            return
        }
        if (!mWrapSelectorWheel && y < 0 && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] >= mMaxValue) {
            mCurrentScrollOffset = mInitialScrollOffset
            return
        }
        mCurrentScrollOffset += y
        while (mCurrentScrollOffset - mInitialScrollOffset > mSelectorTextGapHeight) {
            mCurrentScrollOffset -= mSelectorElementHeight
            decrementSelectorIndices(selectorIndices)
            setValueInternal(selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX], true)
            if (!mWrapSelectorWheel && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] <= mMinValue) {
                mCurrentScrollOffset = mInitialScrollOffset
            }
        }
        while (mCurrentScrollOffset - mInitialScrollOffset < -mSelectorTextGapHeight) {
            mCurrentScrollOffset += mSelectorElementHeight
            incrementSelectorIndices(selectorIndices)
            setValueInternal(selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX], true)
            if (!mWrapSelectorWheel && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] >= mMaxValue) {
                mCurrentScrollOffset = mInitialScrollOffset
            }
        }
        if (startScrollOffset != mCurrentScrollOffset) {
            onScrollChanged(0, mCurrentScrollOffset, 0, startScrollOffset)
        }
    }

    override fun computeVerticalScrollOffset(): Int {
        return mCurrentScrollOffset
    }

    override fun computeVerticalScrollRange(): Int {
        return (mMaxValue - mMinValue + 1) * mSelectorElementHeight
    }

    override fun computeVerticalScrollExtent(): Int {
        return height
    }

    override fun getSolidColor(): Int {
        return mSolidColor
    }

    override fun performClick(): Boolean {
        if (!super.performClick()) {
            showSoftInput()
        }
        return true
    }

    override fun performLongClick(): Boolean {
        if (!super.performLongClick()) {
            showSoftInput()
            mIgnoreMoveEvents = true
        }
        return true
    }

    private fun initAttributeSet() {
        context.obtainStyledAttributes(attributeSet, R.styleable.OpenNumberPicker).apply {
            try {
                mHideWheelUntilFocused = getBoolean(
                    R.styleable.OpenNumberPicker_hideWheelUntilFocused,
                    mHideWheelUntilFocused
                )
                mSolidColor = getInt(
                    R.styleable.OpenNumberPicker_solidColor, mSolidColor
                )
                mSelectionDivider = getDrawable(
                    R.styleable.OpenNumberPicker_selectionDivider
                )?.apply {
                    callback = this@OpenNumberPicker
                    layoutDirection = layoutDirection
                    if (isStateful) setState(drawableState)
                }
                val defSelectionDividerHeight = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT.toFloat(),
                    resources.displayMetrics
                ).toInt()
                mSelectionDividerHeight = getDimensionPixelSize(
                    R.styleable.OpenNumberPicker_selectionDividerHeight,
                    defSelectionDividerHeight
                )
                val defSelectionDividerDistance = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE.toFloat(),
                    resources.displayMetrics
                ).toInt()
                mSelectionDividersDistance = getDimensionPixelSize(
                    R.styleable.OpenNumberPicker_selectionDividersDistance,
                    defSelectionDividerDistance
                )
                mMinHeight = getDimensionPixelSize(
                    R.styleable.OpenNumberPicker_internalMinHeight,
                    SIZE_UNSPECIFIED
                )
                mMaxHeight = getDimensionPixelSize(
                    R.styleable.OpenNumberPicker_internalMaxHeight,
                    SIZE_UNSPECIFIED
                )
                mMinWidth = getDimensionPixelSize(
                    R.styleable.OpenNumberPicker_internalMinWidth,
                    SIZE_UNSPECIFIED
                )
                mMaxWidth = getDimensionPixelSize(
                    R.styleable.OpenNumberPicker_internalMaxWidth,
                    SIZE_UNSPECIFIED
                )
                checkSizeUnspecified()
                mComputeMaxWidth = mMaxWidth == SIZE_UNSPECIFIED
                mVirtualButtonPressedDrawable = getDrawable(
                    R.styleable.OpenNumberPicker_virtualButtonPressedDrawable
                )
            } finally {
                recycle()
            }
        }
    }

    private fun checkSizeUnspecified() {
        require(!(mMinHeight != SIZE_UNSPECIFIED && mMaxHeight != SIZE_UNSPECIFIED && mMinHeight < mMaxHeight)) {
            "mMinHeight > mMaxHeight"
        }
        require(!(mMinWidth != SIZE_UNSPECIFIED && mMaxWidth != SIZE_UNSPECIFIED && mMinWidth < mMaxWidth)) {
            "mMinWidth > mMaxWidth"
        }
    }

    private fun initView() {
        val inflater =
            getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.open_number_picker, this, true)
        mInputText = findViewById(R.id.numberpicker_input)
        mInputText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                mInputText.selectAll()
            } else {
                mInputText.setSelection(0, 0)
                validateInputTextView(v)
            }
        }
        mInputText.filters = arrayOf<InputFilter>(InputTextFilter())
        mInputText.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        mInputText.imeOptions = EditorInfo.IME_ACTION_DONE

        ViewConfiguration.get(context).apply {
            mTouchSlop = scaledTouchSlop
            mMinimumFlingVelocity = scaledMinimumFlingVelocity
            mMaximumFlingVelocity =
                (scaledMaximumFlingVelocity / SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT)
        }
        mTextSize = mInputText.textSize

        mSelectorWheelPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Align.CENTER
            textSize = mTextSize
            setTypeface(mInputText.typeface)
            color = mInputText.textColors.getColorForState(ENABLED_STATE_SET, Color.BLACK)
        }

        mFlingScroller = Scroller(getContext(), null, true)
        mAdjustScroller = Scroller(getContext(), DecelerateInterpolator(2.5f))

        updateInputTextView()

        if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        if (focusable == FOCUSABLE_AUTO) {
            focusable = FOCUSABLE
            isFocusableInTouchMode = true
        }
    }

    private fun makeMeasureSpec(measureSpec: Int, maxSize: Int): Int {
        if (maxSize == SIZE_UNSPECIFIED) {
            return measureSpec
        }
        val size = MeasureSpec.getSize(measureSpec)
        return when (val mode = MeasureSpec.getMode(measureSpec)) {
            MeasureSpec.EXACTLY -> measureSpec
            MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(
                min(
                    size.toDouble(),
                    maxSize.toDouble()
                ).toInt(), MeasureSpec.EXACTLY
            )

            MeasureSpec.UNSPECIFIED -> MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.EXACTLY)
            else -> throw IllegalArgumentException("Unknown measure mode: $mode")
        }
    }

    private fun updateWrapSelectorWheel() {
        val wrappingAllowed: Boolean = (mMaxValue - mMinValue) >= mSelectorIndices.size
        mWrapSelectorWheel = wrappingAllowed && mWrapSelectorWheelPreferred
    }

    private fun incrementSelectorIndices(selectorIndices: IntArray) {
        for (i in 0..<selectorIndices.size - 1) {
            selectorIndices[i] = selectorIndices[i + 1]
        }
        var nextScrollSelectorIndex = selectorIndices[selectorIndices.size - 2] + 1
        if (mWrapSelectorWheel && nextScrollSelectorIndex > mMaxValue) {
            nextScrollSelectorIndex = mMinValue
        }
        selectorIndices[selectorIndices.size - 1] = nextScrollSelectorIndex
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex)
    }

    private fun showSoftInput() {
        val inputMethodManager =
            getContext().getSystemService(InputMethodManager::class.java)
        if (inputMethodManager != null) {
            mInputText.visibility = VISIBLE
            mInputText.requestFocus()
            inputMethodManager.showSoftInput(mInputText, 0)
        }
    }

    private fun setValueInternal(cur: Int, notifyChange: Boolean) {
        var current = cur
        if (mValue == current) {
            return
        }
        // Wrap around the values if we go past the start or end
        if (mWrapSelectorWheel) {
            current = getWrappedSelectorIndex(current)
        } else {
            current = max(current.toDouble(), mMinValue.toDouble()).toInt()
            current = min(current.toDouble(), mMaxValue.toDouble()).toInt()
        }
        val previous = mValue
        mValue = current
        // If we're flinging, we'll update the text view at the end when it becomes visible
        if (mScrollState != OnScrollListener.SCROLL_STATE_FLING) {
            updateInputTextView()
        }
        if (notifyChange) {
            notifyChange(previous)
        }
        initializeSelectorWheelIndices()
        invalidate()
    }

    private fun notifyChange(previous: Int) {
        mOnValueChangeListener?.onValueChange(this, previous, mValue)
    }

    private fun decrementSelectorIndices(selectorIndices: IntArray) {
        for (i in selectorIndices.size - 1 downTo 1) {
            selectorIndices[i] = selectorIndices[i - 1]
        }
        var nextScrollSelectorIndex = selectorIndices[1] - 1
        if (mWrapSelectorWheel && nextScrollSelectorIndex < mMinValue) {
            nextScrollSelectorIndex = mMaxValue
        }
        selectorIndices[0] = nextScrollSelectorIndex
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex)
    }

    private fun ensureCachedScrollSelectorValue(selectorIndex: Int) {
        val cache: SparseArray<String> = mSelectorIndexToStringCache
        var scrollSelectorValue = cache[selectorIndex]
        if (scrollSelectorValue != null) {
            return
        }
        if (selectorIndex < mMinValue || selectorIndex > mMaxValue) {
            scrollSelectorValue = ""
        } else {
            if (mDisplayedValues != null) {
                val displayedValueIndex = selectorIndex - mMinValue
                scrollSelectorValue = mDisplayedValues!![displayedValueIndex]
            } else {
                scrollSelectorValue = formatNumber(selectorIndex)
            }
        }
        cache.put(selectorIndex, scrollSelectorValue)
    }

    private fun formatNumber(value: Int): String? {
        return if (mFormatter != null) mFormatter!!.format(value) else formatNumberWithLocale(value)
    }

    private fun formatNumberWithLocale(value: Int): String {
        return String.format(Locale.getDefault(), "%d", value)
    }

    private fun changeValueByOne(increment: Boolean) {
        hideSoftInput()
        if (!moveToFinalScrollerPosition(mFlingScroller)) {
            moveToFinalScrollerPosition(mAdjustScroller)
        }
        mPreviousScrollerY = 0
        if (increment) {
            mFlingScroller.startScroll(0, 0, 0, -mSelectorElementHeight, SNAP_SCROLL_DURATION)
        } else {
            mFlingScroller.startScroll(0, 0, 0, mSelectorElementHeight, SNAP_SCROLL_DURATION)
        }
        invalidate()
    }

    private fun fling(velocityY: Int) {
        mPreviousScrollerY = 0

        if (velocityY > 0) {
            mFlingScroller.fling(0, 0, 0, velocityY, 0, 0, 0, Int.MAX_VALUE)
        } else {
            mFlingScroller.fling(0, Int.MAX_VALUE, 0, velocityY, 0, 0, 0, Int.MAX_VALUE)
        }

        invalidate()
    }

    private fun removeBeginSoftInputCommand() {
        if (mBeginSoftInputOnLongPressCommand != null) {
            removeCallbacks(mBeginSoftInputOnLongPressCommand)
        }
    }

    private fun removeChangeCurrentByOneFromLongPress() {
        if (mChangeCurrentByOneFromLongPressCommand != null) {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand)
        }
    }

    private fun postBeginSoftInputOnLongPressCommand() {
        if (mBeginSoftInputOnLongPressCommand == null) {
            mBeginSoftInputOnLongPressCommand = BeginSoftInputOnLongPressCommand()
        } else {
            removeCallbacks(mBeginSoftInputOnLongPressCommand)
        }
        postDelayed(
            mBeginSoftInputOnLongPressCommand,
            ViewConfiguration.getLongPressTimeout().toLong()
        )
    }

    private fun postChangeCurrentByOneFromLongPress(increment: Boolean, delayMillis: Long) {
        if (mChangeCurrentByOneFromLongPressCommand == null) {
            mChangeCurrentByOneFromLongPressCommand = ChangeCurrentByOneFromLongPressCommand()
        } else {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand)
        }
        mChangeCurrentByOneFromLongPressCommand?.setStep(increment)
        postDelayed(mChangeCurrentByOneFromLongPressCommand, delayMillis)
    }

    private fun onScrollerFinished(scroller: Scroller) {
        if (scroller === mFlingScroller) {
            ensureScrollWheelAdjusted()
            updateInputTextView()
            onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE)
        } else {
            if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                updateInputTextView()
            }
        }
    }

    private fun onScrollStateChange(scrollState: Int) {
        if (mScrollState == scrollState) {
            return
        }
        mScrollState = scrollState
        mOnScrollListener?.onScrollStateChange(this, scrollState)
    }

    private fun ensureScrollWheelAdjusted(): Boolean {
        // adjust to the closest value
        var deltaY = mInitialScrollOffset - mCurrentScrollOffset
        if (deltaY != 0) {
            mPreviousScrollerY = 0
            if (abs(deltaY.toDouble()) > mSelectorElementHeight / 2) {
                deltaY += if (deltaY > 0) -mSelectorElementHeight else mSelectorElementHeight
            }
            mAdjustScroller!!.startScroll(0, 0, 0, deltaY, SELECTOR_ADJUSTMENT_DURATION_MILLIS)
            invalidate()
            return true
        }
        return false
    }

    private fun hideSoftInput() {
        val inputMethodManager =
            getContext().getSystemService(InputMethodManager::class.java)
        if (inputMethodManager != null && inputMethodManager.isActive(mInputText)) {
            inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
        }
        mInputText.visibility = INVISIBLE
    }

    private fun tryComputeMaxWidth() {
        if (!mComputeMaxWidth) {
            return
        }
        var maxTextWidth = 0
        if (mDisplayedValues == null) {
            var maxDigitWidth = 0f
            for (i in 0..9) {
                val digitWidth = mSelectorWheelPaint.measureText(formatNumberWithLocale(i))
                if (digitWidth > maxDigitWidth) {
                    maxDigitWidth = digitWidth
                }
            }
            var numberOfDigits = 0
            var current = mMaxValue
            while (current > 0) {
                numberOfDigits++
                current /= 10
            }
            maxTextWidth = (numberOfDigits * maxDigitWidth).toInt()
        } else {
            val valueCount: Int = mDisplayedValues!!.size
            for (i in 0..<valueCount) {
                val textWidth = mSelectorWheelPaint.measureText(mDisplayedValues!![i])
                if (textWidth > maxTextWidth) {
                    maxTextWidth = textWidth.toInt()
                }
            }
        }
        maxTextWidth += mInputText.paddingLeft + mInputText.paddingRight
        if (mMaxWidth != maxTextWidth) {
            mMaxWidth = if (maxTextWidth > mMinWidth) {
                maxTextWidth
            } else {
                mMinWidth
            }
            invalidate()
        }
    }

    private fun moveToFinalScrollerPosition(scroller: Scroller): Boolean {
        scroller.forceFinished(true)
        var amountToScroll = scroller.finalY - scroller.currY
        val futureScrollOffset: Int =
            (mCurrentScrollOffset + amountToScroll) % mSelectorElementHeight
        var overshootAdjustment = mInitialScrollOffset - futureScrollOffset
        if (overshootAdjustment != 0) {
            if (abs(overshootAdjustment.toDouble()) > mSelectorElementHeight / 2) {
                if (overshootAdjustment > 0) {
                    overshootAdjustment -= mSelectorElementHeight
                } else {
                    overshootAdjustment += mSelectorElementHeight
                }
            }
            amountToScroll += overshootAdjustment
            scrollBy(0, amountToScroll)
            return true
        }
        return false
    }

    private fun postSetSelectionCommand(selectionStart: Int, selectionEnd: Int) {
        if (mSetSelectionCommand == null) {
            mSetSelectionCommand = SetSelectionCommand(mInputText)
        }
        mSetSelectionCommand?.post(selectionStart, selectionEnd)
    }

    private fun removeAllCallbacks() {
        if (mChangeCurrentByOneFromLongPressCommand != null) {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand)
        }
        mSetSelectionCommand?.cancel()
        if (mBeginSoftInputOnLongPressCommand != null) {
            removeCallbacks(mBeginSoftInputOnLongPressCommand)
        }
        mPressedStateHelper.cancel()
    }

    private fun resolveSizeAndStateRespectingMinSize(
        minSize: Int, measuredSize: Int, measureSpec: Int
    ): Int {
        if (minSize != SIZE_UNSPECIFIED) {
            val desiredWidth = max(minSize.toDouble(), measuredSize.toDouble()).toInt()
            return resolveSizeAndState(desiredWidth, measureSpec, 0)
        } else {
            return measuredSize
        }
    }

    private fun initializeSelectorWheel() {
        initializeSelectorWheelIndices()
        val selectorIndices = mSelectorIndices
        val totalTextHeight = (selectorIndices.size * mTextSize).toInt()
        val totalTextGapHeight: Float = ((bottom - top) - totalTextHeight).toFloat()
        val textGapCount = selectorIndices.size.toFloat()
        mSelectorTextGapHeight = (totalTextGapHeight / textGapCount + 0.5f).toInt()
        mSelectorElementHeight = (mTextSize + mSelectorTextGapHeight).toInt()


        // Ensure that the middle item is positioned the same as the text in
        // mInputText
        val editTextTextPosition = mInputText.baseline + mInputText.top
        mInitialScrollOffset = (editTextTextPosition
                - (mSelectorElementHeight * SELECTOR_MIDDLE_ITEM_INDEX))
        mCurrentScrollOffset = mInitialScrollOffset
        updateInputTextView()
    }

    private fun initializeFadingEdges() {
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(((bottom - top - mTextSize) / 2).toInt())
    }

    private fun initializeSelectorWheelIndices() {
        mSelectorIndexToStringCache.clear()
        val selectorIndices = mSelectorIndices
        val current = getValue()
        for (i in mSelectorIndices.indices) {
            var selectorIndex: Int = current + (i - SELECTOR_MIDDLE_ITEM_INDEX)
            if (mWrapSelectorWheel) {
                selectorIndex = getWrappedSelectorIndex(selectorIndex)
            }
            selectorIndices[i] = selectorIndex
            ensureCachedScrollSelectorValue(selectorIndices[i])
        }
    }

    private fun getWrappedSelectorIndex(selectorIndex: Int): Int {
        if (selectorIndex > mMaxValue) {
            return mMinValue + (selectorIndex - mMaxValue) % (mMaxValue - mMinValue) - 1;
        } else if (selectorIndex < mMinValue) {
            return mMaxValue - (mMinValue - selectorIndex) % (mMaxValue - mMinValue) + 1;
        }
        return selectorIndex;
    }

    private fun validateInputTextView(v: View) {
        val str = (v as TextView).text.toString()
        if (TextUtils.isEmpty(str)) {
            // Restore to the old value as we don't allow empty values
            updateInputTextView()
        } else {
            // Check the new value and ensure it's in range
            val current: Int = getSelectedPos(str.toString())
            setValueInternal(current, true)
        }
    }

    private fun getSelectedPos(va: String): Int {
        var value = va
        if (mDisplayedValues == null) {
            try {
                return value.toInt()
            } catch (e: NumberFormatException) {
                // Ignore as if it's not a number we don't care
            }
        } else {
            for (i in mDisplayedValues!!.indices) {
                // Don't force the user to type in jan when ja will do
                value = value.lowercase(Locale.getDefault())
                if (mDisplayedValues!![i].toLowerCase().startsWith(value)) {
                    return mMinValue + i
                }
            }

            /*
             * The user might have typed in a number into the month field i.e.
             * 10 instead of OCT so support that too.
             */
            try {
                return value.toInt()
            } catch (e: NumberFormatException) {
                // Ignore as if it's not a number we don't care
            }
        }
        return mMinValue
    }

    private fun updateInputTextView(): Boolean {
        val text = if (mDisplayedValues == null)
            formatNumber(mValue)
        else
            mDisplayedValues!![mValue - mMinValue]
        if (!TextUtils.isEmpty(text)) {
            val beforeText: CharSequence = mInputText.text
            if (text != beforeText.toString()) {
                mInputText.setText(text)
                return true
            }
        }
        return false
    }

    class CustomEditText(context: Context, attributeSet: AttributeSet) :
        androidx.appcompat.widget.AppCompatEditText(context, attributeSet) {

        override fun onEditorAction(actionCode: Int) {
            super.onEditorAction(actionCode)
            if (actionCode == EditorInfo.IME_ACTION_DONE) {
                clearFocus()
            }
        }
    }

    inner class PressedStateHelper : Runnable {
        private val mModePress = 1
        private val mModeTapped = 2
        private var mManagedButton = 0
        private var mMode = 0

        fun cancel() {
            mMode = 0
            mManagedButton = 0
            this@OpenNumberPicker.removeCallbacks(this)
            if (mIncrementVirtualButtonPressed) {
                mIncrementVirtualButtonPressed = false
                invalidate(0, mBottomSelectionDividerBottom, right, bottom)
            }
            if (mDecrementVirtualButtonPressed) {
                mDecrementVirtualButtonPressed = false
                invalidate(0, 0, right, mTopSelectionDividerTop)
            }
        }

        fun buttonPressDelayed(button: Int) {
            cancel()
            mMode = mModePress
            mManagedButton = button
            this@OpenNumberPicker.postDelayed(this, ViewConfiguration.getTapTimeout().toLong())
        }

        fun buttonTapped(button: Int) {
            cancel()
            mMode = mModeTapped
            mManagedButton = button
            this@OpenNumberPicker.post(this)
        }

        override fun run() {
            when (mMode) {
                mModePress -> {
                    when (mManagedButton) {
                        BUTTON_INCREMENT -> {
                            mIncrementVirtualButtonPressed = true
                            invalidate(0, mBottomSelectionDividerBottom, right, bottom)
                        }

                        BUTTON_DECREMENT -> {
                            mDecrementVirtualButtonPressed = true
                            invalidate(0, 0, right, mTopSelectionDividerTop)
                        }
                    }
                }

                mModeTapped -> {
                    when (mManagedButton) {
                        BUTTON_INCREMENT -> {
                            if (!mIncrementVirtualButtonPressed) {
                                this@OpenNumberPicker.postDelayed(
                                    this,
                                    ViewConfiguration.getPressedStateDuration().toLong()
                                )
                            }
                            mIncrementVirtualButtonPressed = mIncrementVirtualButtonPressed xor true
                            invalidate(0, mBottomSelectionDividerBottom, right, bottom)
                        }

                        BUTTON_DECREMENT -> {
                            if (!mDecrementVirtualButtonPressed) {
                                this@OpenNumberPicker.postDelayed(
                                    this,
                                    ViewConfiguration.getPressedStateDuration().toLong()
                                )
                            }
                            mDecrementVirtualButtonPressed = mDecrementVirtualButtonPressed xor true
                            invalidate(0, 0, right, mTopSelectionDividerTop)
                        }
                    }
                }
            }
        }
    }

    inner class InputTextFilter : NumberKeyListener() {
        override fun getInputType(): Int {
            return InputType.TYPE_CLASS_TEXT
        }

        override fun getAcceptedChars(): CharArray {
            return DIGIT_CHARACTERS
        }

        override fun filter(
            source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int
        ): CharSequence {
            // We don't know what the output will be, so always cancel any
            // pending set selection command.
            if (mSetSelectionCommand != null) {
                mSetSelectionCommand!!.cancel()
            }

            if (mDisplayedValues == null) {
                var filtered = super.filter(source, start, end, dest, dstart, dend)
                if (filtered == null) {
                    filtered = source.subSequence(start, end)
                }

                val result = (dest.subSequence(0, dstart).toString() + filtered
                        + dest.subSequence(dend, dest.length))

                if ("" == result) {
                    return result
                }
                val `val` = getSelectedPos(result)

                /*
                 * Ensure the user can't type in a value greater than the max
                 * allowed. We have to allow less than min as the user might
                 * want to delete some numbers and then type a new number.
                 * And prevent multiple-"0" that exceeds the length of upper
                 * bound number.
                 */
                return if (`val` > mMaxValue || result.length > mMaxValue.toString().length) {
                    ""
                } else {
                    filtered
                }
            } else {
                val filtered: CharSequence = source.subSequence(start, end).toString()
                if (TextUtils.isEmpty(filtered)) {
                    return ""
                }
                val result = (dest.subSequence(0, dstart).toString() + filtered
                        + dest.subSequence(dend, dest.length))
                val str = result.lowercase(Locale.getDefault())
                for (`val` in mDisplayedValues!!) {
                    val valLowerCase = `val`.lowercase(Locale.getDefault())
                    if (valLowerCase.startsWith(str)) {
                        postSetSelectionCommand(result.length, `val`.length)
                        return `val`.subSequence(dstart, `val`.length)
                    }
                }
                return ""
            }
        }
    }

    inner class ChangeCurrentByOneFromLongPressCommand : Runnable {
        private var mIncrement = false

        internal fun setStep(increment: Boolean) {
            mIncrement = increment
        }

        override fun run() {
            changeValueByOne(mIncrement)
            postDelayed(this, mLongPressUpdateInterval)
        }
    }

    inner class BeginSoftInputOnLongPressCommand : Runnable {
        override fun run() {
            performLongClick()
        }
    }

    private inner class SetSelectionCommand(private val mInputText: EditText) : Runnable {
        private var mSelectionStart = 0
        private var mSelectionEnd = 0

        /** Whether this runnable is currently posted.  */
        private var mPosted = false

        fun post(selectionStart: Int, selectionEnd: Int) {
            mSelectionStart = selectionStart
            mSelectionEnd = selectionEnd

            if (!mPosted) {
                mInputText.post(this)
                mPosted = true
            }
        }

        fun cancel() {
            if (mPosted) {
                mInputText.removeCallbacks(this)
                mPosted = false
            }
        }

        override fun run() {
            mPosted = false
            mInputText.setSelection(mSelectionStart, mSelectionEnd)
        }
    }

    interface OnScrollListener {
        @IntDef(value = [SCROLL_STATE_IDLE, SCROLL_STATE_TOUCH_SCROLL, SCROLL_STATE_FLING])
        annotation class ScrollState

        /**
         * Callback invoked while the number picker scroll state has changed.
         *
         * @param view The view whose scroll state is being reported.
         * @param scrollState The current scroll state. One of
         * [.SCROLL_STATE_IDLE],
         * [.SCROLL_STATE_TOUCH_SCROLL] or
         * [.SCROLL_STATE_IDLE].
         */
        fun onScrollStateChange(view: OpenNumberPicker?, @ScrollState scrollState: Int)

        companion object {
            /**
             * The view is not scrolling.
             */
            const val SCROLL_STATE_IDLE: Int = 0

            /**
             * The user is scrolling using touch, and their finger is still on the screen.
             */
            const val SCROLL_STATE_TOUCH_SCROLL: Int = 1

            /**
             * The user had previously been scrolling using touch and performed a fling.
             */
            const val SCROLL_STATE_FLING: Int = 2
        }
    }

    /**
     * Interface to listen for changes of the current value.
     */
    interface OnValueChangeListener {
        /**
         * Called upon a change of the current value.
         *
         * @param picker The NumberPicker associated with this listener.
         * @param oldVal The previous value.
         * @param newVal The new value.
         */
        fun onValueChange(picker: OpenNumberPicker?, oldVal: Int, newVal: Int)
    }

    interface Formatter {
        /**
         * Formats a string representation of the current value.
         *
         * @param value The currently selected value.
         * @return A formatted string representation.
         */
        fun format(value: Int): String?
    }

    companion object {
        private const val BUTTON_INCREMENT: Int = 1
        private const val BUTTON_DECREMENT: Int = 2

        /**
         * The default unscaled height of the selection divider.
         */
        private const val UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT: Int = 2

        /**
         * The default unscaled distance between the selection dividers.
         */
        private const val UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE: Int = 48

        /**
         * Constant for unspecified size.
         */
        private const val SIZE_UNSPECIFIED: Int = -1

        /**
         * The number of items show in the selector wheel.
         */
        private const val SELECTOR_WHEEL_ITEM_COUNT: Int = 3

        /**
         * The index of the middle selector item.
         */
        private const val SELECTOR_MIDDLE_ITEM_INDEX: Int = SELECTOR_WHEEL_ITEM_COUNT / 2

        /**
         * The coefficient by which to adjust (divide) the max fling velocity.
         */
        private const val SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT: Int = 8

        /**
         * The the duration for adjusting the selector wheel.
         */
        private const val SELECTOR_ADJUSTMENT_DURATION_MILLIS: Int = 800

        /**
         * The duration of scrolling while snapping to a given position.
         */
        private const val SNAP_SCROLL_DURATION: Int = 300

        /**
         * The default update interval during long press.
         */
        private const val DEFAULT_LONG_PRESS_UPDATE_INTERVAL: Long = 300

        /**
         * The strength of fading in the top and bottom while drawing the selector.
         */
        private const val TOP_AND_BOTTOM_FADING_EDGE_STRENGTH: Float = 0.9f

        private val DIGIT_CHARACTERS: CharArray = charArrayOf( // Latin digits are the common case
            '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',  // Arabic-Indic
            '\u0660',
            '\u0661',
            '\u0662',
            '\u0663',
            '\u0664',
            '\u0665',
            '\u0666',
            '\u0667',
            '\u0668',
            '\u0669',  // Extended Arabic-Indic
            '\u06f0',
            '\u06f1',
            '\u06f2',
            '\u06f3',
            '\u06f4',
            '\u06f5',
            '\u06f6',
            '\u06f7',
            '\u06f8',
            '\u06f9',  // Hindi and Marathi (Devanagari script)
            '\u0966',
            '\u0967',
            '\u0968',
            '\u0969',
            '\u096a',
            '\u096b',
            '\u096c',
            '\u096d',
            '\u096e',
            '\u096f',  // Bengali
            '\u09e6',
            '\u09e7',
            '\u09e8',
            '\u09e9',
            '\u09ea',
            '\u09eb',
            '\u09ec',
            '\u09ed',
            '\u09ee',
            '\u09ef',  // Kannada
            '\u0ce6',
            '\u0ce7',
            '\u0ce8',
            '\u0ce9',
            '\u0cea',
            '\u0ceb',
            '\u0cec',
            '\u0ced',
            '\u0cee',
            '\u0cef'
        )
    }
}