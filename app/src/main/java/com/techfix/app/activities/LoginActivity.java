package com.techfix.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.UserDAO;
import com.techfix.app.databinding.ActivityLoginBinding;
import com.techfix.app.models.User;
import com.techfix.app.models.UserRole;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.WindowInsetsHelper;

/**
 * LoginActivity - Unified Authentication for Customers and Staff.
 * - Sign In: Email + Password
 * - Sign Up: Full Name + Phone Number + Email + Password
 * The system automatically detects the account role (Customer vs Staff) and redirects
 * to the appropriate dashboard workspace.
 */
public class LoginActivity extends AppCompatActivity {

    // Auth screen mode: Sign In vs Create Account
    private enum Mode { SIGN_IN, SIGN_UP }

    private ActivityLoginBinding binding;
    private Mode mode = Mode.SIGN_IN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Check if user is already logged in
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            resumeSession(session);
            return;
        }

        // 2. Inflate layout
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.loginContent, binding.loginContent);

        // 3. Setup click listeners
        binding.loginButton.setOnClickListener(this::submitAuthForm);
        binding.switchAuthButton.setOnClickListener(v -> switchMode(mode == Mode.SIGN_UP ? Mode.SIGN_IN : Mode.SIGN_UP));

        // 4. Password strength watcher (for sign up)
        binding.passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int score = Math.min(4, s.length() / 3);
                binding.passwordStrength.setProgressCompat(score, true);
                binding.strengthLabel.setText(score < 2 ? "Weak password" : score < 4 ? "Good password" : "Strong password");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 5. Initial view state
        switchMode(Mode.SIGN_IN);
    }

    /**
     * Resumes existing active session based on user role.
     */
    private void resumeSession(SessionManager session) {
        Class<?> targetActivity = (session.getRole() == UserRole.STAFF) ? StaffActivity.class : CustomerActivity.class;
        startActivity(new Intent(this, targetActivity));
        finish();
    }

    /**
     * Switch between Sign In and Create Account views.
     */
    private void switchMode(Mode nextMode) {
        mode = nextMode;
        boolean isSignUp = (mode == Mode.SIGN_UP);

        binding.nameLayout.setVisibility(isSignUp ? View.VISIBLE : View.GONE);
        binding.phoneLayout.setVisibility(isSignUp ? View.VISIBLE : View.GONE);
        binding.passwordStrength.setVisibility(isSignUp ? View.VISIBLE : View.GONE);
        binding.strengthLabel.setVisibility(isSignUp ? View.VISIBLE : View.GONE);

        binding.authTitle.setText(isSignUp ? "Create account" : "Sign in");
        binding.authSubtitle.setText(isSignUp ? "Create your TechFix repair profile." : "Enter your email and password to access your account.");
        binding.loginButton.setText(isSignUp ? "Create account" : "Sign in");
        binding.switchAuthButton.setText(isSignUp ? "Already have an account? Sign in" : "Don't have an account? Sign up");
    }

    /**
     * Handles login / sign up submission.
     * Authenticates user against SQLite database and automatically routes to Staff or Customer view.
     */
    private void submitAuthForm(View view) {
        String email = binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Snackbar.make(view, "Please enter your email and password", Snackbar.LENGTH_LONG).show();
            return;
        }

        UserDAO userDAO = new UserDAO(DatabaseHelper.getInstance(this));
        SessionManager session = new SessionManager(this);

        if (mode == Mode.SIGN_UP) {
            // Customer Sign Up: Full name, Phone number, Email, Password
            String fullName = binding.nameInput.getText().toString().trim();
            String phone = binding.phoneInput.getText().toString().trim();

            if (fullName.isEmpty()) {
                Snackbar.make(view, "Please enter your full name", Snackbar.LENGTH_LONG).show();
                return;
            }

            if (phone.isEmpty()) {
                Snackbar.make(view, "Please enter your phone number", Snackbar.LENGTH_LONG).show();
                return;
            }

            if (password.length() < 4) {
                Snackbar.make(view, "Password must be at least 4 characters", Snackbar.LENGTH_LONG).show();
                return;
            }

            boolean created = userDAO.create(fullName, email, phone, password);
            if (created) {
                User newUser = userDAO.findByEmail(email);
                UserRole role = (newUser != null && newUser.role != null) ? newUser.role : UserRole.CUSTOMER;
                session.start(newUser.id, role);
                openDashboard(role);
            } else {
                Snackbar.make(view, "Could not create account or email already registered", Snackbar.LENGTH_LONG).show();
            }

        } else {
            // Unified Sign In (Customer & Staff)
            boolean isValid = userDAO.authenticate(email, password);
            if (isValid) {
                User user = userDAO.findByEmail(email);
                UserRole role = (user != null && user.role != null) ? user.role : UserRole.CUSTOMER;

                // Start session with user ID and detected role
                session.start(user.id, role);

                // Automatically route based on role
                openDashboard(role);
            } else {
                Snackbar.make(view, "Incorrect email or password", Snackbar.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Opens the appropriate dashboard based on user role:
     * - STAFF -> StaffActivity
     * - CUSTOMER -> CustomerActivity
     */
    private void openDashboard(UserRole role) {
        Class<?> target = (role == UserRole.STAFF) ? StaffActivity.class : CustomerActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }
}
