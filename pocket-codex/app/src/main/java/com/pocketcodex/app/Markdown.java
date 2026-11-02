package com.pocketcodex.app;

import android.content.Context;
import android.text.Spanned;
import android.widget.TextView;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

/** Markdown 渲染（Markwon，原生 Spannable，不用 WebView；不加载远程图片、不渲染 HTML；链接只认 http/https）。 */
final class Markdown {
    private static Markwon instance;
    private static int themeKey;

    private Markdown() {
    }

    static synchronized Markwon get(Context c) {
        // 深浅色切换后颜色要跟着变
        int key = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (instance == null || key != themeKey) {
            themeKey = key;
            final Context app = c.getApplicationContext();
            instance = Markwon.builder(c)
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(c))
                    .usePlugin(TaskListPlugin.create(c))
                    .usePlugin(new AbstractMarkwonPlugin() {
                        @Override
                        public void configureTheme(MarkwonTheme.Builder b) {
                            b.codeBackgroundColor(Ui.color(c, R.color.code_bg))
                                    .codeBlockBackgroundColor(Ui.color(c, R.color.code_bg))
                                    .codeTextColor(Ui.color(c, R.color.text))
                                    .codeBlockTextColor(Ui.color(c, R.color.text))
                                    .codeTextSize((int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 13, c.getResources().getDisplayMetrics()))
                                    .codeBlockMargin(Ui.dp(c, 12))
                                    .linkColor(Ui.color(c, R.color.primary))
                                    .blockQuoteColor(Ui.color(c, R.color.border))
                                    .headingBreakHeight(0)
                                    .thematicBreakColor(Ui.color(c, R.color.border))
                                    .bulletWidth(Ui.dp(c, 5));
                        }

                        @Override
                        public void configureConfiguration(MarkwonConfiguration.Builder b) {
                            b.linkResolver((view, link) -> {
                                String l = link.toLowerCase(java.util.Locale.ROOT);
                                if (!l.startsWith("http://") && !l.startsWith("https://")) return; // 不启动别的 scheme
                                try {
                                    android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                            .addCategory(android.content.Intent.CATEGORY_BROWSABLE).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                                    app.startActivity(i);
                                } catch (Exception ignored) {
                                    // 没有浏览器
                                }
                            });
                        }
                    })
                    .build();
        }
        return instance;
    }

    static Spanned render(Context c, String md) {
        return get(c).toMarkdown(md);
    }

    static void setTo(TextView tv, Spanned s) {
        get(tv.getContext()).setParsedMarkdown(tv, s);
    }
}
