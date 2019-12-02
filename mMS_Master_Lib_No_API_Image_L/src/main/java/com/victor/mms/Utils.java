package com.victor.mms;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.DisplayMetrics;

public class Utils {
    public static final boolean DBG = true;
    public static final String  TAG = "Victor";
    
    public enum msgType {
           ONEONE_STRING(1),    ONEONE_URI(2),    ONEONE_URL(3), 
          ONESOME_STRING(4),   ONESOME_URI(5),   ONESOME_URL(6), 
          SOMEONE_STRING(7),   SOMEONE_URI(8),   SOMEONE_URL(9), 
        SOMESOME_STRING(10), SOMESOME_URI(11), SOMESOME_URL(12);
        
        private int value;
        
        private msgType(int value) {
            this.value = value;
        }
    }
    
    public class messageBody {
        msgType  type;
        int      timeGap;
        int      maxSize;
        String[] phone;
        String   uri_path;
        Uri      uri;
        String   title;
        String   body;
        Bitmap   bitmap;
        BufferedInputStream inputStream;
        
        public messageBody(msgType type, int timeGap, int maxSize, String[] phone, String uri_path, Uri local_uri, String title, String body, Bitmap bitmap, BufferedInputStream mInputStream){
            this.type = type;
            this.timeGap = timeGap;
            this.maxSize = maxSize;
            this.phone = phone;
            this.uri_path = uri_path;
            this.uri = local_uri;
            this.title = title;
            this.body = body;
            this.bitmap = bitmap;
            this.inputStream = mInputStream;
        }
        
        public msgType getType() {
            return type;
        }
        
        public void setType(msgType type) {
            this.type = type;
        }
        
        public int getTimeGap() {
            return timeGap;
        }
        
        public void setTimeGap(int timeGap) {
            this.timeGap = timeGap;
        }
        
        public int getMaxSize() {
            return maxSize;
        }
        
        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
        
        public String[] getPhone() {
            return phone;
        }
        
        public void setPhone(String[] phone) {
            this.phone = phone;
        }
        
        public void setUri(Uri uri) {
            this.uri = uri;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getBody() {
            return body;
        }
        
        public void setBody(String body) {
            this.body = body;
        }

        public String getUri_path() {
            return uri_path;
        }

        public void setUri_path(String uri_path) {
            this.uri_path = uri_path;
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        public BufferedInputStream getInputStream() {
            return inputStream;
        }

        public void setInputStream(BufferedInputStream mInputStream) {
            this.inputStream = mInputStream;
        }        
    }
    
    public static Bitmap resizingBitmap(Context context, Bitmap bmp, int wdp, int hdp, boolean bRotate) {
        int width = bmp.getWidth( );
        int height = bmp.getHeight( );
        int newWidth = dp2px(context, wdp);
        int newHeight = dp2px(context, hdp);
        
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        
        // create a matrix for the manipulation
        Matrix matrix = new Matrix( );
        // resize the bit map
        matrix.postScale(scaleWidth, scaleHeight);
        
        if (bRotate) {
            // rotate the Bitmap
            matrix.postRotate(90);
        }
        // recreate the new Bitmap
        return Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, false);
    }
    
    public Bitmap resizeBitmapImageFn(Bitmap bmpSource, int maxResolution){ 
        int iWidth = bmpSource.getWidth();
        int iHeight = bmpSource.getHeight();
        int newWidth = iWidth ;
        int newHeight = iHeight ;
        float rate = 0.0f;
        
        if(maxResolution < 1000)
            return bmpSource;
        
        if(iWidth > iHeight ){
            if(maxResolution < iWidth ){ 
                rate = maxResolution / (float) iWidth ; 
                newHeight = (int) (iHeight * rate); 
                newWidth = maxResolution; 
            }
        }else{
            if(maxResolution < iHeight ){
                rate = maxResolution / (float) iHeight ; 
                newWidth = (int) (iWidth * rate);
                newHeight = maxResolution;
            }
        }
 
        return Bitmap.createScaledBitmap(bmpSource, newWidth, newHeight, true); 
    }
    
    public static int dp2px(Context context, int dp) {
        DisplayMetrics outMetrics = new DisplayMetrics( );
        
        ((Activity) context).getWindowManager( ).getDefaultDisplay( ).getMetrics(outMetrics);
        
        return (int) (dp * outMetrics.density);
    }
    
    public static String getPathFromUri(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver( ).query(uri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst( );
            return cursor.getString(column_index);
        }
        finally {
            if (cursor != null) {
                cursor.close( );
            }
        }
    }
    
    public static Uri getUriFromPath(Context context, String path){
        Uri fileUri = Uri.parse( path );
        String filePath = fileUri.getPath();
        Cursor cursor = context.getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, "_data = '" + filePath + "'", null, null );
        cursor.moveToNext();
        int id = cursor.getInt( cursor.getColumnIndex( "_id" ) );
        Uri uri = ContentUris.withAppendedId( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id );
    
        return uri;
    }
    
