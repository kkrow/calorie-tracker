package dev.kkrow.calorietracker.ui.home

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import dev.kkrow.calorietracker.R
import dev.kkrow.calorietracker.data.CalorieRepository
import dev.kkrow.calorietracker.databinding.FragmentHomeBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: CalorieRepository
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var selectedDateMillis: Long = startOfDay(System.currentTimeMillis())
    private var baseBottomPadding: Int = 0
    private var lastProgressValue: Int? = null
    private var lastProgressMax: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        repository = CalorieRepository(requireContext())
        baseBottomPadding = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val extraBottom = maxOf(imeBottom, systemBottom)

            binding.root.setPadding(
                binding.root.paddingLeft,
                binding.root.paddingTop,
                binding.root.paddingRight,
                baseBottomPadding + extraBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.buttonPrevDate.setOnClickListener { moveToAdjacentAllowedDate(-1) }
        binding.buttonNextDate.setOnClickListener { moveToAdjacentAllowedDate(1) }
        binding.textSelectedDate.setOnClickListener { showDatePicker() }
        setupProgressSwipe()

        binding.inputCaloriesPer100.doAfterTextChanged {
            binding.inputCaloriesPer100.error = null
            updatePreview()
        }
        binding.inputGrams.doAfterTextChanged {
            binding.inputGrams.error = null
            updatePreview()
        }
        binding.inputWorkoutCalories.doAfterTextChanged {
            binding.inputWorkoutCalories.error = null
            updatePreview()
        }
        binding.toggleEntryMode.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) {
                updateEntryModeUi()
                updatePreview()
            }
        }

        binding.inputCaloriesPer100.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollToInput()
            }
        }
        binding.inputGrams.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollToInput()
            }
        }
        binding.inputWorkoutCalories.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollToInput()
            }
        }
        binding.inputCaloriesPer100.setOnClickListener { scrollToInput() }
        binding.inputGrams.setOnClickListener { scrollToInput() }
        binding.inputWorkoutCalories.setOnClickListener { scrollToInput() }
        binding.inputCaloriesPer100.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                scrollToInput()
            }
            false
        }
        binding.inputGrams.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                scrollToInput()
            }
            false
        }
        binding.inputWorkoutCalories.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                scrollToInput()
            }
            false
        }

        binding.inputCaloriesPer100.setOnEditorActionListener { _, actionId, event ->
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            val isNextAction = actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE
            if (isEnterKey || isNextAction) {
                binding.inputGrams.requestFocus()
                scrollToInput()
                true
            } else {
                false
            }
        }

        binding.inputGrams.setOnEditorActionListener { _, actionId, event ->
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEND
            if (isEnterKey || isDoneAction) {
                addEntryFromInputs()
                true
            } else {
                false
            }
        }
        binding.inputWorkoutCalories.setOnEditorActionListener { _, actionId, event ->
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEND
            if (isEnterKey || isDoneAction) {
                addEntryFromInputs()
                true
            } else {
                false
            }
        }

        binding.buttonAddEntry.setOnClickListener {
            addEntryFromInputs()
        }

        updateEntryModeUi()
        refreshUi()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun moveToAdjacentAllowedDate(direction: Int): Boolean {
        val targetDate = getAdjacentAllowedDate(direction) ?: return false
        selectedDateMillis = targetDate
        refreshUi()
        return true
    }

    private fun showDatePicker() {
        val datesWithRecords = repository.getDatesWithRecords()
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                val pickedMillis = startOfDay(picked.timeInMillis)
                val pickedKey = dateKeyFormat.format(pickedMillis)

                if (datesWithRecords.contains(pickedKey)) {
                    selectedDateMillis = pickedMillis
                    refreshUi()
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.no_records_for_date,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )

        dialog.setOnShowListener {
            val positive = dialog.getButton(DatePickerDialog.BUTTON_POSITIVE) as Button
            val updateButtonState = {
                val pickerCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, dialog.datePicker.year)
                    set(Calendar.MONTH, dialog.datePicker.month)
                    set(Calendar.DAY_OF_MONTH, dialog.datePicker.dayOfMonth)
                }
                val key = dateKeyFormat.format(startOfDay(pickerCal.timeInMillis))
                positive.isEnabled = datesWithRecords.contains(key)
            }

            dialog.datePicker.init(
                dialog.datePicker.year,
                dialog.datePicker.month,
                dialog.datePicker.dayOfMonth
            ) { _, _, _, _ ->
                updateButtonState()
            }
            updateButtonState()
        }

        dialog.show()
    }

    private fun addEntryFromInputs() {
        val signedCalories = if (isWorkoutMode()) {
            val workoutCalories = parseDecimal(binding.inputWorkoutCalories.text?.toString())
            if (workoutCalories == null) {
                binding.inputWorkoutCalories.error = getString(R.string.invalid_number)
                return
            }
            -abs(workoutCalories.roundToInt())
        } else {
            val caloriesPer100 = parseDecimal(binding.inputCaloriesPer100.text?.toString())
            val grams = parseDecimal(binding.inputGrams.text?.toString())

            if (caloriesPer100 == null || grams == null) {
                if (caloriesPer100 == null) {
                    binding.inputCaloriesPer100.error = getString(R.string.invalid_preview_values)
                }
                if (grams == null) {
                    binding.inputGrams.error = getString(R.string.invalid_preview_values)
                }
                return
            }

            val totalCalories = ((caloriesPer100 * grams) / 100.0).roundToInt()
            abs(totalCalories)
        }
        val key = dateKeyFormat.format(selectedDateMillis)
        repository.addEntry(key, signedCalories)

        binding.inputCaloriesPer100.text?.clear()
        binding.inputGrams.text?.clear()
        binding.inputWorkoutCalories.text?.clear()
        hideKeyboard()
        binding.inputCaloriesPer100.clearFocus()
        binding.inputGrams.clearFocus()
        binding.inputWorkoutCalories.clearFocus()

        refreshUi()
    }

    private fun refreshUi() {
        val allowedDates = getAllowedDatesForButtons()
        selectedDateMillis = normalizeSelectedDate(allowedDates)

        val dateKey = dateKeyFormat.format(selectedDateMillis)
        val dateLabel = SimpleDateFormat("d MMM yyyy", appLocale()).format(selectedDateMillis)

        val limit = repository.getDailyLimit().coerceAtLeast(1)
        val total = repository.getDayTotal(dateKey)
        val dayEntries = repository.getDayEntries(dateKey)
        val consumed = dayEntries.filter { it > 0 }.sum()
        val burned = dayEntries.filter { it < 0 }.sum().let { abs(it) }
        val remaining = limit - total
        val progressValue = remaining.coerceAtLeast(0).coerceAtMost(limit)

        val currentIndex = allowedDates.indexOf(selectedDateMillis)
        binding.buttonPrevDate.isEnabled = currentIndex > 0
        binding.buttonNextDate.isEnabled = currentIndex in 0 until allowedDates.lastIndex
        binding.buttonPrevDate.alpha = if (binding.buttonPrevDate.isEnabled) 1f else 0.35f
        binding.buttonNextDate.alpha = if (binding.buttonNextDate.isEnabled) 1f else 0.35f

        binding.textSelectedDate.text = dateLabel
        binding.textRemainingValue.text = remaining.toString()
        binding.textDayTotal.text = getString(R.string.day_total, total)
        binding.textDayConsumed.text = getString(R.string.day_consumed, consumed)
        binding.textDayBurned.text = getString(R.string.day_burned, burned)

        binding.progressCalories.max = limit
        val shouldAnimateProgress =
            lastProgressValue != null &&
                lastProgressMax == limit
        if (shouldAnimateProgress) {
            binding.progressCalories.setProgressCompat(progressValue, true)
        } else {
            binding.progressCalories.progress = progressValue
        }
        lastProgressValue = progressValue
        lastProgressMax = limit
        val indicatorColor = if (remaining < 0) {
            ContextCompat.getColor(requireContext(), R.color.calorie_progress_overflow)
        } else {
            ContextCompat.getColor(requireContext(), R.color.calorie_progress_normal)
        }
        binding.progressCalories.setIndicatorColor(indicatorColor)

        updatePreview()
    }

    private fun updatePreview() {
        if (isWorkoutMode()) {
            binding.textPreviewCalories.visibility = View.GONE
            return
        }

        val caloriesPer100 = parseDecimal(binding.inputCaloriesPer100.text?.toString())
        val grams = parseDecimal(binding.inputGrams.text?.toString())
        val preview = if (caloriesPer100 != null && grams != null) {
            ((caloriesPer100 * grams) / 100.0).roundToInt()
        } else {
            0
        }
        binding.textPreviewCalories.visibility = View.VISIBLE
        binding.textPreviewCalories.text = getString(R.string.preview_calories, abs(preview))
    }

    private fun isWorkoutMode(): Boolean {
        return binding.toggleEntryMode.checkedButtonId == R.id.buttonModeWorkout
    }

    private fun updateEntryModeUi() {
        val workoutMode = isWorkoutMode()
        binding.layoutInputCaloriesPer100.visibility = if (workoutMode) View.GONE else View.VISIBLE
        binding.layoutInputGrams.visibility = if (workoutMode) View.GONE else View.VISIBLE
        binding.layoutInputWorkoutCalories.visibility = if (workoutMode) View.VISIBLE else View.GONE

        if (workoutMode) {
            binding.inputCaloriesPer100.clearFocus()
            binding.inputGrams.clearFocus()
        } else {
            binding.inputWorkoutCalories.clearFocus()
        }
    }

    private fun parseDecimal(value: String?): Double? {
        val normalized = value?.trim()?.replace(',', '.')
        if (normalized.isNullOrEmpty()) {
            return null
        }
        return normalized.toDoubleOrNull()
    }

    private fun scrollToInput() {
        binding.root.postDelayed({
            val padding = (resources.displayMetrics.density * 16).toInt()
            val targetTopView = if (isWorkoutMode()) {
                binding.layoutInputWorkoutCalories
            } else {
                binding.layoutInputCaloriesPer100
            }
            val desiredTop = (targetTopView.top - padding).coerceAtLeast(0)
            val desiredBottom = binding.buttonAddEntry.bottom + padding
            val viewportBottom = binding.root.scrollY + binding.root.height

            val targetY = if (desiredBottom > viewportBottom) {
                (desiredBottom - binding.root.height).coerceAtLeast(desiredTop)
            } else {
                desiredTop
            }

            binding.root.smoothScrollTo(0, targetY)
        }, 120)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        val token = binding.inputWorkoutCalories.windowToken
            ?: binding.inputGrams.windowToken
            ?: binding.inputCaloriesPer100.windowToken
            ?: return
        imm?.hideSoftInputFromWindow(token, 0)
    }

    private fun setupProgressSwipe() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop.toFloat()
        val triggerDistance = resources.displayMetrics.density * 72f
        var startRawX = 0f
        var startRawY = 0f
        var lastDx = 0f
        var draggingHorizontal = false

        binding.layoutProgress.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    lastDx = 0f
                    draggingHorizontal = false
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    lastDx = dx

                    if (!draggingHorizontal) {
                        if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                            draggingHorizontal = true
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        } else if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            return@setOnTouchListener false
                        }
                    }

                    if (draggingHorizontal) {
                        val dragX = (dx * 0.9f).coerceIn(-220f, 220f)
                        view.translationX = dragX
                        view.alpha = (1f - (abs(dragX) / 520f)).coerceAtLeast(0.6f)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (!draggingHorizontal) {
                        view.animate().translationX(0f).alpha(1f).setDuration(100L).start()
                        return@setOnTouchListener false
                    }

                    val direction = when {
                        lastDx <= -triggerDistance -> 1
                        lastDx >= triggerDistance -> -1
                        else -> 0
                    }
                    if (direction == 0) {
                        resetProgressPosition()
                    } else {
                        animateProgressDateChange(direction)
                    }
                    draggingHorizontal = false
                    true
                }

                else -> false
            }
        }
    }

    private fun animateProgressDateChange(direction: Int) {
        val targetDate = getAdjacentAllowedDate(direction) ?: run {
            resetProgressPosition()
            return
        }
        val currentX = binding.layoutProgress.translationX
        val swipeOut = resources.displayMetrics.density * 120f
        val endOut = if (direction > 0) {
            minOf(currentX - 36f, -swipeOut)
        } else {
            maxOf(currentX + 36f, swipeOut)
        }
        val enterFrom = -endOut.sign * (resources.displayMetrics.density * 64f)

        binding.layoutProgress.animate()
            .translationX(endOut)
            .alpha(0.6f)
            .setDuration(110L)
            .withEndAction {
                binding.layoutProgress.translationX = enterFrom
                binding.layoutProgress.alpha = 0f
                selectedDateMillis = targetDate
                refreshUi()
                binding.layoutProgress.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(150L)
                    .start()
            }
            .start()
    }

    private fun resetProgressPosition() {
        binding.layoutProgress.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(140L)
            .start()
    }

    private fun getAdjacentAllowedDate(direction: Int): Long? {
        val allowedDates = getAllowedDatesForButtons()
        val currentIndex = allowedDates.indexOf(selectedDateMillis)
        if (currentIndex == -1) {
            refreshUi()
            return null
        }
        val targetIndex = currentIndex + direction
        if (targetIndex !in allowedDates.indices) {
            return null
        }
        return allowedDates[targetIndex]
    }

    private fun getAllowedDatesForButtons(): List<Long> {
        val today = startOfDay(System.currentTimeMillis())
        val dates = repository.getDatesWithRecords()
            .mapNotNull { parseDateKey(it) }
            .map { startOfDay(it) }
            .filter { it <= today }
            .toMutableSet()

        dates.add(today)
        return dates.sorted()
    }

    private fun normalizeSelectedDate(allowedDates: List<Long>): Long {
        if (allowedDates.isEmpty()) {
            return startOfDay(System.currentTimeMillis())
        }
        if (selectedDateMillis in allowedDates) {
            return selectedDateMillis
        }

        val today = startOfDay(System.currentTimeMillis())
        if (selectedDateMillis > today) {
            return today
        }

        return allowedDates.lastOrNull { it < selectedDateMillis } ?: allowedDates.first()
    }

    private fun parseDateKey(dateKey: String): Long? {
        return try {
            dateKeyFormat.parse(dateKey)?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun appLocale(): Locale {
        return resources.configuration.locales[0] ?: Locale.getDefault()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
