package jp.ac.meijou.android.mobileapp_team_b;

public class ThemeManager {
    private static int themeIndex = 0; // 0:緑紫, 1:青ピンク, 2...追加

    public static int getThemeIndex() { return themeIndex; }
    public static void setThemeIndex(int idx) { themeIndex = idx; }
}
