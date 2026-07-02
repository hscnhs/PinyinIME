package com.pinyin.ime;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceFragmentCompat;

/**
 * 设置入口 Activity。同时提供跳转到系统“输入法启用 / 切换”页的便捷入口。
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState == null) {
            FragmentManager fm = getSupportFragmentManager();
            Fragment existing = fm.findFragmentById(R.id.settings_container);
            if (existing == null) {
                fm.beginTransaction()
                        .replace(R.id.settings_container, new SettingsFragment())
                        .commit();
            }
        }
    }
}
