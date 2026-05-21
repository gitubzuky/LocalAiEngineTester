#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include <sys/stat.h>

#include "ggml.h"
#include "llama.h"
#include "chat.h"

#define LOG_TAG "LlamaJni"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

struct LlamaHandle {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    uint32_t seed = LLAMA_DEFAULT_SEED;
};

long long file_size_bytes(const std::string & path) {
    struct stat stat_buffer {};
    if (stat(path.c_str(), &stat_buffer) != 0) {
        return -1;
    }
    return static_cast<long long>(stat_buffer.st_size);
}

void llama_log_to_android(ggml_log_level level, const char * text, void *) {
    if (text == nullptr || text[0] == '\0') {
        return;
    }

    const int android_level = level == GGML_LOG_LEVEL_ERROR
        ? ANDROID_LOG_ERROR
        : level == GGML_LOG_LEVEL_WARN
            ? ANDROID_LOG_WARN
            : level == GGML_LOG_LEVEL_DEBUG
                ? ANDROID_LOG_DEBUG
                : ANDROID_LOG_INFO;
    __android_log_write(android_level, LOG_TAG, text);
}

std::string to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return "";
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

std::vector<std::string> to_string_vector(JNIEnv * env, jobjectArray values) {
    std::vector<std::string> result;
    if (values == nullptr) return result;

    const jsize size = env->GetArrayLength(values);
    result.reserve(size);
    for (jsize i = 0; i < size; ++i) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        result.push_back(to_string(env, item));
        env->DeleteLocalRef(item);
    }
    return result;
}

void throw_java(JNIEnv * env, const char * message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
    }
}

bool contains_stop(const std::string & text, const std::vector<std::string> & stops) {
    return std::any_of(stops.begin(), stops.end(), [&text](const std::string & stop) {
        return !stop.empty() && text.find(stop) != std::string::npos;
    });
}

size_t utf8_complete_prefix_length(const std::string & text) {
    size_t i = 0;
    while (i < text.size()) {
        const auto lead = static_cast<unsigned char>(text[i]);
        size_t char_len = 0;
        if ((lead & 0x80) == 0) {
            char_len = 1;
        } else if ((lead & 0xE0) == 0xC0) {
            char_len = 2;
        } else if ((lead & 0xF0) == 0xE0) {
            char_len = 3;
        } else if ((lead & 0xF8) == 0xF0) {
            char_len = 4;
        } else {
            return i;
        }

        if (i + char_len > text.size()) {
            return i;
        }
        for (size_t j = 1; j < char_len; ++j) {
            const auto continuation = static_cast<unsigned char>(text[i + j]);
            if ((continuation & 0xC0) != 0x80) {
                return i;
            }
        }
        i += char_len;
    }
    return i;
}

std::string apply_chat_template(llama_model * model, const std::string & prompt, bool use_chat_template) {
    if (!use_chat_template) {
        return prompt;
    }

    const char * template_source = llama_model_chat_template(model, nullptr);
    if (template_source == nullptr || template_source[0] == '\0') {
        ALOGW("Model has no chat template metadata; using raw prompt.");
        return prompt;
    }

    auto templates = common_chat_templates_init(model, "");
    common_chat_msg user_message;
    user_message.role = "user";
    user_message.content = prompt;

    common_chat_templates_inputs inputs;
    inputs.messages = { user_message };
    inputs.add_generation_prompt = true;
    inputs.use_jinja = true;

    const auto params = common_chat_templates_apply(templates.get(), inputs);
    ALOGI(
        "Applied Jinja chat template promptBytes=%zu renderedBytes=%zu",
        prompt.size(),
        params.prompt.size()
    );
    return params.prompt;
}

bool emit_stream_piece(JNIEnv * env, jobject callback, jmethodID on_token, const std::string & piece) {
    jstring java_piece = env->NewStringUTF(piece.c_str());
    const jboolean should_continue = env->CallBooleanMethod(callback, on_token, java_piece);
    env->DeleteLocalRef(java_piece);
    return !env->ExceptionCheck() && should_continue;
}

