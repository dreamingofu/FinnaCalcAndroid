//
// TaxViewModel.kt
//
// Port of iOS Features/Taxes/TaxEngineViewModel.swift — holds the interview
// answers, persists them under the same key the iOS app uses, and recomputes
// the estimate on every answer so the refund figure moves as the user types.
//
// Answers are the only thing stored: the return and the result are derived,
// so there is one source of truth and no way for a saved estimate to drift
// from the answers behind it.
//

package com.finnacalc.android.features.taxes

import androidx.lifecycle.ViewModel
import com.finnacalc.android.core.util.JsonPrefs
import com.finnacalc.android.features.taxes.engine.AnswerValue
import com.finnacalc.android.features.taxes.engine.Answers
import com.finnacalc.android.features.taxes.engine.TaxCalculationResult
import com.finnacalc.android.features.taxes.engine.buildReturn
import com.finnacalc.android.features.taxes.engine.calculateFederalTax
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** The wire shape of one stored answer (a typed union on disk). */
@Serializable
private data class StoredAnswer(
    val number: Double? = null,
    val text: String? = null,
    val flag: Boolean? = null,
)

class TaxViewModel : ViewModel() {

    private val _answers = MutableStateFlow(load())
    val answers: StateFlow<Answers> = _answers.asStateFlow()

    private val _result = MutableStateFlow(compute(_answers.value))
    val result: StateFlow<TaxCalculationResult> = _result.asStateFlow()

    // Section/question visibility is derived in the UI from the collected
    // `answers` state rather than exposed here as a snapshot — a snapshot read
    // wouldn't recompose when a life-situation answer opens a new section.

    fun set(id: String, value: AnswerValue?) {
        val next = _answers.value.toMutableMap()
        if (value == null) next.remove(id) else next[id] = value
        _answers.value = next
        _result.value = compute(next)
        persist(next)
    }

    fun setNumber(id: String, value: Double?) =
        set(id, value?.takeIf { it != 0.0 }?.let { AnswerValue.Num(it) })

    fun setBoolean(id: String, value: Boolean) = set(id, AnswerValue.Bool(value))

    fun setString(id: String, value: String?) =
        set(id, value?.takeIf { it.isNotEmpty() }?.let { AnswerValue.Str(it) })

    fun reset() {
        _answers.value = emptyMap()
        _result.value = compute(emptyMap())
        persist(emptyMap())
    }

    private fun compute(a: Answers): TaxCalculationResult = calculateFederalTax(buildReturn(a))

    private fun persist(a: Answers) {
        val stored = a.mapValues { (_, v) ->
            when (v) {
                is AnswerValue.Num -> StoredAnswer(number = v.value)
                is AnswerValue.Str -> StoredAnswer(text = v.value)
                is AnswerValue.Bool -> StoredAnswer(flag = v.value)
            }
        }
        JsonPrefs.persist(stored, STORAGE_KEY)
    }

    companion object {
        // The same key the iOS app writes, so the shape stays recognisable.
        private const val STORAGE_KEY = "finnacalc:taxReturn:2025:answers"

        private fun load(): Answers {
            val stored = JsonPrefs.load<Map<String, StoredAnswer>>(STORAGE_KEY) ?: return emptyMap()
            return stored.mapNotNull { (id, v) ->
                when {
                    v.number != null -> id to AnswerValue.Num(v.number)
                    v.text != null -> id to AnswerValue.Str(v.text)
                    v.flag != null -> id to AnswerValue.Bool(v.flag)
                    else -> null
                }
            }.toMap()
        }
    }
}
