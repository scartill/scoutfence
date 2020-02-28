package com.example.scoutfence;

import java.util.Map;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

public class LWIntegrationServer extends NanoHTTPD {
    public LWIntegrationServer(int port) {
        super(port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String msg = "<html><body><h1>Hello server</h1>\n";
        Map<String, List<String>> params = session.getParameters();
        List<String> paramStrings = params.get("test");
        String value = paramStrings.get(0);
        String response = "<html><body><p>Hello, " + value + "</p></body></html>";
        return newFixedLengthResponse( response );
    }
}
