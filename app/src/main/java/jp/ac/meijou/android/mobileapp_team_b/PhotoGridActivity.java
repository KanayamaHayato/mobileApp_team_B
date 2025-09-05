package jp.ac.meijou.android.mobileapp_team_b;

import android.content.ContentUris;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

public class PhotoGridActivity extends AppCompatActivity {

    private final List<Uri> data = new ArrayList<>();
    private RecyclerView recycler;
    private PhotoAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_photo_grid);

        String bucketId = getIntent().getStringExtra("bucketId");
        String bucketName = getIntent().getStringExtra("bucketName");
        setTitle(bucketName == null ? "Photos" : bucketName);

        recycler = findViewById(R.id.recyclerPhotos);
        recycler.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new PhotoAdapter();
        recycler.setAdapter(adapter);

        if (bucketId == null) {
            Toast.makeText(this, "bucketId がありません", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadPhotos(bucketId);
    }

    private void loadPhotos(String bucketId) {
        new Thread(() -> {
            List<Uri> list = queryPhotosInBucket(bucketId);
            runOnUiThread(() -> {
                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private List<Uri> queryPhotosInBucket(String bucketId) {
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{ MediaStore.Images.Media._ID };
        String selection = MediaStore.Images.Media.BUCKET_ID + "=?";
        String[] args = new String[]{ bucketId };
        String orderBy = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        List<Uri> result = new ArrayList<>();
        try (var c = getContentResolver().query(collection, projection, selection, args, orderBy)) {
            if (c == null) return result;
            int colId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (c.moveToNext()) {
                long id = c.getLong(colId);
                result.add(ContentUris.withAppendedId(collection, id));
            }
        }
        return result;
    }

    //最小アダプタ
    class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView img;
            VH(View v) { super(v); img = v.findViewById(R.id.img); }
        }
        @Override public VH onCreateViewHolder(ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext()).inflate(R.layout.item_photo, p, false);
            return new VH(view);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            Uri uri = data.get(pos);
            Glide.with(h.img.getContext()).load(uri).centerCrop().into(h.img);
            //拡大へ飛ばしたいならここでIntent
            h.itemView.setOnClickListener(v ->
                    Toast.makeText(v.getContext(), uri.toString(), Toast.LENGTH_SHORT).show()
            );
        }
        @Override public int getItemCount() { return data.size(); }
    }
}
