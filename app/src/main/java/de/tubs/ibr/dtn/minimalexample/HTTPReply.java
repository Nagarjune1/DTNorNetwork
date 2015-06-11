package de.tubs.ibr.dtn.minimalexample;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

/**
 * Created by Sibren on 6/10/2015.
 */
public class HTTPReply {
    private String mURL;
    private String mResponse;
    private byte[] mData;
    private Map<String, String> mHeaders;
    private boolean exists=false;
    private Context mContext;

    private void createReplyFromInputStream(InputStream input){
        int curr,prev;
        try {
            prev = input.read();
            while ((curr = input.read()) != -1) {
                if (curr != '\n' && prev != '\r') {
                    mResponse += (char) prev;
                } else {
                    break;
                }
                prev = curr;
            }
            prev = input.read();
            String header = "" ;
            String value = "" ;
            boolean inValue = false;
            while ((curr = input.read()) != -1) {
                if (!inValue) {
                    if (curr != ' ' && prev != ':') {
                        if (curr != '\n' && prev != '\r') {
                            header += (char) prev;
                        } else {
                            break;
                        }
                    } else {
                        inValue = true;
                        curr = input.read();
                    }
                } else {
                    if (curr != '\n' && prev != '\r') {
                        value += (char) prev;
                    } else {
                        inValue = false;
                        mHeaders.put(header, value);
                    }
                }
                prev = curr;
            }
            int count = 0;
            input.mark(Integer.MAX_VALUE);
            while (input.read() != -1) count++;

            mData = new byte[count];
            input.reset();
            input.read(mData);
            exists = true;
        } catch (IOException e) {

        }
    }

    private void setURLfromInputStream(InputStream input){
        int prev, curr;
        try {
            prev = input.read();
            while ((curr = input.read()) != -1) {
                if (curr != '\n' && prev != '\r') {
                    mURL += (char) prev;
                } else {
                    break;
                }
                prev = curr;
            }
        } catch (IOException e){

        }
    }

    public HTTPReply(Context context, byte[] replyWithURL){
        mContext=context;
        setURLfromInputStream(new ByteArrayInputStream(replyWithURL));
        createReplyFromInputStream(new ByteArrayInputStream(replyWithURL));
    }

    public HTTPReply(Context context, String url, byte[] fullReply){
        mURL=url;
        createReplyFromInputStream(new ByteArrayInputStream(fullReply));
        mContext=context;
    }

    public HTTPReply(Context context, String filename){
        int curr;
        int prev;
        mURL="";
        mResponse="";
        mContext=context;
        try {
            BufferedInputStream input = new BufferedInputStream(context.openFileInput(filename));
            setURLfromInputStream(input);
            createReplyFromInputStream(input);
        } catch (FileNotFoundException fnfe){

        }
    }

    public void saveToFile(){

        try{
            String filename = computeMD5Hash(mURL);
            BufferedOutputStream output = new BufferedOutputStream(mContext.openFileOutput(
                filename,
                Context.MODE_PRIVATE));

            output.write((mURL+"\r\n").getBytes());
            output.write((mResponse+"\r\n").getBytes());
            for (Map.Entry<String,String> entry : mHeaders.entrySet()){
                output.write((entry.getKey()+": "+entry.getValue()+"\r\n").getBytes());
            }
            output.write("\r\n".getBytes());
            output.write(mData);
            output.close();
        } catch (Exception e){

        }
    }


    public byte[] getData() {
        return mData;
    }

    public String getMimeType(){
        return mHeaders.get("MIME-type")
    }

    public String getURL(){
        return mURL;
    }

    public Map<String,String> getHeaders(){
        return mHeaders;
    }

    public String getFullFilename(){
        return new File(mContext.getFilesDir(),computeMD5Hash(mURL)).getAbsolutePath();
    }

    public static String computeMD5Hash(String password)
    {

        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(password.getBytes());
            byte messageDigest[] = digest.digest();

            StringBuffer MD5Hash = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++)
            {
                String h = Integer.toHexString(0xFF & messageDigest[i]);
                while (h.length() < 2)
                    h = "0" + h;
                MD5Hash.append(h);
            }

            return MD5Hash.toString();

        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }

        return null;

    }

}
