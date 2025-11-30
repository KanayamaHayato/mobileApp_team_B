// src/main/java/.../adapters/BucketAdapter.java
package jp.ac.meijou.android.mobileapp_team_b;

import android.content.Context;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
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
        ImageView imgCover; TextView txtName; TextView txtCount;
        VH(@NonNull View v) {
            super(v);
            imgCover = v.findViewById(R.id.imgCover);
            txtName  = v.findViewById(R.id.txtName);
            txtCount = v.findViewById(R.id.txtCount);
        }
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_bucket, parent, false);
        return new VH(v);
    }

    // データを表示
    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Bucket b = items.get(pos);
        h.txtName.setText(b.bucketName == null ? "(Unknown)" : b.bucketName); // フォルダ名を表示
        h.txtCount.setText(b.count + " 枚"); //写真の枚数を表示
        Glide.with(ctx).load(b.coverUri).centerCrop().into(h.imgCover); // 画像を表示する(Glideというライブラリを使用)
        // タップ時の処理
        h.itemView.setOnClickListener(v -> { if (onClick != null) onClick.onClick(b); });
    }

    @Override public int getItemCount() { return items.size(); }
}
