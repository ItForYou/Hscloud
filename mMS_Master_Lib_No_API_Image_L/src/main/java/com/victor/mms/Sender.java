package com.victor.mms;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.victor.android.send_message.Message;
import com.victor.android.send_message.Settings;
import com.victor.android.send_message.Transaction;
import com.victor.mms.APNHelper.APN;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore.Images;
import android.telephony.TelephonyManager;

public class Sender extends Utils {
    private Context           mContext;
    private Transaction       mTransaction = null;
    private Settings          mSettings;
    private TelephonyManager  mTM;
    
    private final String      KT           = "olleh";
    private final String      LG           = "LG U+";
    private final String      SK           = "SKTelecom";
    
    public enum State {
        UNKNOWN, CONNECTED, NOT_CONNECTED
    }
    
    public enum OperatorName {
        SK, KT, LG
    }
    
    public Sender(Context context) {
        this.mContext = context;
        this.mTM = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        this.mSettings = new Settings( );
        initDefaultMessageSetting();
    }
    
    public Settings getSettings() {
        return mSettings;
    }
    
    public void setSettings(Settings mSettings) {
        this.mSettings = mSettings;
    }
    
    private OperatorName getOprator() {
        String operatorName = mTM.getNetworkOperatorName( );
        String operatorcode = mTM.getNetworkOperator( );
        
        if (SK.equals(operatorName) || "45003".equals(operatorcode) || "45005".equals(operatorcode) || "45011".equals(operatorcode)) {
            return OperatorName.SK;
        }
        else if (KT.equals(operatorName) || "45002".equals(operatorcode) || "45004".equals(operatorcode) || "45008".equals(operatorcode)) {
            return OperatorName.KT;
        }
        else if (LG.equals(operatorName) || "45006".equals(operatorcode)) { 
            return OperatorName.LG; 
        }
        
        return null;
    }
    
    public void initDefaultMessageSetting() {
        try {
            if (mSettings == null) mSettings = new Settings( );
            
            OperatorName mOperator = getOprator( );
            if (mOperator == null) return;
            
            APNHelper apnHelper = new APNHelper(mContext, mOperator);
            List<APN> results = apnHelper.getMMSApns( );
            
            if (results.get(0).MMSCenterUrl != null) mSettings.setMmsc(results.get(0).MMSCenterUrl);
            if (results.get(0).MMSProxy != null) mSettings.setProxy(results.get(0).MMSProxy);
            if (Integer.valueOf(results.get(0).MMSPort) != 0) mSettings.setPort(results.get(0).MMSPort);
            
            mSettings.setGroup(true);
            // Check Delivery Status - If the operator doesn't support it, it is not operated.
            mSettings.setMmsDeliveryReport(false);
            // Check Read Status - If the operator doesn't support it, it is not operated.            
            mSettings.setMmsReadReport(false);
            mSettings.setWifiMmsFix(true);
            mSettings.setDeliveryReports(true);
            mSettings.setSplit(false);
            mSettings.setSplitCounter(false);
            mSettings.setStripUnicode(false);
            mSettings.setSignature("");
            mSettings.setSendLongAsMms(true);
            mSettings.setSendLongAsMmsAfter(3);
            mSettings.setRnrSe(null);
        }
        catch (Exception e) {
            e.printStackTrace( );
        }
    }
    
    // 1:1
    public void sendMMS_OneOne_String(int iMaxSize, String sPhone, String sURI, String sTitle, String sBody) {
        sendMMS_OneOne(msgType.ONEONE_STRING, iMaxSize, sPhone, sURI, null, sTitle, sBody, null);
    }
    
    public void sendMMS_OneOne_Uri(int iMaxSize, String sPhone, Uri uri, String sTitle, String sBody) {
        sendMMS_OneOne(msgType.ONEONE_URI, iMaxSize, sPhone, null, uri, sTitle, sBody, null);
    }
    
