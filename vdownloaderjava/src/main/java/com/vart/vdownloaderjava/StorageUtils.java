package com.vart.vdownloaderjava;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.os.Environment.MEDIA_MOUNTED;

/**
 * @Title: StorageUtils.java
 * @Package com.example.ant_test.image_loader.util
 * @Description: TODO(description)
 * @author Chenxiao
 * @date 2014-10-9 下午2:08:49
 * @version V1.0
 * @company East Money Information Co., Ltd.
 */
public class StorageUtils {

	private static final String TAG = "StorageUtils";
	private static final String EXTERNAL_STORAGE_PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE";

	/**
	 * 获取缓存路径，即在“公共”缓存路径下再新建个以包名为文件名的文件夹
	 */
//	public static File getIndividualCacheDirectory(Context context) {
//		String packageName = context.getPackageName();
//		File cacheDir = getCacheDirectory(context);
//		File individualCacheDir = new File(cacheDir, packageName);
//		if (!individualCacheDir.exists()) {
//			if (!individualCacheDir.mkdir()) {
//				individualCacheDir = cacheDir;
//			}
//		}
//		return individualCacheDir;
//	}

	public static boolean fileExists(Context context, String dictionary, String fileName, boolean refresh) {
		File dir = createCache(context, dictionary);
		File file = new File(dir + File.separator + fileName);
		boolean result = file.exists();
		if (result && refresh) {
			MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null, null);
			scanMedia(context, file);
		}
		return result;
	}

	public static void refreshBitmap(Context context, File file, Bitmap bitmap) {
		MediaStore.Images.Media.insertImage(context.getContentResolver(), bitmap, "", "");
		context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
	}

	public static void scanMedia(Context context, File file) {
//		MediaScannerConnection.scanFile(context, new String[] { file.getPath()}, new String[] {ContentResolver.Mi}, null);
		Uri uri = Uri.fromFile(file);
		Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
		context.sendBroadcast(intent);
	}

	public static void refreshBitmap(Context context, File file) {
		try {
			MediaStore.Images.Media.insertImage(context.getContentResolver(), file.getPath(), file.getName(), null);
			Uri uri = Uri.fromFile(file);
//			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//				MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, new String[]{"image/jpeg"},
//						new MediaScannerConnection.OnScanCompletedListener() {
//							public void onScanCompleted(String path, Uri uri) {
//								Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//								mediaScanIntent.setData(uri);
//								context.sendBroadcast(mediaScanIntent);
//							}
//						});
//			} else {
				context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
//			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static File saveBitmap(Context context, Bitmap bitmap, String dictionary, String fileName) {
		try {
			File file = createFile(context, dictionary, fileName + ".jpg", true);
			FileOutputStream out = new FileOutputStream(file);
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
			out.flush();
			out.close();
			return file;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void deleteFile(Context context, String dictionary, String fileName) {
		File dir = createCache(context, dictionary);
		File file = new File(dir + File.separator + fileName);
		if (file.exists()) {
			file.delete();
			Log.d(TAG, "delete file " + file.getPath());
		}
	}

	public static File createFileDirectly(Context context, String dictionary, String fileName) {
		File dir = new File(Environment.getExternalStorageDirectory(), dictionary);
		File file = new File(dir + File.separator + fileName);
		if (file.exists()) {
			file.delete();
		}
		try {
			file.createNewFile();
			Log.d(TAG, "create file in " + file.getPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return file;
	}

	public static File createFile(Context context, String dictionary, String fileName, boolean deleteIfExists) {
		File dir = createCache(context, dictionary);
		File file = new File(dir + File.separator + fileName);
		if (file.exists()) {
			if (deleteIfExists) {
				Log.d(TAG, "delete exists file");
				file.delete();
			} else {
				Log.d(TAG, "file exists file " + file.getPath());
				return file;
			}
		} else {
			Log.d(TAG, "file not exists " + file.getPath());
		}
		try {
			file.createNewFile();
			Log.d(TAG, "create file in " + file.getPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return file;
	}

	public static File createCache(Context context, String dictionary) {
		File cacheDir = getCacheDirectory(context);
		File dir = new File(cacheDir, dictionary);
		if (!dir.exists()) {
			Log.d(TAG, "dir " + dir + " not exists");
			if (!dir.mkdir()) {
				dir = cacheDir;
			}
		} else  {
			Log.d(TAG, "dir " + dir + " exists");
		}
		return dir;
	}
	
	/**
	 * 获取应用的“公共”缓存路径
	 */
	public static File getCacheDirectory(Context context) {
		File appCacheDir = null;
		String externalStorageState;
		try {
			externalStorageState = Environment.getExternalStorageState();
		} catch (NullPointerException e) { //有这种可能。。
			externalStorageState = "";
		}
		if (MEDIA_MOUNTED.equals(externalStorageState) && hasExternalStoragePermission(context)) {
			appCacheDir = getExternalCacheDir(context);
		}
		if (appCacheDir == null) {
			Log.d(">>>>", "no permission");
			appCacheDir = context.getCacheDir();
		} else {
			Log.d(">>>>", "has permission");
		}
		if (appCacheDir == null) {//如果没有获取到外部存储路径，则使用设备的ROM，这个路径是一定存在的
			String cacheDirPath = "/data/data/" + context.getPackageName() + "/cache/";
			Log.w("", "Can't define system cache directory! " + cacheDirPath + " will be used.");
			appCacheDir = new File(cacheDirPath);
		}
		return appCacheDir;
	}
	
	/**
	 * 获取外部存储的路径，一般是sd卡
	 */
	private static File getExternalCacheDir(Context context) {
		File dataDir = new File(new File(Environment.getExternalStorageDirectory(), "Android"), "data");//=>>>/根目录/Android/data
		File appCacheDir = new File(new File(dataDir, context.getPackageName()), "cache");//==>>>/根目录/Android/data/packagename/cache

		if (!appCacheDir.exists()) {//如果该缓存路径不存在
			if (!appCacheDir.mkdirs()) {//如果创建该缓存失败
				Log.w("", "Unable to create external cache directory");
				return null;
			}
//			try {
//				new File(appCacheDir, ".nomedia").createNewFile();//在缓存目录下新建.nomedia文件，不知道干嘛用的
//			} catch (IOException e) {
//				Log.i("", "Can't create \".nomedia\" file in application external cache directory");
//			}
		}
		return appCacheDir;
	}
	
	/**
	 * 判断应用是否有外部存储的权限
	 */
	private static boolean hasExternalStoragePermission(Context context) {
		int perm = context.checkCallingOrSelfPermission(EXTERNAL_STORAGE_PERMISSION);
		return perm == PackageManager.PERMISSION_GRANTED;
	}

	public static void saveVideoToAlbum(Context context, File file) {
		Log.d(TAG, "saveVideoToAlbum");
		ContentResolver localContentResolver = context.getContentResolver();
		ContentValues localContentValues = getVideoContentValues(file, System.currentTimeMillis());
		Uri localUri = localContentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, localContentValues);

		MediaScannerConnection.scanFile(context, new String[]{file.getPath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
			@Override
			public void onScanCompleted(String s, Uri uri) {
				Log.i("ExternalStorage", "Scanned " + s + ":");
				Log.i("ExternalStorage", "-> uri=" + uri);
			}
		});
		context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
	}

	public static ContentValues getVideoContentValues(File paramFile, long paramLong)
	{
		ContentValues localContentValues = new ContentValues();
		localContentValues.put("title", paramFile.getName());
		localContentValues.put("_display_name", paramFile.getName());
		localContentValues.put("mime_type", "video/mp4");
		localContentValues.put("datetaken", Long.valueOf(paramLong));
		localContentValues.put("date_modified", Long.valueOf(paramLong));
		localContentValues.put("date_added", Long.valueOf(paramLong));
		localContentValues.put("orientation", Integer.valueOf(0));
		localContentValues.put("_data", paramFile.getAbsolutePath());
		localContentValues.put("_size", Long.valueOf(paramFile.length()));
		return localContentValues;
	}

	public static void addVideo(Context context, File file) {
		ContentValues values = new ContentValues(3);
		values.put(MediaStore.Video.Media.TITLE, "My video title");
		values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
		values.put(MediaStore.Video.Media.DATA, file.getAbsolutePath());
		context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
	}
	
}
