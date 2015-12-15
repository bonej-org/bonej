package org.doube.util;

import java.awt.*;
import java.util.ArrayList;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

/**
 * Do useful things with ImageJ's ROI Manager
 * 
 * @author Michael Doube
 * */
public class RoiMan {
    public static final int NO_SLICE_NUMBER = -1;

	/**
	 * Get the calibrated 3D coordinates of point ROIs from the ROI manager
	 * 
	 * @param imp
	 * @param roiMan
	 * @return double[n][3] containing n (x, y, z) coordinates or null if there
	 *         are no points
	 */
	public static double[][] getRoiManPoints(ImagePlus imp, RoiManager roiMan) {
		Calibration cal = imp.getCalibration();
		double vW = cal.pixelWidth;
		double vH = cal.pixelHeight;
		double vD = cal.pixelDepth;
		int nPoints = 0;
		Roi[] roiList = roiMan.getRoisAsArray();
		for (int i = 0; i < roiMan.getCount(); i++) {
			Roi roi = roiList[i];
			if (roi.getType() == 10) {
				nPoints++;
			}
		}
		if (nPoints == 0)
			return null;
		double[][] dataPoints = new double[nPoints][3];
		int j = 0;
		for (int i = 0; i < roiMan.getCount(); i++) {
			Roi roi = roiList[i];
			if (roi.getType() == 10) {
				Rectangle xy = roi.getBounds();
				dataPoints[j][0] = xy.getX() * vW;
				dataPoints[j][1] = xy.getY() * vH;
				dataPoints[j][2] = roi.getPosition() * vD;
				j++;
			}
		}
		return dataPoints;
	}

	/**
	 * Return a list of ROIs that are active in the given slice, s. ROIs without
	 * a slice number are assumed to be active in all slices.
	 *
	 * @param roiMan
	 * @param stack
	 * @param s
	 * @return A list of active ROIs on the slice.
     *         Returns an empty list if roiMan == null or stack == null
     *         Returns an empty list if slice number is out of bounds
	 */
	public static ArrayList<Roi> getSliceRoi(RoiManager roiMan, ImageStack stack, int s) {
		ArrayList<Roi> roiList = new ArrayList<>();

		if (roiMan == null || stack == null) {
			return roiList;
		}

        if (s < 1 || s > stack.getSize()) {
            return roiList;
        }

		Roi[] rois = roiMan.getRoisAsArray();
		for (Roi roi : rois) {
            String roiName = roi.getName();
            if (roiName == null) {
                continue;
            }
			int sliceNumber = roiMan.getSliceNumber(roiName);
            int roiPosition = roi.getPosition();
			if (sliceNumber == s || sliceNumber == NO_SLICE_NUMBER || roiPosition == s) {
                roiList.add(roi);
            }
		}
		return roiList;
	}

	/**
	 * Find the x, y and z limits of the ROIs in the ROI Manager
	 * 
	 * @param roiMan
	 * @param stack
     * @return int[] containing x min, x max, y min, y max, z min and z max.
     *         Returns null if roiMan == null or if roiMan.getCount() == 0
     *         Returns null if stack == null
	 *         If any of the ROIs contains no slice information, z min is set to
	 *         1 and z max is set to Integer.MAX_VALUE
	 */
	public static int[] getLimits(RoiManager roiMan, ImageStack stack) {
		if (roiMan == null || roiMan.getCount() == 0) {
            return null;
        }

        if (stack == null) {
            return null;
        }

        final int LAST_SLIDE = stack.getSize();
        final int DEFAULT_Z_MIN = 1;
        final int DEFAULT_Z_MAX = LAST_SLIDE;

		int xmin = Integer.MAX_VALUE;
		int xmax = 0;
		int ymin = Integer.MAX_VALUE;
		int ymax = 0;
		int zmin = DEFAULT_Z_MAX;
		int zmax = DEFAULT_Z_MIN;
		boolean noZroi = false;
		boolean noValidRois = true;
        Roi[] rois = roiMan.getRoisAsArray();


		for (Roi roi : rois) {
			Rectangle r = roi.getBounds();
            boolean valid = getSafeRoiBounds(r, stack.getWidth(), stack.getHeight());

            if (!valid) {
                continue;
            }

			xmin = Math.min(r.x, xmin);
			xmax = Math.max(r.x + r.width, xmax);
			ymin = Math.min(r.y, ymin);
			ymax = Math.max(r.y + r.height, ymax);

            int slice = roiMan.getSliceNumber(roi.getName());
			if (slice >= 1 && slice <= LAST_SLIDE) {
				zmin = Math.min(slice, zmin);
				zmax = Math.max(slice, zmax);
                noValidRois = false;
			} else if (isActiveOnAllSlices(slice)) {
                noZroi = true; // found a ROI with no Z info
                noValidRois = false;
            }
		}

        if (noValidRois) {
            return null;
        }

		if (noZroi) {
			int[] limits = { xmin, xmax, ymin, ymax, DEFAULT_Z_MIN, DEFAULT_Z_MAX };
			return limits;
		} else {
			int[] limits = { xmin, xmax, ymin, ymax, zmin, zmax };
			return limits;
		}
	}

