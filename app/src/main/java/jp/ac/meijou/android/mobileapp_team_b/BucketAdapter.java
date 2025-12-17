// src/main/java/.../adapters/BucketAdapter.java
package jp.ac.meijou.android.mobileapp_team_b;

import android.content.Context;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;
import jp.ac.meijou.android.mobileapp_team_b.R;
import jp.ac.meijou.android.mobileapp_team_b.Bucket;

public class BucketAdapter extends RecyclerView.Adapter<BucketAdapter.VH> {

    public interface OnBucketClick { void onClick(Bucket bucket); }

    private final Context ctx;
    private final List<Bucket> items;
    private final OnBucketClick onClick;

    public BucketAdapter(Context ctx, List<Bucket> items, OnBucketClick onClick) {
        this.ctx = ctx; this.items = items; this.onClick = onClick;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgCover; TextView txtName; TextView txtCount; View bucketRoot;
        VH(@NonNull View v) {
            super(v);
            imgCover = v.findViewById(R.id.imgCover);
            txtName  = v.findViewById(R.id.txtName);
            txtCount = v.findViewById(R.id.txtCount);
            bucketRoot = v.findViewById(R.id.bucketRoot);
        }
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_bucket, parent, false);
        return new VH(v);
    }

    // データを表示
    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Bucket b = items.get(pos);
        //テーマによる bucket 背景切替
        int bgRes = ThemeManager.isBluePink()
                ? R.color.bp_bucket_background
                : R.color.gp_bucket_background;

        h.bucketRoot.setBackgroundColor(
                ContextCompat.getColor(ctx, bgRes)
        );

        h.txtName.setText(b.bucketName == null ? "(Unknown)" : b.bucketName); // フォルダ名を表示
        h.txtCount.setText(b.count + " 枚"); //写真の枚数を表示

        // Trashフォルダならアイコン、それ以外なら写真を表示

        if (b.bucketName != null && b.bucketName.equalsIgnoreCase("Trash")) {
            // Trashの場合
            // 写真読み込みをキャンセル（前の画像が残らないように）
            Glide.with(ctx).clear(h.imgCover);

            // Android標準のごみ箱アイコンをセット
            // (もし自作の画像を使いたい場合は R.drawable.my_trash_icon に変えてください)
            h.imgCover.setImageResource(android.R.drawable.ic_menu_delete);

            // アイコンが見やすいようにサイズ調整（写真はCropだがアイコンは全体表示）
            h.imgCover.setScaleType(ImageView.ScaleType.FIT_CENTER);

            // アイコンが見やすいように背景色を少しグレーにする
             h.imgCover.setBackgroundColor(android.graphics.Color.LTGRAY);

        } else {
            // Trash以外の場合（通常）
            h.imgCover.setScaleType(ImageView.ScaleType.CENTER_CROP); // 枠いっぱいに表示

            if (b.coverUri != null) {
                Glide.with(ctx).load(b.coverUri).centerCrop().into(h.imgCover);
            } else {
                // 画像がない空フォルダの場合
                h.imgCover.setImageResource(android.R.drawable.ic_menu_gallery); // 適当なデフォルト画像
            }
        }

        // タップ時の処理
        h.itemView.setOnClickListener(v -> { if (onClick != null) onClick.onClick(b); });
    }

    @Override public int getItemCount() { return items.size(); }


    /**
     * アイテムの並び順を入れ替えるメソッド
     * FolderFragment から呼ばれます
     */
    public void moveItem(int fromPosition, int toPosition) {
        Bucket fromItem = items.get(fromPosition);
        items.remove(fromPosition);
        items.add(toPosition, fromItem);
        notifyItemMoved(fromPosition, toPosition);
    }

}
