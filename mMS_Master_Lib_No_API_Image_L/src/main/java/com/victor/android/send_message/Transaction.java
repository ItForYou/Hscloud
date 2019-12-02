/*
 * Copyright 2013 Victor Ju
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.victor.android.send_message;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.android.mms.dom.smil.parser.SmilXmlSerializer;
import com.android.mms.service_alt.MmsNetworkManager;
import com.android.mms.service_alt.MmsRequestManager;
import com.android.mms.service_alt.SendRequest;
import com.android.mms.transaction.HttpUtils;
import com.android.mms.transaction.MmsMessageSender;
import com.android.mms.transaction.ProgressCallbackEntity;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.RateController;
import com.google.android.mms.APN;
import com.google.android.mms.APNHelper;
import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.MMSPart;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu_alt.CharacterSets;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.PduBody;
import com.google.android.mms.pdu_alt.PduComposer;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduPart;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.SendReq;
import com.google.android.mms.smil.SmilHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;
import com.victor.android.send_message.Message.Part;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

/**
 * Class to process transaction requests for sending
 *
 * @author Victor Ju
 */
public class Transaction {
    
    private static final String TAG                = "Victor-Transaction";
    public static Settings      mSettings;
    private Context             mContext;
    private ConnectivityManager mConnMgr;
    
    private boolean             saveMessage        = false;
    
    public String               SMS_SENT           = ".SMS_SENT";
    public String               SMS_DELIVERED      = ".SMS_DELIVERED";
    
    public static String        NOTIFY_SMS_FAILURE = ".NOTIFY_SMS_FAILURE";
    public static final String  MMS_ERROR          = "com.victor.android.send_message.MMS_ERROR";
    public static final String  MMS_SUCCESS        = "com.victor.android.send_message.MMS_SUCCESS";
    public static final String  REFRESH            = "com.victor.android.send_message.REFRESH";
    public static final String  MMS_PROGRESS       = "com.victor.android.send_message.MMS_PROGRESS";
    public static final String  VOICE_FAILED       = "com.victor.android.send_message.VOICE_FAILED";
    public static final String  VOICE_TOKEN        = "com.victor.android.send_message.RNRSE";
    public static final String  NOTIFY_OF_DELIVERY = "com.victor.android.send_message.NOTIFY_DELIVERY";
    public static final String  NOTIFY_OF_MMS      = "com.victor.android.messaging.NEW_MMS_DOWNLOADED";
    
    public static final long    NO_THREAD_ID       = 0;
    
    private boolean             previous_wifi      = false;
    
    private String				sNumber				= null;
    private String				sIdx				= null;
    private String				sWay				= null;
    
    /**
     * Sets mContext and initializes mSettings to default values
     *
     * @param mContext
     *            is the mContext of the activity or service
     */
    public Transaction(Context context) {
        this(context, new Settings( ));
    }
    
    /**
     * Sets mContext and mSettings
     *
     * @param mContext
     *            is the mContext of the activity or service
     * @param mSettings
     *            is the mSettings object to process send requests through
     */
    public Transaction(Context context, Settings settings) {
        mSettings = settings;
        mContext = context.getApplicationContext( );
        
        SMS_SENT = context.getPackageName( ) + SMS_SENT;
        SMS_DELIVERED = context.getPackageName( ) + SMS_DELIVERED;
        
        if (NOTIFY_SMS_FAILURE.equals(".NOTIFY_SMS_FAILURE")) {
            NOTIFY_SMS_FAILURE = context.getPackageName( ) + NOTIFY_SMS_FAILURE;
        }
    }
    
    /**
     * Called to send a new message depending on mSettings and provided Message
     * object If you want to send message as mms, call this from the UI thread
     *
     * @param message
     *            is the message that you want to send
     * @param threadId
     *            is the thread id of who to send the message to (can also be
     *            set to Transaction.NO_THREAD_ID)
     */
    public void sendNewMessage(Message message, long threadId) {
        this.saveMessage = message.getSave( );
        
        // if message:
        // 1) Has images attached
        // or
        // 1) is enabled to send long messages as mms
        // 2) number of pages for that sms exceeds value stored in mSettings for
        // when to send the mms by
        // 3) prefer voice is disabled
        // or
        // 1) more than one address is attached
        // 2) group messaging is enabled
        //
        // then, send as MMS, else send as Voice or SMS
        try {
        	sNumber = message.getAddresses()[0];
        	sIdx = message.getIdx();
        	sWay = message.getWay();
		} catch (Exception e) {
			// TODO: handle exception
		}
       
        if (checkMMS(message)) {
            Log.v(TAG, "sending MMS : SDK Version " + Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT > 10 /* GINGERBREAD 2.3.3-2.3.4 */) {
                Log.d(TAG, "Transaction.sendNewMessage() - SDK VERSION > 10");
                try {
                    Looper.prepare( );
                }
                catch (Exception e) {}
                RateController.init(mContext);
                DownloadManager.init(mContext);
                
                sendMmsMessage(message.getText( ), message.getAddresses( ), message.getImages( ), message.getImageNames( ), message.getParts( ), message.getSubject( ));
                Log.v(TAG, "Text : " + message.getText( ));
                for (String phone : message.getAddresses( )) {
                    Log.v(TAG, "Text : " + phone);
                }
            }
            else {
                Log.d(TAG, "Transaction.sendNewMessage() - SDK VERSION <= 10");
                ArrayList<Uri> contentsList = new ArrayList<Uri>( );
                contentsList.clear( );
                
                MessageVo msg = new MessageVo(message.getAddresses( ), message.getSubject( ), message.getText( ), contentsList);
                MessageSend mMessageSend = new MessageSend(mContext, msg);
                mMessageSend.sendMsg( );
                
                contentsList.clear( );
            }
        }
        else {
            if (message.getType( ) == Message.TYPE_VOICE) {
                sendVoiceMessage(message.getText( ), message.getAddresses( ), threadId);
            }
            else if (message.getType( ) == Message.TYPE_SMSMMS) {
                Log.v(TAG, "sending sms");
                sendSmsMessage(message.getText( ), message.getAddresses( ), threadId, message.getDelay( ));
            }
            else {
                Log.v(TAG, "error with message type, aborting...");
            }
        }
        
    }
    
    String[] nonSaveAddress;
    String   nonSaveText;
    
