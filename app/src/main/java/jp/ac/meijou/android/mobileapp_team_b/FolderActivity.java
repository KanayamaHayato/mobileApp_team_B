package jp.ac.meijou.android.mobileapp_team_b;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import jp.ac.meijou.android.mobileapp_team_b.databinding.ActivityFolderBinding;
import jp.ac.meijou.android.mobileapp_team_b.databinding.ActivityMainBinding;

public class FolderActivity extends AppCompatActivity {
    private ActivityFolderBinding binding;
    private RecyclerView recycler;
    private final List<Bucket> data = new ArrayList<>();
    private BucketAdapter adapter;

    private String readImagesPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private final ActivityResultLauncher<String> reqReadPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) loadBuckets(); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityFolderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //RecyclerView 初期化
        adapter = new BucketAdapter(this, data, bucket -> {
            //バケツをタップしたらフォルダ内グリッドへ遷移するようにする（未実装）
        });
        RecyclerView recycler = binding.recyclerBuckets;
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recycler.setAdapter(adapter);

        ensurePermissionAndLoad();
    }
    private void ensurePermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(this, readImagesPermission())
                == PackageManager.PERMISSION_GRANTED) {
            loadBuckets();
        } else {
            reqReadPerm.launch(readImagesPermission());
        }
    }

    private void loadBuckets() {
        new Thread(() -> {
            List<Bucket> list = MediaStoreHelper.queryBuckets(this);
            runOnUiThread(() -> {
                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }
}