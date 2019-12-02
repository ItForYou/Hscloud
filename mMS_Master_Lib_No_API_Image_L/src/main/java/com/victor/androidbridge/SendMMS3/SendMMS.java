package com.victor.androidbridge.SendMMS3;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore.Images.Media;
import android.util.Log;

import com.victor.androidbridge.SendMMS3.APNHelper.APN;
import com.victor.androidbridge.nokia.IMMConstants;
import com.victor.androidbridge.nokia.MMContent;
import com.victor.androidbridge.nokia.MMEncoder;
import com.victor.androidbridge.nokia.MMMessage;
import com.victor.androidbridge.nokia.MMResponse;
import com.victor.androidbridge.nokia.MMSender;

public class SendMMS {
    private static final String DEFAULT_HTTP_KEY_X_WAP_PROFILE = "x-wap-profile";
    private static final String DEFAULT_WAP_PROFILE	= "http://www.google.com/oha/rdf/ua-profile-kila.xml";
    private static final String HDR_KEY_ACCEPT = "Accept";
    private static final String HDR_KEY_ACCEPT_LANGUAGE = "Accept-Language";

    private static final String HDR_VALUE_ACCEPT =
        "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic";
    
    private static final int	MSG_ID_NEXT_MSG = 1;
    
	private static final String TAG = "SendMMSActivity";
	private ConnectivityManager mConnMgr;
	private PowerManager.WakeLock mWakeLock;
	private ConnectivityBroadcastReceiver mReceiver;
	private Context mContext	= null;
	private OperatorName operatorName = OperatorName.SK;
	private IMMMessage mQuee = null;
	private OnSendComplete	mCompleteListener = null;
			
	private NetworkInfo mNetworkInfo;
	private NetworkInfo mOtherNetworkInfo;
	
	public enum State {
		UNKNOWN,
		CONNECTED,
		NOT_CONNECTED
	}
	
	public enum OperatorName {
		SK,
		KT,
		LG
	}

	private State          mState;
	private boolean        mListening     = false;
	
