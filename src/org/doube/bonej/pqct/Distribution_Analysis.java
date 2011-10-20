package org.doube.bonej.pqct;

import ij.*;
import ij.text.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;						//Calibration
import java.util.*;							//Vector
import ij.plugin.filter.*;
import org.doube.bonej.pqct.analysis.*;		//Analysis stuff..
import org.doube.bonej.pqct.selectroi.*;	//ROI selection..
import org.doube.bonej.pqct.io.*;			//image data 
import java.awt.image.*;					//Creating the result BufferedImage...
import java.awt.*;							//Image, component for debugging...
import javax.swing.JPanel;					//createImage for debugging...
import ij.plugin.filter.Info;
import ij.io.*;

/*
	This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

	N.B.  the above text was copied from http://www.gnu.org/licenses/gpl.html
	unmodified. I have not attached a copy of the GNU license to the source...

	ImageJ density distribution analysis plugin
    Copyright (C) 2011 Timo Rantalainen
*/


public class Distribution_Analysis implements PlugInFilter {
	ImagePlus imp;

	int sectorWidth;
	boolean cOn;
	boolean dOn;
	String resultString;
	String imageInfo;
	double resolution;
	double fatThreshold;
	double areaThreshold;
	double BMDThreshold;
	double scalingFactor;
	double constant;	
	boolean flipDistribution;
	boolean manualRotation;
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		//return DOES_32;
		return DOES_ALL;
	}

	/*
	//For debugging
	public BufferedImage getMyImage(double[] imageIn,int width, int height, double minimum, double maximum, Component imageCreator) {
		int[] image = new int[width*height];
		int pixel;
		for (int x = 0; x < width*height;x++) {
			pixel = (int) (((((double) (imageIn[x] -minimum))/((double)(maximum-minimum)))*255.0));
			image[x]= 255<<24 | pixel <<16| pixel <<8| pixel; 
		}
		Image imageToDraw = new JPanel().createImage(new MemoryImageSource(width,height,image,0,width));
		imageToDraw= imageToDraw.getScaledInstance(1000, -1, Image.SCALE_SMOOTH);
		BufferedImage bufferedImage = (BufferedImage) imageCreator.createImage(imageToDraw.getWidth(null), imageToDraw.getHeight(null));
		Graphics2D gbuf = bufferedImage.createGraphics();
		gbuf.drawImage(imageToDraw, 0, 0,null);
		return bufferedImage;
	}
	*/
	/*
	//For Debugging
	TextWindow checkWindow = new TextWindow(new String("DICOM info"),new String(""),800,400);
	checkWindow.append((String) imp.getProperty("Info"));
	*/
	
	public void run(ImageProcessor ip) {
		imageInfo = new Info().getImageInfo(imp,imp.getChannelProcessor());
		/*Check image calibration*/
		Calibration calibration = imp.getCalibration();
		double[] calibrationCoefficients;
		if (getInfoProperty(imageInfo,"Stratec File") == null){
			calibrationCoefficients = calibration.getCoefficients();
		} else {
			calibrationCoefficients = new double[2];
			calibrationCoefficients[0] = -322.0;
			calibrationCoefficients[1] = 1.724;
		}
		sectorWidth = 10;
		cOn = true;
		dOn = true;
		resolution = 1.0;
		if (getInfoProperty(imageInfo,"Pixel Spacing")!= null){
			String temp = getInfoProperty(imageInfo,"Pixel Spacing");
			if (temp.indexOf("\\")!=-1){
				temp = temp.substring(0,temp.indexOf("\\"));
			}
			resolution = Double.valueOf(temp);
		}
		//Get parameters for scaling the image and for thresholding
		GenericDialog dialog = new GenericDialog("Analysis parameters");
		dialog.addNumericField("Fat threshold", 40.0, 4, 8, null);
		dialog.addNumericField("Area threshold", 550.0, 4, 8, null);
		dialog.addNumericField("BMD threshold", 690.0, 4, 8, null);
		dialog.addNumericField("Scaling_coefficient (slope)", calibrationCoefficients[1], 4, 8, null);
		dialog.addNumericField("Scaling_constant (intercept)",calibrationCoefficients[0], 4, 8, null);
		dialog.addNumericField("In-plane_pixel_size [mm]", resolution, 4, 8, null);
		//Get ROI selection
		String[] choiceLabels = {"Bigger","Smaller","Left","Right","Top","Bottom","Central","Peripheral"};
		dialog.addChoice("Roi_selection", choiceLabels, "Bigger"); 
		String[] rotationLabels = {"According_to_Imax/Imin","Furthest_point"};
		dialog.addChoice("Rotation_selection", rotationLabels, "According_to_Imax/Imin");
		dialog.addCheckbox("Analyse_cortical_results",true);
		dialog.addCheckbox("Analyse_density_distribution",true);
		dialog.addCheckbox("Allow_cleaving",false);
		dialog.addCheckbox("Cleave_retain_smaller",false);
		dialog.addCheckbox("Suppress_result_image",false);
		dialog.addCheckbox("Limit_ROI_search_to_manually_selected",false);
		dialog.addCheckbox("Set_distribution_results_rotation_manually",false);
		dialog.addNumericField("Manual_rotation_[+-_180_deg]", 0.0, 4, 8, null);
		dialog.addCheckbox("Flip_distribution_results",false);
		dialog.addCheckbox("Save_visual_result_image_on_disk",false);
		dialog.addStringField("Image_save_path",Prefs.getDefaultDirectory(),40);
		dialog.showDialog();
		
		if (dialog.wasOKed()){ //Stop in case of cancel..
			fatThreshold				= dialog.getNextNumber();
			areaThreshold				= dialog.getNextNumber();
			BMDThreshold				= dialog.getNextNumber();
			scalingFactor				= dialog.getNextNumber();
			constant					= dialog.getNextNumber();
			resolution					= dialog.getNextNumber();
			String roiChoice			= dialog.getNextChoice();
			String rotationChoice		= dialog.getNextChoice();
			cOn							= dialog.getNextBoolean();
			dOn							= dialog.getNextBoolean();
			boolean allowCleaving		= dialog.getNextBoolean();
			boolean cleaveReturnSmaller = dialog.getNextBoolean();
			boolean suppressImages		= dialog.getNextBoolean();
			boolean manualRoi			= dialog.getNextBoolean();
			manualRotation				= dialog.getNextBoolean();
			double manualAlfa			= dialog.getNextNumber()*Math.PI/180.0;
			flipDistribution			= dialog.getNextBoolean();
			boolean saveImageOnDisk		= dialog.getNextBoolean();
			String imageSavePath 		= dialog.getNextString();
			ScaledImageData scaledImageData;
			
			String imageName;
			if (getInfoProperty(imageInfo,"File Name")!= null){
				imageName = getInfoProperty(imageInfo,"File Name");
			}else{
				if(imp.getImageStackSize() == 1){
					imageName = getInfoProperty(imageInfo,"Title");
				}else{
					imageName = imageInfo.substring(0,imageInfo.indexOf("\n"));
				}
			}

			short[] tempPointer = (short[]) imp.getProcessor().getPixels();			
			int[] unsignedShort = new int[tempPointer.length];			
			if (getInfoProperty(imageInfo,"Stratec File") == null){ //For unsigned short Dicom, which appears to be the default ImageJ DICOM...
				for (int i=0;i<tempPointer.length;++i){unsignedShort[i] = 0x0000FFFF & (int) (tempPointer[i]);}
			}else{
				float[] floatPointer = (float[]) imp.getProcessor().toFloat(1,null).getPixels();
				for (int i=0;i<tempPointer.length;++i){unsignedShort[i] = (int) (floatPointer[i] - Math.pow(2.0,15.0));}
			}
			scaledImageData = new ScaledImageData(unsignedShort, imp.getWidth(), imp.getHeight(),resolution, scalingFactor, constant,3);	//Scale and 3x3 median filter the data
			
			/* 
			//For debugging
			BufferedImage bi2 = getMyImage(scaledImageData.scaledImage,scaledImageData.width,scaledImageData.height,scaledImageData.minimum,scaledImageData.maximum,dialog.getParent());
			new ImagePlus("Visual results",bi2).show();
			*/
			
			ImageAndAnalysisDetails imageAndAnalysisDetails = new ImageAndAnalysisDetails(scalingFactor, constant,fatThreshold, 
															areaThreshold,BMDThreshold,roiChoice,rotationChoice,choiceLabels,
															allowCleaving,cleaveReturnSmaller,manualRoi,manualRotation,manualAlfa,flipDistribution);
			SelectROI roi = new SelectROI(scaledImageData, imageAndAnalysisDetails,imp);

			ResultsTable resultsTable = Analyzer.getResultsTable();
			if (resultsTable == null) {resultsTable = new ResultsTable();}
			if (resultsTable.getCounter() == 0){writeHeader(resultsTable);}
			resultsTable.incrementCounter();
			resultsTable.updateResults();
			printResults(resultsTable);
			ImagePlus resultImage = null;
			if (cOn ){
				CorticalAnalysis cortAnalysis =new CorticalAnalysis(roi);
				printCorticalResults(resultsTable,cortAnalysis);
				if(!dOn){
					BufferedImage bi = roi.getMyImage(roi.scaledImage,roi.sieve,roi.width,roi.height,roi.minimum,roi.maximum,dialog.getParent());
					resultImage = new ImagePlus("Visual results",bi);
				}
				
			}
			if (dOn){
				AnalyzeROI analyzeRoi = new AnalyzeROI(roi,imageAndAnalysisDetails);
				BufferedImage bi = roi.getMyImage(roi.scaledImage,analyzeRoi.marrowCenter,analyzeRoi.pind,analyzeRoi.R,analyzeRoi.R2,analyzeRoi.Theta2,roi.width,roi.height,roi.minimum,roi.maximum,dialog.getParent()); // retrieve image
				resultImage = new ImagePlus("Visual results",bi);
			}
			
			if (!suppressImages && resultImage!= null){
				resultImage.show();
			}
			if (saveImageOnDisk && resultImage!= null){
				FileSaver fSaver = new FileSaver(resultImage);
				fSaver.saveAsPng(imageSavePath+"/"+imageName+".png"); 
			}
			resultsTable.updateResults();
			resultsTable.show("Results");
		}
	}
	
	void writeHeader(ResultsTable resultsTable){
		String[] propertyNames = {"File Name","Patient's Name","Patient ID","Patient's Birth Date","Acquisition Date","Pixel Spacing"};
		String[] parameterNames = {"Fat Threshold","Area Threshold","BMD Threshold","Scaling Coefficient","Scaling Constant"};
		for (int i = 0;i<propertyNames.length;++i){
			resultsTable.getFreeColumn(propertyNames[i]);
		}
		for (int i = 0;i<parameterNames.length;++i){
			resultsTable.getFreeColumn(parameterNames[i]);
		}

	}
	
	String getInfoProperty(String properties,String propertyToGet){
		String toTokenize = properties;
		StringTokenizer st = new StringTokenizer(toTokenize,"\n");
		String currentToken = null;
		while (st.hasMoreTokens() ) {
			currentToken = st.nextToken();
			if (currentToken.indexOf(propertyToGet) != -1){break;}
		}
		if (currentToken.indexOf(propertyToGet) != -1){
			StringTokenizer st2 = new StringTokenizer(currentToken,":");
			String token2 = null;
			while (st2.hasMoreTokens()){
				token2 = st2.nextToken();
			}
			return token2.trim();
		}
		return null;
	}
	
	void printCorticalResults(TextWindow textWindow,CorticalAnalysis cortAnalysis){
		resultString +=Double.toString(cortAnalysis.BMD)+"\t";
		resultString +=Double.toString(cortAnalysis.AREA)+"\t";
		resultString +=Double.toString(cortAnalysis.SSI)+"\t";
		resultString +=Double.toString(cortAnalysis.ToD)+"\t";
		resultString +=Double.toString(cortAnalysis.ToA)+"\t";
		resultString +=Double.toString(cortAnalysis.BSId)+"\t";
	}
	
	void printResults(ResultsTable resultsTable){
		String[] propertyNames = {"File Name","Patient's Name","Patient ID","Patient's Birth Date","Acquisition Date","Pixel Spacing"};
		String[] parameterNames = {"Fat Threshold","Area Threshold","BMD Threshold","Scaling Coefficient","Scaling Constant"};
		String[] parameters = {Double.toString(fatThreshold),Double.toString(areaThreshold),Double.toString(BMDThreshold),Double.toString(scalingFactor),Double.toString(constant)};

		if (imp != null){
			if (getInfoProperty(imageInfo,"File Name")!= null){
				resultsTable.addLabel(propertyNames[0],getInfoProperty(imageInfo,"File Name"));
			}else{
				if(imp.getImageStackSize() == 1){
					resultsTable.addLabel(propertyNames[0],getInfoProperty(imageInfo,"Title"));
				}else{
					resultsTable.addLabel(propertyNames[0],imageInfo.substring(0,imageInfo.indexOf("\n")));
				}
			}
			for (int i = 1;i<propertyNames.length;++i){
				resultsTable.addLabel(propertyNames[i],getInfoProperty(imageInfo,propertyNames[i]));
			}
		}
		
		for (int i = 0;i<parameterNames.length;++i){
			resultsTable.addLabel(parameterNames[i],parameters[i]);
		}
		
	}
	
	void printCorticalResults(ResultsTable resultsTable,CorticalAnalysis cortAnalysis){
		resultsTable.addValue("CoD [mg/cm3]",cortAnalysis.BMD);
		resultsTable.addValue("CoA [mm2]",cortAnalysis.AREA);
		resultsTable.addValue("SSI [mm3]",cortAnalysis.SSI);
		resultsTable.addValue("ToD [mg/cm3]",cortAnalysis.ToD);
		resultsTable.addValue("ToA[mm2]",cortAnalysis.ToA);
		resultsTable.addValue("BSId[g2/cm4]",cortAnalysis.BSId);
	}
	
	void printDistributionResults(TextWindow textWindow,AnalyzeROI analyzeRoi){
		resultString += Double.toString(analyzeRoi.alfa*180/Math.PI)+"\t";
		resultString += Boolean.toString(manualRotation)+"\t";
		resultString += Boolean.toString(flipDistribution)+"\t";
		
		for (int pp = 0;pp<((int) 360/sectorWidth);pp++){
			resultString += analyzeRoi.endocorticalRadii[pp]+"\t";
		}
		for (int pp = 0;pp<((int) 360/sectorWidth);pp++){
			resultString += analyzeRoi.pericorticalRadii[pp]+"\t";
		}
		//Cortex BMD values			
		for (int pp = 0;pp<((int) 360/sectorWidth);pp++){
			resultString += analyzeRoi.endoCorticalBMDs[pp]+"\t";
		}
		for (int pp = 0;pp<((int) 360/sectorWidth);pp++){
			resultString += analyzeRoi.midCorticalBMDs[pp]+"\t";
		}
		for (int pp = 0;pp<((int) 360/sectorWidth);pp++){
			resultString += analyzeRoi.periCorticalBMDs[pp]+"\t";
		}
	}
}
