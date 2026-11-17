#ifndef FUTACHA_TRACKING_H
#define FUTACHA_TRACKING_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct FutachaTracker FutachaTracker;
/* All calls are synchronous and must run on the same analysis worker, including destruction.
 * Images are tightly packed gray8. Exceptions never cross this C boundary. */
FutachaTracker *futacha_tracker_create(void);
void futacha_tracker_destroy(FutachaTracker *tracker);
int futacha_tracker_seed(FutachaTracker *tracker, const int8_t *gray, int width, int height,
                         float x, float y, float w, float h);
/* output: normalized centerX, centerY, width, height, uncertain (0 or 1).
 * Returns 0 on success (including track loss), -1 on invalid input/native failure. */
int futacha_tracker_step(FutachaTracker *tracker, const int8_t *gray, int width, int height,
                         int scene_cut, float output[5]);
#ifdef __cplusplus
}
#endif
#endif
