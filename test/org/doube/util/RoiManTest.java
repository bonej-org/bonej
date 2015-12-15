package org.doube.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

/**
 * Unit tests for the org.doube.util.RoiMan class
 *
 * @author Richard Domander
 */
public class RoiManTest {
	RoiManager mockRoiMan;
	private static ImagePlus testImage;
	private static ImageStack testStack;

	private final static int TEST_IMAGE_WIDTH = 100;
	private final static int TEST_IMAGE_HEIGHT = 100;
	private final static int TEST_IMAGE_DEPTH = 4;

	@BeforeClass
	public static void oneTimeSetUp() {
		IJ.newImage("testImage", "8-bit", TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT, TEST_IMAGE_DEPTH);
		testImage = IJ.getImage();
		testStack = testImage.getStack();
	}

	@AfterClass
	public static void oneTimeTearDown() {
		if (testImage != null) {
			testImage.flush();
			testImage.close();
			testImage = null;
			testStack = null;
		}
	}

	@Before
	public void setUp() throws Exception {
		mockRoiMan = mock(RoiManager.class);
	}

	@Test
	public void testGetSliceRoiReturnsEmptyListIfRoiManagerIsNull() throws Exception {
		ArrayList<Roi> resultRois = RoiMan.getSliceRoi(null, testStack, 1);
		assertEquals(true, resultRois.isEmpty());
	}

	@Test
	public void testGetSliceRoiReturnsEmptyListIfStackIsNull() throws Exception {
		ArrayList<Roi> resultRois = RoiMan.getSliceRoi(mockRoiMan, null, 1);
		assertEquals(true, resultRois.isEmpty());
	}

	@Test
	public void testGetSliceRoiReturnsEmptyListSliceIsOutOfBounds() throws Exception {
		ArrayList<Roi> resultRois = RoiMan.getSliceRoi(mockRoiMan, testStack, 0);
		assertEquals("Out of bounds slice number should return no ROIs", 0, resultRois.size());

		resultRois = RoiMan.getSliceRoi(mockRoiMan, testStack, testStack.getSize() + 1);
		assertEquals("Out of bounds slice number should return no ROIs", 0, resultRois.size());
	}

	@Test
	public void testGetSliceRoi() throws Exception {
		final int NO_ROI_SLICE_NO = 2;
		final int SINGLE_ROI_SLICE_NO = 3;
		final int MULTI_ROI_SLICE_NO = 4;

		// RoiManager.getSliceNumber tries to parse the number of the slice from
		// the label of the Roi it's given.
		// It doesn't - for example - check the slice attribute of the given
		// Roi...
		final String singleRoiLabel = "000" + SINGLE_ROI_SLICE_NO + "-0000-0001";
		final String multiRoi1Label = "000" + MULTI_ROI_SLICE_NO + "-0000-0001";
		final String multiRoi2Label = "000" + MULTI_ROI_SLICE_NO + "-0000-0002";
		final String noSliceLabel = "NO_SLICE";

		Roi singleRoi = new Roi(10, 10, 10, 10);
		singleRoi.setName(singleRoiLabel);

		Roi multiRoi1 = new Roi(10, 10, 10, 10);
		multiRoi1.setName(multiRoi1Label);

		Roi multiRoi2 = new Roi(30, 30, 10, 10);
		multiRoi2.setName(multiRoi2Label);

		Roi noSliceRoi = new Roi(50, 50, 10, 10);
		noSliceRoi.setName(noSliceLabel);

		Roi rois[] = {singleRoi, multiRoi1, multiRoi2, noSliceRoi};

		when(mockRoiMan.getSliceNumber(anyString())).thenCallRealMethod();
		when(mockRoiMan.getRoisAsArray()).thenReturn(rois);

		// Slice with no (associated) Rois
		ArrayList<Roi> resultRois = RoiMan.getSliceRoi(mockRoiMan, testStack, NO_ROI_SLICE_NO);
		assertEquals("Wrong number of ROIs returned", 1, resultRois.size());
		assertEquals("Wrong ROI returned", noSliceLabel, resultRois.get(0).getName());

		// Slice with one Roi
		resultRois = RoiMan.getSliceRoi(mockRoiMan, testStack, SINGLE_ROI_SLICE_NO);

		assertEquals("Wrong number of ROIs returned", 2, resultRois.size());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", singleRoiLabel, resultRois.get(0).getName());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", noSliceLabel, resultRois.get(1).getName());

		// Slice with multiple Rois
		resultRois = RoiMan.getSliceRoi(mockRoiMan, testStack, MULTI_ROI_SLICE_NO);

		assertEquals("Wrong number of ROIs returned", 3, resultRois.size());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", multiRoi1Label, resultRois.get(0).getName());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", multiRoi2Label, resultRois.get(1).getName());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", noSliceLabel, resultRois.get(2).getName());
	}
}