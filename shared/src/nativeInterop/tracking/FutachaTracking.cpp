#include "FutachaTracking.h"
#include <opencv2/core.hpp>
#include <opencv2/features.hpp>
#include <opencv2/geometry.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/video/tracking.hpp>
#include <algorithm>
#include <array>
#include <cmath>
#include <mutex>
#include <vector>

// Same thresholds and cumulative polygon as toshikari's OpticalFlowTracker.kt.
struct FutachaTracker {
    cv::Mat before;
    std::vector<cv::Point2f> points;
    std::vector<cv::Point2f> corners;
    std::array<float, 4> bounds{.5f, .5f, .3f, .3f};
    int width = 0, height = 0;
    bool valid = false;

    void refill() {
        cv::Mat mask = cv::Mat::zeros(height, width, CV_8UC1);
        std::vector<cv::Point> polygon;
        // Java's MatOfPoint conversion truncates floating coordinates.
        for (auto p : corners) polygon.emplace_back(static_cast<int>(p.x), static_cast<int>(p.y));
        cv::fillConvexPoly(mask, polygon, cv::Scalar(255));
        cv::goodFeaturesToTrack(before, points, 96, .01, 4., mask);
        valid = points.size() >= 8;
    }

    bool advance(const cv::Mat &after) {
        std::vector<cv::Point2f> next, back, from, to;
        std::vector<unsigned char> ok, back_ok;
        std::vector<float> error, back_error;
        cv::calcOpticalFlowPyrLK(before, after, points, next, ok, error, cv::Size(21, 21), 3);
        cv::calcOpticalFlowPyrLK(after, before, next, back, back_ok, back_error, cv::Size(21, 21), 3);
        for (size_t i = 0; i < points.size(); ++i) {
            auto q = next[i];
            if (ok[i] && back_ok[i] && error[i] < 35 && std::isfinite(q.x) && std::isfinite(q.y) &&
                q.x >= 0 && q.x <= width && q.y >= 0 && q.y <= height &&
                cv::norm(points[i] - back[i]) <= 1.5) {
                from.push_back(points[i]); to.push_back(q);
            }
        }
        if (from.size() < 8 || from.size() * 2 < points.size()) return false;
        cv::Mat inliers;
        cv::Mat affine = cv::estimateAffinePartial2D(from, to, inliers, cv::RANSAC, 2., 500, .99, 5);
        if (affine.empty() || cv::countNonZero(inliers) < std::max(8, static_cast<int>(from.size() * 3 / 5))) return false;
        const double *m = affine.ptr<double>();
        for (int i = 0; i < 6; ++i) if (!std::isfinite(m[i])) return false;
        const double scale = std::hypot(m[0], m[3]);
        if (scale < .8 || scale > 1.25 || std::abs(std::atan2(m[3], m[0])) > CV_PI / 5) return false;
        auto transformed = corners;
        float left = INFINITY, top = INFINITY, right = -INFINITY, bottom = -INFINITY;
        for (auto &p : transformed) {
            p = cv::Point2f(m[0] * p.x + m[1] * p.y + m[2], m[3] * p.x + m[4] * p.y + m[5]);
            left = std::min(left, p.x); right = std::max(right, p.x);
            top = std::min(top, p.y); bottom = std::max(bottom, p.y);
        }
        if (right <= 0 || bottom <= 0 || left >= width || top >= height) return false;
        float w = std::clamp((right - left) / width, .025f, 1.f);
        float h = std::clamp((bottom - top) / height, .025f, 1.f);
        float x = std::clamp((left + right) / (2 * width), w / 2, 1 - w / 2);
        float y = std::clamp((top + bottom) / (2 * height), h / 2, 1 - h / 2);
        if (std::hypot(x - bounds[0], y - bounds[1]) > .3) return false;
        bounds = {x, y, w, h}; corners = std::move(transformed);
        after.copyTo(before);
        refill();
        return true;
    }
};

static bool valid_image(const int8_t *gray, int w, int h) {
    return gray && w > 0 && h > 0 && static_cast<int64_t>(w) * h <= 4194304;
}

FutachaTracker *futacha_tracker_create(void) {
    try {
        static std::once_flag initialized;
        std::call_once(initialized, [] { cv::setNumThreads(2); });
        return new FutachaTracker();
    } catch (...) { return nullptr; }
}
void futacha_tracker_destroy(FutachaTracker *tracker) { delete tracker; }

int futacha_tracker_seed(FutachaTracker *t, const int8_t *gray, int w, int h,
                         float x, float y, float rw, float rh) {
    if (!t || !valid_image(gray, w, h) || !std::isfinite(x) || !std::isfinite(y) ||
        !std::isfinite(rw) || !std::isfinite(rh) || rw < .025f || rw > 1 || rh < .025f || rh > 1) return -1;
    t->valid = false;
    try {
        t->width = w; t->height = h; t->bounds = {x, y, rw, rh};
        cv::Mat(h, w, CV_8UC1, const_cast<int8_t *>(gray)).copyTo(t->before);
        t->corners = {{(x-rw/2)*w, (y-rh/2)*h}, {(x+rw/2)*w, (y-rh/2)*h},
                      {(x+rw/2)*w, (y+rh/2)*h}, {(x-rw/2)*w, (y+rh/2)*h}};
        t->refill();
        return 0;
    } catch (...) { return -1; }
}
int futacha_tracker_step(FutachaTracker *t, const int8_t *gray, int w, int h,
                         int scene_cut, float output[5]) {
    if (!t || !output || !valid_image(gray, w, h)) return -1;
    try {
        if (scene_cut || w != t->width || h != t->height) t->valid = false;
        if (t->valid && !t->advance(cv::Mat(h, w, CV_8UC1, const_cast<int8_t *>(gray)))) t->valid = false;
        std::copy(t->bounds.begin(), t->bounds.end(), output);
        output[4] = t->valid ? 0.f : 1.f;
        return 0;
    } catch (...) { t->valid = false; return -1; }
}
