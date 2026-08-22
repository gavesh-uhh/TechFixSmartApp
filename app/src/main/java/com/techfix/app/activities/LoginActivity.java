package com.techfix.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.UserDAO;
import com.techfix.app.databinding.ActivityLoginBinding;
import com.techfix.app.models.User;
import com.techfix.app.models.UserRole;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * LoginActivity - Firebase Authentication for TechFix Repair App.
 * Features:
 * - Sign In: Authenticates with FirebaseAuth (with offline SQLite fallback)
 * - Sign Up: Registers account in FirebaseAuth and stores customer profile in Firestore & SQLite
 * - Auto-detects user role (Customer vs Staff) and redirects to the appropriate dashboard
 */
public class LoginActivity extends AppCompatActivity {

    // Auth screen mode: Sign In vs Create Account
    private enum Mode { SIGN_IN, SIGN_UP }

    private ActivityLoginBinding binding;
    private Mode mode = Mode.SIGN_IN;

    // Firebase instances
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    // Local SQLite DAO & Session
    private UserDAO userDAO;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Initialize session and DAOs
        session = new SessionManager(this);
        userDAO = new UserDAO(DatabaseHelper.getInstance(this));

        // 2. Check if user is already logged in
        if (session.isLoggedIn()) {
            resumeSession(session);
            return;
        }

        // 3. Initialize Firebase
        try {
            firebaseAuth = FirebaseAuth.getInstance();
            firestore = FirebaseFirestore.getInstance();
        } catch (Exception ignored) {}

        // 4. Inflate layout
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.loginContent, binding.loginContent);

        // 5. Setup click listeners
        binding.loginButton.setOnClickListener(this::submitAuthForm);
        binding.switchAuthButton.setOnClickListener(v -> switchMode(mode == Mode.SIGN_UP ? Mode.SIGN_IN : Mode.SIGN_UP));

        // 6. Password strength watcher (for sign up)
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

        // 7. Initial view state
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
     * Handles login / sign up submission using Firebase Authentication.
     */
    private void submitAuthForm(View view) {
        String email = binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString();

        if (email.isEmpty()) {
            binding.emailInput.setError("Please enter your email address");
            binding.emailInput.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            binding.passwordInput.setError("Please enter your password");
            binding.passwordInput.requestFocus();
            return;
        }

        if (mode == Mode.SIGN_UP) {
            handleSignUpWithFirebase(view, email, password);
        } else {
            handleSignInWithFirebase(view, email, password);
        }
    }

    /**
     * Registers a new customer account using Firebase Authentication.
     */
    private void handleSignUpWithFirebase(View view, String email, String password) {
        String fullName = binding.nameInput.getText().toString().trim();
        String phone = binding.phoneInput.getText().toString().trim();

        if (fullName.isEmpty()) {
            binding.nameInput.setError("Please enter your full name");
            binding.nameInput.requestFocus();
            return;
        }

        if (phone.isEmpty()) {
            binding.phoneInput.setError("Please enter your phone number");
            binding.phoneInput.requestFocus();
            return;
        }

        if (password.length() < 6) {
            binding.passwordInput.setError("Password must be at least 6 characters");
            binding.passwordInput.requestFocus();
            return;
        }

        binding.loginButton.setEnabled(false);
        binding.loginButton.setText("Creating account...");

        if (firebaseAuth != null) {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();

                            // Update Display Name in Firebase
                            if (firebaseUser != null) {
                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(fullName)
                                        .build();
                                firebaseUser.updateProfile(profileUpdates);

                                // Save user profile to Firestore
                                if (firestore != null) {
                                    Map<String, Object> userMap = new HashMap<>();
                                    userMap.put("uid", firebaseUser.getUid());
                                    userMap.put("name", fullName);
                                    userMap.put("email", email);
                                    userMap.put("phone", phone);
                                    userMap.put("role", "CUSTOMER");
                                    userMap.put("createdAt", DatabaseHelper.now());

                                    firestore.collection("users").document(firebaseUser.getUid()).set(userMap);
                                }
                            }

                            // Also save to local SQLite for offline access
                            userDAO.create(fullName, email, phone, password);
                            User localUser = userDAO.findByEmail(email);
                            long userId = (localUser != null) ? localUser.id : 1;

                            session.start(userId, UserRole.CUSTOMER);
                            openDashboard(UserRole.CUSTOMER);

                        } else {
                            binding.loginButton.setEnabled(true);
                            binding.loginButton.setText("Create account");
                            String error = (task.getException() != null) ? task.getException().getMessage() : "Sign up failed";
                            Snackbar.make(view, error, Snackbar.LENGTH_LONG).show();
                        }
                    });
        } else {
            // Local fallback if Firebase is uninitialized
            boolean created = userDAO.create(fullName, email, phone, password);
            if (created) {
                User localUser = userDAO.findByEmail(email);
                long userId = (localUser != null) ? localUser.id : 1;
                session.start(userId, UserRole.CUSTOMER);
                openDashboard(UserRole.CUSTOMER);
            } else {
                binding.loginButton.setEnabled(true);
                binding.loginButton.setText("Create account");
                Snackbar.make(view, "Email is already registered", Snackbar.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Signs in an existing customer or staff member using Firebase Authentication.
     */
    private void handleSignInWithFirebase(View view, String email, String password) {
        binding.loginButton.setEnabled(false);
        binding.loginButton.setText("Signing in...");

        // Check if this is the staff account
        boolean isStaffEmail = email.equalsIgnoreCase("staff@techfix.lk");

        if (firebaseAuth != null) {
            firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                            String name = (firebaseUser != null && firebaseUser.getDisplayName() != null) ? firebaseUser.getDisplayName() : "Customer";

                            // Ensure local user exists in SQLite
                            User localUser = userDAO.findByEmail(email);
                            if (localUser == null) {
                                userDAO.create(name, email, "", password);
                                localUser = userDAO.findByEmail(email);
                            }

                            UserRole role = (isStaffEmail || (localUser != null && localUser.role == UserRole.STAFF)) ? UserRole.STAFF : UserRole.CUSTOMER;
                            long userId = (localUser != null) ? localUser.id : 1;

                            session.start(userId, role);
                            openDashboard(role);

                        } else {
                            // Check local SQLite credentials as fallback (e.g. for offline usage or seeded staff)
                            if (userDAO.authenticate(email, password)) {
                                User localUser = userDAO.findByEmail(email);
                                UserRole role = (localUser != null && localUser.role != null) ? localUser.role : (isStaffEmail ? UserRole.STAFF : UserRole.CUSTOMER);
                                long userId = (localUser != null) ? localUser.id : 1;

                                session.start(userId, role);
                                openDashboard(role);
                            } else {
                                binding.loginButton.setEnabled(true);
                                binding.loginButton.setText("Sign in");
                                String error = (task.getException() != null) ? task.getException().getMessage() : "Invalid email or password";
                                Snackbar.make(view, error, Snackbar.LENGTH_LONG).show();
                            }
                        }
                    });
        } else {
            // Local fallback
            if (userDAO.authenticate(email, password)) {
                User localUser = userDAO.findByEmail(email);
                UserRole role = (localUser != null && localUser.role != null) ? localUser.role : (isStaffEmail ? UserRole.STAFF : UserRole.CUSTOMER);
                long userId = (localUser != null) ? localUser.id : 1;

                session.start(userId, role);
                openDashboard(role);
            } else {
                binding.loginButton.setEnabled(true);
                binding.loginButton.setText("Sign in");
                Snackbar.make(view, "Invalid email or password", Snackbar.LENGTH_LONG).show();
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
