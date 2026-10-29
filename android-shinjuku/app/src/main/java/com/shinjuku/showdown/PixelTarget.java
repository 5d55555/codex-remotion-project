package com.shinjuku.showdown;

/**
 * 像素画面的绘制接口。场景（PixelScene）只调用这些，底下有两种实现：
 * GlTarget（游戏里用：GLES2 画进低分辨率帧缓冲，再最近邻放大）和 CanvasTarget（截图测试、GL 不可用时的保底）。
 * 一帧分两层：先是低分辨率的像素层（坐标是虚拟像素），present() 放大铺满屏幕后，是屏幕分辨率的文字层（hud）。
 */
interface PixelTarget {
    int TEX_ATLAS = 0, TEX_BG = 1, TEX_VOID = 2; // 图集、新宿背景、无量空处背景

    /** 开始一帧。像素层 vw×vh 虚拟像素，屏幕 sw×sh 像素，每个虚拟像素 scale 个屏幕像素。 */
    void begin(int vw, int vh, int sw, int sh, float scale);

    /**
     * 像素层贴图：取 tex 里 (sx, sy, w, h) 这一块，它的挂点 (pivotX, pivotY) 放在 (x, y)，
     * 先缩放 (scaleX, scaleY)（-1 是水平翻转）再顺时针转 deg 度。mul 是 ARGB 乘色，add 是 RGB 加色（受击闪白）。
     */
    void sprite(int tex, int sx, int sy, int w, int h, float x, float y, float pivotX, float pivotY,
                float deg, float scaleX, float scaleY, int mul, int add);

    void rect(float x, float y, float w, float h, int argb);

    /** 圆、环、扇形：外半径 rOut，内半径 rIn（≤0 是实心），从 a0 弧度起顺时针扫 aLen 弧度（≥2π 是整圈；0 指向右，y 向下）。 */
    void shape(float cx, float cy, float rOut, float rIn, float a0, float aLen, int argb);

    void line(float x0, float y0, float x1, float y1, float width, int argb);

    /** 之后画的东西用加色混合（发光）还是普通混合。 */
    void additive(boolean on);

    /** 像素层画完：最近邻放大铺满屏幕。之后的 hud() 画在屏幕分辨率上。 */
    void present();

    /** 屏幕分辨率层：从 HudText 图集贴一块，左上角在 (x, y) 屏幕像素，乘色 mul。 */
    void hud(int sx, int sy, int w, int h, float x, float y, int mul);

    void end();
}
