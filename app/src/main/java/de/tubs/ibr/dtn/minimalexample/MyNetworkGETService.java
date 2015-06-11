package de.tubs.ibr.dtn.minimalexample;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.webkit.WebView;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;



public class MyNetworkGETService extends Service {

    public static final String NO_DESTINATION="NO_DTN_DESTINATION";
    private IBinder mBinder=new LocalBinder();

    private static final String TAG="NetworkGET";

    public MyNetworkGETService() {
    }

    public class LocalBinder extends Binder {
        MyNetworkGETService getService(){
            return MyNetworkGETService.this;
        }
    }

    public static void setText(final TextView myView, final String text){
        if(myView!=null){
            myView.post(new Runnable(){
                @Override
                public void run() {
                    myView.setText(Html.fromHtml(text));
                }
            });
        }
    }

    private class DataThread extends Thread {
        //TextView myView;
        WebView myView;
        Parser thePage;
        String mDest;
        public DataThread (Parser url, WebView view, String dest){
            super("DataThread");
            myView=view;
            thePage=url;
            mDest=dest;
        }

        public void run() {
            String returnString = thePage.getURL()+"\r\n";
            try {

                Socket clientSocket = new Socket(thePage.getDomain(), thePage.getPort());
                DataInputStream clientIn = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());

                clientOut.writeBytes(thePage + "\r\n");
                try {
                    while (true) {
                        returnString += (char) clientIn.readByte();
                    }
                } catch (EOFException e) {
                    clientSocket.close();
                }

            } catch (UnknownHostException uhe) {
                returnString += "HTTP 404 Not Found\r\n\r\nUnknown Host: " + uhe;
            } catch (IOException ioe) {
                returnString += "HTTP 500 Error\r\n\r\nIOException: " + ioe;
            }
            if (myView != null && mDest.equals(NO_DESTINATION)) {
                Log.d(TAG, "Posting to page");
                int headerEnd;
                byte[] payload = returnString.getBytes();
                for(headerEnd=3; headerEnd < payload.length; headerEnd++){
                    if(     payload[headerEnd-3]=='\r' && payload[headerEnd-2]=='\n' &&
                            payload[headerEnd-1]=='\r' && payload[headerEnd]=='\n'){
                        break;
                    }
                }

                //mResult.setText(Html.fromHtml(new String(payload,headerEnd,payload.length-headerEnd)));

                BufferedOutputStream output;
                String filename = ExampleActivity.computeMD5Hash(thePage.getURL());
                try {
                    output = new BufferedOutputStream(openFileOutput(
                            filename,
                            Context.MODE_PRIVATE));
                    output.write(payload,headerEnd,payload.length-headerEnd);
                    output.close();
                } catch (Exception e) {

                }
                myView.loadUrl(filename);
                //setText(myView, returnString);
            } else if (!mDest.equals(NO_DESTINATION)) {
                Log.d(TAG, "Sending va DTN");
                Intent intent=new Intent(MyNetworkGETService.this,MyDtnIntentService.class);
                intent.setAction(MyDtnIntentService.ACTION_SEND_MESSAGE);
                intent.putExtra(MyDtnIntentService.EXTRA_DESTINATION, mDest);
                intent.putExtra(MyDtnIntentService.EXTRA_PAYLOAD,
                        (ExampleActivity.REPLY_FLAG + returnString).getBytes());
                startService(intent);

            }
        }
    }

    public void getPage(String url, WebView view, String destination) {

        try {
            Log.d(TAG, "Service called");
            Parser thePage = new Parser("GET " + url + " HTTP/1.0");
            new DataThread(thePage, view, destination).start();
        } catch (Parser.BadRequestException bre) {
            if (view != null && destination.equals(NO_DESTINATION)) {
                //setText(view, "Bad Request: " + bre);
                view.loadData("Bad Request: " + bre, "text/html", "utf-8");
            } else if (!destination.equals(NO_DESTINATION)) {
                Log.d(TAG, "Bad Request via DTN");
                Intent intent=new Intent(MyNetworkGETService.this,MyDtnIntentService.class);
                intent.setAction(MyDtnIntentService.ACTION_SEND_MESSAGE);
                intent.putExtra(MyDtnIntentService.EXTRA_DESTINATION, destination);
                intent.putExtra(MyDtnIntentService.EXTRA_PAYLOAD,
                        (ExampleActivity.REPLY_FLAG + url + "\r\nHTTP 500 Bad request\r\n\r\nBad Request: "
                                + bre).getBytes());
                startService(intent);
            }
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
