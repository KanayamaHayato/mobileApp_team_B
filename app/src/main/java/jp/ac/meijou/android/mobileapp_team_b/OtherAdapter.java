package jp.ac.meijou.android.mobileapp_team_b;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.List;

public class OtherAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TRASH = 0;
    private static final int TYPE_THEME = 1;

    private final Context ctx;
    private Bucket trashBucket; // 後からセットする

    // クリック時の動作を受け取るためのリスナー変数
    private final OnItemClickListener listener;
    private final Runnable onThemeChanged;


    // リスナーの定義インターフェース
    public interface OnItemClickListener {
        void onClick(Bucket bucket);
    }

    // コンストラクタでリスナーを受け取るように変更
    public OtherAdapter(Context context,OnItemClickListener listener,Runnable onThemeChanged){
            this.ctx = context;
            this.listener = listener;
            this.onThemeChanged = onThemeChanged;
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
        MaterialCardView card;
        ImageView imgCover;
        TextView txtName;
        TextView txtCount;

        TrashVH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.bucketCard); // ← ここ重要
            imgCover = itemView.findViewById(R.id.imgCover);
            txtName  = itemView.findViewById(R.id.txtName);
            txtCount = itemView.findViewById(R.id.txtCount);
        }
    }


    static class ThemeVH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        MaterialAutoCompleteTextView dropdown;

        ThemeVH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.themeRoot);
            dropdown = itemView.findViewById(R.id.themeDropdown);
        }
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

            // 現在テーマ
            ThemeOption t = ThemeCatalog.getThemes()
                    .get(ThemeManager.getThemeIndex());

            // カード背景（ごみ箱カードの面）
            h.card.setCardBackgroundColor(
                    ContextCompat.getColor(ctx, t.bucketBg)
            );

            // 枠線も変えたいなら（MaterialCardViewのみ）
            h.card.setStrokeColor(
                    ContextCompat.getColor(ctx, t.buttonStroke)
            );
            // Trashがまだ無い（ロード前）でも落ちないように仮データ
            Bucket b = (trashBucket != null) ? trashBucket : createPlaceholderTrash();

            h.txtName.setText(b.bucketName);
            h.txtCount.setText(b.count + " 枚");

            // Trashアイコン表示
            h.imgCover.setImageResource(android.R.drawable.ic_menu_delete);
            h.imgCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            h.imgCover.setBackgroundColor(android.graphics.Color.LTGRAY);

            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onClick(b);
            });

        } else {
            // Theme行
            ThemeVH h = (ThemeVH) holder;

            List<ThemeOption> themes = ThemeCatalog.getThemes(); // 下で作る
            ArrayAdapter<ThemeOption> ad = new ArrayAdapter<>(ctx,
                    android.R.layout.simple_list_item_1, themes);
            h.dropdown.setAdapter(ad);

// 初期表示
            ThemeOption current = themes.get(ThemeManager.getThemeIndex());
            h.dropdown.setOnItemClickListener(null);
            h.dropdown.setText(current.name, false);

// 選択時
            h.dropdown.setOnItemClickListener((parent, view, idx, id) -> {
                ThemeManager.setThemeIndex(idx);
                notifyItemChanged(1);              // ★Theme行だけ更新
                if (onThemeChanged != null) onThemeChanged.run();
            });
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