    public static final String getPath(Context context, Uri uri) {
        final boolean isAndroidVersionKitKat = Build.VERSION.SDK_INT >=  19; // ( == Build.VERSION_CODE.KITKAT )
        
        if(isGooglePhotoUri(uri)) {
            return uri.getLastPathSegment();
        }
        
        if(isAndroidVersionKitKat && DocumentManager.isDocumentUri(uri)) {
            if(isMediaDocument(uri) && DocumentManager.isDocumentUri(uri)) {
                final String docId = DocumentManager.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    
                } else if ("video".equals(type)) {
                    return null;
                    
                } else if ("audio".equals(type)) {
                    return null;
                }
                
                final String selection = Images.Media._ID + "=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
            
        }
        
        if(isPathSDCardType(uri)) {
            final String selection = Images.Media._ID + "=?";
            final String[] selectionArgs = new String[] {
                    uri.getLastPathSegment()
            };
            
            return getDataColumn(context,  MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs);
        }
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        
        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs ,null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        
        return null;
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
    
    public static boolean isPathSDCardType(Uri uri) {
        return "external".equals(uri.getPathSegments().get(0));
    }
    
    public static boolean isGooglePhotoUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
    
    public static void loadRecipients() {
        loadRecipients("");
    }
    
    public static String loadRecipients(String filepath) {
        String recipients = null;
        
        if (filepath.equals("")) recipients = RecipientsFileLoad.loadRecipients( );
        else recipients = RecipientsFileLoad.loadRecipients(filepath);
        
        return recipients;
    }
    
    @SuppressWarnings("deprecation")
    public static Bitmap addPicture(Context context, Uri uri) {
        if (uri != null) {
            Bitmap resizeBmp = null;
            BitmapFactory.Options option = new BitmapFactory.Options( );
            option.inSampleSize = 4;
            option.inPurgeable = true;
            option.inDither = true;
            String dataPath = getPathFromUri(context, uri);
            resizeBmp = BitmapFactory.decodeFile(dataPath, option);
            
            while (true) {
                if ((resizeBmp.getHeight( ) * resizeBmp.getWidth( ) * 4 / (1024)) > 1024) {
                    resizeBmp = BitmapFactory.decodeFile(getPathFromUri(context, uri), option);
                    option.inSampleSize++;
                }
                else break;
            }
            
            return resizeBmp;
        }
        return null;
    }
    
    public static byte[] addVideo(Context context, Uri uri) {
        if (uri != null) {
            try {
                String videoPath = getPathFromUri(context, uri);
                
                FileInputStream fin = null;
                fin = new FileInputStream(videoPath);
                BufferedInputStream bis = new BufferedInputStream(fin);
                DataInputStream dis = new DataInputStream(bis);
                byte fileContent[] = toByteArray(dis);
                
                return fileContent;
            }
            catch (IOException io_e) {
                // TODO: handle error
            }
        }
        return null;
    }
    
    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream( );
        copy(in, out);
        return out.toByteArray( );
    }
    
    public static long copy(InputStream from, OutputStream to) throws IOException {
        // TODO Auto-generated method stub
        byte[] buf = new byte[4000];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) break;
            
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }
    
    public static Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }
    

    public static Uri createSaveCropFile(){
        Uri uri;
        String url = "tmp_" + String.valueOf(System.currentTimeMillis()) + ".jpg";
        uri = Uri.fromFile(new File(Environment.getExternalStorageDirectory(), url));
        return uri;
    }
    
    public static File getImageFile(Context context, Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        if (uri == null) {
        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        
        Cursor mCursor = context.getContentResolver().query(uri, projection, null, null, 
        MediaStore.Images.Media.DATE_MODIFIED + " desc");
        if(mCursor == null || mCursor.getCount() < 1) {
        return null; // no cursor or no record
        }
        int column_index = mCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        mCursor.moveToFirst();
        
        String path = mCursor.getString(column_index);
        
        if (mCursor !=null ) {
        mCursor.close();
        mCursor = null;
        }
        
        return new File(path);
    }
    
    public static boolean copyFile(File srcFile, File destFile) {
        boolean result = false;
        
        try {
            InputStream in = new FileInputStream(srcFile);
            try {
                result = copyToFile(in, destFile);
            } finally{
                in.close();
            }
        } catch (IOException e) {
            result = false;
        }
            
        return result;
    }
    

    private static boolean copyToFile(InputStream inputStream, File destFile) {
        try {
            OutputStream out = new FileOutputStream(destFile);
            try {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
            } finally {
                out.close();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    public static Bitmap resizeBitmap(Context context, Uri uri){
        BitmapFactory.Options option = new BitmapFactory.Options();
        option.inSampleSize = 1;
        option.inDither = true;
        String dataPath = Utils.getPathFromUri(context, uri);
        Bitmap resizeBmp = BitmapFactory.decodeFile(dataPath, option);
        
        while(true)
        {
            if((resizeBmp.getHeight() * resizeBmp.getWidth() * 4) > 10485760)    
            {
                resizeBmp = BitmapFactory.decodeFile(Utils.getPathFromUri(context, uri), option);
                option.inSampleSize = option.inSampleSize + 2;
            }
            else
                break;
        }
        
        return resizeBmp;
    }
}
