package com.aj.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Outline;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AJ Launcher - first native Android launcher build.
 * Minimum Android version: 13 (API 33).
 */
public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 201;
    private static final int PICK_VIDEO = 202;
    private static final String PREFS = "aj_launcher_settings";

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private SharedPreferences preferences;

    private FrameLayout root;
    private ImageView wallpaperImage;
    private VideoView wallpaperVideo;
    private AlertDialog settingsDialog;
    private View wallpaperShade;
    private LinearLayout homeColumn;
    private LinearLayout clockBlock;
    private TextView timeText;
    private TextView dateText;
    private GridLayout appGrid;

    private int iconSize;
    private int columns;
    private int spacing;
    private int clockSizeSp;
    private String iconShape;
    private boolean showLabels;
    private boolean showDate;
    private boolean showSeconds;
    private boolean modernMode;
    private boolean videoSound;
    private boolean batterySaver;
    private String clockPosition;
    private String clockColor;
    private String imageUri;
    private String videoUri;
    private String clockFormat;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            clockHandler.postDelayed(this, 1000L);
        }
    };

    private static class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;

        AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        readSettings();
        loadApps();
        buildHome();
        clockHandler.post(clockTick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wallpaperVideo != null && videoUri != null && !batterySaver) {
            wallpaperVideo.start();
        }
    }

    @Override
    protected void onPause() {
        if (wallpaperVideo != null && wallpaperVideo.isPlaying()) {
            wallpaperVideo.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        clockHandler.removeCallbacks(clockTick);
        if (wallpaperVideo != null) {
            wallpaperVideo.stopPlayback();
        }
        super.onDestroy();
    }

    private void readSettings() {
        iconSize = preferences.getInt("icon_size", 42);
        columns = preferences.getInt("columns", 5);
        spacing = preferences.getInt("spacing", 2);
        clockSizeSp = preferences.getInt("clock_size_sp", 42);
        iconShape = preferences.getString("icon_shape", "rounded");
        showLabels = preferences.getBoolean("show_labels", true);
        showDate = preferences.getBoolean("show_date", true);
        showSeconds = preferences.getBoolean("show_seconds", false);
        modernMode = preferences.getBoolean("modern_mode", true);
        videoSound = preferences.getBoolean("video_sound", false);
        batterySaver = preferences.getBoolean("battery_saver", false);
        clockPosition = preferences.getString("clock_position", "top");
        clockColor = preferences.getString("clock_color", "#FFFFFF");
        imageUri = preferences.getString("image_uri", null);
        videoUri = preferences.getString("video_uri", null);
        clockFormat = preferences.getString("clock_format", "digital");
    }

    private void saveSettings() {
        preferences.edit()
                .putInt("icon_size", iconSize)
                .putInt("columns", columns)
                .putInt("spacing", spacing)
                .putInt("clock_size_sp", clockSizeSp)
                .putString("icon_shape", iconShape)
                .putBoolean("show_labels", showLabels)
                .putBoolean("show_date", showDate)
                .putBoolean("show_seconds", showSeconds)
                .putBoolean("modern_mode", modernMode)
                .putBoolean("video_sound", videoSound)
                .putBoolean("battery_saver", batterySaver)
                .putString("clock_position", clockPosition)
                .putString("clock_color", clockColor)
                .putString("image_uri", imageUri)
                .putString("video_uri", videoUri)
                .putString("clock_format", clockFormat)
                .apply();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void buildHome() {
        root = new FrameLayout(this);
        root.setBackground(makeHomeBackground());

        wallpaperVideo = new VideoView(this);
        wallpaperVideo.setVisibility(View.GONE);
        root.addView(wallpaperVideo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        wallpaperImage = new ImageView(this);
        wallpaperImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperImage.setVisibility(View.GONE);
        root.addView(wallpaperImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        wallpaperShade = new View(this);
        wallpaperShade.setBackgroundColor(modernMode ? 0x33000000 : 0x11000000);
        root.addView(wallpaperShade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        homeColumn = new LinearLayout(this);
        homeColumn.setOrientation(LinearLayout.VERTICAL);
        homeColumn.setPadding(dp(14), dp(8), dp(14), dp(10));
        homeColumn.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(homeColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildTopBar();
        buildClock();
        buildAppGrid();
        positionClockBlock();
        TextView footer = new TextView(this);
        footer.setText("AJ LAUNCHER");
        footer.setTextColor(modernMode ? 0x99FFFFFF : 0x9953667A);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(8), 0, dp(3));
        homeColumn.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        applySavedWallpaper();
        updateClock();
    }

    private Drawable makeHomeBackground() {
        int start = modernMode ? Color.rgb(15, 27, 45) : Color.rgb(231, 237, 244);
        int end = modernMode ? Color.rgb(36, 58, 83) : Color.rgb(249, 250, 252);
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{start, end});
        return drawable;
    }

    private void buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = new TextView(this);
        brand.setText("AJ Launcher");
        brand.setTextSize(16);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        brand.setTextColor(primaryTextColor());
        bar.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView settings = new TextView(this);
        settings.setText("⚙");
        settings.setTextSize(25);
        settings.setGravity(Gravity.CENTER);
        settings.setTextColor(primaryTextColor());
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(modernMode ? 0x553C526D : 0x22FFFFFF);
        buttonBg.setCornerRadius(dp(18));
        settings.setBackground(buttonBg);
        settings.setPadding(dp(10), dp(3), dp(10), dp(3));
        settings.setOnClickListener(v -> showSettings());
        bar.addView(settings, new LinearLayout.LayoutParams(dp(46), dp(42)));
        homeColumn.addView(bar);
    }

    private void buildClock() {
        clockBlock = new LinearLayout(this);
        clockBlock.setOrientation(LinearLayout.VERTICAL);
        clockBlock.setGravity(Gravity.CENTER);
        clockBlock.setPadding(0, dp(22), 0, dp(22));

        timeText = new TextView(this);
        timeText.setGravity(Gravity.CENTER);
        timeText.setTextSize(clockSizeSp);
        timeText.setTypeface(null, android.graphics.Typeface.BOLD);
        timeText.setTextColor(parseColor(clockColor, primaryTextColor()));
        clockBlock.addView(timeText);

        dateText = new TextView(this);
        dateText.setGravity(Gravity.CENTER);
        dateText.setTextSize(14);
        dateText.setTextColor(parseColor(clockColor, primaryTextColor()));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(3);
        clockBlock.addView(dateText, dateParams);
        dateText.setVisibility(showDate ? View.VISIBLE : View.GONE);

        homeColumn.addView(clockBlock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildAppGrid() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);

        appGrid = new GridLayout(this);
        appGrid.setColumnCount(columns);
        appGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        appGrid.setUseDefaultMargins(false);
        scroll.addView(appGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        homeColumn.addView(scroll, scrollParams);
        populateGrid();
    }

    private void positionClockBlock() {
        if (clockBlock == null || homeColumn == null) return;
        homeColumn.removeView(clockBlock);
        int insertAt = "bottom".equals(clockPosition) ? homeColumn.getChildCount() : 1;
        homeColumn.addView(clockBlock, insertAt);
    }

    private int primaryTextColor() {
        return modernMode ? Color.WHITE : Color.rgb(30, 46, 65);
    }

    private int secondaryTextColor() {
        return modernMode ? 0xFFD0D9E5 : 0xFF596A7E;
    }

    private int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void updateClock() {
        if (timeText == null || dateText == null) return;
        Locale arabic = new Locale("ar", "IQ");
        String timePattern = showSeconds ? "hh:mm:ss" : "hh:mm";
        if (clockFormat.equals("24h")) timePattern = showSeconds ? "HH:mm:ss" : "HH:mm";
        timeText.setText(new SimpleDateFormat(timePattern, Locale.getDefault()).format(new Date()));
        dateText.setText(new SimpleDateFormat("EEEE، d MMMM yyyy", arabic).format(new Date()));
        dateText.setVisibility(showDate ? View.VISIBLE : View.GONE);
        timeText.setTextColor(parseColor(clockColor, primaryTextColor()));
        dateText.setTextColor(parseColor(clockColor, secondaryTextColor()));
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results = pm.queryIntentActivities(launcherIntent, 0);
        for (ResolveInfo info : results) {
            if (info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName)) continue;
            String label = String.valueOf(info.loadLabel(pm));
            apps.add(new AppEntry(packageName, label, info.loadIcon(pm)));
        }

        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry left, AppEntry right) {
                return left.label.compareToIgnoreCase(right.label);
            }
        });

        String savedOrder = preferences.getString("app_order", "");
        if (!savedOrder.isEmpty()) {
            String[] names = savedOrder.split("\\|");
            Map<String, Integer> positions = new HashMap<>();
            for (int i = 0; i < names.length; i++) positions.put(names[i], i);
            Collections.sort(apps, (a, b) -> Integer.compare(
                    positions.getOrDefault(a.packageName, Integer.MAX_VALUE),
                    positions.getOrDefault(b.packageName, Integer.MAX_VALUE)));
        }
    }

    private void populateGrid() {
        if (appGrid == null) return;
        appGrid.removeAllViews();
        int width = getResources().getDisplayMetrics().widthPixels - dp(28);
        int cellWidth = Math.max(dp(52), width / Math.max(1, columns));
        int margin = dp(spacing);

        for (AppEntry app : apps) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(1), dp(4), dp(1), dp(4));
            cell.setTag(app.packageName);

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setBackground(makeIconBackground());
            icon.setClipToOutline(true);
            icon.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    int w = view.getWidth();
                    int h = view.getHeight();
                    if ("circle".equals(iconShape)) {
                        outline.setOval(0, 0, w, h);
                    } else {
                        float radius = "square".equals(iconShape) ? dp(4) : dp(13);
                        outline.setRoundRect(0, 0, w, h, radius);
                    }
                }
            });
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize));
            cell.addView(icon, iconParams);

            if (showLabels) {
                TextView label = new TextView(this);
                label.setText(app.label);
                label.setTextColor(primaryTextColor());
                label.setTextSize(11);
                label.setGravity(Gravity.CENTER);
                label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                labelParams.topMargin = dp(3);
                cell.addView(label, labelParams);
            }

            cell.setOnClickListener(v -> launchApp(app.packageName));
            cell.setOnLongClickListener(v -> {
                ClipData data = ClipData.newPlainText("AJ app", app.packageName);
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
                v.startDragAndDrop(data, shadow, null, 0);
                return true;
            });
            cell.setOnDragListener((view, event) -> handleAppDrop(view, event));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = Math.max(dp(48), cellWidth - (margin * 2));
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(margin, margin, margin, margin);
            appGrid.addView(cell, params);
        }
    }

    private Drawable makeIconBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(modernMode ? 0x334D6685 : 0x33FFFFFF);
        if ("circle".equals(iconShape)) {
            bg.setShape(GradientDrawable.OVAL);
        } else {
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius("square".equals(iconShape) ? dp(4) : dp(13));
        }
        return bg;
    }

    private boolean handleAppDrop(View target, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getClipDescription() != null;
            case DragEvent.ACTION_DRAG_ENTERED:
                target.setAlpha(0.55f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP:
                target.setAlpha(1f);
                String sourcePackage = String.valueOf(event.getClipData().getItemAt(0).getText());
                String targetPackage = String.valueOf(target.getTag());
                moveAppBefore(sourcePackage, targetPackage);
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.setAlpha(1f);
                return true;
            default:
                return true;
        }
    }

    private void moveAppBefore(String source, String target) {
        if (source.equals(target)) return;
        AppEntry moving = null;
        AppEntry targetEntry = null;
        for (AppEntry app : apps) {
            if (app.packageName.equals(source)) moving = app;
            if (app.packageName.equals(target)) targetEntry = app;
        }
        if (moving == null || targetEntry == null) return;
        apps.remove(moving);
        int index = apps.indexOf(targetEntry);
        apps.add(Math.max(0, index), moving);
        StringBuilder order = new StringBuilder();
        for (AppEntry app : apps) {
            if (order.length() > 0) order.append('|');
            order.append(app.packageName);
        }
        preferences.edit().putString("app_order", order.toString()).apply();
        populateGrid();
    }

    private void launchApp(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            try {
                startActivity(intent);
            } catch (Exception e) {
                showMessage("تعذر فتح التطبيق");
            }
        }
    }

    private void showSettings() {
        if (settingsDialog != null && settingsDialog.isShowing()) {
            settingsDialog.dismiss();
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(8), dp(18), dp(8));
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);

        addSectionTitle(panel, "المظهر");
        addAction(panel, modernMode ? "الوضع الحالي: عصري — اضغط للتبديل إلى البسيط" : "الوضع الحالي: بسيط — اضغط للتبديل إلى العصري", () -> {
            modernMode = !modernMode;
            saveSettings();
            refreshHome();
            showSettings();
        });

        addSectionTitle(panel, "الأيقونات");
        TextView sizeLabel = addInfo(panel, "حجم الأيقونات: " + iconSize + " dp");
        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(40);
        sizeSeek.setProgress(iconSize - 32);
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                iconSize = 32 + progress;
                sizeLabel.setText("حجم الأيقونات: " + iconSize + " dp");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
                populateGrid();
            }
        });
        panel.addView(sizeSeek);

        addAction(panel, "عدد الأعمدة: " + columns + " (اضغط للتغيير)", () -> {
            columns = columns == 4 ? 5 : columns == 5 ? 6 : 4;
            saveSettings();
            refreshHome();
        });
        addAction(panel, "شكل الأيقونات: " + shapeName(iconShape) + " (اضغط للتغيير)", () -> {
            iconShape = "rounded".equals(iconShape) ? "circle" : "circle".equals(iconShape) ? "square" : "rounded";
            saveSettings();
            refreshHome();
        });
        TextView spacingLabel = addInfo(panel, "المسافة بين الأيقونات: " + spacing);
        SeekBar spacingSeek = new SeekBar(this);
        spacingSeek.setMax(8);
        spacingSeek.setProgress(spacing - 1);
        spacingSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                spacing = progress + 1;
                spacingLabel.setText("المسافة بين الأيقونات: " + spacing);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
                populateGrid();
            }
        });
        panel.addView(spacingSeek);
        CheckBox labelsCheck = addCheck(panel, "إظهار أسماء التطبيقات", showLabels, checked -> {
            showLabels = checked;
            saveSettings();
            refreshHome();
        });

        addSectionTitle(panel, "الساعة والتاريخ");
        TextView clockSizeLabel = addInfo(panel, "حجم الساعة: " + clockSizeSp + " sp");
        SeekBar clockSizeSeek = new SeekBar(this);
        clockSizeSeek.setMax(40);
        clockSizeSeek.setProgress(clockSizeSp - 24);
        clockSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                clockSizeSp = 24 + progress;
                clockSizeLabel.setText("حجم الساعة: " + clockSizeSp + " sp");
                if (timeText != null) timeText.setTextSize(clockSizeSp);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { saveSettings(); }
        });
        panel.addView(clockSizeSeek);
        addAction(panel, "موقع الساعة: " + ("bottom".equals(clockPosition) ? "أسفل" : "أعلى") + " (اضغط للتغيير)", () -> {
            clockPosition = "bottom".equals(clockPosition) ? "top" : "bottom";
            saveSettings();
            refreshHome();
        });
        addAction(panel, "لون الساعة: " + clockColor + " (اضغط للتغيير)", () -> {
            String[] colors = {"#FFFFFF", "#E8BD63", "#69BAFF", "#70D8A0", "#FF8585"};
            int index = 0;
            for (int i = 0; i < colors.length; i++) if (colors[i].equals(clockColor)) index = i;
            clockColor = colors[(index + 1) % colors.length];
            saveSettings();
            updateClock();
            showSettings();
        });
        addAction(panel, "تنسيق الساعة: " + ("24h".equals(clockFormat) ? "24 ساعة" : "12 ساعة") + " (اضغط للتغيير)", () -> {
            clockFormat = "24h".equals(clockFormat) ? "12h" : "24h";
            saveSettings();
            updateClock();
            showSettings();
        });
        addCheck(panel, "إظهار التاريخ", showDate, checked -> {
            showDate = checked;
            saveSettings();
            updateClock();
        });
        addCheck(panel, "إظهار الثواني", showSeconds, checked -> {
            showSeconds = checked;
            saveSettings();
            updateClock();
        });

        addSectionTitle(panel, "الخلفيات");
        addAction(panel, "اختيار صورة أو صورة متحركة من الهاتف", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);
        });
        addAction(panel, "اختيار فيديو من الهاتف", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            startActivityForResult(intent, PICK_VIDEO);
        });
        addCheck(panel, "تشغيل صوت الفيديو", videoSound, checked -> {
            videoSound = checked;
            saveSettings();
            applySavedWallpaper();
        });
        addCheck(panel, "توفير البطارية (إيقاف فيديو الخلفية عند الخروج)", batterySaver, checked -> {
            batterySaver = checked;
            saveSettings();
            if (batterySaver && wallpaperVideo != null) wallpaperVideo.pause();
            else applySavedWallpaper();
        });
        addAction(panel, "إزالة الخلفية المخصصة", () -> {
            imageUri = null;
            videoUri = null;
            saveSettings();
            applySavedWallpaper();
        });

        addSectionTitle(panel, "إعادة الترتيب");
        addInfo(panel, "اضغط مطولاً على أيقونة واسحبها فوق أيقونة أخرى لتغيير ترتيبها.");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إعدادات AJ Launcher")
                .setView(scroll)
                .setPositiveButton("تم", (d, which) -> {
                    saveSettings();
                    refreshHome();
                })
                .create();
        settingsDialog = dialog;
        dialog.show();
        dialog.getWindow().setLayout(
                Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(420)),
                Math.min(getResources().getDisplayMetrics().heightPixels - dp(100), dp(680)));
    }

    private interface CheckChanged {
        void onChanged(boolean checked);
    }

    private CheckBox addCheck(LinearLayout parent, String text, boolean checked, CheckChanged listener) {
        CheckBox check = new CheckBox(this);
        check.setText(text);
        check.setTextSize(14);
        check.setChecked(checked);
        check.setOnCheckedChangeListener((button, isChecked) -> listener.onChanged(isChecked));
        parent.addView(check);
        return check;
    }

    private TextView addInfo(LinearLayout parent, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(Color.DKGRAY);
        view.setPadding(0, dp(8), 0, dp(2));
        parent.addView(view);
        return view;
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.rgb(35, 85, 145));
        title.setPadding(0, dp(14), 0, dp(5));
        parent.addView(title);
    }

    private void addAction(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setOnClickListener(v -> {
            if (settingsDialog != null && settingsDialog.isShowing()) settingsDialog.dismiss();
            action.run();
        });
        parent.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String shapeName(String value) {
        if ("circle".equals(value)) return "دائري";
        if ("square".equals(value)) return "مربع";
        return "مستدير";
    }

    private void refreshHome() {
        readSettings();
        loadApps();
        buildHome();
    }

    private void applySavedWallpaper() {
        if (wallpaperImage == null || wallpaperVideo == null) return;
        wallpaperVideo.stopPlayback();
        wallpaperVideo.setVisibility(View.GONE);
        wallpaperImage.setVisibility(View.GONE);

        if (videoUri != null && !batterySaver) {
            try {
                wallpaperVideo.setVideoURI(Uri.parse(videoUri));
                wallpaperVideo.setOnPreparedListener(player -> {
                    player.setLooping(true);
                    float volume = videoSound ? 1f : 0f;
                    player.setVolume(volume, volume);
                    if (!batterySaver) wallpaperVideo.start();
                });
                wallpaperVideo.setVisibility(View.VISIBLE);
                return;
            } catch (Exception e) {
                videoUri = null;
                saveSettings();
            }
        }

        if (imageUri != null) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), Uri.parse(imageUri));
                Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                    int maxDimension = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    int screenMax = Math.max(getResources().getDisplayMetrics().widthPixels,
                            getResources().getDisplayMetrics().heightPixels) * 2;
                    if (maxDimension > screenMax) {
                        decoder.setTargetSampleSize(Math.max(1, maxDimension / screenMax));
                    }
                });
                wallpaperImage.setImageDrawable(drawable);
                wallpaperImage.setVisibility(View.VISIBLE);
                if (drawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) drawable).start();
                }
                return;
            } catch (Exception e) {
                imageUri = null;
                saveSettings();
            }
        }
        root.setBackground(makeHomeBackground());
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (SecurityException ignored) { }

        if (requestCode == PICK_IMAGE) {
            imageUri = uri.toString();
            videoUri = null;
        } else if (requestCode == PICK_VIDEO) {
            videoUri = uri.toString();
            imageUri = null;
        }
        saveSettings();
        applySavedWallpaper();
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("موافق", null)
                .show();
    }
}
