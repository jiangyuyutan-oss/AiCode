package com.aicode.core.ui.glass

/**
 * 水玻璃档的 AGSL 波浪折射 shader：对上游效果链（blur 之后）的采样坐标做
 * 双向正弦叠加偏移，产生静态水纹；[WATER_WAVE_STATIC_TIME] 相位固定时零逐帧开销。
 * uniform 契约：uContent 上游 shader、uTime 相位（秒）、uAmplitude 偏移幅度（px）。
 */
const val WATER_WAVE_SHADER_KEY = "aicode_water_wave"

const val WATER_WAVE_UNIFORM_CONTENT = "uContent"

const val WATER_WAVE_STATIC_TIME = 7.3f

const val WATER_WAVE_SHADER: String = """
    uniform shader uContent;
    uniform float uTime;
    uniform float uAmplitude;

    half4 main(float2 coord) {
        float t = uTime;
        float dx = sin(coord.y * 0.02 + t * 1.3) + 0.5 * sin(coord.y * 0.045 - t * 0.7);
        float dy = cos(coord.x * 0.02 + t * 1.1) + 0.5 * cos(coord.x * 0.045 - t * 0.9);
        float2 offset = float2(dx, dy) * uAmplitude;
        return uContent.eval(coord + offset);
    }
"""
