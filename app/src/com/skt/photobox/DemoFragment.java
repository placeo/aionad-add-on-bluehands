package com.skt.photobox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.Toast;

// import com.afollestad.materialdialogs.MaterialDialog;
// import com.afollestad.materialdialogs.list.DialogCallback;
// import com.afollestad.materialdialogs.list.ListCallbackSingleChoice;
import com.jiangdg.ausbc.MultiCameraClient;
import com.jiangdg.ausbc.base.CameraFragment;
import com.jiangdg.ausbc.callback.ICameraStateCallBack;
import com.jiangdg.ausbc.widget.AspectRatioTextureView;
import com.jiangdg.ausbc.widget.IAspectRatio;
import com.jiangdg.ausbc.utils.bus.BusKey;
import com.jiangdg.ausbc.utils.bus.EventBus;
import com.skt.photobox.databinding.FragmentDemoBinding;
import com.jiangdg.ausbc.camera.bean.CameraRequest;

import java.util.ArrayList;
import java.util.List;
import android.app.AlertDialog;

import timber.log.Timber;

// /*
//  * Copyright 2017-2022 Jiangdg
//  * Copyright 2024 vshcryabets@gmail.com
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

/** CameraFragment Usage Demo
 *
 * @author Created by jiangdg on 2022/1/28
 */
public class DemoFragment extends CameraFragment implements View.OnClickListener {

    private PopupWindow mMoreMenu = null;
    private FragmentDemoBinding mViewBinding;

