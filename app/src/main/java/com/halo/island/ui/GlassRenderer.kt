package com.halo.island.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.island.core.*
import com.halo.island.halo

/** Only app-owned pixels. Never captures a screen or another application's window. */
data class AppBackdrop(val bitmap: Bitmap, val bounds: Rect)

fun effectiveQuality(
    request: GlassQuality,
    sdk: Int,
    powerSave: Boolean,
    lowRam: Boolean,
): GlassQuality =
    when {
        request == GlassQuality.LITE || powerSave -> GlassQuality.LITE
        request == GlassQuality.AUTO ->
            if (lowRam) GlassQuality.BALANCED
            else if (sdk >= 33) GlassQuality.HIGH else GlassQuality.BALANCED
        sdk < 33 && request in listOf(GlassQuality.ULTRA, GlassQuality.HIGH) ->
            GlassQuality.BALANCED
        else -> request
    }

@Composable
fun GlassRenderer(
    settings: HaloSettings,
    radius: Float,
    accent: Color,
    overlay: Boolean,
    backdrop: AppBackdrop?,
    modifier: Modifier = Modifier,
) {
    val c = androidx.compose.ui.platform.LocalContext.current
    val power by c.halo.battery.powerSaver.collectAsStateWithLifecycle()
    val low = c.getSystemService(android.app.ActivityManager::class.java).isLowRamDevice
    val quality = effectiveQuality(settings.quality, Build.VERSION.SDK_INT, power, low)
    val shape = RoundedCornerShape(radius.dp)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val holder = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { OpticalShader() }.getOrNull() else null
    }
    val tint = Color(if (settings.customTint != 0L) settings.customTint else settings.theme.tint)
    Canvas(
        modifier
            .shadow(
                (settings.shadow * 24).dp,
                shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(shape)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
    ) {
        val r = radius.dp.toPx().coerceAtMost(minOf(size.width, size.height) / 2)
        drawRoundRect(tint.copy(alpha = settings.opacity), cornerRadius = CornerRadius(r))
        if (
            !overlay &&
                backdrop != null &&
                holder != null &&
                Build.VERSION.SDK_INT >= 33 &&
                quality in listOf(GlassQuality.ULTRA, GlassQuality.HIGH)
        ) {
            runCatching { holder.draw(this, backdrop, bounds, settings, r, quality) }
        }
        if (quality != GlassQuality.LITE) {
            drawRoundRect(
                Brush.radialGradient(
                    listOf(
                        accent.copy(alpha = (.04f + settings.brightness * .35f)),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * .12f, size.height * .8f),
                    radius = size.width * .85f,
                ),
                cornerRadius = CornerRadius(r),
            )
            drawRoundRect(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = settings.brightness * .32f),
                        Color.Transparent,
                        Color.Black.copy(alpha = settings.contrast * .15f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width * .4f, size.height),
                ),
                cornerRadius = CornerRadius(r),
            )
        }
        if (quality in listOf(GlassQuality.ULTRA, GlassQuality.HIGH)) {
            drawRoundRect(
                Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = .035f + settings.brightness * .1f),
                        Color.Transparent,
                    ),
                    start = Offset(-size.width * .4f, 0f),
                    end = Offset(size.width * .6f, size.height),
                ),
                cornerRadius = CornerRadius(r),
            )
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = settings.dispersion * .10f),
                        Color.Transparent,
                        Color(0xFFCDD5F2).copy(alpha = settings.dispersion * .09f),
                    )
                ),
                topLeft = Offset(2f, 2f),
                size =
                    Size((size.width - 4f).coerceAtLeast(1f), (size.height - 4f).coerceAtLeast(1f)),
                cornerRadius = CornerRadius(r),
                style = Stroke(1f),
            )
        }
        drawRoundRect(
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = (settings.rim * .65f)),
                    Color.White.copy(alpha = .025f),
                    Color.White.copy(alpha = settings.rim * .16f),
                    Color.White.copy(alpha = settings.rim * .40f),
                )
            ),
            topLeft = Offset(.75f, .75f),
            size =
                Size((size.width - 1.5f).coerceAtLeast(1f), (size.height - 1.5f).coerceAtLeast(1f)),
            cornerRadius = CornerRadius(r),
            style = Stroke(1.5f),
        )
    }
}

