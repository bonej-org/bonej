package org.doube.util;

import ij.ImagePlus;
import ij.ImageStack;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * Utility methods for working with the {@link Skeletonize3D_} plugin
 *
 * @author Michael Doube
 * @author Mark Hiner
 */
public class SkeletonUtils {

	/**
	 * Gets a medial axis skeleton from a binary imp using a topology-preserving
	 * iterative algorithm
	 *
	 * @param imp
	 *            input image
	 * @return skeletonised image
	 */

	public static ImagePlus getSkeleton(final ImagePlus imp) {
		final ImagePlus imp2 = imp.duplicate();
		final ImageStack stack2 = imp2.getStack();
		final Skeletonize3D_ sk = new Skeletonize3D_();

		// Prepare data
		sk.prepareData(stack2);

		// Compute Thinning
		sk.computeThinImage(stack2);

		// Convert image to binary 0-255
		for (int i = 1; i <= stack2.getSize(); i++)
			stack2.getProcessor(i).multiply(255);

		imp2.setCalibration(imp.getCalibration());
		imp2.setTitle("Skeleton of " + imp.getTitle());
		return imp2;
	}
}
