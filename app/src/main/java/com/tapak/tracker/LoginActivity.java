package com.tapak.tracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLoginEmail, btnRegisterEmail, btnCheckVerified, btnResendVerification, btnGoogleSignIn;
    private ProgressBar progressAuth;

    private FirebaseAuthHelper authHelper;
    private LiveUpdateManager liveUpdateManager;
    private boolean isNavigatingToMain = false;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null && account.getIdToken() != null) {
                            authHelper.firebaseAuthWithGoogle(account.getIdToken(), new FirebaseAuthHelper.AuthCallback() {
                                @Override
                                public void onSuccess(FirebaseUser user) {
                                    setLoading(false);
                                    openMainActivity();
                                }

                                @Override
                                public void onError(String message) {
                                    setLoading(false);
                                    Toast.makeText(LoginActivity.this, "Firebase Google Error: " + message, Toast.LENGTH_LONG).show();
                                }
                            });
                        } else {
                            setLoading(false);
                            Toast.makeText(this, "ID Token Google kosong. Cek SHA-1 di Firebase Console.", Toast.LENGTH_LONG).show();
                        }
                    } catch (ApiException e) {
                        setLoading(false);
                        Toast.makeText(this, "Google Sign-In Code " + e.getStatusCode() + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    setLoading(false);
                    Toast.makeText(this, "Pemilihan akun Google dibatalkan atau gagal.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authHelper = new FirebaseAuthHelper(this);
        liveUpdateManager = new LiveUpdateManager(this);
        liveUpdateManager.checkForUpdate();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLoginEmail = findViewById(R.id.btnLoginEmail);
        btnRegisterEmail = findViewById(R.id.btnRegisterEmail);
        btnCheckVerified = findViewById(R.id.btnCheckVerified);
        btnResendVerification = findViewById(R.id.btnResendVerification);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        progressAuth = findViewById(R.id.progressAuth);

        btnLoginEmail.setOnClickListener(v -> handleEmailAuth(false));
        btnRegisterEmail.setOnClickListener(v -> handleEmailAuth(true));

        btnCheckVerified.setOnClickListener(v -> {
            setLoading(true);
            authHelper.checkEmailVerificationStatus(new FirebaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(FirebaseUser user) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, "Email terverifikasi!", Toast.LENGTH_SHORT).show();
                    openMainActivity();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        btnResendVerification.setOnClickListener(v -> {
            setLoading(true);
            authHelper.resendVerificationEmail(new FirebaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(FirebaseUser user) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, "Email verifikasi dikirim ulang. Cek inbox/spam.", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        btnGoogleSignIn.setOnClickListener(v -> {
            setLoading(true);
            googleSignInLauncher.launch(authHelper.getGoogleSignInIntent());
        });

        FirebaseUser currentUser = authHelper.getCurrentUser();
        if (currentUser != null) {
            currentUser.reload().addOnCompleteListener(task -> {
                if (!isFinishing() && !isDestroyed()) {
                    if (currentUser.isEmailVerified() || !"password".equals(getProviderId(currentUser))) {
                        openMainActivity();
                    }
                }
            });
        }
    }

    private String getProviderId(FirebaseUser user) {
        if (user.getProviderData().size() > 1) {
            return user.getProviderData().get(1).getProviderId();
        }
        return "password";
    }

    private void handleEmailAuth(boolean isRegister) {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.length() < 6) {
            Toast.makeText(this, "Isi email valid dan password minimal 6 karakter", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        FirebaseAuthHelper.AuthCallback callback = new FirebaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false);
                if (isRegister) {
                    Toast.makeText(
                            LoginActivity.this,
                            "Pendaftaran berhasil! Tautan verifikasi dikirim ke " + user.getEmail() + ". Verifikasi dulu lalu tekan CEK VERIFIKASI.",
                            Toast.LENGTH_LONG
                    ).show();
                } else {
                    openMainActivity();
                }
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Gagal: " + message, Toast.LENGTH_LONG).show();
            }
        };

        if (isRegister) {
            authHelper.registerWithEmailAndSendVerification(email, password, callback);
        } else {
            authHelper.signInWithEmail(email, password, callback);
        }
    }

    private void setLoading(boolean loading) {
        progressAuth.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLoginEmail.setEnabled(!loading);
        btnRegisterEmail.setEnabled(!loading);
        btnCheckVerified.setEnabled(!loading);
        btnResendVerification.setEnabled(!loading);
        btnGoogleSignIn.setEnabled(!loading);
    }

    private void openMainActivity() {
        if (isNavigatingToMain || isFinishing() || isDestroyed()) {
            return;
        }
        isNavigatingToMain = true;
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
