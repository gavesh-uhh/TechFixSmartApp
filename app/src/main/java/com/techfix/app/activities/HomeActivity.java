package com.techfix.app.activities;

import com.techfix.app.R;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.techfix.app.databinding.ActivityHomeBinding;
import com.techfix.app.util.WindowInsetsHelper;

public class HomeActivity extends AppCompatActivity {
    private ActivityHomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.homeContent, binding.homeContent);

        binding.continueButton.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        binding.colomboChip.setOnClickListener(v -> map("6.9271,79.8612", "TechFix Colombo"));
        binding.galleChip.setOnClickListener(v -> map("6.0329,80.2168", "TechFix Galle"));
    }

    private void map(String point, String label) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + point + "?q=" + Uri.encode(label))));
    }
}
