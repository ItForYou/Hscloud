package com.victor.android.send_message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.net.Uri;

public class MessageVo{
    public  String[]        mRecipients     = null;
    private String             mSubject        = null;
    private String            mMessage        = null;
    private ArrayList<Uri>     mContentsList     = null;
    
    public MessageVo(String recipients, String subject, String message, ArrayList<Uri> contentsList) {
        this.mRecipients = recipients.split(",");
        this.mSubject = subject;
        this.mMessage = message;
        this.mContentsList = contentsList;
    }
    
    public MessageVo(String[] recipients, String subject, String message, ArrayList<Uri> contentsList) {
        this.mRecipients = recipients;
        this.mSubject = subject;
        this.mMessage = message;
        this.mContentsList = contentsList;
    }
    
    public String[] getRecipients() {
        return mRecipients;
    }
    
    public List<String> getRecipientsList() {
        if( mRecipients!=null ) {
            return Arrays.asList(mRecipients);
        }
        return null;
    }
    
    public void setRecipients(String recipients, String regularExpression) {
        this.mRecipients = recipients.split(regularExpression);
    }
    
    public void setRecipients(String[] recipients) {
        this.mRecipients = recipients;
    }

    public String getSubject() {
        return mSubject;
    }

    public void setSubject(String subject) {
        this.mSubject = subject;
    }

    public String getMessage() {
        return mMessage;
    }

    public void setMessage(String message) {
        this.mMessage = message;
    }

    public ArrayList<Uri> getContentsList() {
        return mContentsList;
    }

    public void setContentsList(ArrayList<Uri> contentsList) {
        this.mContentsList = contentsList;
    }
    
    public boolean isMMS() {
        return (mContentsList!=null && mContentsList.size() > 0) ||
               (mMessage != null && mMessage.trim().length() > 140 && mMessage.trim().length() !=0)  ||
               (mSubject != null && mSubject.trim().length() > 0)
               ? true : false;
    }
    
    public int getNumberCnt() {
        return mRecipients.length;
    }
    
    public static MessageVo clone(MessageVo msg) {
        return new MessageVo(msg.getRecipients(), 
                                msg.getSubject(),
                                msg.getMessage(),
                                msg.getContentsList());
    }
    
    public static MessageVo clone(String recipients, MessageVo msg) {
        return new MessageVo(recipients, 
                                msg.getSubject(),
                                msg.getMessage(),
                                msg.getContentsList());
    }
}
