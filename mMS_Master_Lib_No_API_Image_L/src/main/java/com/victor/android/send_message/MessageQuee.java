package com.victor.android.send_message;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;

import com.victor.androidbridge.SendMMS3.IMMMessage;
import com.victor.androidbridge.nokia.IMMConstants;
import com.victor.androidbridge.nokia.MMContent;
import com.victor.androidbridge.nokia.MMMessage;

public class MessageQuee implements IMMMessage{
	private List<MessageVo> quee 			= new ArrayList<MessageVo>();
	private int				mIndex			= 0;
	private Context			mContext		= null;
	public  int             mCount          = 1;
	public  int             mMaxCount       = 0;
	
	public MessageQuee(Context context, MessageVo message, int maxCount) {
		this.mContext         = context;
		this.mMaxCount        = maxCount;
		
		String[] recipientsArray = message.getRecipients();
		String temp = "";
		for( int i=1; i <= recipientsArray.length; i++ ) {
			if(recipientsArray[i-1] != null && recipientsArray[i-1].trim().length() > 0)
				temp += recipientsArray[i-1]+ ",";
		}
		
		if( !temp.equals("") ) {
			quee.add( MessageVo.clone(temp.substring(0, temp.length()-1), message) );
			mCount++;
		}
	}
	
	private MMMessage convertMMMessage(MessageVo messageVo) {
		MMMessage mm = new MMMessage();
		
		List<String> numberList = messageVo.getRecipientsList();
		
	    mm.setVersion(IMMConstants.MMS_VERSION_10);
	    mm.setMessageType(IMMConstants.MESSAGE_TYPE_M_SEND_REQ);
	    mm.setTransactionId("0000000066");
	    mm.setDate(new Date(System.currentTimeMillis()));

	    mm.setFrom(getMyPhoneNumber()+"/TYPE=PLMN");
	    for( String number:numberList) {
	    	number = number.replaceAll("-", "");
			mm.addToAddress(number+"/TYPE=PLMN");
		}
	    
	    mm.setDeliveryReport(false);
	    mm.setReadReply(false);
	    mm.setSenderVisibility(IMMConstants.SENDER_VISIBILITY_SHOW);
	    
	    String subject = messageVo.getSubject();
	    mm.setSubject((subject==null||subject.trim().length()==0)?"No subject":subject);
	    
	    mm.setMessageClass(IMMConstants.MESSAGE_CLASS_PERSONAL);
	    mm.setPriority(IMMConstants.PRIORITY_HIGH);
	    mm.setContentType(IMMConstants.CT_APPLICATION_MULTIPART_MIXED);
	    
	    MMContent part = new MMContent();
	    byte[] buf = new byte[]{};
	    try {
	    	buf = messageVo.getMessage().getBytes("euc-kr");
	    } catch ( UnsupportedEncodingException e ) {
	    	e.printStackTrace();
	    }
	    part.setContent(buf, 0, buf.length);
	    part.setContentId("<0>");
	    part.setType(IMMConstants.CT_TEXT_PLAIN+"; charset=\"euc-kr\";");
	    mm.addContent(part);
	    
	    addContents(mm, messageVo);

		return mm;
	}
	
	private void addContents(MMMessage mm, MessageVo msg) {
		ArrayList<Uri> contentsList = new ArrayList<Uri>();
		contentsList = msg.getContentsList();
		
		int i=1;
		for( Uri uri:contentsList ) {
		    MMContent part = getMMContent(uri,i++);
			mm.addContent(part);
		}
	}
	
	private MMContent getMMContent(Uri uri, int id) {
		MMContent part = null;

		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			Bitmap bmp = Images.Media.getBitmap(mContext.getContentResolver(), uri);
			Bitmap resizeBmp = null;
		    if(bmp.getWidth() > 4000 || bmp.getHeight() > 4000)
		        resizeBmp = resizingBitmap(mContext, bmp, bmp.getWidth()/10 , bmp.getHeight()/10, false);
		    else if(bmp.getWidth() > 3000 || bmp.getHeight() > 3000)
		        resizeBmp = resizingBitmap(mContext, bmp, bmp.getWidth()/8 , bmp.getHeight()/8, false);
		    else if(bmp.getWidth() > 2000 || bmp.getHeight() > 2000)
		        resizeBmp = resizingBitmap(mContext, bmp, bmp.getWidth()/6 , bmp.getHeight()/6, false);
		    else if(bmp.getWidth() > 1000 || bmp.getHeight() > 1000)
		        resizeBmp = resizingBitmap(mContext, bmp, bmp.getWidth()/4 , bmp.getHeight()/4, false);
		    else
		        resizeBmp = resizingBitmap(mContext, bmp, bmp.getWidth() , bmp.getHeight(), false);

		    resizeBmp.compress(CompressFormat.PNG, 90, os);
		    bmp.recycle();

			part = new MMContent();
			byte[] buf = os.toByteArray();
			part.setContent(buf, 0, buf.length);
			part.setContentId("<"+id+">");
			part.setType(IMMConstants.CT_IMAGE_PNG);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return part;
	}
	
	public static Bitmap resizingBitmap(Context context, Bitmap bmp, int wdp, int hdp, boolean bRotate) {
		int width     = bmp.getWidth(); 
		int height    = bmp.getHeight(); 
		int newWidth  = dp2px(context, wdp); 
		int newHeight = dp2px(context, hdp); 
		
		float scaleWidth  = ((float)newWidth) / width;
		float scaleHeight = ((float)newHeight) / height;
		
		// create a matrix for the manipulation 
		Matrix matrix = new Matrix(); 
		// resize the bit map 
		matrix.postScale(scaleWidth, scaleHeight); 
		
		if(bRotate){
			// rotate the Bitmap 
			matrix.postRotate(90);
		}
		// recreate the new Bitmap
		return Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, false); 
	}

	public static int dp2px(Context context, int dp){
		DisplayMetrics outMetrics = new DisplayMetrics();
		 
		((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
		 
		return (int) (dp*outMetrics.density);
	}
	
	private String getMyPhoneNumber() {
		TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE); 
		return tm.getLine1Number();
	}
	
	@Override
	public MMMessage getMMMessage() {
		if( mIndex < quee.size() && mIndex >= 0 ) {
		    if(mIndex >= quee.size())
	            mIndex = 0;
	        
			return convertMMMessage(quee.get(mIndex++));
		}
		return null;
	}
	
    public int getMMMessageSize() {
        return mCount;
    }	
	
	@Override
	public MMMessage getMMMessage(int index) {
		MMMessage msg = null;
		if( index < quee.size() && index >= 0 ) {
			msg = convertMMMessage(quee.get(index));
		}
		return msg;
	}

	@Override
	public void removeAll() {
		quee.clear();
	}

	@Override
	public void remove(int index) {
		if( index < quee.size() && index >= 0 ) {
			quee.remove(index);
		}
	}

	@Override
	public void add(MMMessage msg) {
		// TODO::convert MMMessage to MessageVo.
	}
}
