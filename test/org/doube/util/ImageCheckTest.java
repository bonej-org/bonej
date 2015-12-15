package org.doube.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import ij.ImagePlus;
import ij.process.ImageStatistics;

/**
 * Unit tests for org.doube.util.ImageCheck class
 *
 * @author Richard Domander
 */
public class ImageCheckTest {
	private static final int BINARY_WHITE = 0xFF;
	private static final int BINARY_BLACK = 0x00;

	@Test
	public void testIsBinaryReturnsFalseIfImageIsNull() throws Exception {
		boolean result = ImageCheck.isBinary(null);
		assertFalse("Null image should not be binary", result);
	}

	@Test
	public void testIsBinaryReturnsFalseIfImageHasWrongType() throws Exception {
		ImagePlus testImage = mock(ImagePlus.class);
		int wrongTypes[] = {ImagePlus.COLOR_256, ImagePlus.COLOR_RGB, ImagePlus.GRAY16, ImagePlus.GRAY32};

		for (int wrongType : wrongTypes) {
			when(testImage.getType()).thenReturn(wrongType);

			boolean result = ImageCheck.isBinary(testImage);
			assertFalse("Only ImagePlus.GRAY8 type should be binary", result);
		}
	}

	@Test
	public void testIsBinaryUsesHistogramCorrectly() throws Exception {
		final int EIGHT_BYTES = 256;

		// more than two colors
		ImageStatistics nonBinaryStats = new ImageStatistics();
		nonBinaryStats.pixelCount = 3;
		nonBinaryStats.histogram = new int[EIGHT_BYTES];
		nonBinaryStats.histogram[BINARY_BLACK] = 1;
		nonBinaryStats.histogram[BINARY_WHITE] = 1;

		ImagePlus testImage = mock(ImagePlus.class);
		when(testImage.getStatistics()).thenReturn(nonBinaryStats);

		boolean result = ImageCheck.isBinary(testImage);
		assertFalse("Image with more than two colors must not be binary", result);

		// wrong two colors
		ImageStatistics wrongBinaryStats = new ImageStatistics();
		wrongBinaryStats.pixelCount = 2;
		wrongBinaryStats.histogram = new int[EIGHT_BYTES];
		wrongBinaryStats.histogram[BINARY_BLACK] = 1;
		wrongBinaryStats.histogram[BINARY_BLACK + 1] = 1;

		when(testImage.getStatistics()).thenReturn(wrongBinaryStats);

		result = ImageCheck.isBinary(testImage);
		assertFalse("Image with wrong two colors (not " + BINARY_BLACK + " & " + BINARY_WHITE + ") must not be binary",
				result);

		// binary colors
		ImageStatistics binaryStats = new ImageStatistics();
		binaryStats.pixelCount = 2;
		binaryStats.histogram = new int[EIGHT_BYTES];
		binaryStats.histogram[BINARY_BLACK] = 1;
		binaryStats.histogram[BINARY_WHITE] = 1;

		when(testImage.getStatistics()).thenReturn(binaryStats);

		result = ImageCheck.isBinary(testImage);
		assertTrue("Image with two colors (" + BINARY_BLACK + " & " + BINARY_WHITE + ") should be binary", result);
	}
}