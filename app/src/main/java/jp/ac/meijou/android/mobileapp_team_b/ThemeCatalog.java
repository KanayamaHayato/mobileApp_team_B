package jp.ac.meijou.android.mobileapp_team_b;

import java.util.Arrays;
import java.util.List;

import jp.ac.meijou.android.mobileapp_team_b.R;
import jp.ac.meijou.android.mobileapp_team_b.ThemeOption;

public class ThemeCatalog {
    public static List<ThemeOption> getThemes() {
        return Arrays.asList(
                new ThemeOption("緑 × 紫",
                        R.color.gp_background,
                        R.color.gp_title_background,
                        R.color.gp_button,
                        R.color.gp_button_stroke,
                        R.color.gp_bucket_background,
                        R.color.gp_button_stroke,
                        R.color.gp_text_primary
                ),
                new ThemeOption("青 × ピンク",
                        R.color.bp_background,
                        R.color.bp_title_background,
                        R.color.bp_button,
                        R.color.bp_button_stroke,
                        R.color.bp_bucket_background,
                        R.color.bp_button_stroke,
                        R.color.bp_text_primary
                )

        );
    }
}
