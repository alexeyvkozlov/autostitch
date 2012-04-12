/*
* Copyright (c) 2011-2012, Peter Abeles. All Rights Reserved.
*
* This file is part of BoofCV (http://boofcv.org).
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.cs482group.autostitch;

import boofcv.abst.feature.associate.GeneralAssociation;
import boofcv.abst.feature.describe.DescribeRegionPoint;
import boofcv.abst.feature.detect.interest.InterestPointDetector;
import boofcv.alg.feature.associate.ScoreAssociateEuclideanSq;
import boofcv.alg.geo.AssociatedPair;
import boofcv.alg.sfm.robust.DistanceHomographySq;
import boofcv.alg.sfm.robust.GenerateHomographyLinear;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.describe.FactoryDescribeRegionPoint;
import boofcv.factory.feature.detect.interest.FactoryInterestPoint;
import boofcv.numerics.fitting.modelset.ModelMatcher;
import boofcv.numerics.fitting.modelset.ransac.SimpleInlierRansac;
import boofcv.struct.FastQueue;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDescQueue;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageSingleBand;
import georegression.struct.homo.Homography2D_F64;
import georegression.struct.point.Point2D_F64;

//import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.FileOutputStream;

import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.content.ContentResolver;


public class AutoStitchEngine {
	
	private static final String TAG = "AutoStitchEngine";
	private static ASHomographyStitchPanel hsp;
	
	/**
	* Using abstracted code, find a transform which minimizes the difference between corresponding features
	* in both images. This code is completely model independent and is the core algorithms.
	*/
	@SuppressWarnings("rawtypes")
	public static<T extends ImageSingleBand> Homography2D_F64 computeTransform( T imageA , T imageB ,
		InterestPointDetector<T> detector ,
		DescribeRegionPoint<T> describe ,
		GeneralAssociation<TupleDesc_F64> associate ,
		ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher ) {
		
		// see if the detector has everything that the describer needs
		if( describe.requiresOrientation() && !detector.hasOrientation() )
			throw new IllegalArgumentException("Requires orientation be provided.");
		if( describe.requiresScale() && !detector.hasScale() )
			throw new IllegalArgumentException("Requires scale be provided.");
		
		// get the length of the description
		int descriptionDOF = describe.getDescriptionLength();
		
		List<Point2D_F64> pointsA = new ArrayList<Point2D_F64>();
		FastQueue<TupleDesc_F64> descA = new TupleDescQueue(descriptionDOF,true);
		List<Point2D_F64> pointsB = new ArrayList<Point2D_F64>();
		FastQueue<TupleDesc_F64> descB = new TupleDescQueue(descriptionDOF,true);
		
		// extract feature locations and descriptions from each image
		describeImage(imageA, detector, describe, pointsA, descA);
		describeImage(imageB, detector, describe, pointsB, descB);
		
		// Associate features between the two images
		associate.associate(descA,descB);
		
		// create a list of AssociatedPairs that tell the model matcher how a feature moved
		FastQueue<AssociatedIndex> matches = associate.getMatches();
		List<AssociatedPair> pairs = new ArrayList<AssociatedPair>();
		
		for( int i = 0; i < matches.size(); i++ ) {
			AssociatedIndex match = matches.get(i);
			
			Point2D_F64 a = pointsA.get(match.src);
			Point2D_F64 b = pointsB.get(match.dst);
			
			pairs.add( new AssociatedPair(a,b,false));
		}
		
		// find the best fit model to describe the change between these images
		if( !modelMatcher.process(pairs) )
			throw new RuntimeException("Model Matcher failed!");
		
		// return the found image transform
		return modelMatcher.getModel();
	}
	
	/**
	* Detects features inside the two images and computes descriptions at those points.
	*/
	@SuppressWarnings("rawtypes")
	private static <T extends ImageSingleBand> void describeImage(T image,
	InterestPointDetector<T> detector,
	DescribeRegionPoint<T> describe,
	List<Point2D_F64> points,
	FastQueue<TupleDesc_F64> descs) {
		
		detector.detect(image);
		describe.setImage(image);
		
		descs.reset();
		TupleDesc_F64 desc = descs.pop();
		for( int i = 0; i < detector.getNumberOfFeatures(); i++ ) {
			// get the feature location info
			Point2D_F64 p = detector.getLocation(i);
			double yaw = detector.getOrientation(i);
			double scale = detector.getScale(i);
			
			// extract the description and save the results into the provided description
			if( describe.process(p.x,p.y,yaw,scale,desc) != null ) {
				points.add(p.copy());
				desc = descs.pop();
			}
		}
		// remove the last element from the queue, which has not been used.
		descs.removeTail();
	}
	
	/**
	* Given two input images create and display an image where the two have been overlayed on top of each other.
	*/
	@SuppressWarnings("rawtypes")
	public static <T extends ImageSingleBand> void stitch( Bitmap imageA , Bitmap imageB ,
	Class<T> imageType ) {
		
		// need to replace ConvertBufferedImage.convertFromSingle
		// with BoofcvAdaptor.convertFrom(..)
		//T inputA = ConvertBufferedImage.convertFromSingle(imageA, null, imageType);
		//T inputB = ConvertBufferedImage.convertFromSingle(imageB, null, imageType);
		
		@SuppressWarnings("unchecked")
		T inputA = (T) BoofcvAdaptor.convertFrom(imageA, null);
		@SuppressWarnings("unchecked")
		T inputB = (T) BoofcvAdaptor.convertFrom(imageB, null);
		
		// Detect using the standard SURF feature descriptor and describer
		InterestPointDetector<T> detector = FactoryInterestPoint.fastHessian(1, 2, 400, 1, 9, 4, 4);
		DescribeRegionPoint<T> describe = FactoryDescribeRegionPoint.surf(true,imageType);
		
		/* 
		 * KNN
		 * -----------------------------------------------------
		 * FactoryAssociation returns the GeneralAssociation class that will
		 * find the closest descriptor vectors. These vectors are passed in
		 * to the class in the computeTransform(...) function above
		 * To replace with KNN
		 * 1. Extend the class the implements the greedy algorithm
		 * 2. Write the new KNN alg to return a GeneralAssociation class that implements it
		 * 3. rewrite the lines of code below
		 */
		// This is the GREEDY method for finding neighbors
		// Need to replace FactoryAssociation.greedy(...) with KNN
		GeneralAssociation<TupleDesc_F64> associate = FactoryAssociation.greedy(new ScoreAssociateEuclideanSq(),2,-1,true);
		
		// fit the images using a homography. This works well for rotations and distant objects.
		GenerateHomographyLinear modelFitter = new GenerateHomographyLinear();
		DistanceHomographySq distance = new DistanceHomographySq();
		int minSamples = modelFitter.getMinimumPoints();
		
		
		/*
		 * RANSAC
		 * -------------------------------------
		 * SimpleInlierRansac implements RANSAC and extends ModelMatcher
		 * For image stitching we need a model matcher
		 * To replace
		 * 1. extend SimpleInlierRansac
		 * 2. write your own RANSAC implementation
		 */
		// model matcher implements RANSAC
		ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher =
		new SimpleInlierRansac<Homography2D_F64,AssociatedPair>(123,modelFitter,distance,60,minSamples,30,1000,9);
		
		try {
			// this throws exception:
			Homography2D_F64 H = computeTransform(inputA, inputB, detector, describe, associate, modelMatcher);
			hsp = new ASHomographyStitchPanel(0.5,inputA.width,inputA.height);
			hsp.configure(imageA,imageB,H);
		} catch (Exception e) {
			Log.e(TAG, "Compute Transfrom failed");
		}
		
		// these custom panels probably won't work in Android
		// shouldn't use them or ShowImages class
		// need to replace panel.configure with BoofcvAdaptor.configure(..)
		
		//HomographyStitchPanel panel = new HomographyStitchPanel(0.5,inputA.width,inputA.height);
		//panel.configure(imageA,imageB,H);
		//ShowImages.showWindow(panel,"Stitched Images");
	}

	public void panoramaStitch(ArrayList<Uri> imgList, ContentResolver cr) {
		
    	try {
    		int index = 0;
    		
    		if(imgList.size()<2) {
    			Log.e(TAG, "Less than 2 images: can't stitch 1 image with itself");
    			return;
    		}
    		
    		// load 1st image in list -> working image
    		Bitmap imageA = MediaStore.Images.Media.getBitmap(cr, imgList.get(0));
    		do {
    			index++;
    			// load the next image to stitch with the working image
    			Bitmap imageB = MediaStore.Images.Media.getBitmap(cr, imgList.get(index));
    			// stitch working image and new image
    			AutoStitchEngine.stitch(imageA, imageB, ImageFloat32.class);
    			// recycle the old working image
    			imageA.recycle();
    			imageA = null;
    			// load the new working image
    			imageA = hsp.getOutput();
    			// recycle the old 'new image'
    			imageB.recycle();
    		} while (index < imgList.size());
    		
    		
    	} catch (Exception e) {
    		Log.e(TAG, e.getMessage());
    	}
		
	}
	
	public void saveImage(Bitmap image) {
		try {
			image.compress(Bitmap.CompressFormat.JPEG, 90, new FileOutputStream("/sdcard/" + AutoStitchEngine.getNewImageName()));
		} catch (Exception e) {
			Log.e(TAG, "saving image failed: " + e.getMessage());
		}
	}
	
    public static String getNewImageName() {
    	// standard image naming scheme
    	return ("IMG_" + (new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())) + ".jpg");
    }
	
	public static void main( String args[] ) {
		/*
		 * The image loading process for Android is done with URIs
		 * need to remove UtilImageIO and replace with correct code
		 */
		
		/*// original code
		BufferedImage imageA,imageB;
		imageA = UtilImageIO.loadImage("../data/evaluation/stitch/mountain_rotate_01.jpg");
		imageB = UtilImageIO.loadImage("../data/evaluation/stitch/mountain_rotate_03.jpg");
		stitch(imageA,imageB, ImageFloat32.class);
		
		imageA = UtilImageIO.loadImage("../data/evaluation/stitch/kayak_01.jpg");
		imageB = UtilImageIO.loadImage("../data/evaluation/stitch/kayak_03.jpg");
		stitch(imageA,imageB, ImageFloat32.class);
		
		imageA = UtilImageIO.loadImage("../data/evaluation/scale/rainforest_01.jpg");
		imageB = UtilImageIO.loadImage("../data/evaluation/scale/rainforest_02.jpg");
		stitch(imageA,imageB, ImageFloat32.class);
		*/
		
		/*
		 * The order of implementation is:
		 * 1. KNN
		 * 2. RANSAC
		 * 3. Blending
		 * 4. Projection
		 */
		
		/*
		 * KNN and RANSAC sections are found above in the stitch(...) function
		 */
		
		/*
		 * BLENDING CODE
		 * --------------------------------------
		 * The images are stored in both rgb arrays in MultiSpectral and gray
		 * scale in ImageSingleBand.
		 * BoofCV contains classes for applying filters and performing operations
		 * like applying Gausians and finding derivatives
		 * 
		 */
		
		/* 
		 *  PROJECTION / HOMOGRAPHY
		 *  --------------------------------------
		 *  the homography computed above is to map image A and B to panel C
		 *  the code for this is here:
		 *  https://github.com/lessthanoptimal/BoofCV/blob/master/main/visualize/src/boofcv/gui/image/HomographyStitchPanel.java
		 *  --------------------------------------
		 *  the source code above is a good example of how to map points in
		 *  two images to a common canvas
		 * 
		 */
	}
}