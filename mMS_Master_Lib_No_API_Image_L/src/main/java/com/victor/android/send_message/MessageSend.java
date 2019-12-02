package com.victor.android.send_message;

import java.util.List;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import com.victor.androidbridge.SendMMS3.IMMMessage;
import com.victor.androidbridge.SendMMS3.OnSendComplete;
import com.victor.androidbridge.SendMMS3.SendMMS;
import com.victor.androidbridge.SendMMS3.SendMMS.OperatorName;

public class MessageSend {
    private final String    KT                    = "olleh";
    private final String    SK                    = "SKTelecom";
            
    private Context         mContext              = null;
    private MessageVo       mMessage              = null;
    public int              mMaxCount             = 0;
    
    public MessageSend(Context context, MessageVo message) {
        this.mContext = context;
        this.mMessage = message;
    
        mMaxCount = mMessage.mRecipients.length;
    }
    
    public void sendMsg() {
        if( mMessage.isMMS() ) {
            sendMMS();
        } else {
            sendSMS();
        }
    }

    protected void sendMMS() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                IMMMessage quee = new MessageQuee(mContext, mMessage, mMaxCount);

                new SendMMS(mContext, getOprator(), quee, new OnSendComplete() {
                    public void onSendComplete(boolean bResult) {
                        if(bResult == true){
                        	
                        }
                    }
                });
            }
        });
        thread.start();
    }    

    protected void sendSMS() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                String SENT = "SMS_SENT";
                String DELIVERED = "SMS_DELIVERED";

                SmsManager sms = SmsManager.getDefault();
                
                PendingIntent sentPI = PendingIntent.getBroadcast(mContext, 0, new Intent(SENT), 0);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(mContext, 0, new Intent(DELIVERED), 0);
                
                List<String> numberList = mMessage.getRecipientsList();
                
                if( numberList==null || numberList.size()<=0 ) { 
                    return;
                }
                
                for( String number:numberList) {
                    number = number.replaceAll("-", "");
                    sms.sendTextMessage(number, null, mMessage.getMessage(), sentPI, deliveredPI);
                }
            }
        });
        thread.start();
    }
    
    private OperatorName getOprator() {
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        String operatorName = tm.getNetworkOperatorName();
        if( operatorName.equals(SK) ) {
            return OperatorName.SK;
        } else if( operatorName.equals(KT) ) {
            return OperatorName.KT;
        } else { // LG U++
            return OperatorName.LG;
        }
    }    
}
