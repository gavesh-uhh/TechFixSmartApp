package com.techfix.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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
import com.techfix.app.util.Analytics;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private enum Mode { SIGN_IN, SIGN_UP }

    private ActivityLoginBinding binding;
    private Mode mode = Mode.SIGN_IN;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private UserDAO userDAO;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        userDAO = new UserDAO(DatabaseHelper.getInstance(this));

        if (session.isLoggedIn()) {
            resumeSession(session);
            return;
        }

        try {
            firebaseAuth = FirebaseAuth.getInstance();
            firestore = FirebaseFirestore.getInstance();
        } catch (Exception ignored) {}

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.applyScrollContent(binding.loginContent);

        binding.loginButton.setOnClickListener(this::submitAuthForm);
        binding.switchAuthButton.setOnClickListener(v -> switchMode(mode == Mode.SIGN_UP ? Mode.SIGN_IN : Mode.SIGN_UP));
        binding.forgotPasswordText.setOnClickListener(this::showForgotPasswordDialog);

        binding.passwordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int score = Math.min(4, s.length() / 3);
                binding.passwordStrength.setProgressCompat(score, true);
                binding.strengthLabel.setText(score < 2 ? "Weak password" : score < 4 ? "Good password" : "Strong password");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        switchMode(Mode.SIGN_IN);
    }

    private void resumeSession(SessionManager session) {
        Class<?> targetActivity = (session.getRole() == UserRole.STAFF) ? StaffActivity.class : CustomerActivity.class;
        startActivity(new Intent(this, targetActivity));
        finish();
    }

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

    private void submitAuthForm(View view) {
        String email = binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString().trim();

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
            handleSignUp(view, email, password);
        } else {
            handleSignIn(view, email, password);
        }
    }

    private void handleSignIn(View view, String email, String password) {
        binding.loginButton.setEnabled(false);
        binding.loginButton.setText("Signing in...");

        boolean isStaff = email.equalsIgnoreCase("staff@techfix.lk");

        if (userDAO.authenticate(email, password)) {
            User localUser = userDAO.findByEmail(email);
            UserRole role = (isStaff || (localUser != null && localUser.role == UserRole.STAFF)) ? UserRole.STAFF : UserRole.CUSTOMER;
            long userId = (localUser != null) ? localUser.id : 1;

            if (firebaseAuth != null) {
                final String uName = (localUser != null) ? localUser.name : "User";
                firebaseAuth.signInWithEmailAndPassword(email, password)
                        .addOnFailureListener(e -> {
                            firebaseAuth.createUserWithEmailAndPassword(email, password)
                                    .addOnSuccessListener(authResult -> {
                                        if (firebaseAuth.getCurrentUser() != null) {
                                            UserProfileChangeRequest updates = new UserProfileChangeRequest.Builder()
                                                    .setDisplayName(uName).build();
                                            firebaseAuth.getCurrentUser().updateProfile(updates);
                                        }
                                    });
                        });
            }

            session.start(userId, role);
            Toast.makeText(this, "Signed in successfully", Toast.LENGTH_SHORT).show();
            com.techfix.app.util.Analytics.log(this, "login");
            openDashboard(role);
            return;
        }

        if (firebaseAuth != null) {
            firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                            String name = (firebaseUser != null && firebaseUser.getDisplayName() != null) ? firebaseUser.getDisplayName() : "Customer";

                            User localUser = userDAO.findByEmail(email);
                            if (localUser == null) {
                                userDAO.create(name, email, "", password);
                                localUser = userDAO.findByEmail(email);
                            }

                            UserRole role = (isStaff || (localUser != null && localUser.role == UserRole.STAFF)) ? UserRole.STAFF : UserRole.CUSTOMER;
                            long userId = (localUser != null) ? localUser.id : 1;

                            session.start(userId, role);
                            Toast.makeText(this, "Signed in successfully", Toast.LENGTH_SHORT).show();
                            com.techfix.app.util.Analytics.log(this, "login");
                            openDashboard(role);

                        } else {
                            binding.loginButton.setEnabled(true);
                            binding.loginButton.setText("Sign in");
                            String error = (task.getException() != null) ? task.getException().getMessage() : "Invalid email or password";
                            Snackbar.make(view, error, Snackbar.LENGTH_LONG).show();
                        }
                    });
        } else {
            binding.loginButton.setEnabled(true);
            binding.loginButton.setText("Sign in");
            Snackbar.make(view, "Invalid email or password", Snackbar.LENGTH_LONG).show();
        }
    }

    private void handleSignUp(View view, String email, String password) {
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

        userDAO.create(fullName, email, phone, password);
        User localUser = userDAO.findByEmail(email);
        long userId = (localUser != null) ? localUser.id : 1;

        if (firebaseAuth != null) {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(fullName)
                                        .build();
                                firebaseUser.updateProfile(profileUpdates);

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

                            session.start(userId, UserRole.CUSTOMER);
                            Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show();
                            com.techfix.app.util.Analytics.log(this, "sign_up");
                            openDashboard(UserRole.CUSTOMER);

                        } else {
                            firebaseAuth.signInWithEmailAndPassword(email, password)
                                    .addOnSuccessListener(authResult -> {
                                        session.start(userId, UserRole.CUSTOMER);
                                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                                        com.techfix.app.util.Analytics.log(this, "login");
                                        openDashboard(UserRole.CUSTOMER);
                                    })
                                    .addOnFailureListener(e -> {
                                        binding.loginButton.setEnabled(true);
                                        binding.loginButton.setText("Create account");
                                        String error = (task.getException() != null) ? task.getException().getMessage() : "Sign up failed";
                                        Snackbar.make(view, error, Snackbar.LENGTH_LONG).show();
                                    });
                        }
                    });
        } else {
            session.start(userId, UserRole.CUSTOMER);
            Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show();
            Analytics.log(this, "sign_up");
            openDashboard(UserRole.CUSTOMER);
        }
    }

    private void showForgotPasswordDialog(View v) {
        android.widget.EditText emailField = new android.widget.EditText(this);
        emailField.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailField.setHint("Email address");
        String current = binding.emailInput.getText() != null ? binding.emailInput.getText().toString().trim() : "";
        emailField.setText(current);

        new AlertDialog.Builder(this)
                .setTitle("Reset password")
                .setMessage("We'll email you a link to reset your password.")
                .setView(emailField)
                .setPositiveButton("Send link", (dialog, which) -> {
                    String email = emailField.getText().toString().trim();
                    if (email.isEmpty()) {
                        Snackbar.make(v, "Enter your email address first", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    if (firebaseAuth == null) {
                        Snackbar.make(v, "Password reset is unavailable offline", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    firebaseAuth.sendPasswordResetEmail(email)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    com.techfix.app.util.Analytics.log(this, "password_reset_sent");
                                    Snackbar.make(v, "Reset link sent to " + email + " — check your inbox", Snackbar.LENGTH_LONG).show();
                                } else {
                                    String error = (task.getException() != null) ? task.getException().getMessage() : "Could not send reset email";
                                    Snackbar.make(v, error, Snackbar.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openDashboard(UserRole role) {
        Class<?> target = (role == UserRole.STAFF) ? StaffActivity.class : CustomerActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }
}
