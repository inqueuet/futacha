#pragma once
#include <stddef.h>
#include <stdint.h>
typedef struct FutachaVoiceSession FutachaVoiceSession;
int futacha_voice_open(const char *core, const char *runtime, const char *dictionary,
                      const char *model, unsigned threads, FutachaVoiceSession **session);
/* Only query can return this recoverable failure; other native errors remain fatal. */
#define FUTACHA_VOICE_TEXT_ANALYSIS_ERROR 1
int futacha_voice_query(FutachaVoiceSession *session, const char *text, char **json);
int futacha_voice_synthesis(FutachaVoiceSession *session, const char *query, uint8_t **wav, size_t *size);
void futacha_voice_json_free(char *json);
void futacha_voice_wav_free(uint8_t *wav);
void futacha_voice_close(FutachaVoiceSession *session);
const char *futacha_voice_error(void);
