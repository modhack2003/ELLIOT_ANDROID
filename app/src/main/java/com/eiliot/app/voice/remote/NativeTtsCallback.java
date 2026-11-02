package com.eiliot.app.voice.remote;

import androidx.annotation.Keep;
import kotlin.jvm.functions.Function1;

/** Stable boxed callback signature looked up directly by sherpa-onnx JNI. */
@Keep
public final class NativeTtsCallback implements Function1<float[], Integer> {
    private final Function1<float[], Integer> delegate;

    public NativeTtsCallback(Function1<float[], Integer> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Integer invoke(float[] samples) {
        return delegate.invoke(samples);
    }
}
