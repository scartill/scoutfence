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

import fi.iki.elonen.NanoHTTPD;

public class LWIntegrationServer extends NanoHTTPD {
    private ILorawanJsonHandler handler = null;

    public LWIntegrationServer(int port, ILorawanJsonHandler handler) {
        super(port);
        this.handler = handler;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> files = new HashMap<String, String>();
        try {
            session.parseBody(files);
        } catch (IOException ioe) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
        } catch (ResponseException re) {
            return newFixedLengthResponse(re.getStatus(), MIME_PLAINTEXT, re.getMessage());
        }
        final String body = session.getQueryParameterString();

        Handler mainThreadHangler = new Handler(Looper.getMainLooper());

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                handler.HandleLorawanJSON(body);
            }
        };
        mainThreadHangler.post(runnable);

        return newFixedLengthResponse("OK");
    }
}
