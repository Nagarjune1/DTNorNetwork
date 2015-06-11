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
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;


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
        char mType;

        public DataThread (Parser url, WebView view, String dest, char type){
            super("DataThread");
            myView=view;
            thePage=url;
            mDest=dest;
            mType=type;
        }

        public void run() {
            String returnString = mType+thePage.getURL()+"\r\n";
            ByteArrayOutputStream returnArrayStream= new ByteArrayOutputStream();
            byte[] returnArray;
            try{

                returnArrayStream.write(returnString.getBytes());

                Socket clientSocket = new Socket(thePage.getDomain(), thePage.getPort());
                DataInputStream clientIn = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());

                clientOut.writeBytes(thePage + "\r\n");
                int count=0;
                try {

                    while (true) {
                        byte output = clientIn.readByte();
                        returnArrayStream.write(output);
                        count++;
                    }

                } catch (EOFException e) {
                    clientSocket.close();

                }
                returnArray=returnArrayStream.toByteArray();
                String debugString="";
                for(int i=0; i < returnArray.length; i++){
                    debugString+= " " + (int)returnArray[i];
                }
                Log.d(TAG,"Data: " + debugString);
                Log.d(TAG, "count: " + count + " len: "+returnArray.length);

            } catch (UnknownHostException uhe) {
                returnString += "HTTP 404 Not Found\r\n\r\nUnknown Host: " + uhe;
                returnArray=returnString.getBytes();
            } catch (IOException ioe) {
                returnString += "HTTP 500 Error\r\n\r\nIOException: " + ioe;
                returnArray=returnString.getBytes();
            }
            if (myView != null && mDest.equals(NO_DESTINATION)) {
                Log.d(TAG, "Posting to page");

                myView.post(new Runnable() {
                    @Override
                    public void run() {
                        myView.loadUrl(thePage.getURL());
                    }
                });
                //setText(myView, returnString);
            } else if (!mDest.equals(NO_DESTINATION)) {
                Log.d(TAG, "Sending va DTN");
                Intent intent=new Intent(MyNetworkGETService.this,MyDtnIntentService.class);
                intent.setAction(MyDtnIntentService.ACTION_SEND_MESSAGE);
                intent.putExtra(MyDtnIntentService.EXTRA_DESTINATION, mDest);
                intent.putExtra(MyDtnIntentService.EXTRA_PAYLOAD, returnArray);
                startService(intent);

            }
        }
    }

    public void getPage(String url, WebView view, String destination, char type) {

        try {
            Parser thePage = new Parser("GET " + url + " HTTP/1.0");
            new DataThread(thePage, view, destination, type).start();
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
                        (type + url + "\r\nHTTP 500 Bad request\r\n\r\nBad Request: "
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
