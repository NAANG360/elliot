#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include <string>
#include <vector>
#include <algorithm>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,"MissMinutesAI",__VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_naang360_missminutes_NativeBrain_nativeAvailable(JNIEnv*, jclass) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_naang360_missminutes_NativeBrain_generateNative(JNIEnv* env, jclass, jstring jpath, jstring jprompt) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    const char* prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (!path || !prompt || std::string(path).empty()) {
        if (path) env->ReleaseStringUTFChars(jpath,path);
        if (prompt) env->ReleaseStringUTFChars(jprompt,prompt);
        return env->NewStringUTF("[LOCAL_BRAIN] Select a GGUF model first, sugar.");
    }

    llama_backend_init();
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    llama_model* model = llama_model_load_from_file(path, model_params);
    if (!model) {
        env->ReleaseStringUTFChars(jpath,path);
        env->ReleaseStringUTFChars(jprompt,prompt);
        return env->NewStringUTF("[LOCAL_BRAIN_ERROR] The GGUF model could not be loaded.");
    }

    const llama_vocab* vocab = llama_model_get_vocab(model);
    std::string full =
        "You are Miss Minutes, a cheerful theatrical chronal assistant. "
        "You are an original fan-made local assistant, not official Marvel or TVA software. "
        "Be helpful, concise, playful, slightly mischievous, and warm. "
        "Never claim supernatural authority.\n\nUser: " + std::string(prompt) + "\nAssistant:";

    int n_prompt = -llama_tokenize(vocab, full.c_str(), full.size(), nullptr, 0, true, true);
    if (n_prompt <= 0 || n_prompt > 1800) {
        llama_model_free(model); llama_backend_free();
        env->ReleaseStringUTFChars(jpath,path); env->ReleaseStringUTFChars(jprompt,prompt);
        return env->NewStringUTF("[LOCAL_BRAIN_ERROR] Prompt is too large.");
    }
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, full.c_str(), full.size(), tokens.data(), tokens.size(), true, true) < 0) {
        llama_model_free(model); llama_backend_free();
        env->ReleaseStringUTFChars(jpath,path); env->ReleaseStringUTFChars(jprompt,prompt);
        return env->NewStringUTF("[LOCAL_BRAIN_ERROR] Tokenization failed.");
    }

    const int n_predict = 180;
    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_prompt + n_predict + 8;
    ctx_params.n_batch = std::min(n_prompt, 512);
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;
    auto* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        llama_model_free(model); llama_backend_free();
        env->ReleaseStringUTFChars(jpath,path); env->ReleaseStringUTFChars(jprompt,prompt);
        return env->NewStringUTF("[LOCAL_BRAIN_ERROR] Could not create inference context.");
    }

    auto sp = llama_sampler_chain_default_params();
    auto* sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.92f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.75f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    std::string out;
    for (int pos = 0; pos + batch.n_tokens < n_prompt + n_predict; ) {
        if (llama_decode(ctx, batch) != 0) break;
        pos += batch.n_tokens;
        llama_token id = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab,id)) break;
        char buf[256];
        int n = llama_token_to_piece(vocab,id,buf,sizeof(buf),0,true);
        if (n > 0) out.append(buf,n);
        batch = llama_batch_get_one(&id,1);
    }

    llama_sampler_free(sampler);
    llama_free(ctx);
    llama_model_free(model);
    llama_backend_free();
    env->ReleaseStringUTFChars(jpath,path);
    env->ReleaseStringUTFChars(jprompt,prompt);
    if (out.empty()) out = "[LOCAL_BRAIN] I had a little temporal hiccup. Try that again.";
    return env->NewStringUTF(out.c_str());
}
