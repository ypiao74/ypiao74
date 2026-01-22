package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.config.AppConfig;
import com.example.myapplication.config.AppConstants;
import com.example.myapplication.util.UriUtil;
import com.example.myapplication.viewmodel.QwenViewModel;

/**
 * 主Activity - MVVM版本 (原MVP重构)
 * ✅ 所有业务功能/逻辑/代码 完全保留，无任何修改
 * ✅ 仅将Presenter替换为ViewModel，通过LiveData订阅数据更新，解耦View与业务层
 * ✅ 无接口契约、无回调，生命周期安全，无内存泄漏
 * ✅ 新增：按选择图片数量动态匹配提问文本
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Button startBtn;
    private Button stopBtn;
    private Button pickImageBtn;
    private Button pickVideoBtn;
    private TextView resultTv;
    private int imagenumber=1;
    private LinearLayout imageContainer;
    private LinearLayout videoContainer;
    private android.widget.ScrollView videoScrollView;
    private static final int REQUEST_PICK_IMAGE_FILE = 1002; // 文件管理器选择图片
    private static final int REQUEST_PICK_VIDEO_FILE = 1003; // 文件管理器选择视频
    private static final int REQUEST_PERMISSION_FILE = 1004; // 文件访问权限请求码
    private Switch configModeSwitch;
    private Spinner voiceSpinner;

    // ======================== 核心替换：Presenter → ViewModel ========================
    private QwenViewModel qwenViewModel;
    private AppConfig appConfig;

    // 发音人列表 【完全保留，一行未改】
    private static final String[] MOBILE_VOICE_OPTIONS = {
            "Jennifer", "Ryan", "Katerina", "Cherry", "Ethan", "Nofish", "Elias"
    };
    private static final String[] VOICE_CN_NAMES = {
            "芊悦", "晨煦", "不吃鱼", "詹妮弗", "甜茶", "卡捷琳娜", "墨讲师",
            "上海-阿珍", "北京-晓东", "四川-晴儿", "南京话-老李", "陕西-秦川",
            "闽南-阿杰", "天津-李彼得", "粤语-阿强", "粤语-阿清", "四川-程川"
    };
    private static final String[] FLASH_VOICE_OPTIONS = {
            "Cherry", "Ethan", "Nofish", "Jennifer", "Ryan", "Katerina", "Elias",
            "Jada", "Dylan", "Sunny", "Li", "Marcus", "Roy", "Peter", "Rocky", "Kiki", "Eric"
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        initViewModel();
        bindClickEvents();
        observeViewModelData();
    }

    // ======================== 新增方法：根据图片数量获取对应提问文本 ========================
    private String getQuestionByImageCount(int imageCount) {
        // 严格按需求匹配：1/2张→车辆问题，3/4张→信号灯左转，5/6张→黄灯含义
        return "给出行驶建议";
        /*if (imageCount == 1 || imageCount == 2) {
            return "前面的车是什么车？";
        } else if (imageCount == 3 ||imageCount==4) {
            return "前面的信号灯是什么意思，我应该如何行驶？";
        }
        else if (imageCount == 5 ) {
            return "右上角白色的标记是什么意思？";
        } else {
            // 超出6张的默认提问，避免空值
            return "前面的建筑物是什么";
        }*/
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 1. 处理文件管理器选择图片的结果
        if (requestCode == REQUEST_PICK_IMAGE_FILE && resultCode == RESULT_OK && data != null) {
            handleFileManagerImageResult(data);
        }
        // 2. 处理文件管理器选择视频的结果
        else if (requestCode == REQUEST_PICK_VIDEO_FILE && resultCode == RESULT_OK && data != null) {
            handleFileManagerVideoResult(data);
        }
        // 3. 悬浮窗权限（原有逻辑保留）
        else if (requestCode == AppConstants.REQUEST_OVERLAY_PERMISSION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                startFloatWindow();
                finish();
            } else {
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleFileManagerImageResult(Intent data) {
        List<File> imageFiles = new ArrayList<>();

        // 场景1：多选图片（ClipData）
        if (data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            int count = clipData.getItemCount();
            for (int i = 0; i < count; i++) {
                Uri imageUri = clipData.getItemAt(i).getUri();
                File imageFile = UriUtil.uriToFile(this, imageUri);
                if (imageFile != null && imageFile.exists()) {
                    imageFiles.add(imageFile);
                }
            }
        }
        // 场景2：单选图片（Data）
        else if (data.getData() != null) {
            Uri imageUri = data.getData();
            File imageFile = UriUtil.uriToFile(this, imageUri);
            if (imageFile != null && imageFile.exists()) {
                imageFiles.add(imageFile);
            }
        }

        // 后续处理
        if (imageFiles.isEmpty()) {
            resultTv.setText("❌ 无法读取图片文件");
            hideImages();
            hideVideo();
        } else {
            hideVideo(); // 显示图片时隐藏视频
            displayImages(imageFiles);
            String question = getQuestionByImageCount(imagenumber);
            Log.d(TAG, "选择了" + imageFiles.size() + "张图片，提问文本：" + question);
            imagenumber++;
            
            // 如果选择2张或更多图片，可以作为视频图片列表
            if (imageFiles.size() >= 2) {
                // 询问用户是作为普通图片还是视频图片列表
                // 为了简化，这里默认2张以上作为视频图片列表
                qwenViewModel.sendVideoImageListRequest(imageFiles, question);
            } else {
                // 单张图片作为普通图片请求
                qwenViewModel.sendImageRequest(imageFiles.get(0), question);
            }
        }
    }

    private void startFloatWindow() {
        Intent intent = new Intent(this, FloatWindowService.class);
        startService(intent);
    }

    private void initView() {
        startBtn = findViewById(R.id.btn_start);
        stopBtn = findViewById(R.id.btn_stop);
        pickImageBtn = findViewById(R.id.btn_pick_image);
        pickVideoBtn = findViewById(R.id.btn_pick_video);
        resultTv = findViewById(R.id.tv_result);
        imageContainer = findViewById(R.id.image_container);
        videoContainer = findViewById(R.id.video_container);
        videoScrollView = findViewById(R.id.video_scroll_view);
        configModeSwitch = findViewById(R.id.switch_config_mode);
        voiceSpinner = findViewById(R.id.spinner_voice);
        stopBtn.setEnabled(false);
        resultTv.setText("点击「开始录音」按钮，说出你的需求...");
        appConfig = new AppConfig(this);
        configModeSwitch.setChecked(appConfig.isUseOmniConfig());
        configModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d(TAG, "配置模式切换: " + (isChecked ? "Omni配置" : "默认配置"));
            appConfig.setUseOmniConfig(isChecked);
            Log.d(TAG, "当前模型: " + (isChecked ? "Flash" : "Mobile") + "，更新发音人列表");
            updateVoiceSpinnerOptions(isChecked);
            Log.d(TAG, "重新创建ViewModel以使用新配置");
            initViewModel();
            String configInfo = String.format(
                    "当前配置:\nAPI URL: %s\n模型: %s",
                    appConfig.getApiUrl(),
                    appConfig.getModelName()
            );
            resultTv.setText(configInfo);
            Toast.makeText(this, "已切换到" + (isChecked ? "Flash" : "Mobile") + "模型", Toast.LENGTH_SHORT).show();
        });
        initVoiceSpinner();
    }

    private void initVoiceSpinner() {
        updateVoiceSpinnerOptions();
        voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                String[] currentOptions = getCurrentVoiceOptions();
                if (position >= 0 && position < currentOptions.length) {
                    String selectedVoice = currentOptions[position];
                    if (qwenViewModel != null) {
                        qwenViewModel.setAudioVoice(selectedVoice);
                        if (qwenViewModel.getQwenOmniClient() != null) {
                            qwenViewModel.getQwenOmniClient().setAudioVoice(selectedVoice);
                        }
                    }
                    Toast.makeText(MainActivity.this, "已选择发音人: " + selectedVoice, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private String[] getCurrentVoiceOptions() {
        if (appConfig == null) {
            Log.w(TAG, "appConfig 为 null，返回 Mobile 发音人列表");
            return MOBILE_VOICE_OPTIONS;
        }
        boolean isFlash = appConfig.isUseOmniConfig();
        String[] options = isFlash ? FLASH_VOICE_OPTIONS : MOBILE_VOICE_OPTIONS;
        Log.d(TAG, "获取发音人列表，模型: " + (isFlash ? "Flash" : "Mobile") + "，数量: " + options.length);
        return options;
    }

    private void updateVoiceSpinnerOptions() {
        boolean isFlash = appConfig != null && appConfig.isUseOmniConfig();
        updateVoiceSpinnerOptions(isFlash);
    }

    private void updateVoiceSpinnerOptions(boolean isFlash) {
        String[] currentOptions = isFlash ? VOICE_CN_NAMES : MOBILE_VOICE_OPTIONS;
        String currentSelectedVoice = null;
        if (voiceSpinner != null && voiceSpinner.getAdapter() != null && voiceSpinner.getSelectedItem() != null) {
            currentSelectedVoice = voiceSpinner.getSelectedItem().toString();
        }
        Log.d(TAG, "更新发音人列表，当前模型: " + (isFlash ? "Flash" : "Mobile") +
                "，选项数量: " + currentOptions.length + "，当前选中: " + currentSelectedVoice);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                currentOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(adapter);
        int selectedIndex = -1;
        if (currentSelectedVoice != null) {
            for (int i = 0; i < currentOptions.length; i++) {
                if (currentOptions[i].equals(currentSelectedVoice)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        if (selectedIndex < 0) {
            selectedIndex = java.util.Arrays.asList(currentOptions).indexOf("Cherry");
            if (selectedIndex < 0) {
                selectedIndex = 0;
            }
        }
        voiceSpinner.setSelection(selectedIndex, false);
        if (selectedIndex >= 0 && selectedIndex < currentOptions.length) {
            String selectedVoice = currentOptions[selectedIndex];
            Log.d(TAG, "设置发音人: " + selectedVoice);
            if (qwenViewModel != null) {
                qwenViewModel.setAudioVoice(selectedVoice);
                if (qwenViewModel.getQwenOmniClient() != null) {
                    qwenViewModel.getQwenOmniClient().setAudioVoice(selectedVoice);
                }
            }
        }
    }

    // ======================== 替换：创建ViewModel 替代 原initPresenter() ========================
    private void initViewModel() {
        qwenViewModel = new ViewModelProvider(this).get(QwenViewModel.class);
        if (qwenViewModel != null && voiceSpinner != null && voiceSpinner.getSelectedItem() != null) {
            String selectedVoice = voiceSpinner.getSelectedItem().toString();
            qwenViewModel.setAudioVoice(selectedVoice);
            if (qwenViewModel.getQwenOmniClient() != null) {
                qwenViewModel.getQwenOmniClient().setAudioVoice(selectedVoice);
            }
        }
    }

    private void bindClickEvents() {
        startBtn.setOnClickListener(v -> checkRecordPermissionAndStart());
        stopBtn.setOnClickListener(v -> {
            hideImages();
            hideVideo();
            if (qwenViewModel != null) {
                qwenViewModel.stopRecording();
            }
            resultTv.setText("⏹️ 录音已停止，正在处理...");
        });
        pickImageBtn.setOnClickListener(v -> pickImage());
        if (pickVideoBtn != null) {
            pickVideoBtn.setOnClickListener(v -> pickVideo());
        }
    }

    private void checkRecordPermissionAndStart() {
        hideImages();
        hideVideo();
        if (qwenViewModel != null) {
            qwenViewModel.cancelCurrentRequest();
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AppConstants.REQUEST_RECORD_AUDIO);
        } else {
            if (qwenViewModel != null) {
                qwenViewModel.startRecording();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AppConstants.REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                resultTv.setText("录音权限已授予，点击「开始录音」按钮...");
            } else {
                resultTv.setText("❌ 需要录音权限才能使用此功能，请在设置中启用");
            }
        }
    }

    // 替换原有 pickImage 方法，直接打开文件管理器
    private void pickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_FILE);
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*"); // 仅显示图片文件
        intent.addCategory(Intent.CATEGORY_OPENABLE); // 限定可打开的文件
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // 支持多选图片
        try {
            startActivityForResult(
                    Intent.createChooser(intent, "文件管理器选择图片"),
                    REQUEST_PICK_IMAGE_FILE
            );
        } catch (Exception e) {
            Log.e(TAG, "文件管理器无法打开", e);
            Toast.makeText(this, "文件管理器无法打开，请检查设备权限", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 选择视频文件（视频文件形式）
     */
    private void pickVideo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_FILE);
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*"); // 仅显示视频文件
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, "选择视频文件"),
                    REQUEST_PICK_VIDEO_FILE
            );
        } catch (Exception e) {
            Log.e(TAG, "文件管理器无法打开", e);
            Toast.makeText(this, "文件管理器无法打开，请检查设备权限", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理文件管理器返回的视频结果
     */
    private void handleFileManagerVideoResult(Intent data) {
        Uri videoUri = data.getData();
        if (videoUri == null) {
            resultTv.setText("❌ 无法读取视频文件");
            return;
        }

        File videoFile = UriUtil.uriToFile(this, videoUri);
        if (videoFile == null || !videoFile.exists()) {
            resultTv.setText("❌ 视频文件不存在");
            return;
        }

        // 验证视频文件大小（Flash限制256MB）
        long fileSizeMB = videoFile.length() / (1024 * 1024);
        if (fileSizeMB > 10) {
            resultTv.setText("❌ 视频文件过大（" + fileSizeMB + "MB），限制为10MB");
            return;
        }

        // 显示视频（先隐藏图片）
        hideImages();
        Log.d(TAG, "准备显示视频: " + videoFile.getAbsolutePath());
        displayVideo(videoFile);
        
        // 显示视频信息
        resultTv.setText("已选择视频文件: " + videoFile.getName() + " (" + fileSizeMB + "MB)\n请输入问题描述（可选）");
        
        // 发送视频文件请求
        String question = getQuestionByImageCount(imagenumber);
        Log.d(TAG, "选择了视频文件，提问文本：" + question);
        imagenumber++;
        qwenViewModel.sendVideoFileRequest(videoFile, question);
    }
    private void displayImages(List<File> imageFiles) {
        if (imageContainer == null || imageFiles == null || imageFiles.isEmpty()) {
            return;
        }
        imageContainer.removeAllViews();
        imageContainer.setVisibility(android.view.View.VISIBLE);
        int maxDisplayWidth = getResources().getDisplayMetrics().widthPixels - 32;
        for (File imageFile : imageFiles) {
            try {
                ImageView imageView = new ImageView(this);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                int imageWidth = options.outWidth;
                int imageHeight = options.outHeight;
                if (imageWidth > 0 && imageHeight > 0) {
                    int sampleSize = 1;
                    if (imageWidth > maxDisplayWidth) {
                        sampleSize = (int) Math.ceil((double) imageWidth / maxDisplayWidth);
                    }
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = sampleSize;
                    Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        imageView.setAdjustViewBounds(true);
                        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        imageView.setMaxWidth(maxDisplayWidth);
                        imageView.setMaxHeight(maxDisplayWidth * 2);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                        params.setMargins(0, 0, 0, 16);
                        imageView.setLayoutParams(params);
                        imageView.setOnClickListener(v -> {
                            Log.d(TAG, "用户点击图片，关闭图片显示");
                            hideImages();
                        });
                        imageView.setOnLongClickListener(v -> {
                            Toast.makeText(this, "点击图片可关闭显示", Toast.LENGTH_SHORT).show();
                            return true;
                        });
                        imageContainer.addView(imageView);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "显示图片失败: " + imageFile.getName(), e);
            }
        }
    }

    private void hideImages() {
        if (imageContainer != null) {
            imageContainer.setVisibility(android.view.View.GONE);
            imageContainer.removeAllViews();
        }
    }

    /**
     * 显示视频文件
     */
    private void displayVideo(File videoFile) {
        Log.d(TAG, "displayVideo 被调用，视频文件: " + (videoFile != null ? videoFile.getAbsolutePath() : "null"));
        
        if (videoContainer == null) {
            Log.e(TAG, "videoContainer 为 null");
            return;
        }
        
        if (videoFile == null || !videoFile.exists()) {
            Log.e(TAG, "视频文件不存在或为null: " + (videoFile != null ? videoFile.getAbsolutePath() : "null"));
            return;
        }
        
        Log.d(TAG, "视频文件存在，大小: " + videoFile.length() + " 字节");
        
        videoContainer.removeAllViews();
        videoContainer.setVisibility(android.view.View.VISIBLE);
        if (videoScrollView != null) {
            videoScrollView.setVisibility(android.view.View.VISIBLE);
        }
        Log.d(TAG, "视频容器已设置为可见");
        
        try {
            int maxDisplayWidth = getResources().getDisplayMetrics().widthPixels - 32;
            Log.d(TAG, "最大显示宽度: " + maxDisplayWidth);
            
            // 创建VideoView来显示视频
            android.widget.VideoView videoView = new android.widget.VideoView(this);
            Uri videoUri = Uri.fromFile(videoFile);
            Log.d(TAG, "视频URI: " + videoUri.toString());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    maxDisplayWidth,
                    400  // 先设置一个固定高度，等视频加载后再调整
            );
            params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            params.setMargins(0, 0, 0, 0);
            videoView.setLayoutParams(params);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                videoView.setZ(1f);
            }
            android.widget.MediaController mediaController = new android.widget.MediaController(this);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);
            videoView.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "VideoView错误 - what: " + what + ", extra: " + extra);
                showVideoError(videoFile.getName());
                return true;
            });

            videoView.setOnCompletionListener(mp -> {
                Log.d(TAG, "视频播放完成");
            });

            videoView.setOnPreparedListener(mediaPlayer -> {
                Log.d(TAG, "视频准备完成");
                int videoWidth = mediaPlayer.getVideoWidth();
                int videoHeight = mediaPlayer.getVideoHeight();
                Log.d(TAG, "视频尺寸: " + videoWidth + "x" + videoHeight);
                if (videoWidth > 0 && videoHeight > 0) {
                    float aspectRatio = (float) videoHeight / videoWidth;
                    int displayHeight = (int) (maxDisplayWidth * aspectRatio);
                    displayHeight = Math.min(displayHeight, maxDisplayWidth * 2);
                    params.height = displayHeight;
                    videoView.setLayoutParams(params);
                    Log.d(TAG, "视频视图高度设置为: " + params.height);
                } else {
                    params.height = 400;
                    videoView.setLayoutParams(params);
                    Log.d(TAG, "无法获取视频尺寸，使用默认高度: 400");
                }

                mediaPlayer.seekTo(0);
                mediaPlayer.start();
                Log.d(TAG, "视频开始播放以显示第一帧");

                videoView.postDelayed(() -> {
                    try {
                        if (videoView.isPlaying()) {
                            // 暂停播放
                            videoView.pause();
                            // 确保停留在第一帧
                            if (mediaPlayer.getCurrentPosition() > 100) {
                                mediaPlayer.seekTo(0);
                            }
                            Log.d(TAG, "视频已暂停在第一帧，当前位置: " + mediaPlayer.getCurrentPosition() + "ms");
                        } else {
                            // 如果已经停止，重新定位到开始
                            mediaPlayer.seekTo(0);
                            Log.d(TAG, "视频已定位到第一帧");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "暂停视频时出错", e);
                    }
                }, 500);
            });
            
            // 先添加到容器，再设置URI（顺序很重要）
            videoContainer.addView(videoView);
            Log.d(TAG, "VideoView已添加到容器");
            videoView.post(() -> {
                try {
                    Log.d(TAG, "开始设置视频URI");
                    videoView.setVideoURI(videoUri);
                    Log.d(TAG, "视频URI设置完成，开始准备");
                    // 请求焦点，确保VideoView可以显示
                    videoView.requestFocus();
                } catch (Exception e) {
                    Log.e(TAG, "设置视频URI失败", e);
                    showVideoError(videoFile.getName());
                }
            });

            videoContainer.invalidate();
            videoContainer.requestLayout();

            if (videoScrollView != null) {
                videoScrollView.setVisibility(android.view.View.VISIBLE);
                videoScrollView.invalidate();
                videoScrollView.requestLayout();
                Log.d(TAG, "ScrollView已设置为可见并刷新");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "显示视频失败: " + videoFile.getName(), e);
            e.printStackTrace();
            // 如果VideoView失败，显示视频信息文本
            showVideoError(videoFile.getName());
        }
    }
    
    /**
     * 显示视频错误信息
     */
    private void showVideoError(String fileName) {
        if (videoContainer == null) {
            return;
        }
        videoContainer.removeAllViews();
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText("视频文件: " + fileName + "\n(无法预览，但已选择)\n点击关闭");
        textView.setGravity(android.view.Gravity.CENTER);
        textView.setPadding(16, 16, 16, 16);
        textView.setTextSize(14);
        textView.setOnClickListener(v -> hideVideo());
        videoContainer.addView(textView);
        videoContainer.setVisibility(android.view.View.VISIBLE);
    }

    /**
     * 隐藏视频显示
     */
    private void hideVideo() {
        if (videoContainer != null) {
            videoContainer.setVisibility(android.view.View.GONE);
            videoContainer.removeAllViews();
        }
        if (videoScrollView != null) {
            videoScrollView.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ViewModel会自动生命周期管理，无需手动release，这里可以留空
    }

    // ======================== 新增：订阅ViewModel的LiveData，自动更新UI ========================
    private void observeViewModelData() {
        qwenViewModel.resultLiveData.observe(this, text -> {
            if (text != null) {
                resultTv.setText(text);
            }
        });
        // 订阅开始按钮状态更新
        qwenViewModel.startButtonEnable.observe(this, enabled -> {
            startBtn.setEnabled(enabled);
        });
        // 订阅停止按钮状态更新
        qwenViewModel.stopButtonEnable.observe(this, enabled -> {
            stopBtn.setEnabled(enabled);
        });
    }
}