    @Override
    protected CameraRequest getCameraRequest() {
        return new CameraRequest.Builder()
                .setPreviewWidth(DemoApplication.getGlobalResolutionWidth())
                .setPreviewHeight(DemoApplication.getGlobalResolutionHeight())
                .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_YUYV)
                .create();
    }

    @Override
    public void initView() {
        super.initView();
        mViewBinding.resolutionBtn.setOnClickListener(this);
        mViewBinding.btnStart.setOnClickListener(this);
        mViewBinding.btnStop.setOnClickListener(this);
    }

    @Override
    public void initData() {
        super.initData();

        EventBus.INSTANCE.<kotlin.Unit>with(BusKey.KEY_CAPTURE_IMAGE).observe(this, unused -> {
            requireActivity().runOnUiThread(() -> 
                Toast.makeText(requireContext(), "Capture command received via HTTP!", Toast.LENGTH_SHORT).show()
            );
        });

        EventBus eventBus = EventBus.INSTANCE; // Get the EventBus instance

        // Observe frame rate changes
        eventBus.<Integer>with(BusKey.KEY_FRAME_RATE).observe(this, frameRate -> 
            mViewBinding.frameRateTv.setText("frame rate:  " + frameRate + " fps")
        );
    
        // Observe render readiness
        eventBus.<Boolean>with(BusKey.KEY_RENDER_READY).observe(this, ready -> {
            if (ready == null || !ready) return;
            // Additional logic if needed when render is ready
        });
    }

    @Override
    public void onCameraState(MultiCameraClient.ICamera self, ICameraStateCallBack.State code, String msg) {
        switch (code) {
            case OPENED:
                handleCameraOpened();
                break;
            case CLOSED:
                handleCameraClosed();
                break;
            case ERROR:
                handleCameraError(msg);
                break;
        }
    }

    private void handleCameraError(String msg) {
        mViewBinding.uvcLogoIv.setVisibility(View.VISIBLE);
        mViewBinding.frameRateTv.setVisibility(View.GONE);
        Toast.makeText(requireContext(), "camera opened error: " + msg, Toast.LENGTH_LONG).show();
    }

    private void handleCameraClosed() {
        mViewBinding.uvcLogoIv.setVisibility(View.VISIBLE);
        mViewBinding.frameRateTv.setVisibility(View.GONE);
        Toast.makeText(requireContext(), "camera closed success", Toast.LENGTH_LONG).show();
    }

    private void handleCameraOpened() {
        mViewBinding.uvcLogoIv.setVisibility(View.GONE);
        mViewBinding.frameRateTv.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), "camera opened success", Toast.LENGTH_LONG).show();
    }

    @Override
    public IAspectRatio getCameraView() {
        return new AspectRatioTextureView(requireContext());
    }

    @Override
    public ViewGroup getCameraViewContainer() {
        return mViewBinding.cameraViewContainer;
    }

    @Override
    public View getRootView(LayoutInflater inflater, ViewGroup container) {
        mViewBinding = FragmentDemoBinding.inflate(inflater, container, false);
        return mViewBinding.getRoot();
    }

    @Override
    public int getGravity() {
        return Gravity.CENTER;
    }

    @Override
    public void onClick(final View v) {
        Timber.d("%s, %s called", new Throwable().getStackTrace()[0].getClassName(), new Throwable().getStackTrace()[0].getMethodName());
        clickAnimation(v, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (v == mViewBinding.resolutionBtn) {
                    showResolutionDialog();
                } else if (v == mViewBinding.btnStart) {
                    // Handle start timer
                } else if (v == mViewBinding.btnStop) {
                    // Handle stop timer
                } else {
                    // more settings
                }
            }
        });
        Timber.d("%s, %s end", new Throwable().getStackTrace()[0].getClassName(), new Throwable().getStackTrace()[0].getMethodName());
    }

    @SuppressLint("CheckResult")
    private void showResolutionDialog() {
        Timber.d("%s, %s called", new Throwable().getStackTrace()[0].getClassName(), new Throwable().getStackTrace()[0].getMethodName());
        List<Size> sizeList = new ArrayList<>();
        sizeList.add(new Size(160, 120));
        sizeList.add(new Size(320, 240));
        sizeList.add(new Size(640, 480));
        sizeList.add(new Size(1280, 720));
        sizeList.add(new Size(1920, 1080));

        String[] items = new String[sizeList.size()];
        for (int i = 0; i < sizeList.size(); i++) {
            items[i] = sizeList.get(i).width + "x" + sizeList.get(i).height;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Resolution")
                .setItems(items, (dialog, which) -> {
                    // Handle resolution selection
                    Size selectedSize = sizeList.get(which);
                    // Set the selected resolution to the camera
                    updateResolution(selectedSize.width, selectedSize.height);
                })
                .show();
        Timber.d("%s, %s end", new Throwable().getStackTrace()[0].getClassName(), new Throwable().getStackTrace()[0].getMethodName());
    }

    private void clickAnimation(View v, Animator.AnimatorListener listener) {
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 0.4f, 1.0f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 0.4f, 1.0f);
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0.4f, 1.0f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(150);
        animatorSet.addListener(listener);
        animatorSet.playTogether(scaleXAnim, scaleYAnim, alphaAnim);
        animatorSet.start();
    }

    private String calculateTime(int seconds, int minute, Integer hour) {
        StringBuilder mBuilder = new StringBuilder();
        //时
        if (hour != null) {
            if (hour < 10) {
                mBuilder.append("0");
                mBuilder.append(hour);
            } else {
                mBuilder.append(hour);
            }
            mBuilder.append(":");
        }
        // 分
        if (minute < 10) {
            mBuilder.append("0");
            mBuilder.append(minute);
        } else {
            mBuilder.append(minute);
        }
        //秒
        mBuilder.append(":");
        if (seconds < 10) {
            mBuilder.append("0");
            mBuilder.append(seconds);
        } else {
            mBuilder.append(seconds);
        }
        return mBuilder.toString();
    }

    @Override
    public int getSelectedDeviceId() {
        return requireArguments().getInt(MainActivity.KEY_USB_DEVICE);
    }

    public static final int WHAT_START_TIMER = 0x00;
    public static final int WHAT_STOP_TIMER = 0x01;

    public static DemoFragment newInstance(int usbDeviceId) {
        DemoFragment fragment = new DemoFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(MainActivity.KEY_USB_DEVICE, usbDeviceId);
        fragment.setArguments(bundle);
        return fragment;
    }
}

// Auxiliary Size class to represent width and height since original code refers to a Size type.
class Size {
    public int width;
    public int height;

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }
}

