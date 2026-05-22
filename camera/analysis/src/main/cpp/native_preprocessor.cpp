#include <algorithm>
#include <cmath>
#include <cstdint>
#include <jni.h>

namespace {

constexpr int kLayoutNhwc = 0;
constexpr int kPixelRgb = 0;
constexpr int kNormalizeNone = 0;
constexpr int kNormalizeZeroToOne = 1;
constexpr int kNormalizeMinusOneToOne = 2;
constexpr int kNormalizeMeanStd = 3;

struct YuvPlanes {
    const uint8_t *y;
    const uint8_t *u;
    const uint8_t *v;
    int width;
    int height;
    int y_row_stride;
    int y_pixel_stride;
    int u_row_stride;
    int u_pixel_stride;
    int v_row_stride;
    int v_pixel_stride;
};

struct Rgb {
    float r;
    float g;
    float b;
};

float clamp_float(float value, float min_value, float max_value) {
    return std::max(min_value, std::min(value, max_value));
}

int clamp_int(int value, int min_value, int max_value) {
    return std::max(min_value, std::min(value, max_value));
}

uint8_t sample_plane(const uint8_t *plane, int row_stride, int pixel_stride, int x, int y) {
    return plane[y * row_stride + x * pixel_stride];
}

void map_oriented_to_source(
        float oriented_x,
        float oriented_y,
        int rotation_degrees,
        bool mirror_horizontal,
        int source_width,
        int source_height,
        float *source_x,
        float *source_y) {
    int oriented_width = rotation_degrees % 180 == 0 ? source_width : source_height;
    if (mirror_horizontal) {
        oriented_x = static_cast<float>(oriented_width - 1) - oriented_x;
    }

    switch ((rotation_degrees % 360 + 360) % 360) {
        case 90:
            *source_x = oriented_y;
            *source_y = static_cast<float>(source_height - 1) - oriented_x;
            break;
        case 180:
            *source_x = static_cast<float>(source_width - 1) - oriented_x;
            *source_y = static_cast<float>(source_height - 1) - oriented_y;
            break;
        case 270:
            *source_x = static_cast<float>(source_width - 1) - oriented_y;
            *source_y = oriented_x;
            break;
        case 0:
        default:
            *source_x = oriented_x;
            *source_y = oriented_y;
            break;
    }
}

Rgb sample_yuv_nearest(const YuvPlanes &planes, float source_x, float source_y) {
    const int x = clamp_int(static_cast<int>(std::round(source_x)), 0, planes.width - 1);
    const int y = clamp_int(static_cast<int>(std::round(source_y)), 0, planes.height - 1);
    const int uv_x = clamp_int(x / 2, 0, planes.width / 2 - 1);
    const int uv_y = clamp_int(y / 2, 0, planes.height / 2 - 1);

    const float y_value = static_cast<float>(
            sample_plane(planes.y, planes.y_row_stride, planes.y_pixel_stride, x, y));
    const float u_value = static_cast<float>(
            sample_plane(planes.u, planes.u_row_stride, planes.u_pixel_stride, uv_x, uv_y)) - 128.0f;
    const float v_value = static_cast<float>(
            sample_plane(planes.v, planes.v_row_stride, planes.v_pixel_stride, uv_x, uv_y)) - 128.0f;

    Rgb rgb{};
    rgb.r = clamp_float(y_value + 1.402f * v_value, 0.0f, 255.0f);
    rgb.g = clamp_float(y_value - 0.344136f * u_value - 0.714136f * v_value, 0.0f, 255.0f);
    rgb.b = clamp_float(y_value + 1.772f * u_value, 0.0f, 255.0f);
    return rgb;
}

Rgb sample_yuv_bilinear(
        const YuvPlanes &planes,
        float oriented_x,
        float oriented_y,
        int rotation_degrees,
        bool mirror_horizontal) {
    float source_x = 0.0f;
    float source_y = 0.0f;
    map_oriented_to_source(
            oriented_x,
            oriented_y,
            rotation_degrees,
            mirror_horizontal,
            planes.width,
            planes.height,
            &source_x,
            &source_y);

    source_x = clamp_float(source_x, 0.0f, static_cast<float>(planes.width - 1));
    source_y = clamp_float(source_y, 0.0f, static_cast<float>(planes.height - 1));

    const int x0 = clamp_int(static_cast<int>(std::floor(source_x)), 0, planes.width - 1);
    const int y0 = clamp_int(static_cast<int>(std::floor(source_y)), 0, planes.height - 1);
    const int x1 = clamp_int(x0 + 1, 0, planes.width - 1);
    const int y1 = clamp_int(y0 + 1, 0, planes.height - 1);
    const float wx = source_x - static_cast<float>(x0);
    const float wy = source_y - static_cast<float>(y0);

    const Rgb c00 = sample_yuv_nearest(planes, static_cast<float>(x0), static_cast<float>(y0));
    const Rgb c10 = sample_yuv_nearest(planes, static_cast<float>(x1), static_cast<float>(y0));
    const Rgb c01 = sample_yuv_nearest(planes, static_cast<float>(x0), static_cast<float>(y1));
    const Rgb c11 = sample_yuv_nearest(planes, static_cast<float>(x1), static_cast<float>(y1));

    Rgb rgb{};
    rgb.r = (1.0f - wx) * (1.0f - wy) * c00.r + wx * (1.0f - wy) * c10.r +
            (1.0f - wx) * wy * c01.r + wx * wy * c11.r;
    rgb.g = (1.0f - wx) * (1.0f - wy) * c00.g + wx * (1.0f - wy) * c10.g +
            (1.0f - wx) * wy * c01.g + wx * wy * c11.g;
    rgb.b = (1.0f - wx) * (1.0f - wy) * c00.b + wx * (1.0f - wy) * c10.b +
            (1.0f - wx) * wy * c01.b + wx * wy * c11.b;
    return rgb;
}

float normalize_value(
        float value,
        int channel,
        int normalization,
        const jfloat *mean,
        int mean_length,
        const jfloat *std,
        int std_length) {
    switch (normalization) {
        case kNormalizeZeroToOne:
            return value / 255.0f;
        case kNormalizeMinusOneToOne:
            return value / 127.5f - 1.0f;
        case kNormalizeMeanStd: {
            const float channel_mean = channel < mean_length ? mean[channel] : 0.0f;
            const float channel_std = channel < std_length ? std[channel] : 1.0f;
            return (value - channel_mean) / channel_std;
        }
        case kNormalizeNone:
        default:
            return value;
    }
}

} // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_zure_localaiengine_camera_analysis_nativebackend_NativePreprocessor_nativeVersion(
        JNIEnv *env,
        jobject /* thiz */) {
    return env->NewStringUTF("camera-analysis-native/0.2");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_zure_localaiengine_camera_analysis_nativebackend_NativePreprocessor_preprocessYuv420ToFloatTensor(
        JNIEnv *env,
        jobject /* thiz */,
        jobject y_plane,
        jobject u_plane,
        jobject v_plane,
        jint source_width,
        jint source_height,
        jint y_row_stride,
        jint y_pixel_stride,
        jint u_row_stride,
        jint u_pixel_stride,
        jint v_row_stride,
        jint v_pixel_stride,
        jint rotation_degrees,
        jboolean mirror_horizontal,
        jfloat crop_left,
        jfloat crop_top,
        jfloat crop_width,
        jfloat crop_height,
        jint output_width,
        jint output_height,
        jint tensor_layout,
        jint pixel_order,
        jint normalization,
        jfloatArray mean_array,
        jfloatArray std_array,
        jobject output_buffer) {
    auto *y = static_cast<const uint8_t *>(env->GetDirectBufferAddress(y_plane));
    auto *u = static_cast<const uint8_t *>(env->GetDirectBufferAddress(u_plane));
    auto *v = static_cast<const uint8_t *>(env->GetDirectBufferAddress(v_plane));
    auto *output = static_cast<float *>(env->GetDirectBufferAddress(output_buffer));
    if (y == nullptr || u == nullptr || v == nullptr || output == nullptr) {
        return;
    }

    const jfloat *mean = mean_array != nullptr ? env->GetFloatArrayElements(mean_array, nullptr) : nullptr;
    const jfloat *std = std_array != nullptr ? env->GetFloatArrayElements(std_array, nullptr) : nullptr;
    const int mean_length = mean_array != nullptr ? env->GetArrayLength(mean_array) : 0;
    const int std_length = std_array != nullptr ? env->GetArrayLength(std_array) : 0;

    const YuvPlanes planes{
            y,
            u,
            v,
            source_width,
            source_height,
            y_row_stride,
            y_pixel_stride,
            u_row_stride,
            u_pixel_stride,
            v_row_stride,
            v_pixel_stride
    };

    for (int oy = 0; oy < output_height; ++oy) {
        for (int ox = 0; ox < output_width; ++ox) {
            const float oriented_x = crop_left +
                                     (static_cast<float>(ox) + 0.5f) * crop_width /
                                     static_cast<float>(output_width) - 0.5f;
            const float oriented_y = crop_top +
                                     (static_cast<float>(oy) + 0.5f) * crop_height /
                                     static_cast<float>(output_height) - 0.5f;
            const Rgb rgb = sample_yuv_bilinear(
                    planes,
                    oriented_x,
                    oriented_y,
                    rotation_degrees,
                    mirror_horizontal == JNI_TRUE);
            const float source_channels[3] = {rgb.r, rgb.g, rgb.b};
            const int channel_order[3] = {
                    pixel_order == kPixelRgb ? 0 : 2,
                    1,
                    pixel_order == kPixelRgb ? 2 : 0
            };

            for (int channel = 0; channel < 3; ++channel) {
                const float value = normalize_value(
                        source_channels[channel_order[channel]],
                        channel,
                        normalization,
                        mean,
                        mean_length,
                        std,
                        std_length);
                const int index = tensor_layout == kLayoutNhwc
                                  ? (oy * output_width + ox) * 3 + channel
                                  : channel * output_width * output_height + oy * output_width + ox;
                output[index] = value;
            }
        }
    }

    if (mean != nullptr) {
        env->ReleaseFloatArrayElements(mean_array, const_cast<jfloat *>(mean), JNI_ABORT);
    }
    if (std != nullptr) {
        env->ReleaseFloatArrayElements(std_array, const_cast<jfloat *>(std), JNI_ABORT);
    }
}
