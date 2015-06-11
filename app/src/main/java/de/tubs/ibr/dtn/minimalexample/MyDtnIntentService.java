package de.tubs.ibr.dtn.minimalexample;

import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import de.tubs.ibr.dtn.api.Block;
import de.tubs.ibr.dtn.api.Bundle;
import de.tubs.ibr.dtn.api.BundleID;
import de.tubs.ibr.dtn.api.DTNClient;
import de.tubs.ibr.dtn.api.DTNClient.Session;
import de.tubs.ibr.dtn.api.DTNIntentService;
import de.tubs.ibr.dtn.api.DataHandler;
import de.tubs.ibr.dtn.api.Registration;
import de.tubs.ibr.dtn.api.ServiceNotAvailableException;
import de.tubs.ibr.dtn.api.SessionDestroyedException;
import de.tubs.ibr.dtn.api.SimpleDataHandler;
import de.tubs.ibr.dtn.api.SingletonEndpoint;
import de.tubs.ibr.dtn.api.TransferMode;

public class MyDtnIntentService extends DTNIntentService {

    private static final String TAG = "MyDtnIntentService";
    private DTNClient.Session mSession = null;

    public static final String ACTION_SEND_MESSAGE = "de.tubs.ibr.dtn.minimalexample.SEND_MESSAGE";
    public static final String ACTION_RECV_MESSAGE = "de.tubs.ibr.dtn.minimalexample.RECV_MESSAGE";

    private static final String ACTION_MARK_DELIVERED = "de.tubs.ibr.dtn.minimalexample.DELIVERED";
    private static final String EXTRA_BUNDLEID = "de.tubs.ibr.dtn.minimalexample.BUNDLEID";

    public static final String EXTRA_SOURCE = "de.tubs.ibr.dtn.minimalexample.SOURCE";
    public static final String EXTRA_DESTINATION = "de.tubs.ibr.dtn.minimalexample.DESTINATION";
    public static final String EXTRA_PAYLOAD = "de.tubs.ibr.dtn.minimalexample.PAYLOAD";

    public MyDtnIntentService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Registration reg = new Registration("minimal-example");
        try {
            initialize(reg);
        } catch (ServiceNotAvailableException e) {
            Log.e(TAG, "Service not available", e);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();

        if (de.tubs.ibr.dtn.Intent.RECEIVE.equals(action)) {
            Log.d(TAG,"Generic receive");
            try {
                while (mSession.queryNext())Log.d(TAG,"Next Queried");
                Log.d(TAG,"All Done");
            } catch (SessionDestroyedException e) {
                Log.e(TAG, "session destroyed", e);
            }
        }
        else if (ACTION_SEND_MESSAGE.equals(action)) {
            try {
                Log.d(TAG,"Sending message to: "+intent.getStringExtra(EXTRA_DESTINATION));
                SingletonEndpoint destination = new SingletonEndpoint(intent.getStringExtra(EXTRA_DESTINATION));
                BundleID thisbundle = mSession.send(destination, 3600, intent.getByteArrayExtra(EXTRA_PAYLOAD));
/*
                Log.d(TAG, "Sending " + new String(intent.getByteArrayExtra(EXTRA_PAYLOAD)) +
                        " to " + intent.getStringExtra(EXTRA_DESTINATION) + " in bundle " +
                        thisbundle.toString());
*/
            } catch (SessionDestroyedException e) {
                Log.e(TAG, "session destroyed", e);
            }
        }
        else if (ACTION_MARK_DELIVERED.equals(action)) {
            try {
                BundleID id = intent.getParcelableExtra(EXTRA_BUNDLEID);
                if (id != null) mSession.delivered(id);
            } catch (SessionDestroyedException e) {
                Log.e(TAG, "session destroyed", e);
            }
        }
    }

    @Override
    protected void onSessionConnected(Session session) {
        mSession = session;
        mSession.setDataHandler(mDataHandler);
    }

    @Override
    protected void onSessionDisconnected() {
        mSession = null;
    }


    /**
     * This data handler is used to process incoming bundles
     */
    private DataHandler mDataHandler = new DataHandler() {

        private Bundle mBundle = null;

        @Override
        public void startBundle(Bundle bundle) {
            // store the bundle header locally
            mBundle = bundle;
        }

        @Override
        public void endBundle() {
            // complete bundle received
            BundleID received = new BundleID(mBundle);

            Log.d(TAG, "Message received from " + received.getSource());

            // mark the bundle as delivered
            Intent i = new Intent(MyDtnIntentService.this, MyDtnIntentService.class);
            i.setAction(ACTION_MARK_DELIVERED);
            i.putExtra(EXTRA_BUNDLEID, received);
            startService(i);

            // free the bundle header
            mBundle = null;

        }

        @Override
        public TransferMode startBlock(Block block) {
            // we are only interested in payload blocks (type = 1)
            if (block.type == 1) {
                // return SIMPLE mode to received the payload as "payload()" calls
                return TransferMode.SIMPLE;
            } else {
                // return NULL to discard the payload of this block
                return TransferMode.NULL;
            }
        }

        @Override
        public void endBlock() {
            // nothing to do here.
        }

        @Override
        public ParcelFileDescriptor fd() {
            // This method is used to hand-over a file descriptor to the
            // DTN service. We do not need the method here and always return
            // null.
            return null;
        }

        @Override
        public void payload(byte[] data) {
            // payload is received here
            Log.d(TAG, "payload received: " );

            // forward message to an activity
            Intent mi = new Intent(ACTION_RECV_MESSAGE);
            mi.putExtra(EXTRA_SOURCE, mBundle.getSource().toString());
            mi.putExtra(EXTRA_PAYLOAD, data);
            sendBroadcast(mi);


        }

        @Override
        public void progress(long offset, long length) {
            // if payload is written to a file descriptor, the progress
            // will be announced here
            Log.d(TAG, offset + " of " + length + " bytes received");
        }
    };
/*
    private DataHandler mDataHandler = new SimpleDataHandler() {
        @Override
        protected void onMessage(BundleID id, byte[] data) {
            Log.d(TAG, "Message received from " + id.getSource());

            // forward message to an activity
            Intent mi = new Intent(ACTION_RECV_MESSAGE);
            mi.putExtra(EXTRA_SOURCE, id.getSource().toString());
            mi.putExtra(EXTRA_PAYLOAD, data);
            sendBroadcast(mi);

            // mark the bundle as delivered
            Intent i = new Intent(MyDtnIntentService.this, MyDtnIntentService.class);
            i.setAction(ACTION_MARK_DELIVERED);
            i.putExtra(EXTRA_BUNDLEID, id);
            startService(i);
        }
    };
    */
}
