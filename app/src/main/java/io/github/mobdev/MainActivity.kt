package io.github.mobdev

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var expressionTextView: TextView
    private lateinit var resultTextView: TextView

    private var currentInput: String = "0"
    private var storedValue: Double? = null
    private var pendingOperator: String? = null
    private var shouldResetInput: Boolean = false

    companion object {
        private const val KEY_CURRENT_INPUT = "key_current_input"
        private const val KEY_STORED_VALUE = "key_stored_value"
        private const val KEY_PENDING_OPERATOR = "key_pending_operator"
        private const val KEY_SHOULD_RESET_INPUT = "key_should_reset_input"
        private const val KEY_EXPRESSION = "key_expression"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        expressionTextView = findViewById(R.id.tvExpression)
        resultTextView = findViewById(R.id.tvResult)

        restoreState(savedInstanceState)
        setupClickListeners()
        updateDisplay()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_INPUT, currentInput)
        if (storedValue != null) {
            outState.putDouble(KEY_STORED_VALUE, storedValue!!)
        }
        outState.putString(KEY_PENDING_OPERATOR, pendingOperator)
        outState.putBoolean(KEY_SHOULD_RESET_INPUT, shouldResetInput)
        outState.putString(KEY_EXPRESSION, expressionTextView.text.toString())
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            expressionTextView.text = ""
            return
        }

        currentInput = savedInstanceState.getString(KEY_CURRENT_INPUT, "0")
        storedValue = if (savedInstanceState.containsKey(KEY_STORED_VALUE)) {
            savedInstanceState.getDouble(KEY_STORED_VALUE)
        } else {
            null
        }
        pendingOperator = savedInstanceState.getString(KEY_PENDING_OPERATOR)
        shouldResetInput = savedInstanceState.getBoolean(KEY_SHOULD_RESET_INPUT, false)
        expressionTextView.text = savedInstanceState.getString(KEY_EXPRESSION, "")
    }

    private fun setupClickListeners() {
        val digitButtons = listOf(
            R.id.btn0 to "0",
            R.id.btn1 to "1",
            R.id.btn2 to "2",
            R.id.btn3 to "3",
            R.id.btn4 to "4",
            R.id.btn5 to "5",
            R.id.btn6 to "6",
            R.id.btn7 to "7",
            R.id.btn8 to "8",
            R.id.btn9 to "9"
        )

        digitButtons.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener { appendDigit(digit) }
        }

        findViewById<Button>(R.id.btnDot).setOnClickListener { appendDot() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { clearAll() }
        findViewById<Button>(R.id.btnPlus).setOnClickListener { setOperator("+") }
        findViewById<Button>(R.id.btnMinus).setOnClickListener { setOperator("-") }
        findViewById<Button>(R.id.btnMultiply).setOnClickListener { setOperator("×") }
        findViewById<Button>(R.id.btnDivide).setOnClickListener { setOperator("÷") }
        findViewById<Button>(R.id.btnEquals).setOnClickListener { calculateResult() }
    }

    private fun appendDigit(digit: String) {
        if (shouldResetInput) {
            currentInput = digit
            shouldResetInput = false
        } else {
            currentInput = if (currentInput == "0") digit else currentInput + digit
        }
        updateDisplay()
    }

    private fun appendDot() {
        if (shouldResetInput) {
            currentInput = "0."
            shouldResetInput = false
        } else if (!currentInput.contains('.')) {
            currentInput += "."
        }
        updateDisplay()
    }

    private fun setOperator(operator: String) {
        val currentValue = currentInput.toDoubleOrNull() ?: 0.0

        if (storedValue == null) {
            storedValue = currentValue
        } else if (!shouldResetInput && pendingOperator != null) {
            if (isDivisionByZero(pendingOperator!!, currentValue)) {
                showError()
                return
            }
            storedValue = performCalculation(storedValue!!, currentValue, pendingOperator!!)
            currentInput = formatNumber(storedValue!!)
        }

        pendingOperator = operator
        shouldResetInput = true
        expressionTextView.text = "${formatNumber(storedValue ?: currentValue)} $operator"
        updateDisplay()
    }

    private fun calculateResult() {
        val firstValue = storedValue ?: return
        val operator = pendingOperator ?: return
        val secondValue = currentInput.toDoubleOrNull() ?: 0.0

        if (isDivisionByZero(operator, secondValue)) {
            showError()
            return
        }

        val result = performCalculation(firstValue, secondValue, operator)

        expressionTextView.text = "${formatNumber(firstValue)} $operator ${formatNumber(secondValue)} ="
        currentInput = formatNumber(result)
        storedValue = null
        pendingOperator = null
        shouldResetInput = true

        updateDisplay()
    }

    private fun isDivisionByZero(operator: String, second: Double): Boolean {
        return operator == "÷" && second == 0.0
    }

    private fun showError() {
        currentInput = "0"
        storedValue = null
        pendingOperator = null
        shouldResetInput = true
        expressionTextView.text = ""
        resultTextView.text = getString(R.string.error_division_by_zero)
    }

    private fun performCalculation(first: Double, second: Double, operator: String): Double {
        return when (operator) {
            "+" -> first + second
            "-" -> first - second
            "×" -> first * second
            "÷" -> first / second
            else -> second
        }
    }

    private fun clearAll() {
        currentInput = "0"
        storedValue = null
        pendingOperator = null
        shouldResetInput = false
        expressionTextView.text = ""
        updateDisplay()
    }

    private fun updateDisplay() {
        resultTextView.text = currentInput
    }

    private fun formatNumber(value: Double): String {
        return if (value.isNaN()) {
            "NaN"
        } else if (value.isInfinite()) {
            "Infinity"
        } else if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }
}
