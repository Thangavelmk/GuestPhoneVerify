package com.example.guestphoneverify;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Existing-app integration patch:
 * - SMS OTP through the deployed Twilio Verify backend.
 * - Address sharing opens a WhatsApp chat with a prefilled message.
 * - Location sharing opens a map pin and then WhatsApp with the map link prefilled.
 *
 * Set HOTEL_ADDRESS, HOTEL_LATITUDE, HOTEL_LONGITUDE and WHATSAPP_RECIPIENT before use.
 */
public class MainActivity extends Activity {
    private static final String BASE_URL = "https://guestphoneverify-backend.onrender.com";

    // TODO: Replace with the hotel's complete postal address.
    private static final String HOTEL_ADDRESS =
            "Hotel Silver Key Executive, No. 69 Hospital Road, Indrapriyadarshini Nagar, Kailash Nagar, Perumbakkam, Chennai - 600100";
    // TODO: Replace with the hotel's exact coordinates from Google Maps.
    private static final double HOTEL_LATITUDE = 12.9100;
    private static final double HOTEL_LONGITUDE = 80.2270;
    // TODO: Replace with the WhatsApp recipient's number, international digits only, no + or spaces.
    // Example India number: 919876543210. Leave empty to open WhatsApp's recipient chooser.
    private static final String WHATSAPP_RECIPIENT = "";

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private EditText phoneInput, otpInput;
    private Button sendButton, verifyButton, resendButton, addressButton, locationButton;
    private TextView statusText, verifiedNumberText;
    private boolean codeSent = false;
    private String activePhone = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        String last = getSharedPreferences("guest_verify", MODE_PRIVATE)
                .getString("verified_phone", "");
        if (!last.isEmpty()) verifiedNumberText.setText("Last verified number on this phone: " + last);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(32), dp(24), dp(28));
        page.setBackgroundColor(Color.rgb(246, 248, 247));
        scroll.addView(page);

        page.addView(label("HOTEL CHECK-IN", 12, Color.rgb(23, 107, 91), true));
        TextView title = label("Guest Phone\nVerification", 30, Color.rgb(24, 39, 35), true);
        LinearLayout.LayoutParams titleParams = params(-1, -2);
        titleParams.topMargin = dp(12);
        page.addView(title, titleParams);
        TextView subtitle = label("Verify the guest's mobile number and share hotel details.", 15,
                Color.rgb(91, 105, 100), false);
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(8);
        page.addView(subtitle, subtitleParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(20));
        card.setBackground(roundRect(Color.WHITE, dp(18), Color.rgb(228, 234, 231)));
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(24);
        page.addView(card, cardParams);

        card.addView(label("Mobile number", 14, Color.rgb(42, 57, 52), true));
        phoneInput = input("+91 98765 43210", InputType.TYPE_CLASS_PHONE);
        addField(card, phoneInput, 8);
        sendButton = button("Send to OTP");
        addField(card, sendButton, 14);

        TextView otpLabel = label("One-time password (OTP)", 14, Color.rgb(42, 57, 52), true);
        LinearLayout.LayoutParams otpLabelParams = params(-1, -2);
        otpLabelParams.topMargin = dp(22);
        card.addView(otpLabel, otpLabelParams);
        otpInput = input("Enter OTP", InputType.TYPE_CLASS_NUMBER);
        otpInput.setEnabled(false);
        addField(card, otpInput, 8);

        verifyButton = button("Validate OTP");
        verifyButton.setEnabled(false);
        addField(card, verifyButton, 12);
        resendButton = textButton("Resend OTP");
        resendButton.setVisibility(View.GONE);
        card.addView(resendButton, params(-1, dp(44)));

        addressButton = button("Share Address in WhatsApp");
        addField(card, addressButton, 14);
        locationButton = button("Share Location in WhatsApp");
        addField(card, locationButton, 10);

        statusText = label("Enter the mobile number with country code to request an SMS OTP.",
                14, Color.rgb(91, 105, 100), false);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(18);
        card.addView(statusText, statusParams);

        verifiedNumberText = label("", 12, Color.rgb(91, 105, 100), false);
        LinearLayout.LayoutParams verifiedParams = params(-1, -2);
        verifiedParams.topMargin = dp(20);
        page.addView(verifiedNumberText, verifiedParams);
        page.addView(label("SMS delivery and validation use the secure Twilio backend. WhatsApp opens with a prefilled message; the user must tap Send.", 12,
                Color.rgb(104, 116, 111), false));

        setContentView(scroll);
        sendButton.setOnClickListener(v -> requestOtp());
        resendButton.setOnClickListener(v -> requestOtp());
        verifyButton.setOnClickListener(v -> verifyOtp());
        addressButton.setOnClickListener(v -> shareAddress());
        locationButton.setOnClickListener(v -> shareLocation());
    }

    private void requestOtp() {
        String phone = normalizedPhone();
        if (!phone.matches("^\\+[1-9][0-9]{7,14}$")) {
            phoneInput.setError("Use international format, e.g. +919876543210");
            statusText.setText("Please enter a valid number with country code.");
            return;
        }
        activePhone = phone;
        hideKeyboard();
        setBusy(true);
        statusText.setText("Requesting SMS OTP…");
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("channel", "sms");
        } catch (Exception e) {
            setBusy(false);
            return;
        }
        network.execute(() -> {
            ApiResult result = postJson("/api/otp/start", body);
            runOnUiThread(() -> {
                setBusy(false);
                if (result.error != null) {
                    statusText.setText("Unable to send OTP. Check the backend or Twilio logs.");
                    Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                    return;
                }
                if (result.json.optBoolean("ok", false)) {
                    codeSent = true;
                    otpInput.setEnabled(true);
                    verifyButton.setEnabled(true);
                    resendButton.setVisibility(View.VISIBLE);
                    phoneInput.setEnabled(false);
                    statusText.setText("SMS OTP requested. Enter the code received.");
                } else {
                    statusText.setText("Twilio could not start verification.");
                    Toast.makeText(this, result.json.optString("error", "OTP request failed"),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void verifyOtp() {
        String code = otpInput.getText().toString().trim();
        if (!code.matches("^[0-9]{4,10}$") || activePhone.isEmpty()) {
            otpInput.setError("Enter the code received by SMS");
            return;
        }
        setBusy(true);
        statusText.setText("Verifying OTP…");
        JSONObject body = new JSONObject();
        try {
            body.put("phone", activePhone);
            body.put("channel", "sms");
            body.put("code", code);
        } catch (Exception e) {
            setBusy(false);
            return;
        }
        network.execute(() -> {
            ApiResult result = postJson("/api/otp/check", body);
            runOnUiThread(() -> {
                setBusy(false);
                if (result.error != null) {
                    statusText.setText("Unable to verify. Check connection and retry.");
                    Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                    return;
                }
                if (result.json.optBoolean("ok", false)
                        && "approved".equalsIgnoreCase(result.json.optString("status", ""))) {
                    getSharedPreferences("guest_verify", MODE_PRIVATE).edit()
                            .putString("verified_phone", activePhone).apply();
                    statusText.setText("✓ MOBILE NUMBER VERIFIED\n" + activePhone);
                    statusText.setTextColor(Color.rgb(23, 107, 75));
                    verifiedNumberText.setText("Last verified number on this phone: " + activePhone);
                    otpInput.setEnabled(false);
                    verifyButton.setEnabled(false);
                    sendButton.setText("Verify another number");
                    resendButton.setVisibility(View.GONE);
                    codeSent = false;
                } else {
                    statusText.setText("OTP not approved. Enter the latest OTP or request a new one.");
                    statusText.setTextColor(Color.rgb(177, 42, 42));
                    otpInput.setError("Incorrect, expired, or unapproved OTP");
                }
            });
        });
    }

    private void shareAddress() {
        String message = "Hotel address:\n" + HOTEL_ADDRESS;
        openWhatsApp(message);
    }

    private void shareLocation() {
        Uri mapUri = Uri.parse("https://www.google.com/maps/search/?api=1&query="
                + HOTEL_LATITUDE + "," + HOTEL_LONGITUDE);
        // Open the map pin first. User can return to the app and then send it through WhatsApp.
        try {
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
            startActivity(mapIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Maps.", Toast.LENGTH_LONG).show();
        }
        String message = "Hotel location:\n" + mapUri.toString() + "\n" + HOTEL_ADDRESS;
        // Open WhatsApp after the map app receives the pin link.
        // Android may pause this activity while Maps is open; use this button again to open WhatsApp.
        // For direct combined flow use Share Location button twice if the system holds Maps in foreground.
        openWhatsApp(message);
    }

    private void openWhatsApp(String message) {
        try {
            String encoded = URLEncoder.encode(message, "UTF-8");
            String url = "https://wa.me/" + WHATSAPP_RECIPIENT + "?text=" + encoded;
            if (WHATSAPP_RECIPIENT.isEmpty()) {
                url = "https://api.whatsapp.com/send?text=" + encoded;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (Exception whatsappMissing) {
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://api.whatsapp.com/send?text="
                                + URLEncoder.encode(message, "UTF-8")));
                startActivity(browser);
            } catch (Exception e) {
                Toast.makeText(this, "WhatsApp could not be opened.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private ApiResult postJson(String path, JSONObject body) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream input = (status >= 200 && status < 400)
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder response = new StringBuilder();
            if (input != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
            }
            JSONObject json = response.length() == 0 ? new JSONObject()
                    : new JSONObject(response.toString());
            if (status < 200 || status >= 300) {
                return new ApiResult(json, "Backend HTTP " + status
                        + (json.has("error") ? ": " + json.optString("error") : ""));
            }
            return new ApiResult(json, null);
        } catch (Exception e) {
            return new ApiResult(new JSONObject(), e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String normalizedPhone() {
        return phoneInput.getText().toString().trim().replaceAll("[\\s()-]", "");
    }
    private void addField(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams p = params(-1, dp(view instanceof EditText ? 54 : 52));
        p.topMargin = dp(top);
        parent.addView(view, p);
    }
    private void setBusy(boolean busy) {
        sendButton.setEnabled(!busy);
        resendButton.setEnabled(!busy);
        verifyButton.setEnabled(!busy && codeSent);
        phoneInput.setEnabled(!busy && !codeSent);
        otpInput.setEnabled(!busy && codeSent);
    }
    private void hideKeyboard() {
        try {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(phoneInput.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        network.shutdown();
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private EditText input(String hint, int type) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setHint(hint);
        edit.setInputType(type);
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(roundRect(Color.rgb(249, 251, 250), dp(10), Color.rgb(218, 226, 222)));
        return edit;
    }
    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackground(roundRect(Color.rgb(23, 107, 91), dp(12), Color.rgb(23, 107, 91)));
        return button;
    }
    private Button textButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.rgb(23, 107, 91));
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }
    private GradientDrawable roundRect(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }
    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    
private static class ApiResult {
    final JSONObject json;
    final String error;

    ApiResult(JSONObject json, String error) {
        this.json = json;
        this.error = error;
    }
}

}
