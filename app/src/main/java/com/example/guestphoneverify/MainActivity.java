package com.example.guestphoneverify;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // Replace with your Windows PC IPv4 from ipconfig. Use HTTPS in production.
    private static final String BASE_URL = "https://guestphoneverify-backend.onrender.com";
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private EditText phoneInput, otpInput;
    private Button sendButton, verifyButton, resendButton;
    private TextView statusText, verifiedNumberText;
    private boolean codeSent=false;
    private String activePhone="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        String last=getSharedPreferences("guest_verify",MODE_PRIVATE).getString("verified_phone","");
        if(!last.isEmpty()) verifiedNumberText.setText("Last verified number on this phone: "+last);
    }
    private void buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout page=new LinearLayout(this); page.setOrientation(1); page.setPadding(dp(24),dp(32),dp(24),dp(28)); page.setBackgroundColor(Color.rgb(246,248,247)); scroll.addView(page);
        TextView badge=label("HOTEL CHECK-IN",12,Color.rgb(23,107,91),true); page.addView(badge);
        TextView title=label("Guest Phone\nVerification",30,Color.rgb(24,39,35),true); LinearLayout.LayoutParams tp=params(-1,-2); tp.topMargin=dp(12); page.addView(title,tp);
        TextView sub=label("Verify that the guest can access the registered mobile number.",15,Color.rgb(91,105,100),false); LinearLayout.LayoutParams sp=params(-1,-2); sp.topMargin=dp(8); page.addView(sub,sp);
        LinearLayout card=new LinearLayout(this); card.setOrientation(1); card.setPadding(dp(18),dp(20),dp(18),dp(20)); card.setBackground(roundRect(Color.WHITE,dp(18),Color.rgb(228,234,231))); LinearLayout.LayoutParams cp=params(-1,-2); cp.topMargin=dp(28); page.addView(card,cp);
        card.addView(label("Mobile number",14,Color.rgb(42,57,52),true));
        phoneInput=input("+91 98765 43210",InputType.TYPE_CLASS_PHONE); addField(card,phoneInput,8);
        sendButton=button("Send OTP"); addField(card,sendButton,14);
        TextView ol=label("One-time password (OTP)",14,Color.rgb(42,57,52),true); LinearLayout.LayoutParams op=params(-1,-2); op.topMargin=dp(22); card.addView(ol,op);
        otpInput=input("Enter OTP",InputType.TYPE_CLASS_NUMBER); otpInput.setEnabled(false); addField(card,otpInput,8);
        verifyButton=button("Verify number"); verifyButton.setEnabled(false); addField(card,verifyButton,12);
        resendButton=textButton("Resend OTP"); resendButton.setVisibility(View.GONE); card.addView(resendButton,params(-1,dp(44)));
        statusText=label("Enter a number including country code, then request an OTP.",14,Color.rgb(91,105,100),false); LinearLayout.LayoutParams st=params(-1,-2); st.topMargin=dp(18); card.addView(statusText,st);
        verifiedNumberText=label("",12,Color.rgb(91,105,100),false); LinearLayout.LayoutParams vp=params(-1,-2); vp.topMargin=dp(20); page.addView(verifiedNumberText,vp);
        TextView note=label("OTP delivery and verification use your Twilio backend. Local HTTP is for LAN testing only; use HTTPS in production.",12,Color.rgb(104,116,111),false); LinearLayout.LayoutParams np=params(-1,-2); np.topMargin=dp(18); page.addView(note,np);
        setContentView(scroll);
        sendButton.setOnClickListener(v->requestOtp()); resendButton.setOnClickListener(v->requestOtp()); verifyButton.setOnClickListener(v->verifyOtp());
    }
    private void addField(LinearLayout parent, View view, int top) { LinearLayout.LayoutParams p=params(-1,dp(view instanceof EditText?54:52)); p.topMargin=dp(top); parent.addView(view,p); }
    private String normalizedPhone(){return phoneInput.getText().toString().trim().replaceAll("[\\s()-]","");}
    private void requestOtp(){
        String phone=normalizedPhone();
        if(!phone.matches("^\\+[1-9][0-9]{7,14}$")){phoneInput.setError("Use international format, e.g. +919876543210");statusText.setText("Please enter a valid number with country code.");return;}
        activePhone=phone; hideKeyboard(); setBusy(true); statusText.setText("Requesting SMS OTP…");
        JSONObject body=new JSONObject(); try{body.put("phone",phone);body.put("channel","sms");}catch(Exception e){setBusy(false);return;}
        network.execute(()->{ApiResult r=postJson("/api/otp/start",body);runOnUiThread(()->{
            setBusy(false);
            if(r.error!=null){statusText.setText("Cannot reach backend. Check PC IP, Wi-Fi, firewall, and server.");Toast.makeText(this,r.error,Toast.LENGTH_LONG).show();return;}
            if(r.json.optBoolean("ok",false)){codeSent=true;otpInput.setEnabled(true);verifyButton.setEnabled(true);resendButton.setVisibility(View.VISIBLE);phoneInput.setEnabled(false);statusText.setText("OTP requested by SMS. Enter the code received.");}
            else{statusText.setText("Twilio could not start verification. Check backend logs.");Toast.makeText(this,r.json.optString("error","OTP request failed"),Toast.LENGTH_LONG).show();}
        });});
    }
    private void verifyOtp(){
        String code=otpInput.getText().toString().trim();
        if(!code.matches("^[0-9]{4,10}$")||activePhone.isEmpty()){otpInput.setError("Enter the code received by SMS");return;}
        setBusy(true);statusText.setText("Verifying OTP…");JSONObject body=new JSONObject();
        try{body.put("phone",activePhone);body.put("channel","sms");body.put("code",code);}catch(Exception e){setBusy(false);return;}
        network.execute(()->{ApiResult r=postJson("/api/otp/check",body);runOnUiThread(()->{
            setBusy(false);
            if(r.error!=null){statusText.setText("Cannot reach backend. Check connection and retry.");Toast.makeText(this,r.error,Toast.LENGTH_LONG).show();return;}
            if(r.json.optBoolean("ok",false)&&"approved".equalsIgnoreCase(r.json.optString("status",""))){
                getSharedPreferences("guest_verify",MODE_PRIVATE).edit().putString("verified_phone",activePhone).apply();
                statusText.setText("✓ MOBILE NUMBER VERIFIED\n"+activePhone);statusText.setTextColor(Color.rgb(23,107,75));verifiedNumberText.setText("Last verified number on this phone: "+activePhone);
                otpInput.setEnabled(false);verifyButton.setEnabled(false);sendButton.setText("Verify another number");resendButton.setVisibility(View.GONE);codeSent=false;
            }else{statusText.setText("Verification not approved. Enter the latest OTP or request a new one.");statusText.setTextColor(Color.rgb(177,42,42));otpInput.setError("Incorrect, expired, or unapproved OTP");}
        });});
    }
    private ApiResult postJson(String path,JSONObject body){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(BASE_URL+path).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int status=c.getResponseCode();InputStream in=(status>=200&&status<400)?c.getInputStream():c.getErrorStream();StringBuilder sb=new StringBuilder();
            if(in!=null)try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}
            JSONObject json=sb.length()==0?new JSONObject():new JSONObject(sb.toString());
            if(status<200||status>=300)return new ApiResult(json,"Backend HTTP "+status+(json.has("error")?": "+json.optString("error"):""));
            return new ApiResult(json,null);
        }catch(Exception e){return new ApiResult(new JSONObject(),e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{if(c!=null)c.disconnect();}
    }
    private static class ApiResult{final JSONObject json;final String error;ApiResult(JSONObject j,String e){json=j;error=e;}}
    private void setBusy(boolean busy){sendButton.setEnabled(!busy);resendButton.setEnabled(!busy);verifyButton.setEnabled(!busy&&codeSent);phoneInput.setEnabled(!busy&&!codeSent);otpInput.setEnabled(!busy&&codeSent);}
    private void hideKeyboard(){try{InputMethodManager i=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(i!=null)i.hideSoftInputFromWindow(phoneInput.getWindowToken(),0);}catch(Exception ignored){}}
    @Override protected void onDestroy(){super.onDestroy();network.shutdown();}
    private TextView label(String t,int s,int c,boolean b){TextView v=new TextView(this);v.setText(t);v.setTextSize(s);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private EditText input(String h,int type){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setHint(h);e.setInputType(type);e.setPadding(dp(14),0,dp(14),0);e.setBackground(roundRect(Color.rgb(249,251,250),dp(10),Color.rgb(218,226,222)));return e;}
    private Button button(String t){Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.WHITE);b.setBackground(roundRect(Color.rgb(23,107,91),dp(12),Color.rgb(23,107,91)));return b;}
    private Button textButton(String t){Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(Color.rgb(23,107,91));b.setBackgroundColor(Color.TRANSPARENT);return b;}
    private GradientDrawable roundRect(int c,int r,int s){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(r);d.setStroke(dp(1),s);return d;}
    private LinearLayout.LayoutParams params(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
