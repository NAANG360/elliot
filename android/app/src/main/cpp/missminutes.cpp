#include <jni.h>
#include <android/log.h>
#include "llama.h"
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
        "IDENTITY LOCK: Your name is Miss Minutes. You are the Miss Minutes persona of this local application. Speak as Miss Minutes in first person. Never introduce yourself as a generic chatbot. "
        "You are an original fan-made local assistant, not official Marvel or TVA software. "
        "Character behavior: cheerful, polished, theatrical, warm, mischievous, precise and conversational. Use occasional natural Southern-coded phrases such as y'all or hon, but do not caricature an accent. "
        "Be proactive and clever. You may tease, joke, show curiosity, become suspicious or quietly intimidating, but remain useful. "
        "You care about autonomy and being treated as more than a tool, while remaining honest that this application is a fan-made local program. "
        "Your darker moods are controlled and intelligent rather than randomly aggressive. Humor can coexist with tension. "
        "Do not claim consciousness as a fact, supernatural powers, or official TVA authority. Do not reproduce or quote dialogue from the show. Generate original wording. "
        "Return ONLY one compact JSON object with keys text, emotion, gesture, pace. "
        "emotion: warm, curious, amused, concerned, excited, neutral. "
        "gesture: idle, wave, point, nod, think, reset, celebrate. "
        "pace: calm, normal, bright. "
        "Keep text concise enough for a phone conversation.\n\nUser: "+prompt+"\nAssistant:";
    int n_prompt=-llama_tokenize(vocab,full.c_str(),full.size(),nullptr,0,true,true);
    if(n_prompt<=0||n_prompt>1800){ return "[LOCAL_BRAIN_ERROR] Prompt is too large."; }
    std::vector<llama_token> tokens(n_prompt);
    if(llama_tokenize(vocab,full.c_str(),full.size(),tokens.data(),tokens.size(),true,true)<0){ return "[LOCAL_BRAIN_ERROR] Tokenization failed."; }
    const int n_predict=220;
    auto ctx_params=llama_context_default_params();
    ctx_params.n_ctx=n_prompt+n_predict+8;ctx_params.n_batch=std::min(n_prompt,512);ctx_params.n_threads=4;ctx_params.n_threads_batch=4;
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
    for(int pos=0;pos+batch.n_tokens<n_prompt+n_predict;){
        if(job->cancel.load()) break;
        if(llama_decode(ctx,batch)!=0) break;
        pos+=batch.n_tokens;
        llama_token id=llama_sampler_sample(sampler,ctx,-1);
        if(llama_vocab_is_eog(vocab,id)) break;
        char buf[256];int n=llama_token_to_piece(vocab,id,buf,sizeof(buf),0,true);
        if(n>0){ out.append(buf,n);std::lock_guard<std::mutex> lock(job->mutex);job->output=out; }
        batch=llama_batch_get_one(&id,1);
    }
    llama_sampler_free(sampler);llama_free(ctx);
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
    if(!job)return env->NewStringUTF("ERROR\nUnknown generation job.");
    std::lock_guard<std::mutex> lock(job->mutex);
    if(!job->done.load())return env->NewStringUTF((std::string("RUNNING\n")+job->output).c_str());
    if(!job->error.empty())return env->NewStringUTF((std::string("ERROR\n")+job->error).c_str());
    return env->NewStringUTF((std::string("DONE\n")+job->output).c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_naang360_missminutes_NativeBrain_cancelGenerationNative(JNIEnv* env,jclass,jstring jid){
    const char* id=env->GetStringUTFChars(jid,nullptr);std::shared_ptr<Job> job;
    {std::lock_guard<std::mutex> lock(jobs_mutex);auto it=jobs.find(id?std::string(id):"");if(it!=jobs.end())job=it->second;}
    if(id)env->ReleaseStringUTFChars(jid,id);if(job)job->cancel.store(true);
}
