/**
 * File Name: FdActivity.java
 * Date: 2/5/2013
 * 
 * Extends: Activity
 * Implements: CvCameraViewListener
 *
 */

package edu.wright.ceg4210.eyetracker.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import edu.wright.ceg4210.eyetracker.R;

public class DemoModeActivity extends Activity implements CvCameraViewListener {

	private static final String TAG = "EyeDection::Activity"; // Name for Log ID
	private static final Scalar FACE_RECT_COLOR = new Scalar(255, 0, 0, 255);
	private static final int MINRECTSIZE = 100;
	private static final int MAXRECTWIDTH = 200;
	private static final int MAXRECTHEIGHT = 190;
	private static final int EXPECTEDNUMEYES = 2;

	private Mat mRgba;
	private Mat mGray;
	private File mCascadeFile;
	private CascadeClassifier mJavaDetector; // Object used to detect objects in video screen

	private MenuItem quit;

	private CameraBridgeViewBase mOpenCvCameraView;

	private File sdCard = null;
	private File dir = null;

	/*
	 * BaseLoader Callback - Ensures propery OpenCV libraries are loaded. Reads in haarcascade_eye.xml. Feature
	 * detection will use Viola-Jones method.
	 */
	private final BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

		@Override
		public void onManagerConnected(final int status) {

			switch (status) {

			// OpenCV loaded successfully
			case LoaderCallbackInterface.SUCCESS: {
				Log.i(TAG, "OpenCV loaded successfully");

				System.loadLibrary("detection_based_tracker");

				try {
					final InputStream is = getResources().openRawResource(R.raw.haarcascade_eye);
					final File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
					mCascadeFile = new File(cascadeDir, "haarcascade_eye.xml");
					final FileOutputStream os = new FileOutputStream(mCascadeFile);

					final byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = is.read(buffer)) != -1) {
						os.write(buffer, 0, bytesRead);
					}

					is.close();
					os.close();

					mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
					if (mJavaDetector.empty()) {
						Log.e(TAG, "Failed to load cascade classifier");
						mJavaDetector = null;
					} else {
						Log.i(TAG, "Loaded cascade classifier from "
								+ mCascadeFile.getAbsolutePath());
					}

					cascadeDir.delete();

				} catch (final IOException e) {
					e.printStackTrace();
					Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
				}
				mOpenCvCameraView.enableFpsMeter();
				mOpenCvCameraView.enableView();
			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}
	}; // end BaseLoaderCallback

	public DemoModeActivity() {

		Log.i(TAG, "Instantiated new " + this.getClass());
		sdCard = Environment.getExternalStorageDirectory();
		dir = new File(sdCard.getAbsolutePath() + "/testImages/");
		dir.mkdirs();
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.face_detect_surface_view);

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
		mOpenCvCameraView.setCvCameraViewListener(this);
	}

	@Override
	public void onPause() {
		if (mOpenCvCameraView != null) {
			mOpenCvCameraView.disableView();
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
				mLoaderCallback);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mOpenCvCameraView.disableView();
	}

	@Override
	public void onCameraViewStarted(final int width, final int height) {
		mGray = new Mat();
		mRgba = new Mat();
	}

	@Override
	public void onCameraViewStopped() {
		mGray.release();
		mRgba.release();
	}

	/**
	 * Mat onCameraFrame(Mat inputFrame) - Calls CascadeCassifier detectMultiScale witch will detect eyes and store a
	 * rectangular representation of the detected area in MatOfRect eyes.
	 * 
	 * A new File will be created to store the section of the frame where an eye was detected.
	 * 
	 */
	@Override
	public Mat onCameraFrame(final Mat inputFrame) {

		inputFrame.copyTo(mRgba);
		Imgproc.cvtColor(inputFrame, mGray, Imgproc.COLOR_RGBA2GRAY);

		final MatOfRect eyes = new MatOfRect();

		mJavaDetector.detectMultiScale(mGray, eyes, 1.1, 2, 2,
				new Size(MINRECTSIZE, MINRECTSIZE), new Size(MAXRECTWIDTH, MAXRECTHEIGHT));

		/*
		 * Create two files, one for each eye. A time stamp is created to append to file names to differentiate between
		 * different sets of eyes. Two eyes with matching time stamps are from the same frame. Assume left/right eye.
		 */

		final long timestamp = System.currentTimeMillis();
		final File eye1 = new File(dir + File.separator + "eye1" + "-"
				+ timestamp + ".jpg");
		final File eye2 = new File(dir + File.separator + "eye2" + "-"
				+ timestamp + ".jpg");

		final Rect[] facesArray = eyes.toArray();

		if (facesArray.length >= EXPECTEDNUMEYES) { // Avoid array index out of bounds

			// draw rectangle to screen
			Core.rectangle(mRgba, facesArray[0].tl(), facesArray[0].br(),
					FACE_RECT_COLOR, 3);
			// create sub-Matrix contained just eye from whole frame and write to file
			Mat ROI = mRgba.submat(facesArray[0]);
			Imgproc.cvtColor(ROI, ROI, Imgproc.COLOR_RGBA2BGR);
			Highgui.imwrite(eye1.getAbsolutePath(), ROI);

			Core.rectangle(mRgba, facesArray[1].tl(), facesArray[1].br(),
					FACE_RECT_COLOR, 3);
			ROI = mRgba.submat(facesArray[1]);
			Imgproc.cvtColor(ROI, ROI, Imgproc.COLOR_RGBA2BGR);
			Highgui.imwrite(eye2.getAbsolutePath(), ROI);
		}

		return mRgba;
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		Log.i(TAG, "called onCreateOptionsMenu");
		quit = menu.add("Exit");

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
		if (item == quit) {
			mOpenCvCameraView.disableView();
			finish();
		}

		return true;
	}

} // End FdActivity
