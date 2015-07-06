package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.SurfaceTextureDisplay;
import com.trioscope.chameleon.stream.NoSSLv3SocketFactory;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;
import com.trioscope.chameleon.types.PeerInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionEstablishedActivity extends ActionBarActivity {
    public static final String PEER_INFO = "PEER_INFO";
    private ChameleonApplication chameleonApplication;
    private ConnectToServerTask connectToServerTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_established);

        chameleonApplication = (ChameleonApplication) getApplication();

        // Display camera preview
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.relativeLayout_session_preview);
        layout.addView(generatePreviewDisplay());

        // Retrieve peer info to start streaming
        Gson gson = new Gson();
        Intent intent = getIntent();
        PeerInfo peerInfo = gson.fromJson(intent.getStringExtra(PEER_INFO), PeerInfo.class);

        // Start streaming the preview
        chameleonApplication.getStreamListener().setStreamingStarted(true);

        connectToServerTask = new ConnectToServerTask(peerInfo.getIpAddress(), peerInfo.getPort());
        connectToServerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private SurfaceTextureDisplay generatePreviewDisplay(){
        final EGLContextAvailableMessage eglContextAvailableMessage = chameleonApplication.getGlobalEglContextInfo();
        SurfaceTextureDisplay previewDisplay = new SurfaceTextureDisplay(this);
        previewDisplay.setEGLContextFactory(new GLSurfaceView.EGLContextFactory() {
            @Override
            public javax.microedition.khronos.egl.EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
                log.info("Creating shared EGLContext");
                int[] attrib2_list = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE
                };

                EGLContext newContext = ((EGL10) EGLContext.getEGL()).eglCreateContext(
                        display, eglConfig, eglContextAvailableMessage.getEglContext(), attrib2_list);
                log.info("Created a shared EGL context: {}", newContext);
                return newContext;
            }

            @Override
            public void destroyContext(EGL10 egl, EGLDisplay display, javax.microedition.khronos.egl.EGLContext context) {
                log.info("EGLContext is being destroyed");
                egl.eglDestroyContext(display, context);
            }
        });

        previewDisplay.setTextureId(eglContextAvailableMessage.getGlTextureId());
        previewDisplay.setToDisplay(eglContextAvailableMessage.getSurfaceTexture());
        //previewDisplay.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        previewDisplay.setRenderer(previewDisplay.new SurfaceTextureRenderer(((ChameleonApplication) getApplication()).getRotationState()));
        return previewDisplay;
    }

    class ConnectToServerTask extends AsyncTask<Void, Void, Void>{
        private Thread mThread;

        public ConnectToServerTask(final InetAddress hostIp, final int port){
            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    connectToRemoteHost(hostIp, port);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            mThread.start();
            return null;
        }

        public void tearDown(){
            mThread.interrupt();
        }

    }

    private void connectToRemoteHost(final InetAddress remoteHostIp, final int port){
        log.info("Connect to remote host invoked Thread = {}", Thread.currentThread());
        try {
            // Wait till we can reach the remote host. May take time to refresh ARP cache
            while (!remoteHostIp.isReachable(1000));
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore trustStore = KeyStore.getInstance("BKS");
            InputStream trustStoreInputStream =  getApplicationContext().getResources().openRawResource(R.raw.truststore);
            trustStore.load(trustStoreInputStream, "poiuyt".toCharArray());
            trustStoreInputStream.close();
            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext ctx = SSLContext.getInstance("TLSv1.2");
            ctx.init(null, tmf.getTrustManagers(), null);
            //ctx.init(null, null, null);
            SSLSocketFactory sslFactory = new NoSSLv3SocketFactory(ctx.getSocketFactory());
            SSLSocket socket = (SSLSocket) sslFactory.createSocket(remoteHostIp, port);
            socket.setEnabledProtocols(new String[]{"SSLv3"});
            log.info("SSL client enabled protocols {}", Arrays.toString(socket.getEnabledProtocols()));
            log.info("SSL client enabled cipher suites {}", Arrays.toString(socket.getEnabledCipherSuites()));
            //socket.startHandshake();

            //socket.setSoTimeout(5000);

            final ImageView imageView = (ImageView) findViewById(R.id.imageView_stream_remote);
            final byte[] buffer = new byte[4096 * 5];
            InputStream inputStream = socket.getInputStream();
            while (!Thread.currentThread().isInterrupted()){
                // TODO More robust
                final int bytesRead = inputStream.read(buffer);
                if (bytesRead != -1){
                    //log.info("Received preview image from remote server bytes = " + bytesRead);
                    final Bitmap bmp = BitmapFactory.decodeByteArray(buffer, 0, bytesRead);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (imageView != null) {
                                imageView.setImageBitmap(bmp);
                            }
                        }
                    });
                }
            }
        } catch (IOException e) {
            //throw new RuntimeException(e);
            log.warn("Connection to remote server closed", e);
            //log.warn("Connection to remote server closed", e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_connection_established, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {

        chameleonApplication.setSessionStarted(false);
        chameleonApplication.getStreamListener().setStreamingStarted(false);

        //Re-use MainActivity instance if already present. If not, create new instance.
        Intent openMainActivity= new Intent(getApplicationContext(), MainActivity.class);
        openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(openMainActivity);
        super.onBackPressed();
    }
}
