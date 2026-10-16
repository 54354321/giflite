package com.izumikonata.giflite;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public final class ResultProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException {
        String name=uri.getLastPathSegment();
        if(name==null||!name.matches("gif-[0-9]+\\.gif"))throw new FileNotFoundException();
        File f=new File(getContext().getCacheDir(),name);if(!f.isFile())throw new FileNotFoundException();return f;
    }
    public String getType(Uri uri){return "image/gif";}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File f=file(uri);String[] cols=projection!=null?projection:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++){if(cols[i].equals(OpenableColumns.DISPLAY_NAME))row[i]=f.getName();if(cols[i].equals(OpenableColumns.SIZE))row[i]=f.length();}c.addRow(row);return c;}catch(Exception e){return null;}
    }
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
