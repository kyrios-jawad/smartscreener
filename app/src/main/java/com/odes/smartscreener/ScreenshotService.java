package com.odes.smartscreener;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.*;
import android.os.*;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ScreenshotService extends Service {

    private static final String CHANNEL_ID = "ScreenshotServiceChannel";
    private static final String SCREENSHOT_PATH = Environment.getExternalStorageDirectory() + "/Pictures/Screenshots/";
    private final HashSet<String> seen = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Smart Screener")
                .setContentText("Watching for new screenshots...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }

        new Thread(this::watchScreenshots).start();
    }

    private void watchScreenshots() {
        ContentResolver contentResolver = getContentResolver();
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
        };

        while (true) {
            long currentTimeSeconds = System.currentTimeMillis() / 1000;

            Cursor cursor = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    MediaStore.Images.Media.DATE_ADDED + " >= ?",
                    new String[]{String.valueOf(currentTimeSeconds - 10)}, // last 5 seconds
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));

                    if (name.toLowerCase().contains("screenshot") && !seen.contains(name)) {
                        seen.add(name);
                        Log.d("ScreenshotService", "Detected screenshot: " + path);
                        overlayTimestamp(new File(path));
                    }
                }
                cursor.close();
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }


    private void overlayTimestamp(File file) {
        try {
            Bitmap original = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (original == null) return;

            Bitmap mutable = original.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutable);

            // Format the timestamp (adjust as needed)
            String timestamp = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());

            // Text paint
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(18); // smaller text
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setFakeBoldText(true);

            // Measure text
            Rect textBounds = new Rect();
            textPaint.getTextBounds(timestamp, 0, timestamp.length(), textBounds);

            // Padding and margins
            int paddingX = 30;
            int paddingY = 20;
            int margin = 30;

            int rectWidth = textBounds.width() + 2 * paddingX;
            int rectHeight = textBounds.height() + 2 * paddingY;

            int x = (canvas.getWidth() - rectWidth) / 2;
            int y = margin;

            // Background paint (rounded semi-transparent)
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(Color.BLACK);
            bgPaint.setAlpha(230); // ~90% opacity

            // Draw rounded background
            RectF rect = new RectF(x, y, x + rectWidth, y + rectHeight);
            canvas.drawRoundRect(rect, 50, 50, bgPaint); // pill shape

            // Draw text centered inside rect
            float textX = x + paddingX;
            float textY = y + paddingY + textBounds.height(); // account for ascent
            canvas.drawText(timestamp, textX, textY, textPaint);

            // Save
            FileOutputStream out = new FileOutputStream(file);
            mutable.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();

        } catch (Exception e) {
            Log.e("ScreenshotService", "Failed to overlay timestamp", e);
        }
    }



    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Screenshot Watcher", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
