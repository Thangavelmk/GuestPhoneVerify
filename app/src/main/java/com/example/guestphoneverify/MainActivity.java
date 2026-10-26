package com.example.guestphoneverify;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private FirebaseAuth auth;
    private EditText phoneInput, otpInput;
    private Button sendButton, verifyButton, resendButton;
    private TextView statusText, verifiedNumberText;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private boolean codeSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        buildUi();
        String lastVerified = getSharedPreferences("guest_verify", MODE_PRIVATE).getString("verified_phone", "");
        if (!lastVerified.isEmpty()) {
            verifiedNumberText.setText("Last verified number on this phone: " + lastVerified);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(32), dp(24), dp(28));
        page.setBackgroundColor(Color.rgb(246, 248, 247));
        scroll.addView(page);

        TextView badge = label("HOTEL CHECK-IN", 12, Color.rgb(23, 107, 91), true);
        badge.setLetterSpacing(0.08f);
        page.addView(badge);

        TextView title = label("Guest Phone\nVerification", 30, Color.rgb(24, 39, 35), true);
        LinearLayout.LayoutParams titleParams = params(-1, -2);
        titleParams.topMargin = dp(12);
        page.addView(title, titleParams);

        TextView subtitle = label("Verify that the guest can access the registered mobile number.", 15, Color.rgb(91, 105, 100), false);
        LinearLayout.LayoutParams subParams = params(-1, -2);
        subParams.topMargin = dp(8);
        page.addView(subtitle, subParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(20));
        card.setBackground(roundRect(Color.WHITE, dp(18), Color.rgb(228, 234, 231)));
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(28);
        page.addView(card, cardParams);

        card.addView(label("Mobile number", 14, Color.rgb(42, 57, 52), true));
        phoneInput = input("+91 98765 43210", InputType.TYPE_CLASS_PHONE);
        LinearLayout.LayoutParams fieldParams = params(-1, dp(54));
        fieldParams.topMargin = dp(8);
        card.addView(phoneInput, fieldParams);

        sendButton = button("Send OTP");
        LinearLayout.LayoutParams sendParams = params(-1, dp(52));
        sendParams.topMargin = dp(14);
        card.addView(sendButton, sendParams);

        TextView otpLabel = label("One-time password (OTP)", 14, Color.rgb(42, 57, 52), true);
        LinearLayout.LayoutParams otpLabelParams = params(-1, -2);
        otpLabelParams.topMargin = dp(22);
        card.addView(otpLabel, otpLabelParams);
        otpInput = input("Enter 6-digit OTP", InputType.TYPE_CLASS_NUMBER);
        otpInput.setEnabled(false);
        LinearLayout.LayoutParams otpParams = params(-1, dp(54));
        otpParams.topMargin = dp(8);
        card.addView(otpInput, otpParams);

        verifyButton = button("Verify number");
        verifyButton.setEnabled(false);
        LinearLayout.LayoutParams verifyParams = params(-1, dp(52));
        verifyParams.topMargin = dp(12);
        card.addView(verifyButton, verifyParams);

        resendButton = textButton("Resend OTP");
        resendButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams resendParams = params(-1, dp(44));
        card.addView(resendButton, resendParams);

        statusText = label("Enter a number including country code, then request an OTP.", 14, Color.rgb(91, 105, 100), false);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(18);
        card.addView(statusText, statusParams);

        verifiedNumberText = label("", 12, Color.rgb(91, 105, 100), false);
        LinearLayout.LayoutParams lastParams = params(-1, -2);
        lastParams.topMargin = dp(20);
        page.addView(verifiedNumberText, lastParams);

        TextView note = label("Privacy note: this app stores only the last verified phone number on this device. OTP delivery and verification require internet and Firebase Phone Authentication setup.", 12, Color.rgb(104, 116, 111), false);
        LinearLayout.LayoutParams noteParams = params(-1, -2);
        noteParams.topMargin = dp(18);
        page.addView(note, noteParams);

        setContentView(scroll);
        sendButton.setOnClickListener(v -> requestOtp(false));
        resendButton.setOnClickListener(v -> requestOtp(true));
        verifyButton.setOnClickListener(v -> verifyOtp());
    }

    private void requestOtp(boolean resend) {
        String phone = phoneInput.getText().toString().trim().replace(" ", "");
        if (!phone.matches("^\\+[1-9][0-9]{7,14}$")) {
            phoneInput.setError("Use international format, e.g. +919876543210");
            statusText.setText("Please enter a valid number with country code.");
            return;
        }
        hideKeyboard();
        setBusy(true);
        statusText.setText("Requesting OTP…");
        PhoneAuthOptions.Builder builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks);
        if (resend && resendToken != null) builder.setForceResendingToken(resendToken);
        auth.setLanguageCode("en");
        PhoneAuthProvider.verifyPhoneNumber(builder.build());
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                @Override public void onVerificationCompleted(PhoneAuthCredential credential) {
                    // Firebase may auto-verify on supported devices. Complete the sign-in securely.
                    signInWithCredential(credential);
                }
                @Override public void onVerificationFailed(FirebaseException e) {
                    setBusy(false);
                    if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        statusText.setText("Invalid phone number. Check the country code and try again.");
                    } else if (e instanceof FirebaseAuthMissingActivityForRecaptchaException) {
                        statusText.setText("Verification setup issue. Please try again.");
                    } else {
                        statusText.setText("OTP could not be sent. Check internet and Firebase setup, then try again.");
                    }
                    Toast.makeText(MainActivity.this, e.getMessage() == null ? "OTP request failed" : e.getMessage(), Toast.LENGTH_LONG).show();
                }
                @Override public void onCodeSent(String id, PhoneAuthProvider.ForceResendingToken token) {
                    setBusy(false);
                    verificationId = id;
                    resendToken = token;
                    codeSent = true;
                    otpInput.setEnabled(true);
                    verifyButton.setEnabled(true);
                    resendButton.setVisibility(View.VISIBLE);
                    statusText.setText("OTP sent. Enter the code received on that phone.");
                }
                @Override public void onCodeAutoRetrievalTimeOut(String id) {
                    verificationId = id;
                    if (codeSent) statusText.setText("OTP timer ended. Enter the received OTP or request a new one.");
                }
            };

    private void verifyOtp() {
        String code = otpInput.getText().toString().trim();
        if (verificationId == null || code.length() != 6) {
            otpInput.setError("Enter the 6-digit OTP");
            return;
        }
        setBusy(true);
        statusText.setText("Verifying OTP…");
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithCredential(credential);
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            setBusy(false);
            if (task.isSuccessful()) {
                FirebaseUser user = task.getResult().getUser();
                String verifiedPhone = user != null && user.getPhoneNumber() != null
                        ? user.getPhoneNumber() : phoneInput.getText().toString().trim().replace(" ", "");
                getSharedPreferences("guest_verify", MODE_PRIVATE).edit().putString("verified_phone", verifiedPhone).apply();
                statusText.setText("✓ MOBILE NUMBER VERIFIED\n" + verifiedPhone);
                statusText.setTextColor(Color.rgb(23, 107, 75));
                verifiedNumberText.setText("Last verified number on this phone: " + verifiedPhone);
                otpInput.setEnabled(false);
                verifyButton.setEnabled(false);
                sendButton.setText("Verify another number");
                resendButton.setVisibility(View.GONE);
                codeSent = false;
            } else {
                statusText.setText("Verification failed. The OTP may be incorrect or expired. Try again.");
                statusText.setTextColor(Color.rgb(177, 42, 42));
                otpInput.setError("Check the OTP");
            }
        });
    }

    private void setBusy(boolean busy) {
        sendButton.setEnabled(!busy);
        resendButton.setEnabled(!busy);
        verifyButton.setEnabled(!busy && codeSent);
        phoneInput.setEnabled(!busy && !codeSent);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(phoneInput.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText input(String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setHint(hint);
        edit.setInputType(inputType);
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(roundRect(Color.rgb(249, 251, 250), dp(10), Color.rgb(218, 226, 222)));
        return edit;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundRect(Color.rgb(23, 107, 91), dp(12), Color.rgb(23, 107, 91)));
        return b;
    }

    private Button textButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(23, 107, 91));
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
