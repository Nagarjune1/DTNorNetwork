package de.tubs.ibr.dtn.minimalexample;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.protocol.HTTP;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class ExampleActivity extends Activity {

    public static final char REQUEST_FLAG='Q';
    public static final char REPLY_FLAG='A';
    public static final char SUBS_FLAG='S';
    public static final char BACK_FLAG='B';

    TextView mDestination = null;
    TextView mMessage = null;
   // private TextView mResult;
    private MyNetworkGETService mService;
    boolean mBound=false;
    private WebView mWebView=null;

    private final static String TAG="ExampleActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example);

        mDestination = (TextView)findViewById(R.id.textDestination);
        mMessage = (TextView)findViewById(R.id.textMessage);
       // mResult=(TextView) findViewById(R.id.resultText);
        mWebView=(WebView) findViewById(R.id.resultWeb);
        mWebView.setDownloadListener(new downloadHandler());
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                Log.d(TAG, "Trying to load: " + url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {

                ConnectivityManager cm = (ConnectivityManager)
                        getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
                    return null;

                try {
                    HTTPReply reply = new HTTPReply(getApplicationContext(), request.getUrl().toString());
                    WebResourceResponse response = new WebResourceResponse(reply.getMimeType(),
                            "utf-8",
                            reply.responseCode(),
                            reply.reasonPhrase(),
                            reply.getHeaders(),
                            new ByteArrayInputStream(reply.getData()));
                    Log.d(TAG, "attempting to load " + request.getUrl().toString() + " type " + reply.getMimeType());
                    InputStream test = response.getData();
                    String allBytes="";
                    int read;
                    int count=0;
                    try {
                        while ((read = test.read()) != -1) {
                            allBytes += " " + read;
                            count++;
                        }
                        Log.d(TAG, "data: " + allBytes);
                        Log.d(TAG,"count: " + count);
                    } catch (IOException e) {}
                    return response;

                } catch (FileNotFoundException fnfe) {
                    Intent intent = new Intent(ExampleActivity.this, MyDtnIntentService.class);
                    intent.setAction(MyDtnIntentService.ACTION_SEND_MESSAGE);
                    intent.putExtra(MyDtnIntentService.EXTRA_DESTINATION, mDestination.getText().toString());
                    intent.putExtra(MyDtnIntentService.EXTRA_PAYLOAD,
                            (SUBS_FLAG + request.getUrl().toString()).getBytes());
                    startService(intent);
                    Log.d(TAG, "sending intercept for DTN " + request.getUrl());
                    return null;

                }



            }


        });
        Log.d(TAG,"Download Listener set");

        if ( de.tubs.ibr.dtn.Intent.ENDPOINT_INTERACT.equals( getIntent().getAction() ) ) {
            // check if an endpoint exists
            if (getIntent().getExtras().containsKey(de.tubs.ibr.dtn.Intent.EXTRA_ENDPOINT)) {
                // extract endpoint
                String endpoint = getIntent().getExtras().getString(de.tubs.ibr.dtn.Intent.EXTRA_ENDPOINT);

                 if (endpoint.startsWith("dtn:")) {
                    mDestination.setText(endpoint + "/minimal-example");
                }

            }
        }

        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConnectivityManager cm = (ConnectivityManager)
                        getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                boolean isWifiConn = networkInfo.isConnected();
                networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                boolean isMobileConn = networkInfo.isConnected();
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                TelephonyManager tm =
                        (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

                if (activeNetwork != null) {
                    switch (activeNetwork.getType()) {
                        case (ConnectivityManager.TYPE_WIFI):
                            Toast.makeText(ExampleActivity.this, "Wi-Fi only", Toast.LENGTH_LONG).show();



                            BufferedInputStream input;
                            try {

                                //String filename=computeMD5Hash(mMessage.getText().toString());

                                String urlname = mMessage.getText().toString();
/*
                                input = new BufferedInputStream(openFileInput(filename));
                                int read;
                                String buffer="";
                                while ((read = input.read()) != -1) {
                                    buffer+=(char)read;
                                }
                                input.close();

                                File temp = new File(getFilesDir(),filename);

                                //mResult.setText(Html.fromHtml(buffer));
                                //mWebView.loadUrl("file:///"+temp.getAbsolutePath());
                                mWebView.loadDataWithBaseURL(urlname.substring(0,urlname.lastIndexOf('/')+1),buffer,"text/html","utf-8",null);
                                */

                                HTTPReply page = new HTTPReply(getApplicationContext(),urlname);
                                mWebView.loadDataWithBaseURL(page.getURL(),new String(page.getData()),page.getMimeType(),"utf-8",null);

                                Toast.makeText(ExampleActivity.this,
                                        "Loaded from file",
                                        Toast.LENGTH_LONG).show();

                            } catch(Exception e) {
                                Log.d(TAG,"Exception in file open: " + e);
                                Toast.makeText(ExampleActivity.this,
                                        REQUEST_FLAG + mMessage.getText().toString(),
                                        Toast.LENGTH_LONG).show();

                                Intent intent = new Intent(ExampleActivity.this, MyDtnIntentService.class);
                                intent.setAction(MyDtnIntentService.ACTION_SEND_MESSAGE);
                                intent.putExtra(MyDtnIntentService.EXTRA_DESTINATION, mDestination.getText().toString());
                                intent.putExtra(MyDtnIntentService.EXTRA_PAYLOAD,
                                        (REQUEST_FLAG + mMessage.getText().toString()).getBytes());
                                startService(intent);
                            }
                    break;
                    case (ConnectivityManager.TYPE_MOBILE): {
                        switch (tm.getNetworkType()) {
                            case (TelephonyManager.NETWORK_TYPE_LTE):
                            case (TelephonyManager.NETWORK_TYPE_HSPAP):
                            case (TelephonyManager.NETWORK_TYPE_EDGE):
                            case (TelephonyManager.NETWORK_TYPE_GPRS):
                                mService.getPage(mMessage.getText().toString(), mWebView, MyNetworkGETService.NO_DESTINATION, '\0');
                                break;
                            default:
                                break;
                        }
                        break;
                    }
                    default:
                        Toast.makeText(ExampleActivity.this, "Other?", Toast.LENGTH_LONG).show();
                        break;
                }
            }

            else

            {
                Toast.makeText(ExampleActivity.this, "None, I guess?", Toast.LENGTH_LONG).show();

            }
        }
    });

        IntentFilter filter = new IntentFilter(MyDtnIntentService.ACTION_RECV_MESSAGE);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_example, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_clear) {
            clearPages();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void clearPages(){
        String[] files=fileList();
        for(int i=0; i < files.length; i++){
            deleteFile(files[i]);
        }
        Toast.makeText(ExampleActivity.this, "Pages cleared", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }
    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this,MyNetworkGETService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mBound){
            unbindService(mConnection);
        }
    }

    protected ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((MyNetworkGETService.LocalBinder) service).getService();
            mBound=true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound=false;
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mDestination.setText(intent.getStringExtra(MyDtnIntentService.EXTRA_SOURCE));
            //Toast.makeText(ExampleActivity.this, new String(intent.getByteArrayExtra(MyDtnIntentService.EXTRA_PAYLOAD)), Toast.LENGTH_LONG).show();
            if (MyDtnIntentService.ACTION_RECV_MESSAGE.equals(intent.getAction())) {

                byte[] payload = intent.getByteArrayExtra(MyDtnIntentService.EXTRA_PAYLOAD);
                //Log.d(TAG, "Received: " + new String(payload));
                if((char)payload[0]==REQUEST_FLAG || (char)payload[0]==SUBS_FLAG){
                    Log.d(TAG, "Got a request");

                    char type;
                    if((char)payload[0]==REQUEST_FLAG){
                        type=REPLY_FLAG;
                    } else {
                        type=BACK_FLAG;
                    }

                    mService.getPage(new String(payload, 1, payload.length - 1), null,
                            intent.getStringExtra(MyDtnIntentService.EXTRA_SOURCE), type);
                } else if((char)payload[0]==REPLY_FLAG || (char)payload[0]==BACK_FLAG){

                    HTTPReply reply = new HTTPReply(getApplicationContext(), Arrays.copyOfRange(payload, 1, payload.length));
                    reply.saveToFile();

                    Log.d(TAG, "Saving " + reply.getURL());
                    if((char)payload[0]==REPLY_FLAG) {
                        Log.d(TAG, "Displaying " + reply.getURL());
                        mWebView.loadDataWithBaseURL(reply.getURL(), new String(reply.getData()), reply.getMimeType(), "utf-8", null);
                    }
                }
            }
        }
    };

    private class downloadHandler implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            Log.d(TAG, "To DL: "+ url + " uA: " + userAgent +
                    " cD: " + contentDisposition + " m: " + mimetype + " cL: "+ contentLength);

        }
    }


}
