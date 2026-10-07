# Miss Minutes Voice Lab

Pipeline:
source media -> clean single-speaker WAV -> transcript -> Piper training/fine-tuning -> ONNX voice -> Android import -> Miss Minutes speaks.

Android voice bundle:
- model.onnx (or any *.onnx)
- matching *.onnx.json
- tokens.txt
- espeak-ng-data/

The app imports the whole folder with VOICE LAB -> IMPORT PIPER VOICE FOLDER and runs the voice locally through sherpa-onnx.

## Authorized YouTube workflow

prepare_dataset.py accepts YouTube URLs only when --authorized is supplied. Use that only for videos you own, have permission to process, or otherwise have a lawful right to use. YouTube's Terms restrict downloading/reproducing content except where authorized by the service, rights holder, or applicable law.

Example:
python prepare_dataset.py --youtube "https://www.youtube.com/watch?v=YOUR_VIDEO" --authorized --output dataset/

Local recordings:
python prepare_dataset.py --input recordings/ --output dataset/

The helper uses yt-dlp, ffmpeg and faster-whisper, makes mono 22.05 kHz clips, and writes dataset/metadata.csv. Review the clips and transcripts before training.

## Training

Run Piper training on a GPU machine/VM. A fine-tuning checkpoint is normally much more practical than training from zero.

Use the training command documented by the Piper checkout installed on your machine. A common current/legacy-compatible pattern is:

python -m piper_train fit --data.voice_name "miss-minutes-custom" --data.csv_path "$PWD/dataset/metadata.csv" --data.audio_dir "$PWD/dataset/wav" --model.sample_rate 22050 --data.espeak_voice "en-us" --data.cache_dir "$PWD/dataset/cache" --data.config_path "$PWD/dataset/config.json" --data.batch_size 16 --ckpt_path /path/to/base.ckpt

Export:
python -m piper_train.export_onnx /path/to/model.ckpt /path/to/voice.onnx
cp dataset/config.json voice.onnx.json

Then:
python train_voice.py make-android-bundle --onnx voice.onnx --config voice.onnx.json --dataset dataset --output miss-minutes-voice/

Training quality depends heavily on clean, mostly single-speaker audio and accurate transcripts. Random clips containing music, other speakers or effects will hurt the model.

Only use recordings/model weights you are authorized to use. The project does not bundle an unauthorized performer clone.