    private void sendSmsMessage(String text, String[] addresses, long threadId, int delay) {
        Log.v(TAG, "message text: " + text);
        Uri messageUri = null;
        int messageId = 0;
        if (saveMessage) {
            Log.v(TAG, "saving message");
            // add signature to original text to be saved in database (does not
            // strip unicode for saving though)
            if (!mSettings.getSignature( ).equals("")) {
                text += "\n" + mSettings.getSignature( );
            }
            
            // save the message for each of the addresses
            for (int i = 0; i < addresses.length; i++) {
                Calendar cal = Calendar.getInstance( );
                ContentValues values = new ContentValues( );
                values.put("address", addresses[i]);
                values.put("body", mSettings.getStripUnicode( ) ? StripAccents.stripAccents(text) : text);
                values.put("date", cal.getTimeInMillis( ) + "");
                values.put("read", 1);
                values.put("type", 4);
                
                // attempt to create correct thread id if one is not supplied
                if (threadId == NO_THREAD_ID || addresses.length > 1) {
                    threadId = Utils.getOrCreateThreadId(mContext, addresses[i]);
                }

                Log.v(TAG, "sendSmsMessage - Saving message with thread id: " + threadId);

                values.put("thread_id", threadId);
                messageUri = mContext.getContentResolver().insert(Uri.parse("content://sms/"), values);

                Log.v(TAG, "sendSmsMessage - Inserted to uri_path: " + messageUri);

                Cursor query = mContext.getContentResolver().query(messageUri, new String[] { "_id" }, null, null, null);
                if (query != null && query.moveToFirst()) {
                    messageId = query.getInt(0);
                }

                Log.v(TAG, "sendSmsMessage - Message id: " + messageId);

                // set up sent and delivered pending intents to be used with
                // message request
                PendingIntent sentPI = PendingIntent.getBroadcast(mContext, messageId, new Intent(SMS_SENT).putExtra("message_uri", messageUri == null ? "" : messageUri.toString( )), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(mContext, messageId, new Intent(SMS_DELIVERED).putExtra("message_uri", messageUri == null ? "" : messageUri.toString( )), PendingIntent.FLAG_UPDATE_CURRENT);
                
                ArrayList<PendingIntent> sPI = new ArrayList<PendingIntent>( );
                ArrayList<PendingIntent> dPI = new ArrayList<PendingIntent>( );
                
                String body = text;
                
                // edit the body of the text if unicode needs to be stripped
                if (mSettings.getStripUnicode( )) {
                    body = StripAccents.stripAccents(body);
                }
                
                if (!mSettings.getPreText( ).equals("")) {
                    body = mSettings.getPreText( ) + " " + body;
                }

                SmsManager smsManager = SmsManager.getDefault();
                Log.v(TAG, "sendSmsMessage - Found sms manager");

                if (mSettings.getSplit()) {
                    Log.v(TAG, "sendSmsMessage - Splitting message");
                    // figure out the length of supported message
                    int[] splitData = SmsMessage.calculateLength(body, false);

                    // we take the current length + the remaining length to get
                    // the total number of characters
                    // that message set can support, and then divide by the
                    // number of message that will require
                    // to get the length supported by a single message
                    int length = (body.length() + splitData[2]) / splitData[0];
                    Log.v(TAG, "sendSmsMessage - Length: " + length);

                    boolean counter = false;
                    if (mSettings.getSplitCounter() && body.length() > length) {
                        counter = true;
                        length -= 6;
                    }

                    // get the split messages
                    String[] textToSend = splitByLength(body, length, counter);

                    // send each message part to each recipient attached to
                    // message
                    for (int j = 0; j < textToSend.length; j++) {
                        ArrayList<String> parts = smsManager.divideMessage(textToSend[j]);

                        for (int k = 0; k < parts.size(); k++) {
                            sPI.add(saveMessage ? sentPI : null);
                            dPI.add(mSettings.getDeliveryReports() && saveMessage ? deliveredPI : null);
                        }

                        Log.v(TAG, "sendSmsMessage - Sending split message");
                        sendDelayedSms(smsManager, addresses[i], parts, sPI, dPI, delay, messageUri);
                    }
                }
                else {
                    Log.v(TAG, "sendSmsMessage - Sending without splitting");
                    // send the message normally without forcing anything to be
                    // split
                    ArrayList<String> parts = smsManager.divideMessage(body);

                    for (int j = 0; j < parts.size(); j++) {
                        sPI.add(saveMessage ? sentPI : null);
                        dPI.add(mSettings.getDeliveryReports() && saveMessage ? deliveredPI : null);
                    }

                    try {
                        Log.v(TAG, "sendSmsMessage - Sent message");
                        sendDelayedSms(smsManager, addresses[i], parts, sPI, dPI, delay, messageUri);
                    }
                    catch (Exception e) {
                        // whoops...
                        Log.v(TAG, "sendSmsMessage - Error sending message");
                        e.printStackTrace();

                        try {
                            ((Activity) mContext).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, "Message could not be sent", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                        catch (Exception f) {
                            f.printStackTrace();
                        }
                    }
                }
            }
        }
        else {
            nonSaveAddress = addresses;
            nonSaveText = text;
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    String SENT      = "SMS_SENT";
                    String DELIVERED = "SMS_DELIVERED";

                    SmsManager    sms           = SmsManager.getDefault();
                    PendingIntent sentPI        = PendingIntent.getBroadcast(mContext, 0, new Intent(SENT), 0);
                    PendingIntent deliveredPI   = PendingIntent.getBroadcast(mContext, 0, new Intent(DELIVERED), 0);
                    List<String>  numberList    = Arrays.asList(nonSaveAddress);

                    if (numberList == null || numberList.size() <= 0) {
                        return;
                    }

                    for (String number : numberList) {
                        number = number.replaceAll("-", "");
                        sms.sendTextMessage(number, null, nonSaveText, sentPI, deliveredPI);
                    }
                }
            });
            thread.start();
        }

    }

    private void sendDelayedSms(final SmsManager smsManager, final String address, final ArrayList<String> parts, final ArrayList<PendingIntent> sPI, final ArrayList<PendingIntent> dPI, final int delay, final Uri messageUri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                }
                catch (Exception e) {}

                TelephonyManager telManager = (TelephonyManager) mContext.getSystemService(mContext.TELEPHONY_SERVICE);
                String           phoneNum   = telManager.getLine1Number();
                
                // if (checkIfMessageExistsAfterDelay(messageUri)) {
                Log.v(TAG, "sendDelayedSms - Message sent after delay");
                try {
                    smsManager.sendMultipartTextMessage(address, phoneNum, parts, sPI, dPI);
                }
                catch (Exception e) {
                    Log.e(TAG, "sendDelayedSms - Exception thrown", e);
                }
            }
        }).start();
    }

    // BufferedInputStream to Byte Array
    private static final int BUF_SIZE = 1024000;

    public long copy(BufferedInputStream from, BufferedOutputStream to) throws IOException {
        byte[] buf = new byte[BUF_SIZE];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    public byte[] toByteArray(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        BufferedOutputStream out = new BufferedOutputStream(bytesOut);
        copy(in, out);
        return bytesOut.toByteArray();
    }

    private void sendMmsMessage(String text, String[] addresses, Bitmap[] image, String[] imageNames, List<Part> media, String subject) {
        // Merge the string[] of addresses into a single string so they can be inserted into the database easier
        String address = "";

        for (int i = 0; i < addresses.length; i++) {
            if (addresses[i] != null)
                address += addresses[i] + " ";
        }

        address = address.trim();

        // create the parts to send
        ArrayList<MMSPart> data = new ArrayList<MMSPart>();

        for (int i = 0; i < image.length; i++) {
            // turn bitmap into byte array to be stored
            byte[] imageBytes = Message.bitmapToByteArray(image[i]);
            
            MMSPart part = new MMSPart( );
            part.MimeType = "image/jpeg";
            part.Name = (imageNames != null) ? imageNames[i] : ("image" + i);
            part.Data = imageBytes;
            data.add(part);
        }
        
        if (!text.equals("")) {
            // add text to the end of the part and send
            MMSPart part = new MMSPart( );
            part.Name = "text.txt";
            part.MimeType = "text/plain";
            part.Data = text.getBytes( );
            data.add(part);
        }
        
        if ((Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) || !Utils.isDefaultSmsApp(mContext)) {
            Log.v(TAG, "sendMmsMessage - (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) && !Utils.isDefaultSmsApp(mContext)");

            MessageInfo info;
            
            try {
                info = getBytes(mContext, saveMessage, address.split(" "), data.toArray(new MMSPart[data.size( )]), subject);
            }
            catch (MmsException e) {
                Toast.makeText(mContext, e.getMessage( ), Toast.LENGTH_SHORT).show( );
                return;
            }
            
            try {
                MmsMessageSender sender = new MmsMessageSender(mContext, info.location, info.bytes.length);
                sender.sendMessage(info.token);
                
                IntentFilter filter = new IntentFilter( );
                filter.addAction(ProgressCallbackEntity.PROGRESS_STATUS_ACTION);
                BroadcastReceiver receiver = new BroadcastReceiver( ) {
                    
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int progress = intent.getIntExtra("progress", -3);
                        Log.v(TAG, "sendMmsMessage - MMS Sending Progress = " + progress);
    
                        // send progress broadcast to update ui if desired...
                        Intent progressIntent = new Intent(MMS_PROGRESS);
                        progressIntent.putExtra("progress", progress);
                        context.sendBroadcast(progressIntent);
    
                        if (progress == ProgressCallbackEntity.PROGRESS_COMPLETE) {
                            context.sendBroadcast(new Intent(REFRESH));
    
                            try {
                                context.unregisterReceiver(this);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                                // TODO fix me
                                // receiver is not registered force close error...
                                // hmm.
                            }
                        }
                        else if (progress == ProgressCallbackEntity.PROGRESS_ABORT) {
                            // This seems to get called only after the progress has
                            // reached 100 and then something else goes wrong,
                            // so here we will try and send again and see if it
                            // works
                            Log.v(TAG, "sendMmsMessage - Sending aborted for some reason...");
                        }
                    }
    
                };
    
                mContext.registerReceiver(receiver, filter);
            }
            catch (Throwable e) {
                Log.e(TAG, "sendMmsMessage - Exception thrown", e);
                // insert the pdu into the database and return the bytes to send
                if (mSettings.getWifiMmsFix()) {
                    sendMMS(info.bytes);
                }
                else {
                    sendMMSWiFi(info.bytes);
                }
            }
        } else {
            Log.v(TAG, "Using upper lollipop method for sending Messaging");

            if (mSettings.getUseSystemSending()) {
                Log.v(TAG, "Using system method for sending");
                sendMmsThroughSystem(mContext, subject, data, addresses);
            } else {
                try {
                    MessageInfo info = getBytes(mContext, saveMessage, address.split(" "), data.toArray(new MMSPart[data.size()]), subject);
                    MmsRequestManager requestManager = new MmsRequestManager(mContext, info.bytes);
                    SendRequest request = new SendRequest(requestManager, Utils.getDefaultSubscriptionId(), info.location, null, null, null, null);
                    MmsNetworkManager manager = new MmsNetworkManager(mContext, Utils.getDefaultSubscriptionId());
                    request.execute(mContext, manager);
                } catch (Exception e) {
                    Log.e(TAG, "error sending mms", e);
                }
            }
        }            
    }

    public static MessageInfo getBytes(Context context, boolean saveMessage, String[] recipients, MMSPart[] parts, String subject) throws MmsException {
        final SendReq sendRequest = new SendReq();

        // create send request addresses
        for (int i = 0; i < recipients.length; i++) {
            final EncodedStringValue[] phoneNumbers = EncodedStringValue.extract(recipients[i]);

            if (phoneNumbers != null && phoneNumbers.length > 0) {
                sendRequest.addTo(phoneNumbers[0]);
            }
        }

        if (subject != null) {
            sendRequest.setSubject(new EncodedStringValue(subject));
        }

        sendRequest.setDate(Calendar.getInstance().getTimeInMillis() / 1000L);

        try {
            sendRequest.setFrom(new EncodedStringValue(Utils.getMyPhoneNumber(context)));
        }
        catch (Exception e) {
            // my number is nothing
            Log.e(TAG, "getBytes - Exception thrown", e);
        }

        final PduBody pduBody = new PduBody();

        // assign parts to the pdu body which contains sending data
        long size = 0;
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                MMSPart part = parts[i];
                if (part != null) {
                    try {
                        PduPart partPdu = new PduPart( );
                        partPdu.setName(part.Name.getBytes( ));
                        partPdu.setContentType(part.MimeType.getBytes( ));
                        
                        if (part.MimeType.startsWith("text")) {
                            partPdu.setCharset(CharacterSets.UTF_8);
                            partPdu.setContentId("text".getBytes( ));
                            partPdu.setContentLocation("text.txt".getBytes( ));
                        }
                        
                        if (part.MimeType.startsWith("image")) {
                            String mTemp = "image" + i;
                            partPdu.setContentId(mTemp.getBytes( ));
                            partPdu.setContentLocation(mTemp.getBytes( ));
                        }
                        
                        if (part.MimeType.startsWith("video")) {
                            String mTemp = "video" + i;
                            partPdu.setContentId(mTemp.getBytes( ));
                            partPdu.setContentLocation(mTemp.getBytes( ));
                        }
                        
                        partPdu.setData(part.Data);
                        
                        pduBody.addPart(partPdu);
                        size += (part.Name.getBytes().length + part.MimeType.getBytes().length + part.Data.length);
                    }
                    catch (Exception e) {
                        Log.e(TAG, "getBytes - Exception thrown", e);
                    }
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(pduBody), out);
        PduPart smilPart = new PduPart();
        smilPart.setContentId       ("smil".getBytes());
        smilPart.setContentLocation ("smil.xml".getBytes());
        smilPart.setContentType     (ContentType.APP_SMIL.getBytes());
        smilPart.setData            (out.toByteArray());
        
        pduBody.addPart(0, smilPart);

        Log.v(TAG, "getBytes - setting message size to " + size + " bytes");
        
        sendRequest.setBody             (pduBody);
        sendRequest.setMessageSize      (size);
        sendRequest.setPriority         (PduHeaders.PRIORITY_HIGH);
        sendRequest.setDeliveryReport   (mSettings.getMmsDeliveryReport() ? PduHeaders.VALUE_YES : PduHeaders.VALUE_NO);
        sendRequest.setReadReport       (mSettings.getMmsReadReport()     ? PduHeaders.VALUE_YES : PduHeaders.VALUE_NO);
        sendRequest.setExpiry           (1000 * 60 * 60 * 24 * 7);
        sendRequest.setMessageClass     (PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());

        // create byte array which will actually be sent
        final PduComposer composer = new PduComposer(context, sendRequest);
        final byte[] bytesToSend;

        try {
            bytesToSend = composer.make();
        }
        catch (OutOfMemoryError e) {
            throw new MmsException("getBytes - Out of memory!");
        }

        MessageInfo info = new MessageInfo();
        info.bytes = bytesToSend;

        if (saveMessage) {
            try {
                PduPersister persister = PduPersister.getPduPersister(context);
                info.location = persister.persist(sendRequest, Uri.parse("content://mms/outbox"), true, mSettings.getGroup(), null);
            }
            catch (Exception e) {
                Log.v(TAG, "getBytes - error saving mms message");
                Log.e(TAG, "getBytes - exception thrown", e);

                // use the old way if something goes wrong with the persister
                insert(context, recipients, parts, subject);
            }
        }

        try {
            if (Utils.isDefaultSmsApp(context))
            {
                Cursor query = context.getContentResolver().query(info.location, new String[] { "thread_id" }, null, null, null);
                if (query != null && query.moveToFirst()) {
                    info.token = query.getLong(query.getColumnIndex("thread_id"));
                }
                else {
                    // just default sending token for what I had before
                    info.token = 4444L;
                }
            }
            else {
                Log.e(TAG, "Not default APP - Just default sending token for what I had before!");
                info.token = 4444L;
            }
        }
        catch (Exception e) {
            Log.e(TAG, "getBytes - Exception thrown", e);
            info.token = 4444L;
        }

        return info;
    }

    public static final long DEFAULT_EXPIRY_TIME = 7 * 24 * 60 * 60;
    public static final int DEFAULT_PRIORITY = PduHeaders.PRIORITY_NORMAL;

    @SuppressLint("NewApi")
    private static void sendMmsThroughSystem(Context context, String subject, List<MMSPart> parts, String[] addresses) {
        try {
            final String fileName = "send." + String.valueOf(Math.abs(new Random().nextLong())) + ".dat";
            File mSendFile = new File(context.getCacheDir(), fileName);

            SendReq sendReq = buildPdu(context, addresses, subject, parts);
            PduPersister persister = PduPersister.getPduPersister(context);
            Uri messageUri = persister.persist(sendReq, Uri.parse("content://mms/outbox"), true, mSettings.getGroup(), null);

            Intent intent = new Intent(MmsSentReceiver.MMS_SENT);
            intent.putExtra(MmsSentReceiver.EXTRA_CONTENT_URI, messageUri.toString());
            intent.putExtra(MmsSentReceiver.EXTRA_FILE_PATH, mSendFile.getPath());
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

            Uri writerUri = (new Uri.Builder())
                    .authority(context.getPackageName() + ".MmsFileProvider")
                    .path(fileName)
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .build();
            FileOutputStream writer = null;
            Uri contentUri = null;
            try {
                writer = new FileOutputStream(mSendFile);
                writer.write(new PduComposer(context, sendReq).make());
                contentUri = writerUri;
            } catch (final IOException e) {
                Log.e(TAG, "Error writing send file", e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                    }
                }
            }

            Bundle configOverrides = new Bundle();
            configOverrides.putBoolean(SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED, mSettings.getGroup());

            if (contentUri != null) {
                SmsManager.getDefault().sendMultimediaMessage(context,
                        contentUri, null, configOverrides, pendingIntent);
            } else {
                Log.e(TAG, "Error writing sending Mms");
                try {
                    pendingIntent.send(SmsManager.MMS_ERROR_IO_ERROR);
                } catch (PendingIntent.CanceledException ex) {
                    Log.e(TAG, "Mms pending intent cancelled?", ex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "error using system sending method", e);
        }
    }    
    
    private static SendReq buildPdu(Context context, String[] recipients, String subject, List<MMSPart> parts) {
        final SendReq req = new SendReq();
        // From, per spec
        final String lineNumber = Utils.getMyPhoneNumber(context);
        
        if (!TextUtils.isEmpty(lineNumber)) {
            req.setFrom(new EncodedStringValue(lineNumber));
        }
        
        // To
        for (String recipient : recipients) {
            req.addTo(new EncodedStringValue(recipient));
        }
        
        // Subject
        if (!TextUtils.isEmpty(subject)) {
            req.setSubject(new EncodedStringValue(subject));
        }
        
        // Date
        req.setDate(System.currentTimeMillis() / 1000);
        
        // Body
        PduBody body = new PduBody();
        
        // Add text part. Always add a smil part for compatibility, without it there
        // may be issues on some carriers/client apps
        int size = 0;
        for (int i = 0; i < parts.size(); i++) {
            MMSPart part = parts.get(i);
            size += addTextPart(body, part, i);
        }
        
        // add a SMIL document for compatibility
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(body), out);
        PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(out.toByteArray());
        body.addPart(0, smilPart);
        
        req.setBody(body);
        
        // Message size
        req.setMessageSize(size);
        
        // Message class
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
        
        // Expiry
        req.setExpiry(DEFAULT_EXPIRY_TIME);
        try {
            // Priority
            req.setPriority(DEFAULT_PRIORITY);
            // Delivery report
            req.setDeliveryReport(PduHeaders.VALUE_NO);
            // Read report
            req.setReadReport(PduHeaders.VALUE_NO);
        } catch (InvalidHeaderValueException e) {
            e.printStackTrace();
        }
        
        return req;
    }

    private static int addTextPart(PduBody pb, MMSPart p, int id) {
        String filename = p.MimeType.split("/")[0] + "_" + id + ".mms";
        final PduPart part = new PduPart();
        // Set Charset if it's a text media.
        if (p.MimeType.startsWith("text")) {
            part.setCharset(CharacterSets.UTF_8);
        }
        // Set Content-Type.
        part.setContentType(p.MimeType.getBytes());
        // Set Content-Location.
        part.setContentLocation(filename.getBytes());
        int index = filename.lastIndexOf(".");
        String contentId = (index == -1) ? filename
                : filename.substring(0, index);
        part.setContentId(contentId.getBytes());
        part.setData(p.Data);
        pb.addPart(part);

        return part.getData().length;
    }

    
    public static class MessageInfo {
        public long   token;
        public Uri    location;
        public byte[] bytes;
    }

    private void sendVoiceMessage(String text, String[] addresses, long threadId) {
        // send a voice message to each recipient based off of koush's voice
        // implementation in Voice+
        for (int i = 0; i < addresses.length; i++) {
            if (saveMessage) {
                ContentValues values = new ContentValues();
                values.put("address",   addresses[i]);
                values.put("body",      text);
                values.put("date",      Calendar.getInstance().getTimeInMillis() + "");
                values.put("read",      1);
                values.put("status",    2);  // if you want to be able to tell the difference between sms and voice, look for this value. 
                                             // SMS will be -1, 0, 64, 128 and voice will be 2.

                // attempt to create correct thread id if one is not supplied
                if (threadId == NO_THREAD_ID || addresses.length > 1) {
                    threadId = Utils.getOrCreateThreadId(mContext, addresses[i]);
                }

                values.put("thread_id", threadId);
                mContext.getContentResolver().insert(Uri.parse("content://sms/outbox"), values);
            }

            if (!mSettings.getSignature().equals("")) {
                text += "\n" + mSettings.getSignature();
            }

            sendVoiceMessage(addresses[i], text);
        }
    }

    // splits text and adds split counter when applicable
    private String[] splitByLength(String s, int chunkSize, boolean counter) {
        int arraySize = (int) Math.ceil((double) s.length() / chunkSize);

        String[] returnArray = new String[arraySize];

        int index = 0;
        for (int i = 0; i < s.length(); i = i + chunkSize) {
            if (s.length() - i < chunkSize) {
                returnArray[index++] = s.substring(i);
            }
            else {
                returnArray[index++] = s.substring(i, i + chunkSize);
            }
        }

        if (counter && returnArray.length > 1) {
            for (int i = 0; i < returnArray.length; i++) {
                returnArray[i] = "(" + (i + 1) + "/" + returnArray.length + ") " + returnArray[i];
            }
        }

        return returnArray;
    }

    private boolean alreadySending = false;

    @SuppressLint("NewApi")
    private void sendMMS(final byte[] bytesToSend) {
        // enable mms connection to mobile data
        mConnMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (mConnMgr != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            revokeWifi(true);
            
            int result = beginMmsConnectivity();

            if (result != 0) {
                Log.v(TAG, "sendMMS - Wait MMS connectivity : " + result);

                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                result = beginMmsConnectivity();
            }

            Log.v(TAG, "sendMMS - Result of connectivity : " + result);

            if (result != 0) {
                // if mms feature is not already running (most likely isn't...) then
                // register a receiver and wait for it to be active
                IntentFilter filter = new IntentFilter();
                filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                final BroadcastReceiver receiver = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context1, Intent intent) {
                        String action = intent.getAction();

                        if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                            return;
                        }

                        @SuppressWarnings("deprecation")
                        NetworkInfo mNetworkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                        if ((mNetworkInfo == null) || (mNetworkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                            return;
                        }

                        if (!mNetworkInfo.isConnected()) {
                            return;
                        }
                        else {
                            // ready to send the message now
                            Log.v(TAG, "sendMMS - Sending through broadcast receiver");
                            alreadySending = true;
                            sendData(bytesToSend);

                            mContext.unregisterReceiver(this);
                        }
                    }
                };

                mContext.registerReceiver(receiver, filter);

                try {
                    Looper.prepare();
                }
                catch (Exception e) {
                    // Already on UI thread probably
                    Log.e(TAG, "sendMMS - Exception though", e);
                }

                // try sending after 3 seconds anyways if for some reason the receiver doesn't work
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!alreadySending) {
                            try {
                                Log.v(TAG, "sendMMS - Sending through handler");
                                mContext.unregisterReceiver(receiver);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                            sendData(bytesToSend);
                        }
                    }
                }, 3000);
            }
            else {
                // mms connection already active, so send the message
                Log.v(TAG, "sendMMS - Sending right away, already ready");
                sendData(bytesToSend);
            }
        }            
        else{
            Log.v(TAG, "sendMMS - Sending Messaging uppon 5.0 Version!");
            
            NetworkRequest.Builder builder = new NetworkRequest.Builder();

            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
            builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
            
            ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Log.i(TAG, "onAvailable " + network.toString() + " " + network.getClass().getName());
                            //if(!network.toString().startsWith("1")){
                                WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
                                if(wifi.isWifiEnabled() == true){
                                    previous_wifi = true;
                                    wifi.setWifiEnabled(false);

                                    try {
                                        Thread.sleep(1000);
                                    }
                                    catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            //}
                            
                            if (!alreadySending){
                                sendData(bytesToSend);
                                alreadySending = true;
                            }                            
                    }
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Log.i(TAG, "onCapabilitiesChanged " + networkCapabilities.getLinkDownstreamBandwidthKbps());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Log.i(TAG, "onLinkPropertiesChanged " + linkProperties.getInterfaceName());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            
            NetworkRequest networkRequest = builder.build();
            mConnMgr.requestNetwork(networkRequest, callback);
            mConnMgr.registerNetworkCallback(networkRequest, callback);
        }
    }

    private void sendMMSWiFi(final byte[] bytesToSend) {
        // enable mms connection to mobile data
        mConnMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo.State state = mConnMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS).getState();

        if ((0 == state.compareTo(NetworkInfo.State.CONNECTED) || 0 == state.compareTo(NetworkInfo.State.CONNECTING))) {
            sendData(bytesToSend);
        }
        else {
            int resultInt = mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableMMS");

            if (resultInt != 0) {
                Log.v(TAG, "sendMMSWiFi - Wait MMS connectivity : " + resultInt);

                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                resultInt = beginMmsConnectivity();
            }

            if (resultInt == 0) {
                try {
                    Utils.ensureRouteToHost(mContext, mSettings.getMmsc(), mSettings.getProxy());
                    sendData(bytesToSend);
                }
                catch (Exception e) {
                    Log.e(TAG, "sendMMSWiFi - exception thrown", e);
                    sendData(bytesToSend);
                }
            }
            else {
                // if mms feature is not already running (most likely isn't...)
                // then register a receiver and wait for it to be active
                IntentFilter filter = new IntentFilter();
                filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                final BroadcastReceiver receiver = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context1, Intent intent) {
                        String action = intent.getAction();

                        if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                            return;
                        }

                        NetworkInfo mNetworkInfo = mConnMgr.getActiveNetworkInfo();
                        if ((mNetworkInfo == null) || (mNetworkInfo.getType() != ConnectivityManager.TYPE_MOBILE_MMS)) {
                            return;
                        }

                        if (!mNetworkInfo.isConnected()) {
                            return;
                        }
                        else {
                            alreadySending = true;

                            try {
                                Utils.ensureRouteToHost(mContext, mSettings.getMmsc(), mSettings.getProxy());
                                sendData(bytesToSend);
                            }
                            catch (Exception e) {
                                Log.e(TAG, "sendMMSWiFi - exception thrown", e);
                                sendData(bytesToSend);
                            }

                            mContext.unregisterReceiver(this);
                        }
                    }
                };

                mContext.registerReceiver(receiver, filter);

                try {
                    Looper.prepare();
                }
                catch (Exception e) {
                    // Already on UI thread probably
                }

                // try sending after 3 seconds anyways if for some reason the receiver doesn't work 
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!alreadySending) {
                            try {
                                mContext.unregisterReceiver(receiver);
                            }
                            catch (Exception e) {

                            }

                            try {
                                Utils.ensureRouteToHost(mContext, mSettings.getMmsc(), mSettings.getProxy());
                                sendData(bytesToSend);
                            }
                            catch (Exception e) {
                                Log.e(TAG, "sendMMSWiFi - exception thrown", e);
                                sendData(bytesToSend);
                            }
                        }
                    }
                }, 3000);
            }
        }
    }

    private void sendData(final byte[] bytesToSend) {
        // be sure this is running on new thread, not UI
        Log.v(TAG, "Starting new thread to send on");
        new Thread(new Runnable() {

            @Override
            public void run() {
                List<APN> apns = new ArrayList<APN>();

                try {
                    APN apn = new APN(mSettings.getMmsc(), mSettings.getPort(), mSettings.getProxy());
                    apns.add(apn);

                    String mmscUrl = apns.get(0).MMSCenterUrl != null ? apns.get(0).MMSCenterUrl.trim() : null;
                    apns.get(0).MMSCenterUrl = mmscUrl;

                    if (apns.get(0).MMSCenterUrl.equals("")) {
                        // attempt to get apns from internal databases, most
                        // likely will fail due to insignificant permissions
                        APNHelper helper = new APNHelper(mContext);
                        apns = helper.getMMSApns();
                    }
                }
                catch (Exception e) {
                    Log.v(TAG, "error in the apns, none are available most likely causing an index out of bounds exception. cant send a message, so therefore mark as failed!");
                    e.printStackTrace();
                    markMmsFailed("apns 오류");
                    return;
                }

                try {
                    // attempts to send the message using given apns
                    Log.v(TAG, "MMS Center URL = " + apns.get(0).MMSCenterUrl + " / MMS Proxy = " + apns.get(0).MMSProxy + " / MMS Port = " + apns.get(0).MMSPort);
                    Log.v(TAG, "Initial attempt at sending starting now");
                    trySending(apns.get(0), bytesToSend, 0);
                }
                catch (Exception e) {
                    // some type of apn error, so notify user of failure
                    Log.v(TAG, "weird error, not sure how this could even be called other than apn stuff");
                    e.printStackTrace();
                    markMmsFailed("통신망 오류");
                }
            }
        }).start();
    }

    public static final int MAX_NUM_RETRIES = 2;
    public int sendBrod = 0;
    private void trySending(final APN apns, final byte[] bytesToSend, final int numRetries) {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ProgressCallbackEntity.PROGRESS_STATUS_ACTION);
            BroadcastReceiver receiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    int progress = intent.getIntExtra("progress", -3);
                    Log.v(TAG, "trySending - Sending Progress = " + progress);

                    // send progress broadcast to update ui if desired...
                    Intent progressIntent = new Intent(MMS_PROGRESS);
                    progressIntent.putExtra("progress", progress);
                    context.sendBroadcast(progressIntent);

                    if (progress == ProgressCallbackEntity.PROGRESS_COMPLETE) {
                        WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
                        if(previous_wifi && wifi.isWifiEnabled() == false)
                            wifi.setWifiEnabled(true);
                        
                        /*
	                        if (saveMessage) {
	                            Cursor query = context.getContentResolver().query(Uri.parse("content://mms"), new String[] { "_id" }, null, null, "date desc");
	                            if (query != null && query.moveToFirst()) {
	                                String id = query.getString(query.getColumnIndex("_id"));
	                                query.close();
	
	                                // move to the sent box
	                                ContentValues values = new ContentValues();
	                                values.put("msg_box", 2);
	                                String where = "_id" + " = '" + id + "'";
	                                context.getContentResolver().update(Uri.parse("content://mms"), values, where, null);
	                            }
	                        }
						*/
                        
                        if(progress == 100 && sendBrod == 0){
                        	sendBrod = 1;
                            Intent mintent = new Intent(MMS_SUCCESS);
                            mintent.putExtra("state", "성공");
                            mintent.putExtra("number", sNumber);
                            mintent.putExtra("idx", sIdx);
                            mintent.putExtra("way", sWay);
                            mintent.putExtra("msg", "발송성공");
                            mContext.sendBroadcast(mintent);
                        }


                        context.sendBroadcast(new Intent(REFRESH));
                        
                        try {
                            context.unregisterReceiver(this);
                        }
                        catch (Exception e) { /* Receiver not registered */}

                        // give everything time to finish up, may help the abort
                        // being shown after the progress is already 100
                        new Handler().postDelayed(new Runnable() {
                            @SuppressLint("NewApi")
                            @SuppressWarnings("deprecation")
                            @Override
                            public void run() {                                
                                if (mConnMgr != null && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                                    mConnMgr.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE_MMS, "enableMMS");
                                    if (mSettings.getWifiMmsFix()) {
                                        reinstateWifi();
                                    }
                                }
                            }
                        }, 1000);
                    }
                    else if (progress == ProgressCallbackEntity.PROGRESS_ABORT) {
                        // This seems to get called only after the progress has
                        // reached 100 and then something else goes wrong, so
                        // here we will try and send again and see if it works
                        Log.v(TAG, "sending aborted for some reason... - Max Retry Count = " + MAX_NUM_RETRIES);
                        context.unregisterReceiver(this);

                        if (numRetries < MAX_NUM_RETRIES) {
                            // sleep and try again in three seconds to see if
                            // that give wifi and mobile data a chance to toggle
                            // in time
                            try {
                                Log.v(TAG, "Will retry to send message again after 3 sec. - Retry = " + numRetries);
                                Thread.sleep(3000);
                            }
                            catch (Exception f) {

                            }

                            if (mSettings.getWifiMmsFix()) {
                                sendMMS(bytesToSend);
                            }
                            else {
                                sendMMSWiFi(bytesToSend);
                            }
                        }
                        else {
                            markMmsFailed("통신망 오류");
                        }
                    }
                }
            };

            mContext.registerReceiver(receiver, filter);

            // This is where the actual post request is made to send the bytes
            // we previously created through the given apns
            Log.v(TAG, "attempt: " + numRetries);
            Utils.ensureRouteToHost(mContext, apns.MMSCenterUrl, apns.MMSProxy);
            HttpUtils.httpConnection(mContext, 4444L, apns.MMSCenterUrl, bytesToSend, HttpUtils.HTTP_POST_METHOD, !TextUtils.isEmpty(apns.MMSProxy), apns.MMSProxy, Integer.parseInt(apns.MMSPort));
        }
        catch (IOException e) {
            Log.v(TAG, "some type of error happened when actually sending maybe? - Retry = " + numRetries);
            e.printStackTrace();

            if (numRetries < MAX_NUM_RETRIES) {
                // sleep and try again in three seconds to see if that give wifi
                // and mobile data a chance to toggle in time
                try {
                    Log.v(TAG, "Will retry to send message again after 3 sec. - Retry = " + numRetries);
                    Thread.sleep(3000);
                }
                catch (Exception f) {
                    f.printStackTrace();
                }

                trySending(apns, bytesToSend, numRetries + 1);
            }
            else {
                markMmsFailed("알수없는 오류");
            }
        }
    }

    private void markMmsFailed(String msg) {
        Log.v(TAG, "markMmsFailed() - Sending Failed!");
        // if it still fails, then mark message as failed
        
        WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        if(previous_wifi && wifi.isWifiEnabled() == false)
            wifi.setWifiEnabled(true);
        
        if (mSettings.getWifiMmsFix() && (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT)) {
            reinstateWifi();
        }
        
        Intent mintent = new Intent(MMS_ERROR);
        mintent.putExtra("state", "실패");
        mintent.putExtra("number", sNumber);
        mintent.putExtra("msg", msg);
        mintent.putExtra("idx", sIdx);
        mintent.putExtra("way", sWay);
        mContext.sendBroadcast(mintent);
        /*
        if (saveMessage) {
            Cursor query = mContext.getContentResolver( ).query(Uri.parse("content://mms"), new String[] { "_id" }, null, null, "date desc");
            if (query != null && query.moveToFirst( )) {
                String id = query.getString(query.getColumnIndex("_id"));
                query.close( );
                
                // mark message as failed
                ContentValues values = new ContentValues( );
                values.put("msg_box", 5);
                String where = "_id" + " = '" + id + "'";
                mContext.getContentResolver( ).update(Uri.parse("content://mms"), values, where, null);
            }
        }
        
        ((Activity) mContext).getWindow( ).getDecorView( ).findViewById(android.R.id.content).post(new Runnable( ) {
            
            @Override
            public void run() {
                mContext.sendBroadcast(new Intent(REFRESH));
                mContext.sendBroadcast(new Intent(NOTIFY_SMS_FAILURE));
                
                // broadcast that mms has failed and you can notify user from
                // there if you would like
                mContext.sendBroadcast(new Intent(MMS_ERROR));
                
            }
            
        });
        */
    }

    private void sendVoiceMessage(final String destAddr, final String text) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String rnrse = mSettings.getRnrSe();
                String account = mSettings.getAccount();
                String authToken;

                try {
                    authToken = Utils.getAuthToken(account, mContext);

                    if (rnrse == null) {
                        rnrse = fetchRnrSe(authToken, mContext);
                    }
                }
                catch (Exception e) {
                    failVoice();
                    return;
                }

                try {
                    sendRnrSe(authToken, rnrse, destAddr, text);
                    successVoice();
                    return;
                }
                catch (Exception e) {}

                try {
                    // try again...
                    rnrse = fetchRnrSe(authToken, mContext);
                    sendRnrSe(authToken, rnrse, destAddr, text);
                    successVoice();
                }
                catch (Exception e) {
                    failVoice();
                }
            }
        }).start();
    }

    // Hit the google voice api to send a text
    private void sendRnrSe(String authToken, String rnrse, String number, String text) throws Exception {
        JsonObject json = 
                Ion.with(mContext)
                   .load("https://www.google.com/voice/sms/send/")
                   .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                   .setBodyParameter("phoneNumber", number)
                   .setBodyParameter("sendErrorSms", "0")
                   .setBodyParameter("text", text)
                   .setBodyParameter("_rnr_se", rnrse)
                   .asJsonObject()
                   .get();

        if (!json.get("ok").getAsBoolean())
            throw new Exception(json.toString());
    }

    private void failVoice() {
        if (saveMessage) {
            Cursor query = mContext.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

            // mark message as failed
            if (query.moveToFirst()) {
                String id = query.getString(query.getColumnIndex("_id"));
                ContentValues values = new ContentValues();
                values.put("type", "5");
                values.put("read", true);
                mContext.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
            }

            query.close();
        }

        mContext.sendBroadcast(new Intent(REFRESH));
        mContext.sendBroadcast(new Intent(VOICE_FAILED));
    }

    private void successVoice() {
        if (saveMessage) {
            Cursor query = mContext.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

            // mark message as sent successfully
            if (query.moveToFirst()) {
                String id = query.getString(query.getColumnIndex("_id"));
                ContentValues values = new ContentValues();
                values.put("type", "2");
                values.put("read", true);
                mContext.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
            }

            query.close();
        }

        mContext.sendBroadcast(new Intent(REFRESH));
    }

    private String fetchRnrSe(String authToken, Context context) throws ExecutionException, InterruptedException {
        JsonObject userInfo = Ion.with(context).load("https://www.google.com/voice/request/user").setHeader("Authorization", "GoogleLogin auth=" + authToken).asJsonObject().get();

        String rnrse = userInfo.get("r").getAsString();

        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Activity.TELEPHONY_SERVICE);
            String number = tm.getLine1Number();
            if (number != null) {
                JsonObject phones = userInfo.getAsJsonObject("phones");
                for (Map.Entry<String, JsonElement> entry : phones.entrySet()) {
                    JsonObject phone = entry.getValue().getAsJsonObject();
                    if (!PhoneNumberUtils.compare(number, phone.get("phoneNumber").getAsString()))
                        continue;
                    if (!phone.get("smsEnabled").getAsBoolean())
                        break;

                    Ion.with(context).load("https://www.google.com/voice/mSettings/editForwardingSms/").setHeader("Authorization", "GoogleLogin auth=" + authToken).setBodyParameter("phoneId", entry.getKey()).setBodyParameter("enabled", "0").setBodyParameter("_rnr_se", rnrse).asJsonObject();
                    break;
                }
            }
        }
        catch (Exception e) {

        }

        // broadcast so you can save it to your shared prefs or something so
        // that it doesn't need to be retrieved every time
        Intent intent = new Intent(VOICE_TOKEN);
        intent.putExtra("_rnr_se", rnrse);
        context.sendBroadcast(intent);

        return rnrse;
    }

    private static Uri insert(Context context, String[] to, MMSPart[] parts, String subject) {
        try {
            Uri destUri = Uri.parse("content://mms");

            Set<String> recipients = new HashSet<String>();
            recipients.addAll(Arrays.asList(to));
            long thread_id = Utils.getOrCreateThreadId(context, recipients);
            long now = System.currentTimeMillis();
            
            // Create a dummy SMS
            ContentValues dummyValues = new ContentValues();
            dummyValues.put("thread_id", thread_id);
            dummyValues.put("body",      " ");

            Uri dummySms = context.getContentResolver().insert(Uri.parse("content://sms/sent"), dummyValues);

            // Create a new message entry
            ContentValues mmsValues = new ContentValues();
            mmsValues.put("thread_id",  thread_id);
            mmsValues.put("date",       now / 1000L);
            mmsValues.put("msg_box",    4);
            mmsValues.put("read",       true);
            mmsValues.put("sub",        subject != null ? subject : "");
            mmsValues.put("sub_cs",     106);
            mmsValues.put("ct_t",       "application/vnd.wap.multipart.related");
            // mmsValues.put("m_id",    System.currentTimeMillis());
            
            long imageBytes = 0;

            for (MMSPart part : parts) {
                imageBytes += part.Data.length;
            }

            mmsValues.put("exp",        imageBytes);
            mmsValues.put("m_cls",      "personal");
            mmsValues.put("m_type",     128);           // 128 (SEND REQ), 132 (RETRIEVE CONF), 130 (NOTIF IND)
            mmsValues.put("v",          19);
            mmsValues.put("pri",        129);
            mmsValues.put("tr_id",      "T" + Long.toHexString(now));
            mmsValues.put("resp_st",    128);

            // Insert message
            Uri     res         = context.getContentResolver().insert(destUri, mmsValues);
            String  messageId   = res.getLastPathSegment().trim();

            // Create part
            for (MMSPart part : parts) {
                if (part.MimeType.startsWith("image")) {
                    createPartImage(context, messageId, part.Data, part.MimeType);
                }
                else if (part.MimeType.startsWith("text")) {
                    createPartText(context, messageId, new String(part.Data, "UTF-8"));
                }
            }

            // Create addresses
            for (String addr : to) {
                createAddr(context, messageId, addr);
            }

            // res = Uri.parse(destUri + "/" + messageId);

            // Delete dummy sms
            context.getContentResolver().delete(dummySms, null, null);

            return res;
        }
        catch (Exception e) {
            Log.v(TAG, "still an error saving... :(");
            Log.e(TAG, "exception thrown", e);
        }

        return null;
    }

    // create the image part to be stored in database
    private static Uri createPartImage(Context context, String id, byte[] imageBytes, String mimeType) throws Exception {
        ContentValues mmsPartValue = new ContentValues();
        mmsPartValue.put("mid", id);
        mmsPartValue.put("ct",  mimeType);
        mmsPartValue.put("cid", "<" + System.currentTimeMillis() + ">");
        
        Uri partUri = Uri.parse("content://mms/" + id + "/part");
        Uri res     = context.getContentResolver().insert(partUri, mmsPartValue);

        // Add data to part
        OutputStream os = context.getContentResolver().openOutputStream(res);
        ByteArrayInputStream is = new ByteArrayInputStream(imageBytes);
        byte[] buffer = new byte[256];

        for (int len = 0; (len = is.read(buffer)) != -1;) {
            os.write(buffer, 0, len);
        }

        os.close();
        is.close();

        return res;
    }

    // create the text part to be stored in database
    private static Uri createPartText(Context context, String id, String text) throws Exception {
        ContentValues mmsPartValue = new ContentValues();
        mmsPartValue.put("mid",     id);
        mmsPartValue.put("ct",      "text/plain");
        mmsPartValue.put("cid",     "<" + System.currentTimeMillis() + ">");
        mmsPartValue.put("text",    text);
        Uri partUri = Uri.parse("content://mms/" + id + "/part");
        Uri res = context.getContentResolver().insert(partUri, mmsPartValue);

        return res;
    }

    // add address to the request
    private static Uri createAddr(Context context, String id, String addr) throws Exception {
        ContentValues addrValues = new ContentValues();
        addrValues.put("address",   addr);
        addrValues.put("charset",   "106");
        addrValues.put("type",      151); // TO
        
        Uri addrUri = Uri.parse("content://mms/" + id + "/addr");
        Uri res     = context.getContentResolver().insert(addrUri, addrValues);

        return res;
    }

    /**
     * A method for checking whether or not a certain message will be sent as
     * mms depending on its contents and the mSettings
     *
     * @param message
     *            is the message that you are checking against
     * @return true if the message will be mms, otherwise false
     */
    public boolean checkMMS(Message message) {
        return message.getImages().length != 0                                                                                                                                  || 
               (message.getParts() != null && message.getParts().size() > 0)                                                                                                    || 
               (mSettings.getSendLongAsMms() && Utils.getNumPages(mSettings, message.getText()) > mSettings.getSendLongAsMmsAfter() && message.getType() != Message.TYPE_VOICE) || 
               (mSettings.getSendLongAsMms() == false && message.getText().getBytes().length >= 140)                                                                            || 
               (message.getAddresses().length > 1 && mSettings.getGroup())                                                                                                      ||
               (message.getSubject() != null && message.getSubject( ).trim( ).length( ) > 0);
    }

    private void reinstateWifi() {
        try {
            mContext.unregisterReceiver(mSettings.discon);
        }
        catch (Exception f) {

        }

        WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(false);
        wifi.setWifiEnabled(mSettings.currentWifiState);
        wifi.reconnect();
        Utils.setMobileDataEnabled(mContext, mSettings.currentDataState);
    }

    private void revokeWifi(boolean saveState) {
        WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

        if (saveState) {
            mSettings.currentWifi = wifi.getConnectionInfo();
            mSettings.currentWifiState = wifi.isWifiEnabled();
            wifi.disconnect();
            mSettings.discon = new DisconnectWifi();
            mContext.registerReceiver(mSettings.discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
            mSettings.currentDataState = Utils.isMobileDataEnabled(mContext);
            Utils.setMobileDataEnabled(mContext, true);
        }
        else {
            wifi.disconnect();
            wifi.disconnect();
            mSettings.discon = new DisconnectWifi();
            mContext.registerReceiver(mSettings.discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
            Utils.setMobileDataEnabled(mContext, true);
        }
    }

    /**
     * @deprecated
     */
    private int beginMmsConnectivity() {
        Log.v(TAG, "starting mms service");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableMMS");
        }
        else{
            try
            {
                TelephonyManager telephonyService = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                Method getMobileDataEnabledMethod = telephonyService.getClass().getDeclaredMethod("getDataEnabled");
    
                if (null != getMobileDataEnabledMethod)
                {
                    if((Boolean) getMobileDataEnabledMethod.invoke(telephonyService) == true)
                        return 0;
                    else
                        return 1;
                }
            }
            catch (Exception ex)
            {
                Log.e(TAG, "Error getting mobile data state", ex);
            }
    
            return 1;
        }
    }
}