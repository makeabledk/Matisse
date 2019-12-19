/*
 * Copyright 2017 Zhihu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zhihu.matisse.internal.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.content.FileProvider;
import androidx.core.os.EnvironmentCompat;

import com.zhihu.matisse.internal.entity.CaptureStrategy;
import com.zhihu.matisse.internal.entity.SelectionSpec;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class MediaStoreCompat {

    public enum FileType {
        Image,
        Video
    }

    public class MediaIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("MediaStoreCompat", intent.toString());
        }
    }

    private final WeakReference<Activity> mContext;
    private final WeakReference<Fragment> mFragment;
    private CaptureStrategy mCaptureStrategy;
//    private Uri mCurrentCaptureUri;
//    private String mCurrentCapturePath;
    private Uri mCurrentImageCaptureUri;
    private String mCurrentImageCapturePath;
    private Uri mCurrentVideoCaptureUri;
    private String mCurrentVideoCapturePath;

    public MediaStoreCompat(Activity activity) {
        mContext = new WeakReference<>(activity);
        mFragment = null;
    }

    public MediaStoreCompat(Activity activity, Fragment fragment) {
        mContext = new WeakReference<>(activity);
        mFragment = new WeakReference<>(fragment);
    }

    /**
     * Checks whether the device has a camera feature or not.
     *
     * @param context a context to check for camera feature.
     * @return true if the device has a camera feature. false otherwise.
     */
    public static boolean hasCameraFeature(Context context) {
        PackageManager pm = context.getApplicationContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    public void setCaptureStrategy(CaptureStrategy strategy) {
        mCaptureStrategy = strategy;
    }

    BroadcastReceiver mBroadcastReceiver;
    IntentSender sender;

    private void setupCaptureIntent(Context context, @NonNull Intent captureIntent, @NonNull FileType fileType) {
        File captureFile = null;
        try {
            captureFile = createFile(fileType);
        } catch (IOException e) { e.printStackTrace(); }

        if (captureFile != null) {
            Uri mCurrentCaptureUri = null;
            String mCurrentCapturePath = null;

            switch (fileType) {
                case Image:
                    mCurrentImageCapturePath = captureFile.getAbsolutePath();
                    mCurrentImageCaptureUri = FileProvider.getUriForFile(mContext.get(),
                            mCaptureStrategy.authority, captureFile);

                    mCurrentCaptureUri = mCurrentImageCaptureUri;
                    mCurrentCapturePath = mCurrentImageCapturePath;
                    break;
                case Video:
                    mCurrentVideoCapturePath = captureFile.getAbsolutePath();
                    mCurrentVideoCaptureUri = FileProvider.getUriForFile(mContext.get(),
                            mCaptureStrategy.authority, captureFile);

                    mCurrentCaptureUri = mCurrentVideoCaptureUri;
                    mCurrentCapturePath = mCurrentVideoCapturePath;
                    break;
            }

            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCurrentCaptureUri);
            captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            List<ResolveInfo> resInfoList = context.getPackageManager()
                    .queryIntentActivities(captureIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, mCurrentCaptureUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
    }

    public void dispatchCaptureIntent(@NonNull SelectionSpec spec, Context context, int requestCode) {
        Intent captureIntent = null;
        FileType fileType = null;
        if (spec.onlyShowImages()) {
            captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            fileType = FileType.Image;
        } else if (spec.onlyShowVideos()) {
            captureIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            fileType = FileType.Video;
        } else if (!spec.onlyShowGif()) {
            // Multiple media types supported, open an intent chooser
            Intent imgIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            setupCaptureIntent(context, imgIntent, FileType.Image);

            Intent vidIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            setupCaptureIntent(context, vidIntent, FileType.Video);

            IntentFilter filter = new IntentFilter(MediaStore.ACTION_IMAGE_CAPTURE);
            filter.addAction(MediaStore.ACTION_VIDEO_CAPTURE);

            captureIntent = Intent.createChooser(imgIntent, "Capture");
            captureIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{vidIntent});

            if (mFragment != null) {
                mFragment.get().startActivityForResult(captureIntent, requestCode);
            } else {
                mContext.get().startActivityForResult(captureIntent, requestCode);
            }
        }

        if (fileType != null) {
            if (captureIntent.resolveActivity(context.getPackageManager()) != null) {
                File captureFile = null;
                try {
                    captureFile = createFile(fileType == FileType.Image ? FileType.Image : FileType.Video);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (captureFile != null) {
                    Uri mCurrentCaptureUri = null;
                    String mCurrentCapturePath = null;

                    switch (fileType) {
                        case Image:
                            mCurrentImageCapturePath = captureFile.getAbsolutePath();
                            mCurrentImageCaptureUri = FileProvider.getUriForFile(mContext.get(),
                                    mCaptureStrategy.authority, captureFile);

                            mCurrentCaptureUri = mCurrentImageCaptureUri;
                            mCurrentCapturePath = mCurrentImageCapturePath;
                            break;
                        case Video:
                            mCurrentVideoCapturePath = captureFile.getAbsolutePath();
                            mCurrentVideoCaptureUri = FileProvider.getUriForFile(mContext.get(),
                                    mCaptureStrategy.authority, captureFile);

                            mCurrentCaptureUri = mCurrentVideoCaptureUri;
                            mCurrentCapturePath = mCurrentVideoCapturePath;
                            break;
                    }

                    captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCurrentCaptureUri);
                    captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    List<ResolveInfo> resInfoList = context.getPackageManager()
                            .queryIntentActivities(captureIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    for (ResolveInfo resolveInfo : resInfoList) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        context.grantUriPermission(packageName, mCurrentCaptureUri,
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    if (mFragment != null) {
                        mFragment.get().startActivityForResult(captureIntent, requestCode);
                    } else {
                        mContext.get().startActivityForResult(captureIntent, requestCode);
                    }
                }
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File createFile(@NonNull FileType fileType) throws IOException {
        // Create a file name
        String timeStamp =
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileFormat = fileType == FileType.Image ? "IMG_%s.jpg" : "VIDEO_%s.mp4";
        String fileName = String.format(fileFormat, timeStamp);
        String environment = fileType == FileType.Image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES;
        File storageDir;
        if (mCaptureStrategy.isPublic) {
            storageDir = Environment.getExternalStoragePublicDirectory(
                    environment);
            if (!storageDir.exists()) storageDir.mkdirs();
        } else {
            storageDir = mContext.get().getExternalFilesDir(environment);
        }
        if (mCaptureStrategy.directory != null) {
            storageDir = new File(storageDir, mCaptureStrategy.directory);
            if (!storageDir.exists()) storageDir.mkdirs();
        }

        // Avoid joining path components manually
        File tempFile = new File(storageDir, fileName);

        // Handle the situation that user's external storage is not ready
        if (!Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(tempFile))) {
            return null;
        }

        return tempFile;
    }

    public Uri getCurrentCaptureUri() {
        if (mCurrentImageCapturePath != null && new File(mCurrentImageCapturePath).exists()) {
            return mCurrentImageCaptureUri;
        }
        return mCurrentVideoCaptureUri;
    }

    public String getCurrentCapturePath() {
        if (new File(mCurrentImageCapturePath).exists()) {
            return mCurrentImageCapturePath;
        }
        return mCurrentVideoCapturePath;
    }
}
