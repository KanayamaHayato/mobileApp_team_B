package jp.ac.meijou.android.mobileapp_team_b;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;

import jp.ac.meijou.android.mobileapp_team_b.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    // カメラ用ランチャーを追加
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    // テーマ替え
    private ConstraintLayout mainLayout;
    private View topBackground;
    private MaterialButton cameraButton;
    private Switch themeSwitch;
    private MainPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //テーマ替え
        mainLayout = binding.main;                  // ← layoutの android:id="@+id/main"
        topBackground = binding.topBackground;      // ← Viewの android:id="@+id/topBackground"
        cameraButton = binding.buttonCamera;
        themeSwitch = binding.switch3;

        // 初期テーマ（緑紫で開始するなら）
        setGreenPurpleTheme();
        themeSwitch.setChecked(false);
        themeSwitch.setText("青 × ピンク");

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ThemeManager.setBluePink(isChecked);

            if (isChecked) {
                setBluePinkTheme();
                themeSwitch.setText("緑 × 紫");
            } else {
                setGreenPurpleTheme();
                themeSwitch.setText("青 × ピンク");
            }
            if (binding.viewPager.getCurrentItem() == 0 && pagerAdapter != null) {
                pagerAdapter.getFolderFragment().refreshTheme();
            }
        });

        // Android 11 (API 30) 以上の場合、強力な権限を取りに行く
        if (Build.VERSION.SDK_INT >= 30) {
            // まだ権限を持っていない場合だけ実行
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "「すべてのファイルの管理」を許可してください", Toast.LENGTH_LONG).show();

                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivity(intent);
                } catch (Exception e) {
                    // 万が一機種によって上記が開けない場合の予備
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        }

        // --- カメラ権限リクエスト用ランチャー ---
        cameraPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        isGranted -> {
                            if (isGranted) {
                                // 許可されたらカメラを開く
                                openCamera();
                            } else {
                                Toast.makeText(this, "カメラ権限がないため起動できません", Toast.LENGTH_SHORT).show();
                            }
                        }
                );

        // カメラ結果を受け取るランチャー
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


        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });




        // カメラ起動（結果を受け取る）
        //  INTENT_ACTION_STILL_IMAGE_CAMERA はただカメラアプリを開くだけなので
        //  ACTION_IMAGE_CAPTURE の「撮った画像を返してくれる」インテントに変更
        binding.buttonCamera.setOnClickListener(view -> {
            // 権限があるか確認
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED) {
                // もう許可されてる → そのままカメラ起動
                openCamera();
            } else {
                // まだ許可されていない → 権限をリクエスト
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            }
        });



        /*
        //画面遷移（FolderActivity）
        //binding.buttonOpenFolders.setOnClickListener(v ->
                //startActivity(new Intent(this, FolderActivity.class))
        //);

         */

        // aiTestボタンを推したらテストページに移動するように
        // activity_main.xmlファイルのボタンと同様，不要になったら消して下さい
        binding.aiTestButton.setOnClickListener((view -> {
            var intent = new Intent(this, AiTest.class);
            startActivity(intent);
        }));

        /*
        // ダークモード
        binding.switch3.setOnClickListener(v ->{
        boolean isChecked = binding.switch3.isChecked();
        int tabTextColor;

        if (isChecked) {
            // ダークモードに切り替え
            binding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background));
//            binding.textView2.setTextColor(ContextCompat.getColor(this, R.color.dark_text)); // アプリ名は変えないように
            binding.tabLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background));
            binding.switch3.setTextColor(ContextCompat.getColor(this, R.color.dark_text));
            EditText searchEditText = binding.searchView.findViewById(androidx.appcompat.R.id.search_src_text);


            tabTextColor = ContextCompat.getColor(this, R.color.dark_text);
            binding.tabLayout.setTabTextColors(tabTextColor, tabTextColor);
        } else {
            // ライトモードに戻す
            binding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.light_background));
//            binding.textView2.setTextColor(ContextCompat.getColor(this, R.color.light_text));
            binding.tabLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background));
            binding.switch3.setTextColor(ContextCompat.getColor(this, R.color.light_text));


            tabTextColor = ContextCompat.getColor(this, R.color.light_text);
            binding.tabLayout.setTabTextColors(tabTextColor, tabTextColor);
        }
        });*/
        pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);


        new com.google.android.material.tabs.TabLayoutMediator(
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
    }
    static class MainPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        private final FolderFragment folderFragment = new FolderFragment();
        private final RecentFragment recentFragment = new RecentFragment();
        private final OtherFragment otherFragment = new OtherFragment();

        public MainPagerAdapter(AppCompatActivity activity) { super(activity); }

        @Override public int getItemCount() { return 3; }

        @Override public androidx.fragment.app.Fragment createFragment(int position) {
            if (position == 0) return folderFragment;
            else if (position == 1) return recentFragment;
            else return otherFragment;
        }

        public FolderFragment getFolderFragment() {
            return folderFragment;
        }
    }


    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    //緑紫テーマ
    private void setGreenPurpleTheme() {
        int bg = ContextCompat.getColor(this, R.color.gp_background);
        int title = ContextCompat.getColor(this, R.color.gp_title_background);
        int btn = ContextCompat.getColor(this, R.color.gp_button);
        int stroke = ContextCompat.getColor(this, R.color.gp_button_stroke);

        binding.main.setBackgroundColor(bg);
        binding.topBackground.setBackgroundColor(title);

        binding.tabLayout.setBackgroundColor(title); // ← TabLayoutも変える

        cameraButton.setBackgroundTintList(ColorStateList.valueOf(btn));
        cameraButton.setStrokeColor(ColorStateList.valueOf(stroke)); // ← 淵も変える
    }

    //青ピンクテーマ
    private void setBluePinkTheme() {
        int bg = ContextCompat.getColor(this, R.color.bp_background);
        int title = ContextCompat.getColor(this, R.color.bp_title_background);
        int btn = ContextCompat.getColor(this, R.color.bp_button);
        int stroke = ContextCompat.getColor(this, R.color.bp_button_stroke);

        binding.main.setBackgroundColor(bg);
        binding.topBackground.setBackgroundColor(title);

        binding.tabLayout.setBackgroundColor(title);

        cameraButton.setBackgroundTintList(ColorStateList.valueOf(btn));
        cameraButton.setStrokeColor(ColorStateList.valueOf(stroke));
    }

}