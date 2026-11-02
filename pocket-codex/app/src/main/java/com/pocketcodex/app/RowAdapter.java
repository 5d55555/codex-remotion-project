package com.pocketcodex.app;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** 通用列表：图标 + 标题 + 副标题 + 右侧小字 + 状态。会话列表和文件列表共用。 */
final class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {
    static final class Row {
        final Object key;
        final int icon, iconTint;
        final CharSequence title, subtitle, meta, status;
        final int maxSubtitleLines;

        Row(Object key, int icon, int iconTint, CharSequence title, CharSequence subtitle, CharSequence meta, CharSequence status, int maxSubtitleLines) {
            this.key = key;
            this.icon = icon;
            this.iconTint = iconTint;
            this.title = title;
            this.subtitle = subtitle;
            this.meta = meta;
            this.status = status;
            this.maxSubtitleLines = maxSubtitleLines;
        }
    }

    interface OnRow {
        void onRow(Row r, View v, boolean longPress);
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnRow on;

    RowAdapter(OnRow on) {
        this.on = on;
        setHasStableIds(true);
    }

    void set(List<Row> l) {
        rows.clear();
        rows.addAll(l);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int i) {
        return rows.get(i).key.hashCode();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title, subtitle, meta, status;

        VH(LinearLayout v, ImageView icon, TextView title, TextView subtitle, TextView meta, TextView status) {
            super(v);
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.meta = meta;
            this.status = status;
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int type) {
        Context c = parent.getContext();
        LinearLayout row = Ui.hbox(c);
        row.setGravity(android.view.Gravity.TOP);
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
        row.setMinimumHeight(Ui.dp(c, 64));
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ImageView icon = Ui.icon(c, R.drawable.ic_chat, R.color.text2, 24);
        LinearLayout.LayoutParams ilp = (LinearLayout.LayoutParams) icon.getLayoutParams();
        ilp.topMargin = Ui.dp(c, 2);
        row.addView(icon, ilp);
        LinearLayout mid = Ui.vbox(c);
        LinearLayout top = Ui.hbox(c);
        TextView title = Ui.text(c, "", 16, R.color.text);
        title.setSingleLine();
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, Ui.weight1());
        TextView meta = Ui.small(c, "");
        LinearLayout.LayoutParams mlp = Ui.wrap();
        mlp.leftMargin = Ui.dp(c, 8);
        top.addView(meta, mlp);
        mid.addView(top);
        TextView sub = Ui.caption(c, "");
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(sub, Ui.matchWrap(c, 2));
        TextView status = Ui.text(c, "", 12, R.color.primary);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        mid.addView(status, Ui.matchWrap(c, 4));
        LinearLayout.LayoutParams lp = Ui.weight1();
        lp.leftMargin = Ui.dp(c, 16);
        row.addView(mid, lp);
        return new VH(row, icon, title, sub, meta, status);
    }

    @Override
    public void onBindViewHolder(VH h, int i) {
        final Row r = rows.get(i);
        Context c = h.itemView.getContext();
        h.icon.setImageResource(r.icon);
        h.icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, r.iconTint)));
        h.title.setText(r.title);
        h.subtitle.setText(r.subtitle);
        h.subtitle.setVisibility(r.subtitle == null || r.subtitle.length() == 0 ? View.GONE : View.VISIBLE);
        h.subtitle.setMaxLines(r.maxSubtitleLines);
        h.meta.setText(r.meta);
        h.status.setText(r.status);
        h.status.setVisibility(r.status == null || r.status.length() == 0 ? View.GONE : View.VISIBLE);
        h.itemView.setOnClickListener(v -> on.onRow(r, v, false));
        h.itemView.setOnLongClickListener(v -> {
            on.onRow(r, v, true);
            return true;
        });
    }
}
