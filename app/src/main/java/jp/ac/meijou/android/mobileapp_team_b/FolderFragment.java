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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.ac.meijou.android.mobileapp_team_b.databinding.FragmentFolderBinding;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONException;

public class FolderFragment extends Fragment implements Searchable {

    private FragmentFolderBinding binding;

    // 表示用（RecyclerViewが見るリスト）
    private final List<Bucket> data = new ArrayList<>();

    // 検索の元になる全件保持用
    private final List<Bucket> allData = new ArrayList<>();

    private BucketAdapter adapter;

    // 現在の検索文字（タブ切替・再読み込み時に再適用するため）
    private String currentQuery = "";

    // どの権限が必要かを判断(バージョンによって違うため)
    private String readImagesPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private final ActivityResultLauncher<String> reqReadPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) loadBuckets(); });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFolderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

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

        // ドラッグ＆ドロップで並び替えをする処理
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, // 上下のドラッグを許可
                0 // 横スワイプ（削除など）は今回は無効(0)にする
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {

                // 1. 移動元の位置と、移動先の位置を取得
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();

                // 2. アダプターに依頼して、見た目とリスト(data)を入れ替える
                adapter.moveItem(fromPos, toPos);

                // 3. (重要) 検索用の元データ(allData)も同期して入れ替えておく
                // これをしないと、検索フィルタを解除したときに順番が戻ってしまいます
                if (currentQuery.isEmpty() && fromPos < allData.size() && toPos < allData.size()) {
                    // 単純な検索なし状態なら、allDataも同じように入れ替える
                    try {
                        Collections.swap(allData, fromPos, toPos);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                return true; // 移動処理完了
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // スワイプ削除は今回実装しないので何もしない
            }

            // ドラッグ操作が終わった（指を離した）ときに保存を実行
            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 検索中じゃないときだけ保存
                if (currentQuery.isEmpty()) {
                    saveFolderOrder();
                }
            }

            // 検索中はドラッグできないようにする（データの不整合を防ぐため）
            @Override
            public boolean isLongPressDragEnabled() {
                return currentQuery.isEmpty();
            }
        });

        // 作成したヘルパーをRecyclerViewに取り付ける
        itemTouchHelper.attachToRecyclerView(binding.recyclerBuckets);


    }

    // アプリ起動時及びホーム画面に戻ってきたときに自動実行(更新)
    @Override
    public void onResume() {
        super.onResume();
        ensurePermissionAndLoad();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public void refreshTheme() {
        if (adapter != null) adapter.notifyDataSetChanged();
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

    // ==========
    // ここから検索対応（Searchable）
    // ==========
    @Override
    public void onSearchQueryChanged(String query) {
        currentQuery = (query == null) ? "" : query;
        applyFilter();
    }

    private void applyFilter() {
        if (adapter == null) return;

        String q = currentQuery.trim().toLowerCase();

        data.clear();
        if (q.isEmpty()) {
            data.addAll(allData);
        } else {
            for (Bucket b : allData) {
                if (b.bucketName != null && b.bucketName.toLowerCase().contains(q)) {
                    data.add(b);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }
    // ==========
    // 検索対応ここまで
    // ==========

    private void loadBuckets() {
        new Thread(() -> {
            List<Bucket> list = MediaStoreHelper.queryBuckets(requireContext());

            // "Trash" 以外だけを集めた新しいリストを作る
            List<Bucket> filteredList = new ArrayList<>();
            for (Bucket b : list) {
                if (!b.bucketName.equalsIgnoreCase("Trash")) {
                    filteredList.add(b);
                }
            }

            // ここで保存順に並び替える
            sortBucketsBySavedOrder(filteredList);

            requireActivity().runOnUiThread(() -> {
                // ✅ 元データ（全件）を更新
                allData.clear();
                allData.addAll(filteredList);

                // ✅ 現在の検索文字で表示を作り直す
                applyFilter();
            });
        }).start();
    }

    @Override
    public void onDestroyView() {
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
                    if (!name.isEmpty()) {
                        createNewFolder(name);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    // 実際にフォルダを新規作成してリストに追加する
    private void createNewFolder(String folderName) {
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File newFolder = new File(picturesDir, folderName);

        // 1. まず、物理的にフォルダが存在するか確認
        if (newFolder.exists()) {

            // 2. 存在する場合、「画面のリスト(data)」に既に含まれているかチェック
            boolean isVisible = false;
            for (Bucket bucket : data) {
                if (bucket.bucketName.equals(folderName)) {
                    isVisible = true;
                    break;
                }
            }

            if (isVisible) {
                Toast.makeText(requireContext(), "そのフォルダは既にリストにあります", Toast.LENGTH_SHORT).show();
                return;
            } else {
                Toast.makeText(requireContext(), "既存のフォルダを再表示します", Toast.LENGTH_SHORT).show();
            }

        } else {
            // 物理的に存在しない場合のみ、作成を実行
            boolean created = newFolder.mkdirs();
            if (!created) {
                Toast.makeText(requireContext(), "フォルダ作成に失敗しました", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(), "作成しました: " + folderName, Toast.LENGTH_SHORT).show();
        }

        // --- 以下、リストへの追加処理（新規作成時・再表示時 共通） ---
        // 画像がないのでMediaStoreからは自動で読み込まれないため、手動でBucketを作って足す
        Bucket newBucket = new Bucket();
        newBucket.bucketName = folderName;
        newBucket.bucketId = "MANUAL_" + folderName; // 仮のID
        newBucket.count = 0; // 空っぽなので0枚
        newBucket.coverUri = null; // 画像がないので表紙もなし

        // ✅ 元データにも追加して、検索状態に応じて表示を更新
        allData.add(0, newBucket);
        applyFilter();

        // 一番上までスクロール（検索で0件の時は意味ないので注意）
        binding.recyclerBuckets.scrollToPosition(0);
    }

    // ★追加: 現在のリストの並び順を保存する
    private void saveFolderOrder() {
        // フォルダ名のリストを作成
        JSONArray jsonArray = new JSONArray();
        for (Bucket b : allData) {
            jsonArray.put(b.bucketName);
        }

        // SharedPreferencesに保存
        SharedPreferences prefs = requireContext().getSharedPreferences("FolderPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("folder_order", jsonArray.toString()).apply();
    }

    // ★追加: 保存された順番通りにリストを並び替える
    private void sortBucketsBySavedOrder(List<Bucket> buckets) {
        SharedPreferences prefs = requireContext().getSharedPreferences("FolderPrefs", Context.MODE_PRIVATE);
        String jsonString = prefs.getString("folder_order", null);

        if (jsonString == null) return; // 保存データがなければ何もしない

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            List<String> savedOrder = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                savedOrder.add(jsonArray.getString(i));
            }

            // 保存されたリストの順番に基づいて並び替え
            Collections.sort(buckets, (b1, b2) -> {
                int index1 = savedOrder.indexOf(b1.bucketName);
                int index2 = savedOrder.indexOf(b2.bucketName);

                // 両方とも保存リストにある場合、その順序に従う
                if (index1 != -1 && index2 != -1) {
                    return Integer.compare(index1, index2);
                }
                // どちらかがリストにない（新規フォルダなど）場合、リストにない方を先頭(-1)にする
                // (ここはお好みで。index1 == -1 ? 1 : -1 だと末尾に行きます)
                if (index1 == -1 && index2 != -1) return -1;
                if (index1 != -1 && index2 == -1) return 1;

                return 0; // 両方ない場合はそのまま
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
