package ml.bmlzootown.hydravion.authenticate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.ArrayList;

import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.browse.MainFragment;

public class QrLoginActivity extends Activity {
    private static final String TAG = "QrLoginActivity";
    
    private LoginServer server;
    private ImageView qrCodeView;
    private TextView urlTextView;
    private TextView statusTextView;
    private Button manualEntryButton;
    private android.view.View statusIndicator;
    private String localServerUrl;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_login);
        
        qrCodeView = findViewById(R.id.qr_code);
        urlTextView = findViewById(R.id.url_text);
        statusTextView = findViewById(R.id.status_text);
        manualEntryButton = findViewById(R.id.manual_entry_button);
        statusIndicator = findViewById(R.id.status_indicator);
        
        // Initially set to red (offline) until server starts
        updateServerStatus(false);
        
        manualEntryButton.setOnClickListener(v -> showManualEntryDialog());
        
        startServerAndShowQR();
    }
    
    private void startServerAndShowQR() {
        try {
            String localIp = getLocalIpAddress();
            if (localIp == null) {
                Toast.makeText(this, "Could not determine local IP address. Please check your network connection.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            
            // Local server URL for cookie input - QR code points here
            localServerUrl = "http://" + localIp + ":8765";
            
            MainFragment.dLog(TAG, "Starting server for cookie input at: " + localServerUrl);
            
            // Start the HTTP server (no Turnstile needed - just cookie input)
            server = new LoginServer(this);
            server.setCallback(new LoginServer.LoginCallback() {
                @Override
                public void onCookieReceived(String sailsSid) {
                    runOnUiThread(() -> {
                        statusTextView.setText("Login successful! Processing...");
                        handleLoginSuccess(sailsSid);
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        statusTextView.setText("Error: " + error);
                        MainFragment.dLog(TAG, "Login error: " + error);
                    });
                }
            });
            
            try {
                server.start();
                MainFragment.dLog(TAG, "Server started on port " + server.getPort());
                // Server started successfully - set indicator to green
                updateServerStatus(true);
            } catch (IOException e) {
                Log.e(TAG, "Failed to start server", e);
                // Server failed to start - set indicator to red
                updateServerStatus(false);
                Toast.makeText(this, "Failed to start login server: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            
            // Generate QR code pointing to local server
            generateQRCode(localServerUrl);
            urlTextView.setText(localServerUrl);
            statusTextView.setText("Waiting for cookie...");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up QR login", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void generateQRCode(String url) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            // Generate larger bitmap for better quality, then scale down
            Bitmap bitmap = barcodeEncoder.encodeBitmap(url, BarcodeFormat.QR_CODE, 500, 500);
            qrCodeView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            Log.e(TAG, "Error generating QR code", e);
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showManualEntryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manual Cookie Entry");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Paste sails.sid cookie value here");
        input.setPadding(50, 20, 50, 20);
        builder.setView(input);
        
        builder.setPositiveButton("Submit", (dialog, which) -> {
            String cookieValue = input.getText().toString().trim();
            if (!cookieValue.isEmpty()) {
                // Ensure it has the full cookie format
                String sailsSid = cookieValue.startsWith("sails.sid=") ? cookieValue : "sails.sid=" + cookieValue;
                handleLoginSuccess(sailsSid);
            } else {
                Toast.makeText(this, "Please enter a cookie value", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    private String getLocalIpAddress() {
        try {
            // Try WiFi first
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();
                if (ipAddress != 0) {
                    return Formatter.formatIpAddress(ipAddress);
                }
            }
            
            // Fallback: enumerate network interfaces
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Error getting local IP", e);
        }
        return null;
    }
    
    private void handleLoginSuccess(String sailsSid) {
        // Stop the server
        if (server != null) {
            server.stop();
            updateServerStatus(false);
        }
        
        // Process the cookie similar to the old login flow
        ArrayList<String> cookies = new ArrayList<>();
        cookies.add(sailsSid);
        
        Intent intent = new Intent();
        intent.putStringArrayListExtra("cookies", cookies);
        setResult(1, intent);
        finish();
    }
    
    private void updateServerStatus(boolean isRunning) {
        if (statusIndicator != null) {
            int drawableRes = isRunning 
                ? R.drawable.status_indicator_online 
                : R.drawable.status_indicator_offline;
            statusIndicator.setBackgroundResource(drawableRes);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
            updateServerStatus(false);
        }
    }
}