@RequiresApi(33)
private class OpticalShader {
    private val shader =
        android.graphics.RuntimeShader(
            """
        uniform shader scene;
        uniform float2 resolution;
        uniform float2 origin;
        uniform float2 sceneSize;
        uniform float2 bitmapSize;
        uniform float corner;
        uniform float bend;
        uniform float spread;
        uniform float frost;
        uniform float3 tint;
        uniform float density;
        float2 lens(float2 p) {
            float2 q=p-resolution*.5;
            float2 b=resolution*.5-float2(corner);
            float2 d=abs(q)-b;
            float sdf=length(max(d,0.0))+min(max(d.x,d.y),0.0)-corner;
            float edge=pow(clamp(1.0+ sdf/max(corner,1.0),0.0,1.0),3.0);
            float2 normal=normalize(q+float2(.001));
            return p-normal*edge*bend*20.0;
        }
        half4 main(float2 p) {
            float2 l=lens(p);
            float2 uv=(origin+l)/sceneSize*bitmapSize;
            float2 delta=normalize(p-resolution*.5+float2(.01))*spread*2.5;
            half4 base=scene.eval(uv);
            half3 rgb=half3(scene.eval(uv+delta).r,base.g,scene.eval(uv-delta).b);
            if(frost>0.01) {
                float2 s=float2(frost*10.0,0.0);
                rgb=(rgb*2.0+scene.eval(uv+s).rgb+scene.eval(uv-s).rgb+scene.eval(uv+s.yx).rgb+scene.eval(uv-s.yx).rgb)/6.0;
            }
            return half4(mix(rgb,half3(tint),half(density)),1.0);
        }
    """
                .trimIndent()
        )
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var lastBitmap: Bitmap? = null

    fun draw(
        scope: DrawScope,
        b: AppBackdrop,
        bounds: Rect,
        s: HaloSettings,
        r: Float,
        q: GlassQuality,
    ) {
        if (b.bounds.width <= 0 || b.bounds.height <= 0) return
        if (lastBitmap !== b.bitmap) {
            shader.setInputShader(
                "scene",
                BitmapShader(b.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
            lastBitmap = b.bitmap
        }
        shader.setFloatUniform("resolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("origin", bounds.left - b.bounds.left, bounds.top - b.bounds.top)
        shader.setFloatUniform("sceneSize", b.bounds.width, b.bounds.height)
        shader.setFloatUniform("bitmapSize", b.bitmap.width.toFloat(), b.bitmap.height.toFloat())
        shader.setFloatUniform("corner", r)
        shader.setFloatUniform("bend", s.refraction)
        shader.setFloatUniform(
            "spread",
            if (q == GlassQuality.ULTRA) s.dispersion else s.dispersion * .5f,
        )
        shader.setFloatUniform("frost", s.blur)
        val tint = Color(if (s.customTint != 0L) s.customTint else s.theme.tint)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue)
        shader.setFloatUniform("density", (.35f + s.opacity * .35f).coerceIn(0f, 1f))
        paint.shader = shader
        scope.drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(0f, 0f, scope.size.width, scope.size.height, r, r, paint)
        }
    }
}

/** Static studio backdrop; generated once, not in a frame loop. */
fun studioBackdrop(): Bitmap {
    val bitmap = Bitmap.createBitmap(720, 440, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bitmap)
    val p = android.graphics.Paint(3)
    c.drawColor(android.graphics.Color.rgb(15, 23, 27))
    p.shader =
        android.graphics.RadialGradient(
            500f,
            90f,
            500f,
            intArrayOf(0xFF4F7174.toInt(), 0xFF10171C.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    c.drawRect(0f, 0f, 720f, 440f, p)
    p.shader = null
    p.style = android.graphics.Paint.Style.STROKE
    p.strokeWidth = 2f
    for (i in 0..8) {
        p.color = android.graphics.Color.argb(25, 205, 230, 222)
        c.drawOval(40f + i * 23, -180f + i * 30, 840f - i * 12, 550f + i * 28, p)
    }
    return bitmap
}
