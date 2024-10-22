package com.cs482group.autostitch;

import android.app.Activity;
import android.os.Bundle;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.*;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.os.Environment;
import java.io.File;
import android.util.Log;
//import boofcv.struct.image.ImageFloat32;

import java.util.ArrayList;

import com.cs482group.autostitch.R;

public class AutoStitchActivity extends Activity implements OnClickListener {
    /** Called when the activity is first created. */
	private static final String TAG = "AutoStitchActivity";
	private static final int CAPTURE_IMAGE_REQUEST_CODE = 100;
	private static final int PICKER_IMAGE_REQUEST_CODE = 200;
	public static final int MEDIA_TYPE_IMAGE = 1;
	
	Button btnCapture;
	Button btnSelect;
	Button btnSettings;
	Button btnStitch;
	Button btnOpen;
	Uri imgUri;
	ArrayList<Uri> imgList = new ArrayList<Uri>();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        imgList = new ArrayList<Uri>();
        
        btnCapture = (Button) this.findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(this);
        btnSelect = (Button) this.findViewById(R.id.btnSelect);
        btnSelect.setOnClickListener(this);
        btnSettings = (Button) this.findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(this);
        btnStitch = (Button) this.findViewById(R.id.btnStitch);
        btnStitch.setOnClickListener(this);
        btnOpen = (Button) this.findViewById(R.id.btnOpen);
        btnOpen.setOnClickListener(this);
    }
    
    public void onClick(View v) {
    	int id = v.getId();
    	switch (id) {
    		case R.id.btnCapture: startCameraIntent(); break;
    		case R.id.btnSelect: startPickerIntent(v); break;
    		case R.id.btnSettings: ; break;
    		case R.id.btnStitch: stitchImages(); break;
    		case R.id.btnOpen: 
    			// image open test
    			if( imgList.size()>0 ) {
    				openImage(imgList.get(0)); 
    			} else { 
    				Log.e(TAG,"imgList.size()==0 can't open img");
    			}
    			break;
    		default: Log.e(TAG, "onClick - unknown id - " + id); // error
    	}
    }
    
    // the button stitch should be disabled until images are selected
    public void stitchImages() {
    	
    	Log.d(TAG,"stitchImages() called");
    	
		if( imgList.size() > 0 ){
			// just a test
			// this.createGrayImage(imgList.get(0));
			try {
				// TEST:
				// open an image, draw found features as circles, save new image
				//Log.d(TAG,"loading image");
				//Bitmap image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imgList.get(0));
				
				//Log.d(TAG,"calling saveImageFeatures");
				//AutoStitchEngine.saveImageFeatures(image);
				//AutoStitchEngine.saveImageFeatures(this.getContentResolver(), imgList.get(0));
				long startTime = System.currentTimeMillis();
				AutoStitchEngine ase = new AutoStitchEngine();
				Log.d(TAG,"calling panoramaStitch");
				ase.panoramaStitch(imgList, this.getContentResolver());
				long endTime = System.currentTimeMillis();
				Log.d(TAG,"Execution time is " + (endTime-startTime) + " ms.");
				
				//image.recycle();
				//image = null;
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		} else {
			Log.w(TAG,"no images selected to stitch");
		}
    	
    	// enable progress bars + others
    	// do work
    	
    	
    	// disable btnStitch
    	// clear image list
    	// clean-up
    	// etc
    }
    
    public void openImage(Uri path) {
    	startActivity(new Intent(Intent.ACTION_VIEW, path));
    }
    
    public void startCameraIntent(){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        String imgName = AutoStitchEngine.getNewImageName();
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AutoStitch");
        File img = new File(dir.getPath() + File.separator + imgName);
        
        imgUri = Uri.fromFile(img);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imgUri);

        startActivityForResult(intent, CAPTURE_IMAGE_REQUEST_CODE);
    }
    
    public void startPickerIntent(View v){
    	Intent imgIntent = new Intent(v.getContext(), ImageListActivity.class);
    	Log.d(TAG, "starting ImageListActivity...");
    	try {
    		startActivityForResult(imgIntent, PICKER_IMAGE_REQUEST_CODE);
    	} catch (Exception e) {
    		Log.e(TAG, e.getMessage());
    	}
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	Log.i(TAG,"onActivityResult started (" + requestCode + ", " + resultCode + ")" );
    	
    	if( data == null ){
    		Log.e(TAG,"NULL Intent");
    		return;
    	}
		
        if (requestCode == CAPTURE_IMAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
            	Log.v(TAG,"image captured and saved");
            } else if (resultCode == RESULT_CANCELED) {
            	Log.e(TAG,"User cancelled the image capture");
            } else {
            	Log.e(TAG,"the image capture failed");
            }
        }
       
        if (requestCode == PICKER_IMAGE_REQUEST_CODE) {
        	
        	Log.i(TAG,"image list activity request code");
        	
        	if (data.hasExtra("com.cs482group.autostitch.checked")){
        		Log.i(TAG,"");
        		String[] chkdIds = data.getStringArrayExtra("com.cs482group.autostitch.checked");
        		
        		if( chkdIds == null ){
        			Log.e(TAG,"NULL checked IDs array");
        		}
        		
        		TextView selectedCount = (TextView) this.findViewById(R.id.txtSelectCount);
        		selectedCount.setText(Integer.toString(chkdIds.length));
        		
        		imgList.clear();
        		
        		Uri temp;
        		
        		for(int i=0; i<chkdIds.length; i++) {
        			temp = this.getUriFromId(chkdIds[i]);
        			if( temp == null ) {
        				Log.e(TAG,"NULL Uri");
        			}
        			imgList.add(this.getUriFromId(chkdIds[i]));
        		}
        		
        		// if imgList.size() > 0
        		// ENABLE btnStitch to be pressed
        		
        		
        		Log.d(TAG, "image/uri list created with " + imgList.size() + " images");
        	} else {
        		Log.w(TAG, "onActivityResult did not return anything");
        	}
        }
    	
    }
    
    /*
     * Created for testing purposes
    public void createGrayImage(Uri img) {
    	try {
    		Bitmap bmp = MediaStore.Images.Media.getBitmap(this.getContentResolver(), img);
    		Bitmap gray = this.bitmapToGray(bmp);
    		String msg = "";
    		
    		Log.i(TAG, "The list of pixles: ");
    		
    		for(int i = 0; i<20; i++) {
    			msg = "orig(" + Color.red(bmp.getPixel(i, 0)) + ",";
    			msg = msg + Color.green(bmp.getPixel(i, 0)) + "," + Color.blue(bmp.getPixel(i, 0)) + ") = ";
    			msg = msg + bmp.getPixel(i, 0) + ";";
    			Log.i(TAG, msg);
    			
    			msg = "gray(" + Color.red(gray.getPixel(i, 0)) + ",";
    			msg = msg + Color.green(gray.getPixel(i, 0)) + "," + Color.blue(gray.getPixel(i, 0)) + ") = ";
    			msg = msg + gray.getPixel(i, 0) + ";";
    			Log.i(TAG, msg);
    		}
    		
    		
    		bmp.recycle();
    		//FileOutputStream fos = new FileOutputStream("/sdcard/test.jpg");
    		gray.compress(Bitmap.CompressFormat.JPEG, 90, new FileOutputStream("/sdcard/test3.jpg"));
    	} catch( Exception e ){
    		Log.e(TAG, e.getMessage());
    	}
    }
    */
    
    public Uri getUriFromId(String id) {
    	return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
    }
    
}