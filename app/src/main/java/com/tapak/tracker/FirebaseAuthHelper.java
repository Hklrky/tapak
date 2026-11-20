package com.tapak.tracker;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class FirebaseAuthHelper {

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    private final FirebaseAuth firebaseAuth;
    private final DatabaseReference usersRef;
    private final GoogleSignInClient googleSignInClient;

    public FirebaseAuthHelper(Context context) {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.usersRef = FirebaseDatabase.getInstance().getReference("users");

        String webClientId = getWebClientId(context);
        GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();

        if (webClientId != null && !webClientId.isEmpty()) {
            gsoBuilder.requestIdToken(webClientId);
        }

        this.googleSignInClient = GoogleSignIn.getClient(context, gsoBuilder.build());
    }

    private String getWebClientId(Context context) {
        int resId = context.getResources().getIdentifier(
                "default_web_client_id",
                "string",
                context.getPackageName()
        );
        return resId != 0 ? context.getString(resId) : "";
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public Intent getGoogleSignInIntent() {
        return googleSignInClient.getSignInIntent();
    }

    public void signInWithEmail(String email, String password, @NonNull AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        user.reload().addOnCompleteListener(reloadTask -> {
                            if (reloadTask.isSuccessful() && user.isEmailVerified()) {
                                saveUserToDatabase(user, "email");
                                callback.onSuccess(user);
                            } else {
                                callback.onError("Email belum diverifikasi. Cek inbox/spam lalu tekan CEK VERIFIKASI.");
                            }
                        });
                    } else {
                        String err = task.getException() != null ? task.getException().getMessage() : "Gagal login email";
                        callback.onError(err);
                    }
                });
    }

    public void registerWithEmailAndSendVerification(String email, String password, @NonNull AuthCallback callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        saveUserToDatabase(user, "email");
                        user.sendEmailVerification().addOnCompleteListener(verifyTask -> {
                            if (verifyTask.isSuccessful()) {
                                callback.onSuccess(user);
                            } else {
                                String err = verifyTask.getException() != null
                                        ? verifyTask.getException().getMessage()
                                        : "Gagal mengirim email verifikasi";
                                callback.onError(err);
                            }
                        });
                    } else {
                        String err = task.getException() != null ? task.getException().getMessage() : "Gagal mendaftar";
                        callback.onError(err);
                    }
                });
    }

    public void checkEmailVerificationStatus(@NonNull AuthCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            callback.onError("Belum ada akun yang masuk.");
            return;
        }
        user.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (user.isEmailVerified()) {
                    saveUserToDatabase(user, "email");
                    callback.onSuccess(user);
                } else {
                    callback.onError("Email belum terverifikasi. Klik tautan di email kamu terlebih dahulu.");
                }
            } else {
                String err = task.getException() != null ? task.getException().getMessage() : "Gagal memeriksa verifikasi";
                callback.onError(err);
            }
        });
    }

    public void resendVerificationEmail(@NonNull AuthCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            callback.onError("Masukkan email & password lalu tekan MASUK terlebih dahulu.");
            return;
        }
        user.sendEmailVerification().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onSuccess(user);
            } else {
                String err = task.getException() != null ? task.getException().getMessage() : "Gagal mengirim ulang email";
                callback.onError(err);
            }
        });
    }

    public void firebaseAuthWithGoogle(String idToken, @NonNull AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        saveUserToDatabase(user, "google");
                        callback.onSuccess(user);
                    } else {
                        String err = task.getException() != null
                                ? task.getException().getMessage()
                                : "Gagal autentikasi Firebase dengan akun Google";
                        callback.onError(err);
                    }
                });
    }

    public void saveUserToDatabase(FirebaseUser user, String provider) {
        if (user == null) return;
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", user.getUid());
        profile.put("email", user.getEmail() != null ? user.getEmail() : "");
        profile.put("name", user.getDisplayName() != null ? user.getDisplayName() : "Pengguna TAPAK");
        profile.put("provider", provider);
        profile.put("emailVerified", user.isEmailVerified());
        profile.put("lastLogin", System.currentTimeMillis());

        usersRef.child(user.getUid()).updateChildren(profile);
    }

    public void signOut() {
        firebaseAuth.signOut();
        googleSignInClient.signOut();
    }
}
