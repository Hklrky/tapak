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
        void onSuccess(FirebaseUser user, boolean isNewUser);
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
                .requestEmail()
                .requestProfile();

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

    public void firebaseAuthWithGoogle(String idToken, @NonNull AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        boolean isNewUser = task.getResult() != null
                                && task.getResult().getAdditionalUserInfo() != null
                                && task.getResult().getAdditionalUserInfo().isNewUser();
                        saveUserToDatabase(user, "google");
                        callback.onSuccess(user, isNewUser);
                    } else {
                        String err = task.getException() != null
                                ? task.getException().getMessage()
                                : "Gagal login dengan akun Google";
                        callback.onError(err);
                    }
                });
    }

    public void signInWithEmail(String email, String password, @NonNull AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        saveUserToDatabase(user, "email");
                        callback.onSuccess(user, false);
                    } else {
                        String err = task.getException() != null ? task.getException().getMessage() : "Gagal masuk";
                        callback.onError(err);
                    }
                });
    }

    public void registerWithEmail(String email, String password, @NonNull AuthCallback callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        saveUserToDatabase(user, "email");
                        callback.onSuccess(user, true);
                    } else {
                        String err = task.getException() != null ? task.getException().getMessage() : "Gagal daftar";
                        callback.onError(err);
                    }
                });
    }

    public void saveUserToDatabase(FirebaseUser user, String provider) {
        if (user == null) return;
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", user.getUid());
        profile.put("email", user.getEmail() != null ? user.getEmail() : "");
        profile.put("name", user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                ? user.getDisplayName()
                : "Pengguna TAPAK");
        profile.put("provider", provider);
        profile.put("lastLogin", System.currentTimeMillis());

        usersRef.child(user.getUid()).updateChildren(profile);
    }

    public void signOut() {
        firebaseAuth.signOut();
        googleSignInClient.signOut();
    }
}
