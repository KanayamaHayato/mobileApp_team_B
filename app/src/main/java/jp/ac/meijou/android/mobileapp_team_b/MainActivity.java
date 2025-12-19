package jp.ac.meijou.android.mobileapp_team_b;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.ByteArrayOutputStream;

import jp.ac.meijou.android.mobileapp_team_b.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    // カメラ用
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    // テーマ替え（KNY_br9側）
    private ConstraintLayout mainLayout;
    private View topBackground;
    private MaterialButton cameraButton;
    private MaterialButton AIButton;
    private MaterialButton folderAddButton;


    // 検索用（master側）
    private SearchController searchController;

    // ViewPager用
    private MainPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Android 11 (API 30) 以上: 全ファイル管理権限（必要なら）
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

        // binding はここで1回だけ
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // -----------------------
        // テーマ替え（統合）
        // -----------------------
        mainLayout = binding.main;
        topBackground = binding.topBackground;
        cameraButton = binding.buttonCamera;
        AIButton = binding.aiTestButton;
        folderAddButton  = binding.folderAddButton;



        ThemeOption initialTheme =
                ThemeCatalog.getThemes().get(ThemeManager.getThemeIndex());
        applyTheme(initialTheme);


        // -----------------------
        // カメラランチャー
        // -----------------------
        cameraPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        isGranted -> {
                            if (isGranted) openCamera();
                            else Toast.makeText(this, "カメラ権限がないため起動できません", Toast.LENGTH_SHORT).show();
                        }
                );

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

        binding.folderAddButton.setOnClickListener(v -> {
            Fragment f = pagerAdapter.getFragment(0);
            if (f instanceof FolderFragment) {
                ((FolderFragment) f).requestCreateFolder();
            }
        });

        // Cameraボタン
        binding.buttonCamera.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            }
        });

        // AITestボタン
        binding.aiTestButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, AiTest.class);
            startActivity(intent);
        });

        // -----------------------
        // ViewPager2 & TabLayout
        // -----------------------
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

        // -----------------------
        // 検索機能（統合）
        // -----------------------
        searchController = new SearchController();
        setSearchTargetForPosition(0);

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

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                setSearchTargetForPosition(position);
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
    //テーマ切り替え用
    private void applyTheme(ThemeOption t) {
        // 背景系
        binding.main.setBackgroundColor(
                ContextCompat.getColor(this, t.appBg)
        );
        binding.topBackground.setBackgroundColor(
                ContextCompat.getColor(this, t.titleBg)
        );
        binding.tabLayout.setBackgroundColor(
                ContextCompat.getColor(this, t.titleBg)
        );

        // 共通のボタン色
        ColorStateList btnBg =
                ColorStateList.valueOf(ContextCompat.getColor(this, t.buttonBg));
        ColorStateList btnStroke =
                ColorStateList.valueOf(ContextCompat.getColor(this, t.buttonStroke));

        // Camera
        cameraButton.setBackgroundTintList(btnBg);
        cameraButton.setStrokeColor(btnStroke);

        // AI
        AIButton.setBackgroundTintList(btnBg);
        AIButton.setStrokeColor(btnStroke);

        // ＋
        folderAddButton.setBackgroundTintList(btnBg);
        folderAddButton.setStrokeColor(btnStroke);
    }


    // Fragmentを保持するAdapter（検索対象を確実に取れる）
    static class MainPagerAdapter extends FragmentStateAdapter {

        private final Fragment[] fragments = new Fragment[] {
                new FolderFragment(),
                new RecentFragment(),
                new OtherFragment()
        };

        public MainPagerAdapter(AppCompatActivity activity) { super(activity); }

        @Override public int getItemCount() { return fragments.length; }

        @Override public Fragment createFragment(int position) { return fragments[position]; }

        public Fragment getFragment(int position) { return fragments[position]; }
    }


    public void applyThemeFromManager() {
        // Fragment 側にも再描画を伝える（任意だが安全）
        ThemeOption t = ThemeCatalog.getThemes().get(ThemeManager.getThemeIndex());
        applyTheme(t); // ★ MainActivity側も塗り直す

        ((FolderFragment) pagerAdapter.getFragment(0)).refreshTheme();
        ((RecentFragment) pagerAdapter.getFragment(1)).refreshTheme();
        ((OtherFragment)  pagerAdapter.getFragment(2)).refreshTheme();

    }

}

