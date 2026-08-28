package com.geckoflux.speed

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.geckoflux.R
import java.util.Locale

/**
 * Native Material 3 Bottom Sheet for Precision Video Speed Control.
 */
class SpeedControllerBottomSheet(
    private var initialSpeed: Float = 1.0f,
    private val onSpeedSelected: (Float) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var tvCurrentSpeedBadge: TextView
    private lateinit var tvSliderVal: TextView
    private lateinit var sliderSpeed: Slider
    private lateinit var chipGroupPresets: ChipGroup
    private lateinit var btnReset: ImageButton
    private lateinit var btnClose: ImageButton

    private var currentSpeed: Float = initialSpeed
    private var isUpdatingFromChip = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_speed_controller, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCurrentSpeedBadge = view.findViewById(R.id.tv_current_speed_badge)
        tvSliderVal = view.findViewById(R.id.tv_slider_val)
        sliderSpeed = view.findViewById(R.id.slider_speed)
        chipGroupPresets = view.findViewById(R.id.chip_group_presets)
        btnReset = view.findViewById(R.id.btn_reset_speed)
        btnClose = view.findViewById(R.id.btn_close_sheet)

        currentSpeed = initialSpeed

        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        updateSpeedDisplays(currentSpeed)
        sliderSpeed.value = currentSpeed.coerceIn(0.25f, 4.00f)
        highlightMatchingChip(currentSpeed)
    }

    private fun setupListeners() {
        // Slider fine-tuning listener
        sliderSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentSpeed = (Math.round(value * 20.0) / 20.0).toFloat() // Round to 0.05
                updateSpeedDisplays(currentSpeed)
                if (!isUpdatingFromChip) {
                    highlightMatchingChip(currentSpeed)
                }
                triggerHapticFeedback()
                onSpeedSelected(currentSpeed)
            }
        }

        // Preset Chips Listener
        chipGroupPresets.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                if (chip != null) {
                    val parsedSpeed = parseSpeedFromChipText(chip.text.toString())
                    if (parsedSpeed != null) {
                        isUpdatingFromChip = true
                        currentSpeed = parsedSpeed
                        sliderSpeed.value = currentSpeed.coerceIn(0.25f, 4.00f)
                        updateSpeedDisplays(currentSpeed)
                        triggerHapticFeedback()
                        onSpeedSelected(currentSpeed)
                        isUpdatingFromChip = false
                    }
                }
            }
        }

        // Reset to 1.0x Button
        btnReset.setOnClickListener {
            currentSpeed = 1.0f
            sliderSpeed.value = 1.0f
            updateSpeedDisplays(currentSpeed)
            highlightMatchingChip(1.0f)
            triggerHapticFeedback()
            onSpeedSelected(1.0f)
        }

        // Close Button
        btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun updateSpeedDisplays(speed: Float) {
        val formatted = String.format(Locale.US, "%.2fx", speed)
        tvCurrentSpeedBadge.text = formatted
        tvSliderVal.text = formatted
    }

    private fun highlightMatchingChip(speed: Float) {
        for (i in 0 until chipGroupPresets.childCount) {
            val child = chipGroupPresets.getChildAt(i) as? Chip ?: continue
            val chipSpeed = parseSpeedFromChipText(child.text.toString())
            if (chipSpeed != null && Math.abs(chipSpeed - speed) < 0.01) {
                child.isChecked = true
                return
            }
        }
        chipGroupPresets.clearCheck()
    }

    private fun parseSpeedFromChipText(text: String): Float? {
        val cleaned = text.replace("x", "").replace("(Normal)", "").trim()
        return cleaned.toFloatOrNull()
    }

    private fun triggerHapticFeedback() {
        view?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    companion object {
        const val TAG = "SpeedControllerBottomSheet"

        fun newInstance(
            currentSpeed: Float,
            onSpeedSelected: (Float) -> Unit
        ): SpeedControllerBottomSheet {
            return SpeedControllerBottomSheet(currentSpeed, onSpeedSelected)
        }
    }
}
