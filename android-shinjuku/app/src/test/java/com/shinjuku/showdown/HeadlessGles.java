package com.shinjuku.showdown;

import org.lwjgl.PointerBuffer;
import org.lwjgl.egl.EGL;
import org.lwjgl.egl.EGL10;
import org.lwjgl.egl.EGL12;
import org.lwjgl.opengles.GLES;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/** 测试用：没有窗口的 GLES2 上下文（Mesa，pbuffer 当默认帧缓冲）。 */
final class HeadlessGles implements AutoCloseable {
    private final long display, surface, context;

    HeadlessGles(int w, int h) {
        Configuration.EGL_LIBRARY_NAME.set("libEGL.so.1");
        Configuration.OPENGLES_LIBRARY_NAME.set("libGLESv2.so.2");
        display = EGL10.eglGetDisplay(0L /* EGL_DEFAULT_DISPLAY */);
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer major = st.mallocInt(1), minor = st.mallocInt(1);
            if (!EGL10.eglInitialize(display, major, minor)) throw new IllegalStateException("eglInitialize " + Integer.toHexString(EGL10.eglGetError()));
            EGL.createDisplayCapabilities(display, major.get(0), minor.get(0));
            EGL12.eglBindAPI(EGL12.EGL_OPENGL_ES_API);
            IntBuffer attrs = st.ints(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT, 0x3040 /* EGL_RENDERABLE_TYPE */, 0x0004 /* EGL_OPENGL_ES2_BIT */, EGL10.EGL_NONE);
            PointerBuffer cfg = st.mallocPointer(1);
            IntBuffer n = st.mallocInt(1);
            if (!EGL10.eglChooseConfig(display, attrs, cfg, n) || n.get(0) == 0) throw new IllegalStateException("eglChooseConfig");
            long config = cfg.get(0);
            surface = EGL10.eglCreatePbufferSurface(display, config, st.ints(EGL10.EGL_WIDTH, w, EGL10.EGL_HEIGHT, h, EGL10.EGL_NONE));
            context = EGL10.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, st.ints(0x3098 /* EGL_CONTEXT_CLIENT_VERSION */, 2, EGL10.EGL_NONE));
            if (surface == 0 || context == 0) throw new IllegalStateException("pbuffer/context " + Integer.toHexString(EGL10.eglGetError()));
            if (!EGL10.eglMakeCurrent(display, surface, surface, context)) throw new IllegalStateException("eglMakeCurrent");
        }
        GLES.createCapabilities();
    }

    @Override
    public void close() {
        EGL10.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        EGL10.eglDestroyContext(display, context);
        EGL10.eglDestroySurface(display, surface);
        EGL10.eglTerminate(display);
    }
}