std::vector<llama_token> tokenize(llama_model * model, const std::string & text, bool add_bos) {
    const llama_vocab * vocab = llama_model_get_vocab(model);
    int token_count = -llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), nullptr, 0, add_bos, true);
    if (token_count < 0) {
        throw std::runtime_error("Failed to calculate token count.");
    }

    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    token_count = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), tokens.data(), token_count, add_bos, true);
    if (token_count < 0) {
        throw std::runtime_error("Failed to tokenize prompt.");
    }

    tokens.resize(static_cast<size_t>(token_count));
    return tokens;
}

std::string token_to_piece(llama_model * model, llama_token token) {
    const llama_vocab * vocab = llama_model_get_vocab(model);
    std::vector<char> piece(32);
    int size = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
    if (size < 0) {
        piece.resize(static_cast<size_t>(-size));
        size = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
    }
    if (size < 0) {
        return "";
    }
    return std::string(piece.data(), static_cast<size_t>(size));
}

std::string generate_text(
    LlamaHandle * handle,
    const std::string & prompt,
    int max_tokens,
    float temperature,
    float top_p,
    int top_k,
    float repetition_penalty,
    int seed,
    bool use_chat_template,
    const std::vector<std::string> & stop_sequences,
    JNIEnv * env = nullptr,
    jobject callback = nullptr
) {
    if (handle == nullptr || handle->model == nullptr || handle->context == nullptr) {
        throw std::runtime_error("Llama model is not loaded.");
    }

    llama_memory_clear(llama_get_memory(handle->context), true);

    const auto generation_started_at = std::chrono::steady_clock::now();
    const std::string formatted_prompt = apply_chat_template(handle->model, prompt, use_chat_template);
    auto prompt_tokens = tokenize(handle->model, formatted_prompt, true);
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()));
    if (llama_decode(handle->context, batch) != 0) {
        throw std::runtime_error("Failed to decode prompt.");
    }
    const auto prompt_decoded_at = std::chrono::steady_clock::now();

    auto sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(-1, repetition_penalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    const uint32_t sampler_seed = seed < 0 ? handle->seed : static_cast<uint32_t>(seed);
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(sampler_seed));

    jmethodID on_token = nullptr;
    if (env != nullptr && callback != nullptr) {
        jclass callback_class = env->GetObjectClass(callback);
        on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(callback_class);
    }

    std::string output;
    std::string pending_stream_bytes;
    const llama_vocab * vocab = llama_model_get_vocab(handle->model);
    int generated_tokens = 0;
    long long first_token_ms = -1;

    for (int i = 0; i < max_tokens; ++i) {
        llama_token token = llama_sampler_sample(sampler, handle->context, -1);
        llama_sampler_accept(sampler, token);

        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        if (generated_tokens == 0) {
            first_token_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - generation_started_at
            ).count();
        }
        ++generated_tokens;

        const std::string piece = token_to_piece(handle->model, token);
        output += piece;

        if (env != nullptr && callback != nullptr && on_token != nullptr) {
            pending_stream_bytes += piece;
            const size_t emit_len = utf8_complete_prefix_length(pending_stream_bytes);
            if (emit_len > 0) {
                const std::string complete_piece = pending_stream_bytes.substr(0, emit_len);
                pending_stream_bytes.erase(0, emit_len);
                if (!emit_stream_piece(env, callback, on_token, complete_piece)) {
                    break;
                }
            }
            if (env->ExceptionCheck()) {
                break;
            }
        }

        if (contains_stop(output, stop_sequences)) {
            break;
        }

        llama_token next = token;
        llama_batch next_batch = llama_batch_get_one(&next, 1);
        if (llama_decode(handle->context, next_batch) != 0) {
            throw std::runtime_error("Failed to decode generated token.");
        }
    }

    if (!pending_stream_bytes.empty()) {
        ALOGW("Dropping incomplete UTF-8 stream tail bytes=%zu", pending_stream_bytes.size());
    }

    llama_sampler_free(sampler);
    const auto generation_finished_at = std::chrono::steady_clock::now();
    const long long prompt_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        prompt_decoded_at - generation_started_at
    ).count();
    const long long total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        generation_finished_at - generation_started_at
    ).count();
    const long long decode_ms = std::max(0LL, total_ms - prompt_ms);
    const double tokens_per_second = decode_ms > 0
        ? (static_cast<double>(generated_tokens) * 1000.0 / static_cast<double>(decode_ms))
        : 0.0;
    ALOGI(
        "Generation perf promptTokens=%zu generatedTokens=%d promptMs=%lld firstTokenMs=%lld decodeMs=%lld totalMs=%lld tokPerSec=%.2f",
        prompt_tokens.size(),
        generated_tokens,
        prompt_ms,
        first_token_ms,
        decode_ms,
        total_ms,
        tokens_per_second
    );
    return output;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_zure_localaiengine_engines_llama_LlamaNativeBridge_loadModel(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jint context_size,
    jint batch_size,
    jint micro_batch_size,
    jint threads,
    jint gpu_layers,
    jint seed
) {
    try {
        llama_backend_init();

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = gpu_layers;

        llama_context_params context_params = llama_context_default_params();
        context_params.n_ctx = context_size;
        context_params.n_batch = batch_size;
        context_params.n_ubatch = micro_batch_size;
        context_params.n_threads = threads;
        context_params.n_threads_batch = threads;
        // Keep Android CPU inference on the conservative attention path; the default AUTO path
        // can enter flash-attn kernels that crash with SIGILL on some devices.
        context_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
        const std::string path = to_string(env, model_path);
        const long long model_size = file_size_bytes(path);
        llama_log_set(llama_log_to_android, nullptr);
        ALOGI(
            "Loading GGUF model path=%s sizeBytes=%lld contextSize=%d batchSize=%d microBatchSize=%d threads=%d gpuLayers=%d seed=%d flashAttn=%s",
            path.c_str(),
            model_size,
            context_size,
            batch_size,
            micro_batch_size,
            threads,
            gpu_layers,
            seed,
            llama_flash_attn_type_name(context_params.flash_attn_type)
        );

        auto handle = std::make_unique<LlamaHandle>();
        handle->seed = seed < 0 ? LLAMA_DEFAULT_SEED : static_cast<uint32_t>(seed);
        handle->model = llama_model_load_from_file(path.c_str(), model_params);
        if (handle->model == nullptr) {
            throw std::runtime_error(
                "Failed to load GGUF model. path=" + path +
                    " sizeBytes=" + std::to_string(model_size)
            );
        }

        handle->context = llama_init_from_model(handle->model, context_params);
        if (handle->context == nullptr) {
            llama_model_free(handle->model);
            throw std::runtime_error("Failed to create llama context.");
        }

        return reinterpret_cast<jlong>(handle.release());
    } catch (const std::exception & e) {
        ALOGE("%s", e.what());
        throw_java(env, e.what());
        return 0L;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_zure_localaiengine_engines_llama_LlamaNativeBridge_generate(
    JNIEnv * env,
    jobject,
    jlong native_handle,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat repetition_penalty,
    jint seed,
    jboolean use_chat_template,
    jobjectArray stop_sequences
) {
    try {
        auto * handle = reinterpret_cast<LlamaHandle *>(native_handle);
        const std::string output = generate_text(
            handle,
            to_string(env, prompt),
            max_tokens,
            temperature,
            top_p,
            top_k,
            repetition_penalty,
            seed,
            use_chat_template,
            to_string_vector(env, stop_sequences)
        );
        return env->NewStringUTF(output.c_str());
    } catch (const std::exception & e) {
        ALOGE("%s", e.what());
        throw_java(env, e.what());
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_zure_localaiengine_engines_llama_LlamaNativeBridge_generateStream(
    JNIEnv * env,
    jobject,
    jlong native_handle,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat repetition_penalty,
    jint seed,
    jboolean use_chat_template,
    jobjectArray stop_sequences,
    jobject callback
) {
    try {
        auto * handle = reinterpret_cast<LlamaHandle *>(native_handle);
        generate_text(
            handle,
            to_string(env, prompt),
            max_tokens,
            temperature,
            top_p,
            top_k,
            repetition_penalty,
            seed,
            use_chat_template,
            to_string_vector(env, stop_sequences),
            env,
            callback
        );
    } catch (const std::exception & e) {
        ALOGE("%s", e.what());
        throw_java(env, e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_zure_localaiengine_engines_llama_LlamaNativeBridge_release(
    JNIEnv *,
    jobject,
    jlong native_handle
) {
    auto * handle = reinterpret_cast<LlamaHandle *>(native_handle);
    if (handle == nullptr) return;

    if (handle->context != nullptr) {
        llama_free(handle->context);
    }
    if (handle->model != nullptr) {
        llama_model_free(handle->model);
    }
    delete handle;
    llama_backend_free();
}
