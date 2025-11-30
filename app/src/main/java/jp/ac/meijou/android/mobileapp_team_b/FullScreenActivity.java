package jp.ac.meijou.android.mobileapp_team_b;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

// 画像の全画面表示用
public class FullScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen);

        ImageView imageView = findViewById(R.id.imgFullScreen);

        // 前の画面から渡された画像のURI(住所)を受け取る
        String uriString = getIntent().getStringExtra("imageUri");

        if (uriString != null) {
            Uri imageUri = Uri.parse(uriString);

            // Glideを使って画像を表示
            Glide.with(this)
                    .load(imageUri)
                    .into(imageView);
        }

        // 画像をタップしたら元の画面に戻る（オプション）
        imageView.setOnClickListener(v -> finish());
    }
}