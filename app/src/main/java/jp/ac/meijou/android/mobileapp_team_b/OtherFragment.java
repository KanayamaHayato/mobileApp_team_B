package jp.ac.meijou.android.mobileapp_team_b;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class OtherFragment extends Fragment {

    private RecyclerView recyclerView;
    private BucketAdapter adapter;
    private final List<Bucket> data = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 先ほど作ったレイアウトを読み込む
        return inflater.inflate(R.layout.fragment_other, container, false);

//        TextView tv = new TextView(requireContext());
//        tv.setText("その他（設定など）");
//        tv.setGravity(Gravity.CENTER);
//        return tv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerOther);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // アダプターの設定 (タップしたらPhotoGridActivityへ移動)
        adapter = new BucketAdapter(requireContext(), data, bucket -> {
            Intent intent = new Intent(requireContext(), PhotoGridActivity.class);
            intent.putExtra("bucketId", bucket.bucketId);
            intent.putExtra("bucketName", bucket.bucketName);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTrashBucket();
    }

    // "Trash" フォルダだけを探して表示する
    private void loadTrashBucket() {
        new Thread(() -> {
            // 全部のフォルダを取得
            List<Bucket> allList = MediaStoreHelper.queryBuckets(requireContext());

            Bucket trashBucket = null;

            // リストの中から "Trash" を探す
            for (Bucket b : allList) {
                if (b.bucketName.equalsIgnoreCase("Trash")) {
                    trashBucket = b;
                    break;
                }
            }

            // もし見つからなかった場合（まだゴミ箱が空で存在しない場合）
            // 手動で「0枚のゴミ箱」を作って表示させる
            if (trashBucket == null) {
                trashBucket = new Bucket();
                trashBucket.bucketName = "Trash";
                trashBucket.bucketId = "MANUAL_TRASH";
                trashBucket.count = 0;
                trashBucket.coverUri = null;
            }

            // 画面更新用に、final変数にする
            Bucket finalTrashBucket = trashBucket;

            requireActivity().runOnUiThread(() -> {
                data.clear();
                data.add(finalTrashBucket); // Trashだけを追加
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

}
