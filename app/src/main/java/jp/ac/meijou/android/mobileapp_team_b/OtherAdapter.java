package jp.ac.meijou.android.mobileapp_team_b;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class OtherAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TRASH = 0;
    private static final int TYPE_THEME = 1;

    private final Context ctx;
    private Bucket trashBucket; // 後からセットする

    public OtherAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setTrashBucket(Bucket b) {
        this.trashBucket = b;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        // Trash + Theme の2行（Trashが未取得でも表示はする）
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0) ? TYPE_TRASH : TYPE_THEME;
    }

    // --- ViewHolders ---
    static class TrashVH extends RecyclerView.ViewHolder {
        BucketAdapter.VH inner; // 既存のbucket表示レイアウトを再利用したい場合
        TrashVH(@NonNull View itemView) {
            super(itemView);
            inner = new BucketAdapter.VH(itemView);
        }
    }

    static class ThemeVH extends RecyclerView.ViewHolder {
        ThemeVH(@NonNull View itemView) { super(itemView); }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(ctx);
        if (viewType == TYPE_TRASH) {
            View v = inf.inflate(R.layout.item_bucket, parent, false);
            return new TrashVH(v);
        } else {
            View v = inf.inflate(R.layout.item_theme_selector, parent, false);
            return new ThemeVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_TRASH) {
            TrashVH h = (TrashVH) holder;

            // Trashがまだ無い（ロード前）でも落ちないように仮データ
            Bucket b = (trashBucket != null) ? trashBucket : createPlaceholderTrash();

            h.inner.txtName.setText(b.bucketName);
            h.inner.txtCount.setText(b.count + " 枚");

            // Trash表示（既存BucketAdapterのロジック簡易版）
            h.inner.imgCover.setImageResource(android.R.drawable.ic_menu_delete);
            h.inner.imgCover.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            h.inner.imgCover.setBackgroundColor(android.graphics.Color.LTGRAY);

        } else {
            // Theme行：今は「表示だけ」なので何もしない
        }
    }

    private Bucket createPlaceholderTrash() {
        Bucket b = new Bucket();
        b.bucketName = "Trash";
        b.bucketId = "MANUAL_TRASH";
        b.count = 0;
        b.coverUri = null;
        return b;
    }
}
