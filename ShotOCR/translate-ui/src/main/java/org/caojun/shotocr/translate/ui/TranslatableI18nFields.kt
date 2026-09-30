package org.caojun.shotocr.translate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.caojun.shotocr.translate.TranslateEngine
import org.caojun.shotocr.translate.TranslationDictionary

/**
 * 一组「可翻译」的中英输入框。
 *
 * 每个输入框内右侧带一个「翻译」按钮：点哪个，就把翻译结果写入哪个输入框，
 * 目标语言即该输入框的语言。
 *
 * 原文（源）规则：取**最上面那个有内容的输入框**；若两栏都为空则按钮禁用。
 * 当源语言与目标语言相同时（例如只有英文、又点英文框的按钮）按钮同样禁用，避免自我覆盖。
 *
 * 翻译成功后，结果以 [TextRange] 定位到末尾并聚焦到该输入框。
 *
 * 本控件自带翻译状态与错误显示，调用方无需接触 [TranslateEngine]。
 */
@Composable
fun TranslatableI18nFields(
    zhValue: TextFieldValue,
    onZhValueChange: (TextFieldValue) -> Unit,
    zhLabel: String,
    enValue: TextFieldValue,
    onEnValueChange: (TextFieldValue) -> Unit,
    enLabel: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val zhFocus = remember { FocusRequester() }
    val enFocus = remember { FocusRequester() }

    // 正在翻译的目标语言：LANG_ZH / LANG_EN / null
    var translatingTarget by remember { mutableStateOf<String?>(null) }
    var zhError by remember { mutableStateOf<String?>(null) }
    var enError by remember { mutableStateOf<String?>(null) }

    val translating = translatingTarget != null
    val zhText = zhValue.text
    val enText = enValue.text

    // 源 = 最上面有内容的输入框；null = 两栏都为空
    val sourceLanguage: String? = when {
        zhText.isNotBlank() -> LANG_ZH
        enText.isNotBlank() -> LANG_EN
        else -> null
    }

    fun translate(source: String, target: String) {
        if (translating) return
        val sourceText = if (source == LANG_ZH) zhText else enText
        if (sourceText.isBlank()) return

        zhError = null
        enError = null

        fun applyResult(result: String) {
            if (target == LANG_ZH) {
                onZhValueChange(TextFieldValue(result, TextRange(result.length)))
                zhFocus.requestFocus()
            } else {
                onEnValueChange(TextFieldValue(result, TextRange(result.length)))
                enFocus.requestFocus()
            }
        }

        // 词典命中是同步瞬时的，直接出结果，避免「翻译中…」闪烁
        TranslationDictionary.lookup(sourceText, source)?.let {
            applyResult(it)
            return
        }

        translatingTarget = target

        scope.launch {
            try {
                TranslateEngine.initialize(context)
                applyResult(TranslateEngine.translate(sourceText.trim(), source, target))
            } catch (t: Throwable) {
                val message = t.message ?: t.javaClass.simpleName
                if (target == LANG_ZH) zhError = message else enError = message
            } finally {
                translatingTarget = null
            }
        }
    }

    // 按钮语义：点击后「译到本框」。因此源必须存在，且源语言 != 本框语言。
    val zhEnabled = !translating && sourceLanguage == LANG_EN
    val enEnabled = !translating && sourceLanguage == LANG_ZH

    Column(modifier = modifier) {
        TranslatableField(
            label = zhLabel,
            value = zhValue,
            onValueChange = onZhValueChange,
            focusRequester = zhFocus,
            translateEnabled = zhEnabled,
            translating = translatingTarget == LANG_ZH,
            onTranslate = { sourceLanguage?.let { translate(it, LANG_ZH) } },
            error = zhError,
        )

        Spacer(modifier = Modifier.height(8.dp))

        TranslatableField(
            label = enLabel,
            value = enValue,
            onValueChange = onEnValueChange,
            focusRequester = enFocus,
            translateEnabled = enEnabled,
            translating = translatingTarget == LANG_EN,
            onTranslate = { sourceLanguage?.let { translate(it, LANG_EN) } },
            error = enError,
        )
    }
}

private val LANG_ZH = TranslateEngine.LANG_ZH
private val LANG_EN = TranslateEngine.LANG_EN

/**
 * 单个可翻译输入框：输入框内右侧为「翻译」按钮，输入框下方为错误提示。
 */
@Composable
private fun TranslatableField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    translateEnabled: Boolean,
    translating: Boolean,
    onTranslate: () -> Unit,
    error: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            TextButton(
                onClick = onTranslate,
                enabled = translateEnabled,
            ) {
                Text(
                    stringResource(
                        if (translating) R.string.translate_ui_translating
                        else R.string.translate_ui_translate
                    )
                )
            }
        },
        supportingText = {
            if (error != null) {
                Text(
                    text = stringResource(R.string.translate_ui_failed, error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}
