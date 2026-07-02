package com.pinyin.ime;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/**
 * 设置 Fragment，加载 res/xml/prefs.xml。
 * 另提供跳转到系统输入法设置（启用本输入法 / 选择当前输入法）的项。
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.prefs, rootKey);

        // 跳转：启用输入法（系统“语言与输入法”设置）
        Preference enablePref = findPreference("pref_enable_ime");
        if (enablePref != null) {
            enablePref.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                return true;
            });
        }

        // 跳转：选择当前输入法（系统输入法选择器）
        Preference switchPref = findPreference("pref_switch_ime");
        if (switchPref != null) {
            switchPref.setOnPreferenceClickListener(p -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
                return true;
            });
        }
    }
}
