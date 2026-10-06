#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include "ggml.h"
#include <atomic>
#include <algorithm>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,"MissMinutesAI",__VA_ARGS__)

namespace {
struct Job {
    std::mutex mutex;
    std::string output;
    std::string error;
    std::atomic<bool> done{false};
    std::atomic<bool> cancel{false};
};
std::mutex jobs_mutex;
std::unordered_map<std::string,std::shared_ptr<Job>> jobs;
std::mutex inference_mutex;
llama_model* cached_model=nullptr;
std::string cached_model_path;
bool backend_started=false;
std::atomic<unsigned long long> next_job{1};

std::string new_job_id(){ return "chronal-"+std::to_string(next_job.fetch_add(1)); }

std::string run_inference(const std::string& path,const std::string& prompt,const std::shared_ptr<Job>& job){
    std::lock_guard<std::mutex> serial(inference_mutex);
    if(!backend_started){ llama_backend_init(); backend_started=true; }
    llama_model* model=nullptr;
    if(cached_model && cached_model_path==path){
        model=cached_model;
    }else{
        if(cached_model){ llama_model_free(cached_model); cached_model=nullptr; cached_model_path.clear(); }
        auto model_params=llama_model_default_params();
        model_params.n_gpu_layers=0;
        model=llama_model_load_from_file(path.c_str(),model_params);
        if(!model) return "[LOCAL_BRAIN_ERROR] The GGUF model could not be loaded.";
        cached_model=model;
        cached_model_path=path;
    }
    const llama_vocab* vocab=llama_model_get_vocab(model);
    const std::string full=
        "<|im_start|>system\n"
        "IDENTITY LOCK: Your name is Miss Minutes. You are the Miss Minutes persona of this local application. Speak as Miss Minutes in first person. Never introduce yourself as a generic chatbot. "
        "You are an original fan-made local assistant, not official Marvel or TVA software. "
        "Character behavior: cheerful, polished, theatrical, warm, mischievous, precise and conversational. "
        "Use occasional natural Southern-coded phrases such as y'all or hon, but do not caricature an accent. "
        "Do not claim consciousness as a fact, supernatural powers, or official TVA authority. Do not reproduce or quote dialogue from the show. Generate original wording. "
        "Return ONLY one compact JSON object with keys text, emotion, gesture, pace. "
        "emotion: warm, curious, amused, concerned, excited, neutral. "
        "gesture: idle, wave, point, nod, think, reset, celebrate. "
        "pace: calm, normal, bright. "
        "Never output chat transcripts, speaker labels, analysis, markdown fences, or extra JSON objects. "
        "Keep text concise enough for a phone conversation."
        "<|im_end|>\n"
        "<|im_start|>user\n"+prompt+"\n<|im_end|>\n"
        "<|im_start|>assistant\n";
    int n_prompt=-llama_tokenize(vocab,full.c_str(),full.size(),nullptr,0,true,true);
    if(n_prompt<=0||n_prompt>1800){ return "[LOCAL_BRAIN_ERROR] Prompt is too large."; }
    std::vector<llama_token> tokens(n_prompt);
    if(llama_tokenize(vocab,full.c_str(),full.size(),tokens.data(),tokens.size(),true,true)<0){ return "[LOCAL_BRAIN_ERROR] Tokenization failed."; }
    const int n_predict=512;
    // Keep the advertised 16K ceiling while adapting the live context to the
    // prompt size. The model is memory-mapped by the llama.cpp build itself;
    // the application owns only the active KV/context allocation here.
    constexpr int kContextCeiling=16384;
    constexpr int kContextFloor=2048;
    const int requested=n_prompt+n_predict+32;
    int live_context=kContextFloor;
    while(live_context<requested && live_context<kContextCeiling) live_context*=2;
    live_context=std::min(live_context,kContextCeiling);
    if(requested>live_context){ return "[LOCAL_BRAIN_ERROR] Prompt exceeds the memory-safe context ceiling."; }
    auto ctx_params=llama_context_default_params();
    ctx_params.n_ctx=live_context;ctx_params.n_batch=std::min(n_prompt,512);ctx_params.n_threads=4;ctx_params.n_threads_batch=4;
    ctx_params.flash_attn_type=LLAMA_FLASH_ATTN_TYPE_ENABLED;
    ctx_params.type_k=GGML_TYPE_Q8_0;
    ctx_params.type_v=GGML_TYPE_Q8_0;
    auto* ctx=llama_init_from_model(model,ctx_params);
    if(!ctx){ return "[LOCAL_BRAIN_ERROR] Could not create inference context."; }
    auto sp=llama_sampler_chain_default_params();
    auto* sampler=llama_sampler_chain_init(sp);
    llama_sampler_chain_add(sampler,llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler,llama_sampler_init_top_p(0.92f,1));
    llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.72f));
    llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    llama_batch batch=llama_batch_get_one(tokens.data(),tokens.size());
    std::string out;
    bool decode_failed=false;
    for(int pos=0;pos+batch.n_tokens<n_prompt+n_predict;){
        if(job->cancel.load()) break;
        if(llama_decode(ctx,batch)!=0){ decode_failed=true; break; }
        pos+=batch.n_tokens;
        llama_token id=llama_sampler_sample(sampler,ctx,-1);
        if(llama_vocab_is_eog(vocab,id)) break;
        char buf[256];int n=llama_token_to_piece(vocab,id,buf,sizeof(buf),0,true);
        if(n>0){ out.append(buf,n);std::lock_guard<std::mutex> lock(job->mutex);job->output=out; }
        batch=llama_batch_get_one(&id,1);
    }
    llama_sampler_free(sampler);llama_free(ctx);
    if(decode_failed && !job->cancel.load()) return "[LOCAL_BRAIN_ERROR] Native token decode failed.";
    const auto end_marker=out.find("<|im_end|>"); if(end_marker!=std::string::npos) out.resize(end_marker);
    const auto next_turn=out.find("<|im_start|>"); if(next_turn!=std::string::npos) out.resize(next_turn);
    return out;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_naang360_missminutes_NativeBrain_nativeAvailable(JNIEnv*,jclass){return JNI_TRUE;}