    public static boolean isActiveOnAllSlices(RoiManager roiManager, Roi roi) {
        if (roi.getName() == null) {
            return false;
        }

        int sliceNumber = roiManager.getSliceNumber(roi.getName());
        return isActiveOnAllSlices(sliceNumber);
    }

    /**
     * Crops the given rectangle to the area [0, 0, width, height]
     *
     * @param bounds
     *            The rectangle to be fitted
     * @param width
     *            Maximum width of the rectangle
     * @param height
     *            Maximum height of the rectangle
     * @return false if the height or width of the fitted rectangle is 0
     *         (Couldn't be cropped inside the area).
     */
    public static boolean getSafeRoiBounds(Rectangle bounds, int width, int height) {
        int xMin = clamp(bounds.x, 0, width);
        int xMax = clamp(bounds.x + bounds.width, 0, width);
        int yMin = clamp(bounds.y, 0, height);
        int yMax = clamp(bounds.y + bounds.height, 0, height);
        int newWidth = xMax - xMin;
        int newHeight = yMax - yMin;

        bounds.setBounds(xMin, yMin, newWidth, newHeight);

        return newWidth > 0 && newHeight > 0;
    }

    /**
	 * Crop a stack to the limits of the ROIs in the ROI Manager and optionally
	 * fill the background with a single pixel value.
	 * 
	 * @param roiMan
	 *            ROI Manager containing ROIs
	 * @param stack
	 *            input stack
	 * @param fillBackground
	 *            if true, background will be set to value
	 * @param fillValue
	 *            value to set background to
	 * @param padding
	 *            empty pixels to pad faces of cropped stack with
	 * @return cropped copy of input stack
	 */
	public static ImageStack cropStack(RoiManager roiMan, ImageStack stack,
			boolean fillBackground, int fillValue, int padding) {
		int[] limits = getLimits(roiMan, stack);
		final int xmin = limits[0];
		final int xmax = limits[1];
		final int ymin = limits[2];
		final int ymax = limits[3];
		final int zmin = limits[4];
		final int zmax = (limits[5] == Integer.MAX_VALUE) ? stack.getSize()
				: limits[5];
		// target stack dimensions
		final int w = xmax - xmin + 2 * padding;
		final int h = ymax - ymin + 2 * padding;
		final int d = zmax - zmin + 2 * padding;

		// offset that places source stack in coordinate frame
		// of target stack (i.e. origin of source stack relative to origin of
		// target stack)
		final int xOff = padding - xmin;
		final int yOff = padding - ymin;
		final int zOff = padding - zmin;

		ImagePlus imp = new ImagePlus("title", stack);
		ImageStack out = new ImageStack(w, h);
		for (int z = 1; z <= d; z++) {
			ImageProcessor ip = imp.getProcessor().createProcessor(w, h);
			final int length = ip.getPixelCount();
			if (z - zOff < 1 || z > stack.getSize()) { // catch out of bounds
				for (int i = 0; i < length; i++)
					ip.set(i, fillValue);
				out.addSlice("padding", ip);
				continue;
			}
			ImageProcessor ipSource = stack.getProcessor(z - zOff);
			if (fillBackground)
				for (int i = 0; i < length; i++)
					ip.set(i, fillValue);
			ArrayList<Roi> rois = getSliceRoi(roiMan, stack, z - zOff);
			for (Roi roi : rois) {
				ipSource.setRoi(roi);
				Rectangle r = roi.getBounds();
				ImageProcessor mask = ipSource.getMask();
				final int rh = r.y + r.height;
				final int rw = r.x + r.width;
				for (int y = r.y; y < rh; y++) {
					final int yyOff = y + yOff;
					for (int x = r.x; x < rw; x++) {
						if (mask == null || mask.get(x - r.x, y - r.y) > 0)
							ip.set(x + xOff, yyOff, ipSource.get(x, y));
					}
				}
			}
			out.addSlice(stack.getSliceLabel(z - zOff), ip);
		}
		return out;
	}

	/**
	 * Remove all ROIs from the ROI manager
	 */
	public static void deleteAll(RoiManager roiMan) {
		Roi[] rois = roiMan.getRoisAsArray();
		for (int i = 0; i < rois.length; i++) {
			if (roiMan.getCount() == 0)
				break;
			roiMan.select(i);
			roiMan.runCommand("delete");
		}
	}

    private static int clamp(int value, int min, int max) {
        if (Integer.compare(value, min) < 0) {
            return min;
        }
        if (Integer.compare(value, max) > 0) {
            return max;
        }
        return value;
    }

    private static boolean isActiveOnAllSlices(int sliceNumber) {
        return sliceNumber == NO_SLICE_NUMBER;
    }
}
