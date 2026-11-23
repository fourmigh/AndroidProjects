#include <jni.h>
#include <android/log.h>

#include "llama.h"

#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "HubLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_mutex;

llama_model *g_model = nullptr;
llama_context *g_ctx = nullptr;

std::atomic<bool> g_backend_inited{false};

// 捕获 llama.cpp 的错误/告警日志，加载失败时回传给 Kotlin，避免只看到
// 一句「模型加载失败」。
std::mutex g_log_mutex;
std::string g_log_buffer;

void hub_log_callback(ggml_log_level level, const char *text, void *) {
    if (level != GGML_LOG_LEVEL_ERROR && level != GGML_LOG_LEVEL_WARN) return;
    std::lock_guard<std::mutex> lock(g_log_mutex);
    g_log_buffer += text;
    if (g_log_buffer.size() > 4000) {
        g_log_buffer.erase(0, g_log_buffer.size() - 4000);
    }
}

void clear_log() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    g_log_buffer.clear();
}

std::string take_log() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    return g_log_buffer;
}

std::mutex g_err_mutex;
std::string g_last_error;

void set_last_error(const std::string &err) {
    std::lock_guard<std::mutex> lock(g_err_mutex);
    g_last_error = err;
}

std::string get_last_error() {
    std::lock_guard<std::mutex> lock(g_err_mutex);
    return g_last_error;
}

std::mutex g_stats_mutex;
std::string g_last_stats;

void set_last_stats(const std::string &s) {
    std::lock_guard<std::mutex> lock(g_stats_mutex);
    g_last_stats = s;
}

std::string get_last_stats() {
    std::lock_guard<std::mutex> lock(g_stats_mutex);
    return g_last_stats;
}

// ---------------------------------------------------------------- helpers

std::string jstring_to_string(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

jbyteArray string_to_jbytes(JNIEnv *env, const std::string &s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    if (arr == nullptr) return nullptr;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()),
                            reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text, bool add_special) {
    std::vector<llama_token> tokens;
    if (text.empty()) return tokens;

    int32_t n = -llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                nullptr, 0, add_special, true);
    if (n <= 0) return tokens;
    tokens.resize(n);
    int32_t written = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                     tokens.data(), static_cast<int32_t>(tokens.size()),
                                     add_special, true);
    if (written < 0) return {};
    tokens.resize(written);
    return tokens;
}

std::string token_to_piece(const llama_vocab *vocab, llama_token token) {
    char buf[512];
    int32_t n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    if (n > 0) return std::string(buf, n);
    if (n < 0) {
        std::vector<char> big(static_cast<size_t>(-n));
        int32_t m = llama_token_to_piece(vocab, token, big.data(), static_cast<int32_t>(big.size()), 0, true);
        if (m > 0) return std::string(big.data(), m);
    }
    return {};
}

/** 解码一段 token；need_logits 时把最后一个 token 的 logits 置为需要。 */
/** 解码一段 token；返回 llama_decode 的错误码（0 = 成功）。 */
int32_t decode_tokens(const std::vector<llama_token> &tokens, llama_pos start_pos, bool need_logits) {
    if (tokens.empty()) return 0;

    const int32_t n = static_cast<int32_t>(tokens.size());
    llama_batch batch = llama_batch_init(n, 0, 1);
    // llama_batch_init 只分配缓冲区，n_tokens 必须手动设置，
    // 否则 llama_decode 会以 "n_tokens == 0" 返回 -1
    batch.n_tokens = n;
    for (int32_t i = 0; i < n; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = start_pos + i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (need_logits && i == n - 1) ? 1 : 0;
    }
    const int32_t rc = llama_decode(g_ctx, batch);
    llama_batch_free(batch);
    return rc;
}

int32_t decode_one(llama_token token, llama_pos pos) {
    return decode_tokens(std::vector<llama_token>{token}, pos, true);
}

bool ends_with_any(const std::string &text, const std::vector<std::string> &stops) {
    for (const auto &s : stops) {
        if (!s.empty() && text.size() >= s.size() &&
            text.compare(text.size() - s.size(), s.size(), s) == 0) {
            return true;
        }
    }
    return false;
}

std::vector<std::string> split(const std::string &text, char sep) {
    std::vector<std::string> out;
    std::string current;
    for (char c : text) {
        if (c == sep) {
            if (!current.empty()) out.push_back(current);
            current.clear();
        } else {
            current.push_back(c);
        }
    }
    if (!current.empty()) out.push_back(current);
    return out;
}

llama_sampler *build_sampler(float temp, int32_t top_k, float top_p, float rep_penalty) {
    if (temp <= 0.0f || top_k == 1) {
        return llama_sampler_init_greedy();
    }
    llama_sampler_chain_params params = llama_sampler_chain_default_params();
    params.no_perf = true;
    llama_sampler *chain = llama_sampler_chain_init(params);
    if (rep_penalty > 1.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_penalties(64, rep_penalty, 0.0f, 0.0f));
    }
    if (top_k > 0) {
        llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
    }
    if (top_p < 1.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
    }
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(0));
    return chain;
}

