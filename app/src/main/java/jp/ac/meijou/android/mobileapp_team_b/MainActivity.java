package jp.ac.meijou.android.mobileapp_team_b;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayoutMediator;

import java.io.ByteArrayOutputStream;

import jp.ac.meijou.android.mobileapp_team_b.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    // カメラ用
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    // 検索用
    private SearchController searchController;

    // ViewPager用
    private MainPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Android 11 (API 30) 以上: 全ファイル管理権限（※必要なら）
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "「すべてのファイルの管理」を許可してください", Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // --- カメラ権限リクエスト用ランチャー ---
        cameraPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        isGranted -> {
                            if (isGranted) openCamera();
                            else Toast.makeText(this, "カメラ権限がないため起動できません", Toast.LENGTH_SHORT).show();
                        }
                );

        // --- カメラ結果を受け取るランチャー ---
        cameraLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                Bundle extras = result.getData().getExtras();
                                if (extras != null) {
                                    Bitmap bitmap = (Bitmap) extras.get("data");
                                    if (bitmap != null) {
                                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                                        byte[] bytes = stream.toByteArray();

                                        Intent intent = new Intent(this, AiTest.class);
                                        intent.putExtra("captured_image", bytes);
                                        startActivity(intent);
                                    }
                                }
                            }
                        }
                );

        // --- Cameraボタン ---
        binding.buttonCamera.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            }
        });

        // --- AITestボタン ---
        binding.aiTestButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, AiTest.class);
            startActivity(intent);
        });

        // --- ダークモード（今の君の処理を保持：必要なとこだけ） ---
        binding.switch3.setOnClickListener(v -> {
            boolean isChecked = binding.switch3.isChecked();
            int tabTextColor;

            if (isChecked) {
                binding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background));
                binding.tabLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background));
                binding.switch3.setTextColor(ContextCompat.getColor(this, R.color.dark_text));

                EditText searchEditText = binding.searchView.findViewById(androidx.appcompat.R.id.search_src_text);
                // ここで searchEditText の色変更とかもできる

                tabTextColor = ContextCompat.getColor(this, R.color.dark_text);
                binding.tabLayout.setTabTextColors(tabTextColor, tabTextColor);

            } else {
                binding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.light_background));
                binding.tabLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background));
                binding.switch3.setTextColor(ContextCompat.getColor(this, R.color.light_text));

                tabTextColor = ContextCompat.getColor(this, R.color.light_text);
                binding.tabLayout.setTabTextColors(tabTextColor, tabTextColor);
            }
        });

        // --- ViewPager2 & TabLayout ---
        pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(
                binding.tabLayout,
                binding.viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0: tab.setText("カテゴリ"); break;
                        case 1: tab.setText("最近使用した..."); break;
                        default: tab.setText("その他"); break;
                    }
                }
        ).attach();

        // =====================
        // 検索機能の配線（ここから）
        // =====================
        searchController = new SearchController();

        // 最初のタブを検索対象に
        setSearchTargetForPosition(0);

        // SearchView → SearchController
        binding.searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchController.onQueryChanged(newText == null ? "" : newText);
                return true;
            }
        });


        // タブ切替で検索対象Fragmentを更新
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                setSearchTargetForPosition(position);
                // 現在の検索文字を新しいタブにも反映（したい場合）
                searchController.onQueryChanged(binding.searchView.getQuery().toString());
            }
        });
    }

    private void setSearchTargetForPosition(int position) {
        Fragment f = pagerAdapter.getFragment(position);
        if (f instanceof Searchable) searchController.setTarget((Searchable) f);
        else searchController.setTarget(null);
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    // ViewPager2 Adapter（Fragmentを保持するので、検索対象を確実に取れる）
    static class MainPagerAdapter extends FragmentStateAdapter {

        private final Fragment[] fragments = new Fragment[] {
                new FolderFragment(),   // カテゴリ
                new RecentFragment(),   // 最近
                new OtherFragment()     // その他
        };

        public MainPagerAdapter(AppCompatActivity activity) { super(activity); }

        @Override public int getItemCount() { return fragments.length; }

        @Override public Fragment createFragment(int position) {
            return fragments[position];
        }

        public Fragment getFragment(int position) {
            return fragments[position];
        }
    }
}
