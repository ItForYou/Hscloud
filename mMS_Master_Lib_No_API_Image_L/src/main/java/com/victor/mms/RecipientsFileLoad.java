package com.victor.mms;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import android.os.Environment;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

public class RecipientsFileLoad {
	private static final String		FOLDER = "/recipients/";
	private static final String		FILE = "contact_lists.txt";
	private static final String     TAG  = "Victor_Load_Recipients";
	
	public static boolean isExistFolder() {
		boolean bRet = false;
		
		File file = new File(getExternalStorageDirectory());
		if(file.isDirectory()==false) {
			bRet = file.mkdirs();
		} else {
			bRet = true;
		}
		
		return bRet;
	}
	
	public static boolean isFileExist() {
		return new File(getExternalStorageDirectory()+FILE).isFile();
	}
	
	public static String loadRecipients() {
	    return loadRecipients("");
	}
	    
    public static String loadRecipients(String path) {
		String recipients = "";
		File file;
		
		if(path.equals(""))
		    file = new File(getExternalStorageDirectory()+FILE);
		else
		    file = new File(path);
		    
		if( file.isFile()==false ) return null;
		try {
			FileInputStream fin = new FileInputStream(file);
			DataInputStream in = new DataInputStream(fin);
			BufferedReader br = new BufferedReader(new InputStreamReader(in)); 
	        String line = null;
	        
	        int i=1;
	        while ((line = br.readLine()) != null) {
	        	if( line.trim().length()>0) {
	        	    if(line.startsWith("010"))
	        	        recipients +=line+",";	
	        	}
	        	
	        	Log.d(TAG,(i++)+" recipients:"+line);
	        }

			br.close();
			in.close();
			fin.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(recipients.trim().length() > 0)
		    return recipients.substring(0, recipients.length()-1);
		else
		    return "";
	}
	
	public static String phoneNumberFormatter(String line) {
		return PhoneNumberUtils.formatNumber(line);
	}
	
	public static String getExternalStorageDirectory() {
		return Environment.getExternalStorageDirectory().getPath()+FOLDER;
	}
}
