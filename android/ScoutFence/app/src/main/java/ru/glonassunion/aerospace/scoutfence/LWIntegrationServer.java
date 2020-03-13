package ru.glonassunion.aerospace.scoutfence;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import fi.iki.elonen.NanoHTTPD;
import org.json.JSONObject;

public class LWIntegrationServer extends NanoHTTPD {
    private ILorawanJsonHandler handler = null;
   
    public LWIntegrationServer(int port, ILorawanJsonHandler handler) {
        super(port);
        this.handler = handler;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Log.i("LoRaWAN", 
            "Processing incoming HTTP request with JSON payload from " + session.getUri() +
            " (method " + session.getMethod() + ")");
        Map<String, String> files = new HashMap<String, String>();
        try {
            session.parseBody(files);
        } catch (IOException ioe) {
            Log.e("LoRaWAN", "IOException while receiving JSON " + ioe.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
        } catch (ResponseException re) {
            Log.e("LoRaWAN", "ResponseException while receiving JSON " + re.getMessage());
            return newFixedLengthResponse(re.getStatus(), MIME_PLAINTEXT, re.getMessage());
        }

        // NB: Nasty nanoHTTPD handles different headers differently
        final String body = session.getQueryParameterString();
        final String postData = files.get("postData");

        Handler mainThreadHangler = new Handler(Looper.getMainLooper());

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if(body != null)
                {
                    handler.HandleLorawanJSON(body);
                } else {
                    handler.HandleLorawanJSON(postData);
                }

            }
        };
        mainThreadHangler.post(runnable);

        return newFixedLengthResponse("OK");
    }
}
