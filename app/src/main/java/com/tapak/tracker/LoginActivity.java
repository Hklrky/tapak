package com.tapak.tracker;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private Button btnLoginEmail, btnRegisterEmail, btnGoogleSignIn;
    private ProgressBar progressAuth;
    private View glassCard;

    private FirebaseAuthHelper authHelper;
    private boolean isNavigatingToMain = false;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                setLoading(false);
                try {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null && account.getIdToken() != null && authHelper != null) {
                            setLoading(true);
                            authHelper.firebaseAuthWithGoogle(account.getIdToken(), new FirebaseAuthHelper.AuthCallback() {
                                @Override
                                public void onSuccess(FirebaseUser user, boolean isNewUser) {
                                    setLoading(false);
                                    Toast.makeText(
                                            LoginActivity.this,
                                            isNewUser ? "Akun Google terdaftar & masuk!" : "Selamat datang di TAPAK!",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    openMainActivity();
                                }

                                @Override
                                public void onError(String message) {
                                    setLoading(false);
                                    Toast.makeText(LoginActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
                                }
                            });
                        } else {
                            Toast.makeText(this, "Token Google kosong. Periksa Firebase & SHA-1.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(this, "Login Google dibatalkan.", Toast.LENGTH_SHORT).show();
                    }
                } catch (ApiException e) {
                    Toast.makeText(this, "Google Sign-In gagal (kode " + e.getStatusCode() + ")", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Kendala saat memproses login Google.", Toast.LENGTH_LONG).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_login);

            glassCard = findViewById(R.id.glassCard);
            etEmail = findViewById(R.id.etEmail);
            etPassword = findViewById(R.id.etPassword);
            btnLoginEmail = findViewById(R.id.btnLoginEmail);
            btnRegisterEmail = findViewById(R.id.btnRegisterEmail);
            btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
            progressAuth = findViewById(R.id.progressAuth);

            if (glassCard != null) {
                glassCard.setAlpha(0f);
                glassCard.animate().alpha(1f).setDuration(350).start();
            }

            try {
                authHelper = new FirebaseAuthHelper(this);
                FirebaseUser currentUser = authHelper.getCurrentUser();
                if (currentUser != null) {
                    openMainActivity();
                    return;
                }
            } catch (Exception e) {
                Toast.makeText(this, "Firebase belum aktif/siap.", Toast.LENGTH_SHORT).show();
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    try {
                        new LiveUpdateManager(LoginActivity.this).checkForUpdate();
                    } catch (Exception ignored) {
                    }
                }
            }, 800L);

            if (btnGoogleSignIn != null) {
                btnGoogleSignIn.setOnClickListener(v -> {
                    if (authHelper == null) {
                        Toast.makeText(this, "Layanan login belum siap.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    setLoading(true);
                    try {
                        googleSignInLauncher.launch(authHelper.getGoogleSignInIntent());
                    } catch (Exception e) {
                        setLoading(false);
                        Toast.makeText(this, "Gagal membuka Google Sign-In.", Toast.LENGTH_LONG).show();
                    }
                });
            }

            if (btnLoginEmail != null) {
                btnLoginEmail.setOnClickListener(v -> handleEmail(false));
            }
            if (btnRegisterEmail != null) {
                btnRegisterEmail.setOnClickListener(v -> handleEmail(true));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error awal aplikasi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleEmail(boolean isRegister) {
        if (authHelper == null) {
            Toast.makeText(this, "Layanan autentikasi belum siap.", Toast.LENGTH_SHORT).show();
            return;
        }
        String email = etEmail != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword != null ? etPassword.getText().toString().trim() : "";

        if (email.isEmpty() || password.length() < 6) {
            Toast.makeText(this, "Isi email valid & password minimal 6 karakter", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        FirebaseAuthHelper.AuthCallback cb = new FirebaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user, boolean isNewUser) {
                setLoading(false);
                Toast.makeText(
                        LoginActivity.this,
                        isNewUser ? "Akun berhasil didaftarkan!" : "Berhasil masuk!",
                        Toast.LENGTH_SHORT
                ).show();
                openMainActivity();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Gagal: " + message, Toast.LENGTH_LONG).show();
            }
        };

        if (isRegister) {
            authHelper.registerWithEmail(email, password, cb);
        } else {
            authHelper.signInWithEmail(email, password, cb);
        }
    }

    private void setLoading(boolean loading) {
        if (progressAuth != null) {
            progressAuth.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(!loading);
        if (btnLoginEmail != null) btnLoginEmail.setEnabled(!loading);
        if (btnRegisterEmail != null) btnRegisterEmail.setEnabled(!loading);
    }

    private void openMainActivity() {
        if (isNavigatingToMain || isFinishing() || isDestroyed()) return;
        isNavigatingToMain = true;
        try {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            isNavigatingToMain = false;
            Toast.makeText(this, "Gagal membuka halaman utama.", Toast.LENGTH_LONG).show();
        }
    }
}
