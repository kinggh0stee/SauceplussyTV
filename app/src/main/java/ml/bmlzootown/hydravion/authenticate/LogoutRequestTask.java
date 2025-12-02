package ml.bmlzootown.hydravion.authenticate;

import android.content.Context;

import com.android.volley.Header;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogoutRequestTask {
    private Context context;

    private static final String version = ml.bmlzootown.hydravion.BuildConfig.VERSION_NAME;
    private static final String userAgent = String.format("Hydravion %s (AndroidTV)", version);

    public LogoutRequestTask(Context context) {
        this.context = context;
    }

    public void logout(String accessToken, final LogoutRequestTask.VolleyCallback callback) {
        // Revoke access token via revocation endpoint
        String uri = "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/revoke";
        RequestQueue queue = Volley.newRequestQueue(this.context);
        StringRequest stringRequest = new StringRequest(Request.Method.POST, uri,
                callback::onSuccess, error -> {
            error.printStackTrace();
            callback.onError(error);
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Accept", "application/json");
                params.put("User-Agent", userAgent);
                return params;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("client_id", "hydravion");
                params.put("token", accessToken);
                return params;
            }
        };

        queue.add(stringRequest);
    }

    public interface VolleyCallback {
        void onSuccess(String response);

        void onError(VolleyError error);
    }

}
