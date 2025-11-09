package ml.bmlzootown.hydravion.authenticate;

import android.content.Context;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

import java.io.IOException;
import java.util.Map;

public class LoginServer extends NanoHTTPD {
    private static final String TAG = "LoginServer";
    private static final int PORT = 8765;
    
    private final Context context;
    private LoginCallback callback;
    
    public interface LoginCallback {
        void onCookieReceived(String sailsSid);
        void onError(String error);
    }
    
    public LoginServer(Context context) {
        super(PORT);
        this.context = context;
    }
    
    @Override
    protected boolean useGzipWhenAccepted(Response response) {
        // Disable GZIP compression to avoid socket closure issues
        return false;
    }
    
    public void setCallback(LoginCallback callback) {
        this.callback = callback;
    }
    
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        
        Log.d(TAG, "Request: " + method + " " + uri);
        
        // Handle CORS preflight requests
        if (method == Method.OPTIONS) {
            Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.addHeader("Access-Control-Allow-Headers", "Content-Type");
            return response;
        }
        
        if (method == Method.GET && uri.equals("/cookie")) {
            return serveCookieInputPage();
        } else if (method == Method.POST && uri.equals("/cookie")) {
            return handleCookieSubmission(session);
        } else if (method == Method.GET && uri.equals("/")) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, 
                "<html><head><meta http-equiv='refresh' content='0;url=/cookie'></head></html>");
        }
        
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
    }
    
    private Response serveCookieInputPage() {
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Hydravion - Login</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body {\n" +
                "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;\n" +
                "            background: #1a1a1a;\n" +
                "            color: #ffffff;\n" +
                "            min-height: 100vh;\n" +
                "            padding: 40px 20px;\n" +
                "            line-height: 1.6;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 800px;\n" +
                "            margin: 0 auto;\n" +
                "        }\n" +
                "        h1 {\n" +
                "            font-size: 36px;\n" +
                "            font-weight: bold;\n" +
                "            margin-bottom: 40px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .instructions {\n" +
                "            background: rgba(255, 255, 255, 0.05);\n" +
                "            padding: 30px;\n" +
                "            border-radius: 8px;\n" +
                "            margin-bottom: 30px;\n" +
                "        }\n" +
                "        .instructions p {\n" +
                "            font-size: 18px;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .instructions h2 {\n" +
                "            font-size: 20px;\n" +
                "            font-weight: bold;\n" +
                "            margin-bottom: 15px;\n" +
                "            margin-top: 25px;\n" +
                "        }\n" +
                "        .instructions ol {\n" +
                "            margin-left: 20px;\n" +
                "            font-size: 16px;\n" +
                "        }\n" +
                "        .instructions li {\n" +
                "            margin-bottom: 10px;\n" +
                "        }\n" +
                "        .instructions code {\n" +
                "            background: rgba(0, 0, 0, 0.3);\n" +
                "            padding: 2px 6px;\n" +
                "            border-radius: 3px;\n" +
                "            font-family: monospace;\n" +
                "        }\n" +
                "        .form-container {\n" +
                "            background: rgba(255, 255, 255, 0.05);\n" +
                "            padding: 30px;\n" +
                "            border-radius: 8px;\n" +
                "        }\n" +
                "        .form-group {\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        label {\n" +
                "            display: block;\n" +
                "            font-size: 16px;\n" +
                "            font-weight: 500;\n" +
                "            margin-bottom: 10px;\n" +
                "        }\n" +
                "        input[type=\"text\"] {\n" +
                "            width: 100%;\n" +
                "            padding: 12px;\n" +
                "            background: rgba(255, 255, 255, 0.1);\n" +
                "            border: 1px solid rgba(255, 255, 255, 0.2);\n" +
                "            border-radius: 4px;\n" +
                "            color: #ffffff;\n" +
                "            font-size: 14px;\n" +
                "            font-family: monospace;\n" +
                "        }\n" +
                "        input[type=\"text\"]:focus {\n" +
                "            outline: none;\n" +
                "            border-color: #007bff;\n" +
                "            background: rgba(255, 255, 255, 0.15);\n" +
                "        }\n" +
                "        input[type=\"text\"]::placeholder {\n" +
                "            color: rgba(255, 255, 255, 0.5);\n" +
                "        }\n" +
                "        button {\n" +
                "            width: 100%;\n" +
                "            padding: 14px;\n" +
                "            background: #007bff;\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            border-radius: 4px;\n" +
                "            font-size: 18px;\n" +
                "            font-weight: 500;\n" +
                "            cursor: pointer;\n" +
                "            transition: background 0.2s;\n" +
                "        }\n" +
                "        button:hover {\n" +
                "            background: #0056b3;\n" +
                "        }\n" +
                "        button:disabled {\n" +
                "            background: #555;\n" +
                "            cursor: not-allowed;\n" +
                "        }\n" +
                "        .message {\n" +
                "            margin-top: 15px;\n" +
                "            padding: 12px;\n" +
                "            border-radius: 4px;\n" +
                "            font-size: 16px;\n" +
                "        }\n" +
                "        .message.error {\n" +
                "            background: rgba(211, 47, 47, 0.2);\n" +
                "            color: #ff6b6b;\n" +
                "            border: 1px solid rgba(211, 47, 47, 0.3);\n" +
                "        }\n" +
                "        .message.success {\n" +
                "            background: rgba(56, 142, 60, 0.2);\n" +
                "            color: #4caf50;\n" +
                "            border: 1px solid rgba(56, 142, 60, 0.3);\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>Login</h1>\n" +
                "        \n" +
                "        <div class=\"instructions\">\n" +
                "            <p>Enter your <code>sails.sid</code> cookie value below to authenticate with Hydravion.</p>\n" +
                "            \n" +
                "            <h2>How to get cookie:</h2>\n" +
                "            <ol>\n" +
                "                <li>Login to <a href=\"https://www.floatplane.com\" target=\"_blank\" style=\"color: #4a9eff;\">floatplane.com</a></li>\n" +
                "                <li>Open DevTools (F12 or right-click > Inspect)</li>\n" +
                "                <li>Go to Application (Chrome) or Storage (Firefox)</li>\n" +
                "                <li style=\"margin-left: 20px;\">&gt; Cookies &gt; floatplane.com</li>\n" +
                "                <li>Copy the <strong>value</strong> of <code>sails.sid</code> (not the name)</li>\n" +
                "                <li>Paste it in the field below</li>\n" +
                "            </ol>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"form-container\">\n" +
                "            <form id=\"cookieForm\">\n" +
                "                <div class=\"form-group\">\n" +
                "                    <label for=\"cookieValue\">sails.sid Cookie Value:</label>\n" +
                "                    <input type=\"text\" id=\"cookieValue\" name=\"cookieValue\" placeholder=\"Paste cookie value here\" required autofocus>\n" +
                "                </div>\n" +
                "                <button type=\"submit\" id=\"submitBtn\">Submit Cookie</button>\n" +
                "                <div id=\"message\"></div>\n" +
                "            </form>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <script>\n" +
                "        document.getElementById('cookieForm').addEventListener('submit', async function(e) {\n" +
                "            e.preventDefault();\n" +
                "            const cookieValue = document.getElementById('cookieValue').value.trim();\n" +
                "            const submitBtn = document.getElementById('submitBtn');\n" +
                "            \n" +
                "            if (!cookieValue) {\n" +
                "                showMessage('Please enter the cookie value.', 'error');\n" +
                "                return;\n" +
                "            }\n" +
                "            \n" +
                "            submitBtn.disabled = true;\n" +
                "            submitBtn.textContent = 'Submitting...';\n" +
                "            \n" +
                "            // Ensure it has the full cookie format\n" +
                "            const sailsSid = cookieValue.startsWith('sails.sid=') ? cookieValue : 'sails.sid=' + cookieValue;\n" +
                "            \n" +
                "            try {\n" +
                "                const response = await fetch('/cookie', {\n" +
                "                    method: 'POST',\n" +
                "                    headers: { 'Content-Type': 'application/json' },\n" +
                "                    body: JSON.stringify({ sailsSid: sailsSid })\n" +
                "                });\n" +
                "                \n" +
                "                if (response.ok) {\n" +
                "                    showMessage('Cookie submitted successfully! You can close this page.', 'success');\n" +
                "                    submitBtn.textContent = 'Success!';\n" +
                "                } else {\n" +
                "                    showMessage('Failed to submit cookie. Please try again.', 'error');\n" +
                "                    submitBtn.disabled = false;\n" +
                "                    submitBtn.textContent = 'Submit Cookie';\n" +
                "                }\n" +
                "            } catch (error) {\n" +
                "                showMessage('Error: ' + error.message, 'error');\n" +
                "                submitBtn.disabled = false;\n" +
                "                submitBtn.textContent = 'Submit Cookie';\n" +
                "            }\n" +
                "        });\n" +
                "        \n" +
                "        function showMessage(msg, type) {\n" +
                "            const msgDiv = document.getElementById('message');\n" +
                "            msgDiv.textContent = msg;\n" +
                "            msgDiv.className = 'message ' + type;\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
        
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }
    
    private Response handleCookieSubmission(IHTTPSession session) {
        String postData = null;
        
        // Use parseBody which properly handles the input stream for NanoHTTPD
        try {
            Map<String, String> files = new java.util.HashMap<>();
            session.parseBody(files);
            
            // For JSON POST data, NanoHTTPD may store it in different ways
            // Check all entries in the files map
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String value = entry.getValue();
                if (value != null && value.trim().startsWith("{")) {
                    postData = value;
                    Log.d(TAG, "Found JSON in parseBody: " + postData.substring(0, Math.min(100, postData.length())));
                    break;
                }
            }
            
            // Also check if there's a "postData" key specifically
            if ((postData == null || postData.isEmpty()) && files.containsKey("postData")) {
                postData = files.get("postData");
                Log.d(TAG, "Found postData key: " + (postData != null ? postData.substring(0, Math.min(100, postData.length())) : "null"));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error using parseBody", e);
        }
        
        // Fallback: check query parameters
        if (postData == null || postData.isEmpty()) {
            Map<String, String> parms = session.getParms();
            if (parms.containsKey("sailsSid")) {
                postData = "{\"sailsSid\":\"" + parms.get("sailsSid") + "\"}";
                Log.d(TAG, "Using query parameter fallback");
            }
        }
        
        if (postData == null || postData.isEmpty()) {
            Response errorResponse = NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", 
                "{\"status\":\"error\",\"message\":\"No data received\"}");
            errorResponse.addHeader("Access-Control-Allow-Origin", "*");
            
            if (callback != null) {
                callback.onError("No cookie data received");
            }
            return errorResponse;
        }
        
        // Parse JSON
        String sailsSid = null;
        if (postData.contains("sailsSid")) {
            // Simple JSON parsing - handle both quoted and unquoted values
            String pattern = "\"sailsSid\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(postData);
            if (m.find()) {
                sailsSid = m.group(1);
            } else {
                // Try without quotes
                int start = postData.indexOf("\"sailsSid\":") + 11;
                if (start > 10) {
                    int end = postData.indexOf(",", start);
                    if (end == -1) end = postData.indexOf("}", start);
                    if (end > start) {
                        String value = postData.substring(start, end).trim();
                        // Remove quotes if present
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        sailsSid = value;
                    }
                }
            }
        }
        
        try {
            if (sailsSid != null && !sailsSid.isEmpty()) {
                // Ensure it has the full cookie format
                if (!sailsSid.startsWith("sails.sid=")) {
                    sailsSid = "sails.sid=" + sailsSid;
                }
                
                Log.d(TAG, "Creating success response for cookie");
                
                // Create response first with CORS headers - send it immediately
                // Content-Type is already set by the MIME type parameter
                Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"status\":\"success\"}");
                response.addHeader("Access-Control-Allow-Origin", "*");
                response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                response.addHeader("Access-Control-Allow-Headers", "Content-Type");
                
                Log.d(TAG, "Response created, starting async callback");
                
                // Store callback and sailsSid for async execution
                final String finalSailsSid = sailsSid;
                final LoginCallback finalCallback = callback;
                
                // Call callback asynchronously AFTER returning response to ensure response is sent first
                new Thread(() -> {
                    try {
                        // Small delay to ensure response is fully sent
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (finalCallback != null) {
                        Log.d(TAG, "Calling onCookieReceived callback");
                        finalCallback.onCookieReceived(finalSailsSid);
                    }
                }).start();
                
                Log.d(TAG, "Returning success response");
                return response;
            } else {
                Log.d(TAG, "Creating error response - invalid cookie data");
                Response errorResponse = NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", 
                    "{\"status\":\"error\",\"message\":\"Invalid cookie data\"}");
                errorResponse.addHeader("Access-Control-Allow-Origin", "*");
                
                // Call error callback asynchronously
                final LoginCallback finalCallback = callback;
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (finalCallback != null) {
                        finalCallback.onError("Invalid cookie data received");
                    }
                }).start();
                
                return errorResponse;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in handleCookieSubmission", e);
            // Always return a response, even on error
            Response errorResponse = NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", 
                "{\"status\":\"error\",\"message\":\"Internal server error\"}");
            errorResponse.addHeader("Access-Control-Allow-Origin", "*");
            return errorResponse;
        }
    }
    
    public int getPort() {
        return PORT;
    }
}

