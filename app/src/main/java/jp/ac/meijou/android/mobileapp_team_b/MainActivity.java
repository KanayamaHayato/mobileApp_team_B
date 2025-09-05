package jp.ac.meijou.android.mobileapp_team_b;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SearchView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import jp.ac.meijou.android.mobileapp_team_b.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        //カメラ起動（暗黙的）
        binding.buttonCamera.setOnClickListener((view -> {
            var intent = new Intent();

            intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            startActivity(intent);

        }));
        /*
        //画面遷移（FolderActivity）
        binding.buttonOpenFolders.setOnClickListener(v ->
                startActivity(new Intent(this, FolderActivity.class))
        );

         */


        // ダークモード
        binding.switch3.setOnClickListener(v ->{
        boolean isChecked = binding.switch3.isChecked();
        int tabTextColor;

        if (isChecked) {
            // ダークモードに切り替え
            binding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background));
            binding.textView2.setTextColor(ContextCompat.getColor(this, R.color.dark_text));
            binding.tabLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background));
            binding.switch3.setTextColor(ContextCompat.getColor(this, R.color.dark_text));
            EditText searchEditText = binding.searchView.findViewById(androidx.appcompat.R.id.search_src_text);


            tabTextColor = ContextCompat.getColor(this, R.color.dark_text);
            binding.tabLayout.setTabTextColors(tabTextColor, tabTextColor);
        } else {
            // ライトモードに戻す
            binding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.light_background));
            binding.textView2.setTextColor(ContextCompat.getColor(this, R.color.light_text));
            binding.tabLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background));
            binding.switch3.setTextColor(ContextCompat.getColor(this, R.color.light_text));


            tabTextColor = ContextCompat.getColor(this, R.color.light_text);
            binding.tabLayout.setTabTextColors(tabTextColor, tabTextColor);
        }
        });
        binding.viewPager.setAdapter(new MainPagerAdapter(this));

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
        public MainPagerAdapter(AppCompatActivity activity) { super(activity); }
        @Override public int getItemCount() { return 3; }
        @Override public androidx.fragment.app.Fragment createFragment(int position) {
            if (position == 0) return new FolderFragment();   // ← ココがカテゴリ
            else if (position == 1) return new RecentFragment(); // ダミーでもOK
            else return new OtherFragment();                    // ダミーでもOK
        }
    }
}