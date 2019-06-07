package me.kaine119.timeinwords

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.content.ContextCompat
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.view.WindowInsets


import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class WordWatchFace : CanvasWatchFaceService() {

    companion object {
        private val NORMAL_TYPEFACE = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)

        /**
         * Updates rate in milliseconds for interactive mode. We update once a second since seconds
         * are displayed in interactive mode.
         */
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0

        private val ALL_HOURS = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven")
        private val ALL_ONES = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
        private val ALL_TEENS = listOf("ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
        private val ALL_TENS = listOf("zero", "one", "Twenty", "Thirty", "Forty", "Fifty")
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: WordWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<WordWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false

        private var mMinuteYOffset = 0F
        private var mHourYOffset= 0f
        private var mFullTimeYOffset = 0f

        private var mWidth: Int = 0
        private var mHeight: Int = 0

        private var mXCenter: Float = 0f
        private var mYCenter: Float = 0f

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mHoursTextPaint: Paint
        private lateinit var mJoinerTextPaint: Paint
        private lateinit var mMinutesTextPaint: Paint
        private lateinit var mFullTimeTextPaint: Paint

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false
        private var mAmbient: Boolean = false

        private val mUpdateTimeHandler: Handler = EngineHandler(this)

        private val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@WordWatchFace)
                    .build())

            mCalendar = Calendar.getInstance()


            val resources = this@WordWatchFace.resources

            // Initializes background.
            mBackgroundPaint = Paint().apply {
                color = ContextCompat.getColor(applicationContext, R.color.background)
            }

            mHoursTextPaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                isAntiAlias = true
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
                textAlign = Paint.Align.CENTER
            }

            mJoinerTextPaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                isAntiAlias = true
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
                textAlign = Paint.Align.CENTER
            }

            mMinutesTextPaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                isAntiAlias = true
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
                textAlign = Paint.Align.CENTER
            }

            mFullTimeTextPaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                isAntiAlias = true
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
                textAlign = Paint.Align.CENTER
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            mWidth = width
            mHeight = height

            mXCenter = mWidth / 2f
            mYCenter = mHeight / 2f

            mMinuteYOffset = mHeight * 0.1f
            mHourYOffset = mHeight * 0.2f
            mFullTimeYOffset = mHeight * 0.34f


        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            if (mLowBitAmbient) {
                mHoursTextPaint.isAntiAlias = !inAmbientMode
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }


        override fun onDraw(canvas: Canvas, bounds: Rect) {
            // Draw the background.
            if (mAmbient) {
                canvas.drawColor(Color.BLACK)
            } else {
                canvas.drawRect(
                        0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), mBackgroundPaint)
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            val time = parseTime(mCalendar)
            canvas.drawText(time.hour, mXCenter, mYCenter + mHourYOffset, mHoursTextPaint)
            canvas.drawText(time.joiner, mXCenter, mYCenter, mJoinerTextPaint)
            canvas.drawText(time.minute, mXCenter, mYCenter - mMinuteYOffset, mMinutesTextPaint)
            val hour = mCalendar.get(Calendar.HOUR)
            val minute = mCalendar.get(Calendar.MINUTE)
            val amPM = if (mCalendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
            val fullTimeString = String.format("%d:%02d %s", if (hour == 0) 12 else hour, minute, amPM)
            canvas.drawText(fullTimeString, mXCenter, mYCenter + mFullTimeYOffset,
                    mFullTimeTextPaint)
        }

        inner class ParsedTime(val hour: String, val joiner: String, val minute: String)

        private fun parseTime(calendar: Calendar): ParsedTime {
            var hours = calendar.get(Calendar.HOUR)
            val amPm = calendar.get(Calendar.AM_PM)
            var mins = calendar.get(Calendar.MINUTE)
            var joiner = "past"
            if (mins > 30) {
                joiner = "to"
                mins = 60 - mins
                hours = (hours + 1) % 12
            }
            val hourString =
                if (hours == 0 && amPm == Calendar.AM) {
                    "Midnight"
                } else if (hours == 0 && amPm == Calendar.PM) {
                    "Noon"
                } else {
                    ALL_HOURS[hours]
                }



            val minString = when (mins) {
                0 -> "Nothing"
                in 1..10 -> ALL_ONES[mins]
                in 11..19 -> ALL_TEENS[mins - 10]
                else ->
                    if (mins % 10 == 0) ALL_TENS[Math.floorDiv(mins, 10)]
                    else ALL_TENS[Math.floorDiv(mins, 10)] + "-" + ALL_ONES[mins % 10]
            }

            return ParsedTime(hourString.toLowerCase(), joiner, minString)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()

                // Update time zone in case it changed while we weren't visible.
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@WordWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@WordWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            // Load resources that have alternate values for round watches.
            val resources = this@WordWatchFace.resources
            val isRound = insets.isRound



            if (isRound) {
                mHoursTextPaint.textSize = resources.getDimension(R.dimen.digital_hours_text_size_round)
                mJoinerTextPaint.textSize = resources.getDimension(R.dimen.digital_joiner_text_size_round)
                mMinutesTextPaint.textSize = resources.getDimension(R.dimen.digital_minutes_text_size_round)
                mFullTimeTextPaint.textSize = resources.getDimension(R.dimen.digital_full_time_text_size_round)
            } else {
                mHoursTextPaint.textSize = resources.getDimension(R.dimen.digital_hours_text_size)
                mJoinerTextPaint.textSize = resources.getDimension(R.dimen.digital_joiner_text_size)
                mMinutesTextPaint.textSize = resources.getDimension(R.dimen.digital_minutes_text_size)
                mFullTimeTextPaint.textSize = resources.getDimension(R.dimen.digital_full_time_text_size)
            }

        }

        /**
         * Starts the [.mUpdateTimeHandler] timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