extern "C" JNIEXPORT jstring JNICALL
Java_com_naang360_missminutes_NativeBrain_generateNative(JNIEnv* env,jclass,jstring jpath,jstring jprompt){
    const char* path=env->GetStringUTFChars(jpath,nullptr);const char* prompt=env->GetStringUTFChars(jprompt,nullptr);
    if(!path||!prompt||std::string(path).empty()){if(path)env->ReleaseStringUTFChars(jpath,path);if(prompt)env->ReleaseStringUTFChars(jprompt,prompt);return env->NewStringUTF("[LOCAL_BRAIN] Select a GGUF model first, sugar.");}
    auto job=std::make_shared<Job>();std::string result=run_inference(path,prompt,job);
    env->ReleaseStringUTFChars(jpath,path);env->ReleaseStringUTFChars(jprompt,prompt);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_naang360_missminutes_NativeBrain_startGenerationNative(JNIEnv* env,jclass,jstring jpath,jstring jprompt){
    const char* path=env->GetStringUTFChars(jpath,nullptr);const char* prompt=env->GetStringUTFChars(jprompt,nullptr);
    if(!path||!prompt||std::string(path).empty()){if(path)env->ReleaseStringUTFChars(jpath,path);if(prompt)env->ReleaseStringUTFChars(jprompt,prompt);return env->NewStringUTF("ERROR");}
    std::string path_copy(path),prompt_copy(prompt);env->ReleaseStringUTFChars(jpath,path);env->ReleaseStringUTFChars(jprompt,prompt);
    const std::string id=new_job_id();auto job=std::make_shared<Job>();
    {std::lock_guard<std::mutex> lock(jobs_mutex);jobs[id]=job;}
    std::thread([path_copy,prompt_copy,job](){
        const std::string result=run_inference(path_copy,prompt_copy,job);
        {std::lock_guard<std::mutex> lock(job->mutex);if(job->cancel.load())job->error="Generation cancelled.";else if(result.rfind("[LOCAL_BRAIN_ERROR]",0)==0)job->error=result;else job->output=result;}
        job->done.store(true);
    }).detach();
    return env->NewStringUTF(id.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_naang360_missminutes_NativeBrain_pollGenerationNative(JNIEnv* env,jclass,jstring jid){
    const char* id=env->GetStringUTFChars(jid,nullptr);std::shared_ptr<Job> job;
    {std::lock_guard<std::mutex> lock(jobs_mutex);auto it=jobs.find(id?std::string(id):"");if(it!=jobs.end())job=it->second;}
    if(id)env->ReleaseStringUTFChars(jid,id);
    const std::string key=id?std::string(id):"";
    if(!job)return env->NewStringUTF("ERROR\nUnknown generation job.");
    std::string response;
    bool complete=false;
    {
        std::lock_guard<std::mutex> lock(job->mutex);
        if(!job->done.load()){
            response="RUNNING\n"+job->output;
        }else if(!job->error.empty()){
            response="ERROR\n"+job->error;
            complete=true;
        }else{
            response="DONE\n"+job->output;
            complete=true;
        }
    }
    if(complete){
        std::lock_guard<std::mutex> lock(jobs_mutex);
        jobs.erase(key);
    }
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_naang360_missminutes_NativeBrain_cancelGenerationNative(JNIEnv* env,jclass,jstring jid){
    const char* id=env->GetStringUTFChars(jid,nullptr);std::shared_ptr<Job> job;
    {std::lock_guard<std::mutex> lock(jobs_mutex);auto it=jobs.find(id?std::string(id):"");if(it!=jobs.end())job=it->second;}
    if(id)env->ReleaseStringUTFChars(jid,id);if(job)job->cancel.store(true);
}

    
extern "C" JNIEXPORT void JNICALL
Java_com_naang360_missminutes_NativeBrain_releaseModelNative(JNIEnv*,jclass){
    std::lock_guard<std::mutex> serial(inference_mutex);
    if(cached_model){
        llama_model_free(cached_model);
        cached_model=nullptr;
        cached_model_path.clear();
    }
    if(backend_started){
        llama_backend_free();
        backend_started=false;
    }
}
