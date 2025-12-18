package jp.ac.meijou.android.mobileapp_team_b;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

public class RecentFragment extends Fragment {

    private RecyclerView.Adapter<?> adapter;

    public void refreshTheme() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (getView() != null) {
            ThemeOption t = ThemeCatalog.getThemes()
                    .get(ThemeManager.getThemeIndex());
            getView().setBackgroundColor(
                    requireContext().getColor(t.appBg)
            );
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 単純にテキストだけ表示するダミー
        TextView tv = new TextView(requireContext());
        tv.setText("最近使用した…（あとで実装）");
        tv.setGravity(Gravity.CENTER);
        return tv;
    }
}
