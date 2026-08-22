package com.techfix.app.activities;

import com.techfix.app.R;

import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.UserDAO;
import com.techfix.app.databinding.ActivityLoginBinding;
import com.techfix.app.models.User;
import com.techfix.app.models.UserRole;
import com.techfix.app.session.SessionManager;

public class LoginActivity extends AppCompatActivity {
    private enum Mode { SIGN_IN, SIGN_UP, STAFF }

    private ActivityLoginBinding binding;
    private Mode mode = Mode.SIGN_IN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) { resume(session); return; }
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.loginButton.setOnClickListener(this::submit);
        binding.switchAuthButton.setOnClickListener(v -> show(mode == Mode.SIGN_UP ? Mode.SIGN_IN : Mode.SIGN_UP));
        binding.staffModeButton.setOnClickListener(v -> show(mode == Mode.STAFF ? Mode.SIGN_IN : Mode.STAFF));
        com.techfix.app.util.WindowInsetsHelper.apply(binding.loginContent, binding.loginContent);
        binding.passwordInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                int score = Math.min(4, s.length() / 3);
                binding.passwordStrength.setProgressCompat(score, true);
                binding.strengthLabel.setText(score < 2 ? "Weak password" : score < 4 ? "Good password" : "Strong password");
            }
            public void afterTextChanged(Editable e) { }
        });
        show(Mode.SIGN_IN);
    }

    private void resume(SessionManager session) {
        Class<?> target = session.getRole() == UserRole.STAFF ? StaffActivity.class : CustomerActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }

    private void show(Mode next) {
        mode = next;
        boolean signUp = mode == Mode.SIGN_UP, staff = mode == Mode.STAFF;
        binding.nameLayout.setVisibility(signUp ? View.VISIBLE : View.GONE);
        binding.passwordStrength.setVisibility(signUp ? View.VISIBLE : View.GONE);
        binding.strengthLabel.setVisibility(signUp ? View.VISIBLE : View.GONE);
        binding.authTitle.setText(staff ? "Staff sign in" : signUp ? "Create account" : "Customer sign in");
        binding.authSubtitle.setText(staff ? "Access branch operations." : signUp ? "Create your repair profile." : "Track and manage your repairs.");
        binding.loginButton.setText(staff ? "Open staff workspace" : signUp ? "Create account" : "Sign in");
        binding.switchAuthButton.setText(signUp ? "Already have an account? Sign in" : "Create customer account");
        binding.switchAuthButton.setVisibility(staff ? View.GONE : View.VISIBLE);
        binding.staffModeButton.setText(staff ? "Customer sign in" : "Staff sign in");
        binding.staffHint.setVisibility(staff ? View.VISIBLE : View.GONE);
    }

    private void submit(View view) {
        String email = binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString();
        UserDAO users = new UserDAO(DatabaseHelper.getInstance(this));
        SessionManager session = new SessionManager(this);
        boolean valid;
        if (mode == Mode.STAFF) {
            User user = users.findByEmail(email);
            valid = user != null && user.role == UserRole.STAFF && users.authenticate(email, password);
            if (valid) { session.start(user.id, user.role); go(StaffActivity.class); }
        } else if (mode == Mode.SIGN_UP) {
            valid = users.create(binding.nameInput.getText().toString(), email, password);
            if (valid) { session.start(users.findByEmail(email).id, UserRole.CUSTOMER); go(CustomerActivity.class); }
        } else {
            valid = users.authenticate(email, password);
            if (valid) { session.start(users.findByEmail(email).id, users.findByEmail(email).role); go(CustomerActivity.class); }
        }
        if (!valid) Snackbar.make(view, mode == Mode.SIGN_UP ? "Check details or use another email" : "Email or password is incorrect", Snackbar.LENGTH_LONG).show();
    }

    private void go(Class<?> target) {
        startActivity(new android.content.Intent(this, target));
        finish();
    }
}
