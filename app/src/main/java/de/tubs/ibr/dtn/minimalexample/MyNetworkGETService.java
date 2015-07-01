package de.tubs.ibr.dtn.minimalexample;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.webkit.WebView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;


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

        public DataThread(Parser url, WebView view, String dest, char type) {
            super("DataThread");
            myView = view;
            thePage = url;
            mDest = dest;
            mType = type;
        }

        public void run() {

            String returnString = mType + thePage.getURL() + "\r\n";
            ByteArrayOutputStream returnArrayStream = new ByteArrayOutputStream();
            byte[] returnArray;
            boolean gotFile;




            try {

                returnArrayStream.write(returnString.getBytes());
                Socket clientSocket = new Socket(thePage.getDomain(), thePage.getPort());
                DataInputStream clientIn = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());

                clientOut.writeBytes(thePage + "\r\n");


                try {

                    while (true) {
                        byte output = clientIn.readByte();
                        returnArrayStream.write(output);
                    }

                } catch (EOFException e) {
                    clientSocket.close();

                }
                returnArray = returnArrayStream.toByteArray();
                gotFile = true;
            } catch (UnknownHostException uhe) {
                gotFile = false;

                returnString += "HTTP 404 Not Found\r\n\r\nUnknown Host: " + uhe;
                returnArray = returnString.getBytes();

            } catch (IOException ioe) {
                gotFile = false;
                returnString += "HTTP 500 Error\r\n\r\nIOException: " + ioe;
                returnArray = returnString.getBytes();

            }
            if (!gotFile && !mDest.equals(NO_DESTINATION)) {
                try {

                    HTTPReply page = new HTTPReply(getApplicationContext(), thePage.getURL());

                    returnArrayStream.write(("HTTP/1.1 " + page.responseCode() + " " +
                            page.reasonPhrase() + "\r\n").getBytes());
                    for (Map.Entry<String, String> pair : page.getHeaders().entrySet()) {
                        returnArrayStream.write((pair.getKey() + ": " +
                                pair.getValue() + "\r\n").getBytes());
                    }
                    returnArrayStream.write("\r\n".getBytes());

                    returnArrayStream.write(page.getData());
                    returnArray = returnArrayStream.toByteArray();


                } catch (Exception e) {
                    Log.d(TAG, "No backup, sending error code: " + e);
                }
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
                Intent intent = new Intent(MyNetworkGETService.this, MyDtnIntentService.class);
                intent.setAction(MyDtnIntentService.ACTION_REPLY_MESSAGE);
                intent.putExtra(MyDtnIntentService.EXTRA_DESTINATION, mDest);
                intent.putExtra(MyDtnIntentService.EXTRA_PAYLOAD, returnArray);
                startService(intent);

                String replycode = returnArrayStream.toString().substring(returnArrayStream.toString().indexOf("\r\n") + 2);
                if (replycode.startsWith("HTTP/1.1 3")) {
                    HTTPReply nextRequest = new HTTPReply(getApplicationContext(), returnArray);
                    if (nextRequest.getHeaders().get("Location") != null) {
                        try {
                            new DataThread((new Parser("GET " + nextRequest.getHeaders().get("Location") + " HTTP/1.0")), myView, mDest, mType).start();
                        } catch (Parser.BadRequestException bre) {
                            Intent nextintent = new Intent(MyNetworkGETService.this, MyDtnIntentService.class);
                            nextintent.setAction(MyDtnIntentService.ACTION_REPLY_MESSAGE);
                            nextintent.putExtra(MyDtnIntentService.EXTRA_DESTINATION, mDest);
                            nextintent.putExtra(MyDtnIntentService.EXTRA_PAYLOAD, (mType +
                                    nextRequest.getHeaders().get("Location") +
                                    "\r\nHTTP 500 Bad request\r\n\r\nBad Request: " + bre).getBytes());
                            startService(nextintent);
                        }
                    }
                }

            }
        }
    }
    public static String getTime() {

        return new SimpleDateFormat("MM-dd hh:mm:ss.SSS").format(new Date());
    }

    public void getPage(String url, WebView view, String destination, char type) {

        if (!destination.equals(NO_DESTINATION)) {
            /*File log = new File(getExternalFilesDir("Logs"), "Log.txt");
            try {
                Log.d(TAG, "Write the request");
                FileWriter fw = new FileWriter(log, true);
                fw.write(getTime() + " - " + url + " request\n");
                fw.close();
            } catch (IOException ioe){
                Log.d(TAG,"Couldn't write to the log file...");
            }*/
        }

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
                intent.setAction(MyDtnIntentService.ACTION_REPLY_MESSAGE);
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