void release_all() {
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

std::string model_meta(const char *key) {
    if (g_model == nullptr) return {};
    std::vector<char> buf(256 * 1024);
    const int32_t n = llama_model_meta_val_str(g_model, key, buf.data(), buf.size());
    if (n < 0) return {};
    return std::string(buf.data(), std::min<size_t>(static_cast<size_t>(n), buf.size()));
}

} // namespace

// ---------------------------------------------------------------- JNI

extern "C" JNIEXPORT jint JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeEngineVersion(JNIEnv *, jclass) {
    return 1;
}

/**
 * 加载模型。
 *
 * 返回 null 表示成功；非 null 是失败原因（已包含 llama.cpp 的真实错误日志）。
 * 修改了签名，Kotlin 侧对应 `String?`。
 */
extern "C" JNIEXPORT jstring JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeLoad(
        JNIEnv *env, jclass, jstring path, jint n_ctx, jint n_threads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string model_path = jstring_to_string(env, path);

    if (!g_backend_inited.exchange(true)) {
        llama_backend_init();
        llama_log_set(hub_log_callback, nullptr);
    }

    release_all();
    clear_log();

    if (model_path.empty()) {
        return env->NewStringUTF("model path is empty");
    }
    if (access(model_path.c_str(), R_OK) != 0) {
        const std::string msg = "cannot read model file: " + model_path;
        LOGE("%s", msg.c_str());
        return env->NewStringUTF(msg.c_str());
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;
    mparams.use_mlock = false;

    g_model = llama_model_load_from_file(model_path.c_str(), mparams);

    // 部分 ROM 的外部存储对 mmap 不友好，退化后再试一次
    if (g_model == nullptr && mparams.use_mmap) {
        LOGW("mmap load failed, retrying without mmap");
        mparams.use_mmap = false;
        g_model = llama_model_load_from_file(model_path.c_str(), mparams);
    }

    if (g_model == nullptr) {
        std::string err = take_log();
        if (err.empty()) err = "llama_model_load_from_file returned null (unknown reason)";
        LOGE("load failed: %s", err.c_str());
        return env->NewStringUTF(err.c_str());
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx > 0 ? static_cast<uint32_t>(n_ctx) : 4096;
    cparams.n_batch = 256;
    cparams.n_ubatch = 128;
    cparams.n_threads = n_threads > 0 ? n_threads : 4;
    cparams.n_threads_batch = n_threads > 0 ? n_threads : 4;
    cparams.no_perf = true;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (g_ctx == nullptr) {
        std::string err = take_log();
        if (err.empty()) err = "llama_init_from_model returned null";
        LOGE("context failed: %s", err.c_str());
        release_all();
        return env->NewStringUTF(err.c_str());
    }

    LOGI("model loaded: %s (n_ctx=%u threads=%d)", model_path.c_str(), cparams.n_ctx, cparams.n_threads);
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeUnload(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_all();
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeArchitecture(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string arch = model_meta("general.architecture");
    return env->NewStringUTF(arch.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeChatTemplate(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string tmpl = model_meta("tokenizer.chat_template");
    return env->NewStringUTF(tmpl.c_str());
}

/**
 * 用模型自带的 chat template 包装单条 user 消息。
 * 返回 UTF-8 字节；空数组表示模板不可用（调用方回退到 ChatML）。
 */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeApplyChat(
        JNIEnv *env, jclass, jstring user_content, jboolean add_assistant) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr) return string_to_jbytes(env, "");

    const std::string tmpl = model_meta("tokenizer.chat_template");
    if (tmpl.empty()) return string_to_jbytes(env, "");

    const std::string user = jstring_to_string(env, user_content);
    llama_chat_message msg{"user", user.c_str()};

    size_t capacity = user.size() * 2 + 4096;
    for (int attempt = 0; attempt < 3; ++attempt) {
        std::vector<char> buf(capacity);
        const int32_t rc = llama_chat_apply_template(
                tmpl.c_str(), &msg, 1, add_assistant == JNI_TRUE, buf.data(),
                static_cast<int32_t>(buf.size()));
        if (rc < 0) return string_to_jbytes(env, "");
        if (static_cast<size_t>(rc) <= buf.size()) {
            return string_to_jbytes(env, std::string(buf.data(), rc));
        }
        capacity = static_cast<size_t>(rc) + 1024;
    }
    return string_to_jbytes(env, "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeLastError(JNIEnv *env, jclass) {
    const std::string err = get_last_error();
    return env->NewStringUTF(err.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeLastStats(JNIEnv *env, jclass) {
    const std::string s = get_last_stats();
    return env->NewStringUTF(s.c_str());
}

/** 对文本分词（调试用）。 */
extern "C" JNIEXPORT jintArray JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeTokenize(
        JNIEnv *env, jclass, jstring text, jboolean add_special) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr) return env->NewIntArray(0);

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const std::vector<llama_token> tokens =
            tokenize(vocab, jstring_to_string(env, text), add_special == JNI_TRUE);

    jintArray arr = env->NewIntArray(static_cast<jsize>(tokens.size()));
    if (arr != nullptr && !tokens.empty()) {
        env->SetIntArrayRegion(arr, 0, static_cast<jsize>(tokens.size()),
                               reinterpret_cast<const jint *>(tokens.data()));
    }
    return arr;
}

/** 把 token 还原成文本（调试用）；返回 UTF-8 字节。 */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeDetokenize(
        JNIEnv *env, jclass, jintArray tokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr) return string_to_jbytes(env, "");

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const jsize n = env->GetArrayLength(tokens);
    if (n <= 0) return string_to_jbytes(env, "");

    std::vector<jint> ids(static_cast<size_t>(n));
    env->GetIntArrayRegion(tokens, 0, n, ids.data());

    std::string out;
    out.reserve(static_cast<size_t>(n) * 4);
    for (jint id : ids) {
        out += token_to_piece(vocab, static_cast<llama_token>(id));
    }
    return string_to_jbytes(env, out);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_caojun_ai_translate_hub_llama_LlamaBridge_nativeTranslate(
        JNIEnv *env, jclass,
        jstring prompt,
        jint max_tokens,
        jfloat temp,
        jint top_k,
        jfloat top_p,
        jfloat rep_penalty,
        jstring stop_csv) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx == nullptr || g_model == nullptr) {
        return string_to_jbytes(env, "");
    }

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const std::string prompt_text = jstring_to_string(env, prompt);
    const std::vector<std::string> stops = split(jstring_to_string(env, stop_csv), '\n');

    set_last_error("");
    clear_log();

    std::vector<llama_token> tokens = tokenize(vocab, prompt_text, true);
    if (tokens.empty()) {
        LOGE("prompt tokenized to empty");
        set_last_error("prompt tokenized to empty");
        return string_to_jbytes(env, "");
    }

    const int32_t ctx_size = static_cast<int32_t>(llama_n_ctx(g_ctx));
    if (static_cast<int32_t>(tokens.size()) + max_tokens > ctx_size) {
        const int32_t keep = std::max(1, ctx_size - max_tokens);
        tokens.erase(tokens.begin(), tokens.end() - keep);
        LOGI("prompt truncated to %d tokens", keep);
    }

    if (llama_memory_t memory = llama_get_memory(g_ctx)) {
        llama_memory_clear(memory, true);
    }

    // prefill（按 n_batch 切块，最后一块需要 logits）
    const int64_t t_prefill_start = ggml_time_us();
    const int32_t n_batch = static_cast<int32_t>(llama_n_batch(g_ctx));
    llama_pos pos = 0;
    for (size_t offset = 0; offset < tokens.size(); offset += n_batch) {
        const size_t end = std::min(tokens.size(), offset + static_cast<size_t>(n_batch));
        std::vector<llama_token> chunk(tokens.begin() + offset, tokens.begin() + end);
        const bool last = end == tokens.size();
        const int32_t rc = decode_tokens(chunk, pos, last);
        if (rc != 0) {
            const std::string reason = "prefill failed rc=" + std::to_string(rc) +
                " n_tokens=" + std::to_string(chunk.size()) +
                " n_ctx=" + std::to_string(llama_n_ctx(g_ctx)) +
                " log=" + take_log();
            LOGE("%s", reason.c_str());
            set_last_error(reason);
            return string_to_jbytes(env, "");
        }
        pos += static_cast<llama_pos>(chunk.size());
    }

    const int64_t t_prefill_end = ggml_time_us();

    llama_sampler *sampler = build_sampler(temp, top_k, top_p, rep_penalty);
    std::string output;
    output.reserve(1024);
    int32_t generated = 0;

    for (int32_t step = 0; step < max_tokens; ++step) {
        const llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        output += token_to_piece(vocab, token);
        ++generated;
        if (ends_with_any(output, stops)) break;

        llama_sampler_accept(sampler, token);
        const int32_t rc = decode_one(token, pos);
        if (rc != 0) {
            const std::string reason = "decode failed rc=" + std::to_string(rc) +
                " step=" + std::to_string(step) + " log=" + take_log();
            LOGE("%s", reason.c_str());
            set_last_error(reason);
            break;
        }
        ++pos;
    }

    const int64_t t_end = ggml_time_us();
    set_last_stats(
        "prefill_ms=" + std::to_string((t_prefill_end - t_prefill_start) / 1000) +
        " decode_ms=" + std::to_string((t_end - t_prefill_end) / 1000) +
        " steps=" + std::to_string(generated) +
        " prompt_tokens=" + std::to_string(tokens.size()) +
        " out_bytes=" + std::to_string(output.size()));

    llama_sampler_free(sampler);

    if (output.empty()) {
        set_last_error("no output (first token was EOS/EOG)");
    }
    return string_to_jbytes(env, output);
}