    public void sendMMS_OneOne_URL(int iMaxSize, String sPhone, String sURL, String sTitle, String sBody) {
        String[] url = new String[1];
        url[0] = sURL;
        Bitmap[] bitmap;
        try {
            DownloadFilesTask mDownloadFile = new DownloadFilesTask( );
            bitmap = mDownloadFile.execute(url).get( );
            sendMMS_OneOne(msgType.ONEONE_URL, iMaxSize, sPhone, null, null, sTitle, sBody, bitmap[0]);
        }
        catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }    
    }
    
    public void sendMMS_OneOne(msgType type, int iMaxSize, String sPhone, String sURI, Uri uri, String sTitle, String sBody, Bitmap bitmap) {
        if (sPhone == null || sPhone.trim( ).length( ) <= 0) return;
        
        String[] phone = new String[1];
        phone[0] = sPhone;
        
        sendMMS_OneSome(type, iMaxSize, phone, sURI, uri, sTitle, sBody, bitmap);
    }
    
    // 1:N
    public void sendMMS_OneSome_String(int iMaxSize, String[] sPhones, String sURI, String sTitle, String sBody) {
        sendMMS_OneSome(msgType.ONESOME_STRING, iMaxSize, sPhones, sURI, null, sTitle, sBody, null);
    }
    
    public void sendMMS_OneSome_Uri(int iMaxSize, String[] sPhones, Uri uri, String sTitle, String sBody) {
        sendMMS_OneSome(msgType.ONESOME_URI, iMaxSize, sPhones, null, uri, sTitle, sBody, null);
    }
    
    public void sendMMS_OneSome_URL(int iMaxSize, String[] sPhones, String sURL, String sTitle, String sBody) {
        String[] url = new String[1];
        url[0] = sURL;
        Bitmap[] bitmap;
        try {
            DownloadFilesTask mDownloadFile = new DownloadFilesTask( );
            bitmap = mDownloadFile.execute(url).get( );
            sendMMS_OneSome(msgType.ONESOME_URL, iMaxSize, sPhones, null, null, sTitle, sBody, bitmap[0]);
        }
        catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }    
    }
    
    @SuppressWarnings("deprecation")
    public void sendMMS_OneSome(msgType type, int iMaxSize, String[] sPhones, String sURI, Uri uri, String sTitle, String sBody, Bitmap bmp) {
        if (sPhones == null || sPhones.length <= 0) return;

        BufferedInputStream mInputStream = null;
        String uri_path = null;
        Bitmap bitmap = null;
        if (sURI != null) {
            uri_path = sURI;
            
            if (uri_path != null && uri_path.trim( ).length( ) > 0) {
                BitmapFactory.Options option = new BitmapFactory.Options( );
                option.inSampleSize = 1;
                option.inPurgeable = true;
                option.inDither = true;
                bitmap = BitmapFactory.decodeFile(uri_path, option);
                if (iMaxSize < 1) iMaxSize = 1;
                bitmap = resizeBitmapImageFn(bitmap, iMaxSize);
            }
        }
        
        Uri local_uri = null;
        if (uri != null) {
            local_uri = uri;
            Bitmap tmp_uri = null;
            try {
                String path = Utils.getPathFromUri(mContext, local_uri);
                tmp_uri = Images.Media.getBitmap(mContext.getContentResolver( ), local_uri);
                tmp_uri = resizeBitmapImageFn(tmp_uri, iMaxSize);
            }
            catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace( );
            }
            catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace( );
            }
            finally {
                bitmap = tmp_uri;
            }
        }
        
        if(bmp != null)
            bitmap = bmp;
        
        sendMessage(new messageBody(type, -1, iMaxSize, sPhones, uri_path, local_uri, sTitle, sBody, bitmap, mInputStream));
    }
    
    // N:1
    public void sendMMS_OneSome_String(int iTimeGap, int iMaxSize, String sPhone, String[] sURI, String[] sTitle, String[] sBody) {
        sendMMS_OneSome(msgType.SOMEONE_STRING, iTimeGap, iMaxSize, sPhone, sURI, null, sTitle, sBody, null);
    }
    
    public void sendMMS_OneSome_Uri(int iTimeGap, int iMaxSize, String sPhone, Uri[] uri, String[] sTitle, String[] sBody) {
        sendMMS_OneSome(msgType.SOMEONE_URI, iTimeGap, iMaxSize, sPhone, null, uri, sTitle, sBody, null);
    }
    
    public void sendMMS_OneSome_URL(int iTimeGap, int iMaxSize, String sPhone, String[] sURL, String[] sTitle, String[] sBody) {
        Bitmap[] bitmap;
        try {
            DownloadFilesTask mDownloadFile = new DownloadFilesTask( );
            bitmap = mDownloadFile.execute(sURL).get( );
            sendMMS_OneSome(msgType.SOMEONE_URL, iTimeGap, iMaxSize, sPhone, null, null, sTitle, sBody, bitmap);
        }
        catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }    
    }
    
    @SuppressWarnings("deprecation")
    public void sendMMS_OneSome(msgType type, int iTimeGap, int iMaxSize, String sPhone, String[] sURI, Uri[] uri, String[] sTitle, String[] sBody, Bitmap bmp[]) {
        if (sPhone == null || sPhone.trim( ).length( ) <= 0) return;
        
        BufferedInputStream mInputStream = null;
        
        String[] phone = new String[1];
        phone[0] = sPhone;
        
        for (int i = 0; i < sBody.length; i++) {
            String uri_path = null;
            Bitmap bitmap = null;
            if (sURI != null) {
                uri_path = sURI[i];
                
                if (uri_path != null && uri_path.trim( ).length( ) > 0) {
                    BitmapFactory.Options option = new BitmapFactory.Options( );
                    option.inSampleSize = 1;
                    option.inPurgeable = true;
                    option.inDither = true;
                    bitmap = BitmapFactory.decodeFile(uri_path, option);
                    if (iMaxSize < 1) iMaxSize = 1;
                    bitmap = resizeBitmapImageFn(bitmap, iMaxSize);
                }
            }
            
            Uri local_uri = null;
            if (uri != null) {
                local_uri = uri[i];
                Bitmap tmp_uri = null;
                try {
                    if (local_uri != null) {
                        tmp_uri = Images.Media.getBitmap(mContext.getContentResolver( ), local_uri);
                        tmp_uri = resizeBitmapImageFn(tmp_uri, iMaxSize);
                    }
                }
                catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace( );
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace( );
                }
                finally {
                    bitmap = tmp_uri;
                }
            }
            
            if(bmp != null && bmp[i] != null)
                bitmap = bmp[i];
            
            String title = sTitle[i];
            String body = sBody[i];
            
            sendMessage(new messageBody(type, iTimeGap, iMaxSize, phone, uri_path, local_uri, title, body, bitmap, mInputStream));
        }
    }
    
    // (1:1) * X
    public void sendMMS_SomeSome_String(int iTimeGap, int iMaxSize, String[] sPhone, String[] sURI, String[] sTitle, String[] sBody) {
        sendMMS_SomeSome(msgType.SOMESOME_STRING, iTimeGap, iMaxSize, sPhone, sURI, null, sTitle, sBody, null);
    }
    
    public void sendMMS_SomeSome_Uri(int iTimeGap, int iMaxSize, String[] sPhone, Uri[] uri, String[] sTitle, String[] sBody) {
        sendMMS_SomeSome(msgType.SOMESOME_URI, iTimeGap, iMaxSize, sPhone, null, uri, sTitle, sBody, null);
    }
    
    public void sendMMS_SomeSome_URL (int iTimeGap, int iMaxSize, String[] sPhone, String[] sURL, String[] sTitle, String[] sBody) {
        Bitmap[] bitmap;
        try {
            DownloadFilesTask mDownloadFile = new DownloadFilesTask( );
            bitmap = mDownloadFile.execute(sURL).get( );
            sendMMS_SomeSome(msgType.SOMESOME_URL, iTimeGap, iMaxSize, sPhone, null, null, sTitle, sBody, bitmap);
        }
        catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }    
    }
    
    @SuppressWarnings("deprecation")
    public void sendMMS_SomeSome(msgType type, int iTimeGap, int iMaxSize, String[] sPhone, String[] sURI, Uri[] uri, String[] sTitle, String[] sBody, Bitmap[] bmp) {
        if (sPhone == null) return;
        
        for (int i = 0; i < sBody.length; i++) {
            String[] phone = new String[1];
            phone[0] = sPhone[i];
            
            BufferedInputStream mInputStream = null;
            
            String uri_path = null;
            Bitmap bitmap = null;
            if (sURI != null) {
                uri_path = sURI[i];
                
                if (uri_path != null && uri_path.trim( ).length( ) > 0) {
                    BitmapFactory.Options option = new BitmapFactory.Options( );
                    option.inSampleSize = 1;
                    option.inPurgeable = true;
                    option.inDither = true;
                    bitmap = BitmapFactory.decodeFile(uri_path, option);
                    if (iMaxSize < 1) iMaxSize = 1;
                    bitmap = resizeBitmapImageFn(bitmap, iMaxSize);
                }
            }
            
            Uri local_uri = null;
            if (uri != null) {
                local_uri = uri[i];
                Bitmap tmp_uri = null;
                try {
                    if (local_uri != null) {
                        tmp_uri = Images.Media.getBitmap(mContext.getContentResolver( ), local_uri);
                        tmp_uri = resizeBitmapImageFn(tmp_uri, iMaxSize);
                    }
                }
                catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace( );
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace( );
                }
                finally {
                    bitmap = tmp_uri;
                }
            }
            
            if(bmp != null && bmp[i] != null)
                bitmap = bmp[i];
            
            String title = sTitle[i];
            String body = sBody[i];
            
            sendMessage(new messageBody(type, iTimeGap, iMaxSize, phone, uri_path, local_uri, title, body, bitmap, mInputStream));
        }
    }
    
    public void sendMessage(messageBody messageBody) {
        new SendAsyncTask(mContext, messageBody).execute(sendAsyncTask);
    }
    
    ISendAsyncTask sendAsyncTask = new ISendAsyncTask( ) {
         messageBody messageBody = null;
         
         @Override
         public void doAction(SendAsyncTask task) {
             messageBody = task.getMessageBody( );
             sendMessage( );
         }
         
         @Override
         public void onComplete() {}
         
         @Override
         public void onPostExecute(Void result) {}
         
         public void sendMessage() {
             Message localMessage = null;
             switch (messageBody.getType( )) {
                 case ONEONE_STRING:
                 case ONEONE_URI:
                 case ONEONE_URL:
                 case SOMEONE_STRING:
                 case SOMEONE_URI:
                 case SOMEONE_URL:
                 case SOMESOME_STRING:
                 case SOMESOME_URI:
                 case SOMESOME_URL:
                     mTransaction = new Transaction(mContext, mSettings);
                     
                     localMessage = new Message(messageBody.getBody( ), messageBody.getPhone( )[0]);
                     
                     if (messageBody.getBitmap( ) != null){ 
                         localMessage.setImage(messageBody.getBitmap( ));
                     }
                     
                     if (messageBody.getUri_path( ) != null && 
                         messageBody.getUri_path( ).trim( ).length( ) > 0) {
                     }
                     
                     if (messageBody.getTitle( ) != null && 
                         messageBody.getTitle( ).trim( ).length( ) > 0) {
                         localMessage.setSubject(messageBody.getTitle( ));
                     }
                     
                     localMessage.setSave(false);
                     localMessage.setDelay(0);
                     
                     mTransaction.sendNewMessage(localMessage, 0L);
                     
                     if (messageBody.getType( ) == msgType.SOMEONE_STRING || 
                         messageBody.getType( ) == msgType.SOMEONE_URI    || 
                         messageBody.getType( ) == msgType.SOMEONE_URL      ) {
                         try {
                             Thread.sleep(messageBody.getTimeGap( ) * 1000);
                         }
                         catch (InterruptedException e1) {
                             // TODO Auto-generated
                             // catch block
                             e1.printStackTrace( );
                         }
                     }
                     break;
                 
                 case ONESOME_STRING:
                 case ONESOME_URI:
                 case ONESOME_URL:
                     mTransaction = new Transaction(mContext, mSettings);
                     
                     localMessage = new Message(messageBody.getBody( ), messageBody.getPhone( ));
                     
                     if (messageBody.getBitmap( ) != null){ 
                         localMessage.setImage(messageBody.getBitmap( ));
                     }
                     
                     if (messageBody.getUri_path( ) != null && 
                         messageBody.getUri_path( ).trim( ).length( ) > 0) {
                     }
                     
                     if (messageBody.getTitle( ) != null && 
                         messageBody.getTitle( ).trim( ).length( ) > 0) {
                         localMessage.setSubject(messageBody.getTitle( ));
                     }
                     
                     localMessage.setSave(false);
                     localMessage.setDelay(0);
                     
                     mTransaction.sendNewMessage(localMessage, 0L);
                     break;
                 
                 default:
                     break;
             }
         }
     };
    
    private class DownloadFilesTask extends AsyncTask<String[], Void, Bitmap[]> {
        Bitmap[]    mStringImg;
        
        @Override
        protected void onPreExecute() {
            super.onPreExecute( );
        }
        
        @Override
        protected Bitmap[] doInBackground(String[]... params) {
            String[] urls   = (String[])    params[0];
            int count       = urls.length;
            mStringImg      = new Bitmap[urls.length];
            
            for (int i = 0; i < count; i++) {
                try {
                    if(urls[i] != null && urls[i].trim( ).length( ) > 0){
                        InputStream is = new java.net.URL(urls[i].toString( )).openStream( );
                        mStringImg[i] = BitmapFactory.decodeStream(is);
                    }
                    if (isCancelled( )) break;
                }
                catch (MalformedURLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace( );
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace( );
                }
            }
            return mStringImg;
        }
        
        @Override
        protected void onPostExecute(Bitmap[] result) {              
        }
        
        @Override
        protected void onCancelled() {
            super.onCancelled( );
        }
    }
}
