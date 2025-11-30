package jp.ac.meijou.android.mobileapp_team_b;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jp.ac.meijou.android.mobileapp_team_b.databinding.FragmentFolderBinding;

public class FolderFragment extends Fragment {

    private FragmentFolderBinding binding;
    private final List<Bucket> data = new ArrayList<>();
    private BucketAdapter adapter;

    // どの権限が必要かを判断(バージョンによって違うため)
    private String readImagesPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private final ActivityResultLauncher<String> reqReadPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) loadBuckets(); });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFolderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new BucketAdapter(requireContext(), data, bucket -> {
            // ここで PhotoGridActivity へ遷移（そのまま流用可）
            startActivity(new android.content.Intent(requireContext(), PhotoGridActivity.class)
                    .putExtra("bucketId", bucket.bucketId)
                    .putExtra("bucketName", bucket.bucketName));
        });

        binding.recyclerBuckets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBuckets.setAdapter(adapter);

        // ＋ボタンを押したときの処理
        binding.folderAddButton.setOnClickListener(v -> showCreateFolderDialog());

        //onResumeで実行しているため必要なくなった(2回実行してしまい，エラーにはならないが無駄)
//        ensurePermissionAndLoad();
    }

    // アプリ起動時及びホーム画面に戻ってきたときに自動実行(更新)
    @Override
    public void onResume() {
        super.onResume();
        // データを再読み込みする
        ensurePermissionAndLoad();
    }


    // 必要な権限を持っているかを確認(無ければポップアップを出して要求)
    private void ensurePermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(), readImagesPermission())
                == PackageManager.PERMISSION_GRANTED) {
            loadBuckets();
        } else {
            reqReadPerm.launch(readImagesPermission()); // 画像を読み込む
        }
    }

    private void loadBuckets() {
        new Thread(() -> {
            List<Bucket> list = MediaStoreHelper.queryBuckets(requireContext());
            requireActivity().runOnUiThread(() -> {
                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }


    // フォルダ新規作成時，名前を入力するダイアログを表示
    private void showCreateFolderDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("フォルダ名を入力");

        new AlertDialog.Builder(requireContext())
                .setTitle("新しいフォルダを作成")
                .setView(input)
                .setPositiveButton("作成", (dialog, which) -> {
                    String name = input.getText().toString();
                    // 名前を入力した上で"作成"を押したらフォルダを新規作成
                    if (!name.isEmpty()) {
                        createNewFolder(name);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    // 実際にフォルダを新規作成してリストに追加する
    private void createNewFolder(String folderName) {
        // 保存場所を決める（ "Pictures" フォルダの中に作るようになっているが変更可）
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File newFolder = new File(picturesDir, folderName);

        // フォルダが存在しない場合のみ、新規作成する
        if (!newFolder.exists()) {
            boolean created = newFolder.mkdirs();
            if (!created) {
                Toast.makeText(requireContext(), "フォルダ作成に失敗しました", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(), "作成しました: " + folderName, Toast.LENGTH_SHORT).show();

            // 空のフォルダを作成した後，再起動等で表示されなくなった時(returnせずに下へ行くことで表示させる)
        } else {
            Toast.makeText(requireContext(), "既存のフォルダを表示します", Toast.LENGTH_SHORT).show();
        }

            //  画面のリストに手動で追加する
            // (注意: 画像がないのでMediaStoreからは自動で読み込まれないため、手動でBucketを作って足す)
            Bucket newBucket = new Bucket();
            newBucket.bucketName = folderName;
            newBucket.bucketId = "MANUAL_" + folderName; // 仮のID
            newBucket.count = 0; // 空っぽなので0枚
            newBucket.coverUri = null; // 画像がないので表紙もなし

            data.add(0, newBucket); // リストの一番上に追加
            adapter.notifyItemInserted(0); // 画面更新
            binding.recyclerBuckets.scrollToPosition(0); // 一番上までスクロール


    }
}