	public SendMMS(Context context, OperatorName name, IMMMessage quee, OnSendComplete l) {
		mContext = context;
		mListening = true;
		mConnMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		mReceiver = new ConnectivityBroadcastReceiver();
		operatorName = name;
		mQuee = quee;
		mCompleteListener = l;
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		context.registerReceiver(mReceiver, filter);

		try {
			
			// Ask to start the connection to the APN. Pulled from Android source code.
			int result = beginMmsConnectivity();

			if (result != PhoneEx.APN_ALREADY_ACTIVE) {
				Log.v(TAG, "Extending MMS connectivity returned " + result + " instead of APN_ALREADY_ACTIVE");
				// Just wait for connectivity startup without
				// any new request of APN switch.
				return;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	protected void endMmsConnectivity() {
		// End the connectivity
		try {
			Log.v(TAG, "endMmsConnectivity");
			if (mConnMgr != null) {
				mConnMgr.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, PhoneEx.FEATURE_ENABLE_MMS);
			}
		} finally {
			releaseWakeLock();
		}
	}

	protected int beginMmsConnectivity() throws IOException {
		// Take a wake lock so we don't fall asleep before the message is downloaded.
		createWakeLock();

		int result = mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, PhoneEx.FEATURE_ENABLE_MMS);

		Log.v(TAG, "beginMmsConnectivity: result=" + result);

		switch (result) {
		case PhoneEx.APN_ALREADY_ACTIVE:
		case PhoneEx.APN_REQUEST_STARTED:
			acquireWakeLock();
			return result;
		}

		throw new IOException("Cannot establish MMS connectivity");
	}

	private synchronized void createWakeLock() {
		// Create a new wake lock if we haven't made one yet.
		if (mWakeLock == null) {
			PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MMS Connectivity");
			mWakeLock.setReferenceCounted(false);
		}
	}

	private void acquireWakeLock() {
		// It's okay to double-acquire this because we are not using it
		// in reference-counted mode.
		mWakeLock.acquire();
	}

	private void releaseWakeLock() {
		// Don't release the wake lock if it hasn't been created and acquired.
		if (mWakeLock != null && mWakeLock.isHeld()) {
			mWakeLock.release();
		}
	}
	
	private void sendMMSUsingNokiaAPI(MMMessage mm)
	{
	    MMEncoder encoder=new MMEncoder();
	    encoder.setMessage(mm);
	    
	    try {
	      encoder.encodeMessage();
	      byte[] out = encoder.getMessage();
	      
	      MMSender sender = new MMSender();
	      APNHelper apnHelper = new APNHelper(mContext, operatorName);
	      List<APN> results = apnHelper.getMMSApns();

	      if(results.size() > 0){

	    	  final String MMSCenterUrl = results.get(0).MMSCenterUrl;
	    	  final String MMSProxy = results.get(0).MMSProxy;
	    	  final int MMSPort = Integer.valueOf(results.get(0).MMSPort);
	    	  final Boolean  isProxySet =   (MMSProxy != null) && (MMSProxy.trim().length() != 0);			
	    	  
	    	  sender.setMMSCURL(MMSCenterUrl);
	    	  sender.addHeader("X-NOKIA-MMSC-Charging", "100");
	    	  sender.addHeader(HDR_KEY_ACCEPT, HDR_VALUE_ACCEPT);
	    	  sender.addHeader(DEFAULT_HTTP_KEY_X_WAP_PROFILE, DEFAULT_WAP_PROFILE);
		      MMResponse mmResponse = sender.send(out, isProxySet, MMSProxy, MMSPort);
		      Log.d(TAG, "Message sent to " + sender.getMMSCURL());
		      Log.d(TAG, "Response code: " + mmResponse.getResponseCode() + " " + mmResponse.getResponseMessage());

		      Enumeration keys = mmResponse.getHeadersList();
		      while (keys.hasMoreElements()){
		        String key = (String) keys.nextElement();
		        String value = (String) mmResponse.getHeaderValue(key);
		        Log.d(TAG, (key + ": " + value));
		      }
		      
		      if(mmResponse.getResponseCode() == 200)
		      {
		    	  // 200 Successful, disconnect and reset.
		    	  endMmsConnectivity();
		    	  mListening = false;
		      }
		      else
		      {
		    	  // kill dew :D hhaha
		      }
	      }	
	    } catch (Exception e) {
	      e.printStackTrace();
	    } finally {
	    }
	}
	
	private void SetMessage(MMMessage mm) {
	    mm.setVersion(IMMConstants.MMS_VERSION_10);
	    mm.setMessageType(IMMConstants.MESSAGE_TYPE_M_SEND_REQ);
	    mm.setTransactionId("0000000066");
	    mm.setDate(new Date(System.currentTimeMillis()));
	    mm.setFrom("+66820223268/TYPE=PLMN"); // doesnt work, i wish this worked as it should be
	    mm.addToAddress("+66820223268/TYPE=PLMN");
	    mm.setDeliveryReport(true);
	    mm.setReadReply(false);
	    mm.setSenderVisibility(IMMConstants.SENDER_VISIBILITY_SHOW);
	    mm.setSubject("This is a nice message!!");
	    mm.setMessageClass(IMMConstants.MESSAGE_CLASS_PERSONAL);
	    mm.setPriority(IMMConstants.PRIORITY_LOW);
	    mm.setContentType(IMMConstants.CT_APPLICATION_MULTIPART_MIXED);
	    
//	    In case of multipart related message and a smil presentation available
	    mm.setContentType(IMMConstants.CT_APPLICATION_MULTIPART_RELATED);
	    mm.setMultipartRelatedType(IMMConstants.CT_APPLICATION_SMIL);
	    mm.setPresentationId("<A0>"); // where <A0> is the id of the content containing the SMIL presentation
	    
	  }

	  private void AddContents(MMMessage mm) {
	    /*Path where contents are stored*/

		// You need to have this file in your SD. Otherwise error..
		File file = new File(Environment.getExternalStorageDirectory(), "19.png"); 
	    Uri outputFileUri = Uri.fromFile( file );
	    ByteArrayOutputStream os = new ByteArrayOutputStream();
	    Bitmap b;

    	try {
			b = Media.getBitmap(mContext.getContentResolver(), outputFileUri);
			b.compress(CompressFormat.PNG, 90, os);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	    // Adds text content
	    MMContent part1 = new MMContent();
	    byte[] buf1 = os.toByteArray();
	    part1.setContent(buf1, 0, buf1.length);
	    part1.setContentId("<0>");
	    part1.setType(IMMConstants.CT_IMAGE_PNG);
	    mm.addContent(part1);
	  
	  }

	private class ConnectivityBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION) || mListening == false) {
				Log.w(TAG, "onReceived() called with " + mState.toString() + " and " + intent);
				return;
			}

			boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

			if (noConnectivity) {
				mState = State.NOT_CONNECTED;
			} else {
				mState = State.CONNECTED;
			}

			mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			mOtherNetworkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);

//			mReason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
//			mIsFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);


			// Check availability of the mobile network.
			if ((mNetworkInfo == null) || (mNetworkInfo.getType() != ConnectivityManager.TYPE_MOBILE_MMS)) {
				Log.v(TAG, "   type is not TYPE_MOBILE_MMS, bail");
				return;
			}

			if (!mNetworkInfo.isConnected()) {
				Log.v(TAG, "TYPE_MOBILE_MMS not connected, bail");
				return;
			}
			else
			{ 
				Log.v(TAG, "Connected..");

				sendMsg();
			}
		}
	};
	
	private void sendMsg() {
		new sendAsyncTask().execute(mQuee.getMMMessage());
	}
	
	class sendAsyncTask extends AsyncTask<MMMessage, Void, Void> {

		@Override
		protected Void doInBackground(MMMessage... params) {
		    MMMessage mm = params[0];   
			sendMMSUsingNokiaAPI(mm);
			return null;
		}
		
	};
}