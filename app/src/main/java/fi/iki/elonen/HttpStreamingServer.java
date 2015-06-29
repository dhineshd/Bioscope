package fi.iki.elonen;

import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Created by johny on 5/17/15.
 */
public class HttpStreamingServer extends NanoHTTPD {
    private static final int SERVER_PORT = 8888;
    private static final String TAG = HttpStreamingServer.class.getName();

    private ParcelFileDescriptor readFileDescriptor;

    public HttpStreamingServer(ParcelFileDescriptor readFileDescriptor) {
        super(SERVER_PORT);
        this.readFileDescriptor = readFileDescriptor;
    }

    public int getServerPort(){
        return SERVER_PORT;
    }

    public Response serve(IHTTPSession session){
        return serve(session.getUri(), session.getMethod(), session.getHeaders(), session.getParms(), null);
    }


    public Response serve(String uri, Method method, Map<String, String> headers, Map<String, String> params, Map<String, String> files) {
        String mimeType = "image/jpeg";
        if (uri.endsWith(".mp4")){
            mimeType = "video/mp4";
        }
        String currentUri = uri;
        if (currentUri != null && currentUri.equals(uri)) {
            String range = null;
            Log.d(TAG, "Request headers:");
            for (String key : headers.keySet()) {
                Log.d(TAG, "  " + key + ":" + headers.get(key));
                if ("range".equals(key)) {
                    range = headers.get(key);
                }
            }
            try {
                if (range == null) {
                    Log.d(TAG, "Sending full response");
                    return getFullResponse(uri, mimeType);
                } else {
                    Log.d(TAG, "Sending partial response");
                    return getPartialResponse(uri, mimeType, range);
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception serving file: " + uri, e);
            }
        } else {
            Log.d(TAG, "Not serving request for: " + uri);
        }

        Response response = new Response(Response.Status.NOT_FOUND, mimeType, new ByteArrayInputStream("File not found".getBytes(StandardCharsets.UTF_8)), 0);
        return response;
    }

    private Response getFullResponse(String filePath, String mimeType) throws FileNotFoundException {
        //cleanupStreams();
        Log.d(TAG, "Request file path = " + filePath);
        return playFromFile(filePath, mimeType);
        //return playFromSocket(mimeType);
    }

    private Response playFromFile(String filePath, String mimeType) throws FileNotFoundException {
        File file = new File(filePath);
        Log.d(TAG, "File size = " + file.length());
        FileInputStream fileInputStream = new FileInputStream(file);
        Response response = new Response(Response.Status.OK, mimeType, fileInputStream, file.length());
        return response;
    }

    private Response playFromSocket(String mimeType){
        InputStream fileInputStream = new AutoCloseInputStream(readFileDescriptor);
        Response response = new Response(Response.Status.OK, mimeType, fileInputStream, -1);
        return response;
    }

    private Response getPartialResponse(String filePath, String mimeType, String rangeHeader) throws IOException {
        return playFromFilePartialResponse(filePath, mimeType, rangeHeader);
        //return playFromSocket(mimeType);
    }
    private Response playFromFilePartialResponse(String filePath, String mimeType, String rangeHeader) throws IOException {

        File file = new File(filePath);
        String rangeValue = rangeHeader.trim().substring("bytes=".length());
        long fileLength = file.length();
        long start, end;
        if (rangeValue.startsWith("-")) {
            end = fileLength - 1;
            start = fileLength - 1
                    - Long.parseLong(rangeValue.substring("-".length()));
        } else {
            String[] range = rangeValue.split("-");
            start = Long.parseLong(range[0]);
            end = range.length > 1 ? Long.parseLong(range[1])
                    : fileLength - 1;
        }
        if (end > fileLength - 1) {
            end = fileLength - 1;
        }

        Log.d(TAG, "Range header = " + rangeHeader);
        if (start <= end) {
            long contentLength = end - start + 1;
            //cleanupStreams();
            FileInputStream fileInputStream = new FileInputStream(file);
            //AutoCloseInputStream fileInputStream = new AutoCloseInputStream(readFileDescriptor);

            //noinspection ResultOfMethodCallIgnored
            fileInputStream.skip(start);
            Response response = new Response(Response.Status.PARTIAL_CONTENT, mimeType, fileInputStream, 0);
            response.addHeader("Content-Length", contentLength + "");
            response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            response.addHeader("Content-Type", mimeType);
            Log.d(TAG, "Response = " + response.toString());
            return response;
        } else {
            //return null;
            //Log.d(TAG, "Returning range not satisfiable response");
            return new Response(Response.Status.RANGE_NOT_SATISFIABLE, mimeType, null, 0);
        }
    }
}
