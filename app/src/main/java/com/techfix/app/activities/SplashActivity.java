package com.techfix.app.activities;

import com.techfix.app.R;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.techfix.app.databinding.ActivitySplashBinding;
import com.techfix.app.util.WindowInsetsHelper;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.splashRoot, binding.splashRoot);
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                startActivity(new Intent(this, HomeActivity.class)), 900);
    }
}
