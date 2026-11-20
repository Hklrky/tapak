package com.tapak.tracker;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
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
    private Button btnLoginEmail, btnRegisterEmail, btnGoogleSignIn;
    private ProgressBar progressAuth;
    private View glassCard, orbOrange, orbBlue;

    private FirebaseAuthHelper authHelper;
    private boolean isNavigatingToMain = false;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                setLoading(false);
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null && account.getIdToken() != null) {
                            setLoading(true);
                            authHelper.firebaseAuthWithGoogle(account.getIdToken(), new FirebaseAuthHelper.AuthCallback() {
                                @Override
                                public void onSuccess(FirebaseUser user, boolean isNewUser) {
                                    setLoading(false);
                                    String msg = isNewUser
                                            ? "Akun Google berhasil didaftarkan & masuk!"
                                            : "Selamat datang kembali di TAPAK!";
                                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
                                    openMainActivity();
                                }

                                @Override
                                public void onError(String message) {
                                    setLoading(false);
                                    Toast.makeText(LoginActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
                                }
                            });
                        } else {
                            Toast.makeText(this, "ID Token Google kosong. Cek SHA-1 di Firebase Console.", Toast.LENGTH_LONG).show();
                        }
                    } catch (ApiException e) {
                        Toast.makeText(this, "Google Sign-In gagal/dibatalkan (kode " + e.getStatusCode() + ")", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "Login Google dibatalkan.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authHelper = new FirebaseAuthHelper(this);
        new LiveUpdateManager(this).checkForUpdate();

        glassCard = findViewById(R.id.glassCard);
        orbOrange = findViewById(R.id.orbOrange);
        orbBlue = findViewById(R.id.orbBlue);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLoginEmail = findViewById(R.id.btnLoginEmail);
        btnRegisterEmail = findViewById(R.id.btnRegisterEmail);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        progressAuth = findViewById(R.id.progressAuth);

        startLightweightLiquidAnimation();

        FirebaseUser currentUser = authHelper.getCurrentUser();
        if (currentUser != null) {
            openMainActivity();
            return;
        }

        btnGoogleSignIn.setOnClickListener(v -> {
            setLoading(true);
            googleSignInLauncher.launch(authHelper.getGoogleSignInIntent());
        });

        btnLoginEmail.setOnClickListener(v -> handleEmail(false));
        btnRegisterEmail.setOnClickListener(v -> handleEmail(true));
    }

    private void startLightweightLiquidAnimation() {
        glassCard.setAlpha(0f);
        glassCard.setTranslationY(24f);
        glassCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420)
                .start();

        animateOrb(orbOrange, 18f, -14f, 3600L);
        animateOrb(orbBlue, -16f, 16f, 4200L);
    }

    private void animateOrb(View target, float dx, float dy, long duration) {
        if (target == null) return;
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
                target,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, dx),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, dy)
        );
        anim.setDuration(duration);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.start();
    }

    private void handleEmail(boolean isRegister) {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

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
        progressAuth.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnGoogleSignIn.setEnabled(!loading);
        btnLoginEmail.setEnabled(!loading);
        btnRegisterEmail.setEnabled(!loading);
    }

    private void openMainActivity() {
        if (isNavigatingToMain || isFinishing() || isDestroyed()) return;
        isNavigatingToMain = true;
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
