package com.herewhite.demo.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.sdk.android.httpdns.HttpDns;
import com.alibaba.sdk.android.httpdns.HttpDnsService;
import com.google.gson.Gson;
import com.herewhite.demo.DemoAPI;
import com.herewhite.demo.LocalFileWebViewClient;
import com.herewhite.demo.R;
import com.herewhite.demo.WhiteWebViewClient;
import com.herewhite.demo.adapter.ColorSelectAdapter;
import com.herewhite.demo.adapter.PageSelectAdapter;
import com.herewhite.demo.manager.SettingManager;
import com.herewhite.demo.utils.ColorUtil;
import com.herewhite.demo.utils.CommonUtil;
import com.herewhite.demo.utils.SelectUtil;
import com.herewhite.demo.widget.CirclePointView;
import com.herewhite.demo.widget.NoScrollGridView;
import com.herewhite.sdk.CommonCallbacks;
import com.herewhite.sdk.domain.AnimationMode;
import com.herewhite.sdk.domain.Scene;
import com.herewhite.sdk.AbstractRoomCallbacks;
import com.herewhite.sdk.Converter;
import com.herewhite.sdk.ConverterCallbacks;
import com.herewhite.sdk.Logger;
import com.herewhite.sdk.Room;
import com.herewhite.sdk.RoomParams;
import com.herewhite.sdk.WhiteSdk;
import com.herewhite.sdk.WhiteSdkConfiguration;
import com.herewhite.sdk.WhiteboardView;
import com.herewhite.sdk.domain.Point;
import com.herewhite.sdk.domain.EventListener;
import com.herewhite.sdk.domain.AkkoEvent;
import com.herewhite.sdk.domain.Appliance;
import com.herewhite.sdk.domain.BroadcastState;
import com.herewhite.sdk.domain.CameraBound;
import com.herewhite.sdk.domain.CameraConfig;
import com.herewhite.sdk.domain.ContentModeConfig;
import com.herewhite.sdk.domain.ConversionInfo;
import com.herewhite.sdk.domain.ConvertException;
import com.herewhite.sdk.domain.ConvertedFiles;
import com.herewhite.sdk.domain.EventEntry;
import com.herewhite.sdk.domain.GlobalState;
import com.herewhite.sdk.domain.ImageInformationWithUrl;
import com.herewhite.sdk.domain.MemberState;
import com.herewhite.sdk.domain.PptPage;
import com.herewhite.sdk.domain.Promise;
import com.herewhite.sdk.domain.RectangleConfig;
import com.herewhite.sdk.domain.RoomPhase;
import com.herewhite.sdk.domain.RoomState;
import com.herewhite.sdk.domain.SDKError;
import com.herewhite.sdk.domain.UrlInterrupter;
import com.herewhite.sdk.domain.ViewMode;
import com.herewhite.sdk.domain.WhiteDisplayerState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import wendu.dsbridge.DWebView;


public class WhiteRoomActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "WhiteRoomActivity";
    /** 和 iOS 名字一致 */
    final String EVENT_NAME = "WhiteCommandCustomEvent";

    final String SCENE_DIR = "/dir";
    final String ROOM_INFO = "room info";
    final String ROOM_ACTION = "room action";
    final Gson gson = new Gson();
    final DemoAPI demoAPI = DemoAPI.get();

    private String uuid;
    private String roomToken;
    private boolean mIsFollow = false;

    private WhiteboardView whiteboardView;
    private Room room;

    private CirclePointView mTopbar_ColorSelect;
    private TextView mTopbar_scaling;
    private TextView mTopbar_pages;
    private View mBottomBar;
    private View mBottomIcon;
    private LinearLayout mShowViewRe;
    private View mBottomBar_shapeSelect;
    private PopupWindow mRectanglePopu = null;

    private PopupWindow mColorPopuWindow;
    private NoScrollGridView mColorGridView;
    private ColorSelectAdapter mColorSelectAdapter;

    private int[] mColorArr;
    private int mLastColor;
    private int mLastTextSize;
    private int mLastStrokeWidth;

    /**
     * 自定义 GlobalState 示例
     * 继承自 GlobalState 的子类，然后调用 {@link WhiteDisplayerState#setCustomGlobalStateClass(Class)}
     */
    class MyGlobalState extends GlobalState {
        public String getOne() {
            return one;
        }

        public void setOne(String one) {
            this.one = one;
        }

        String one;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        whiteboardView.removeAllViews();
        whiteboardView.destroy();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_white_room);

        whiteboardView = findViewById(R.id.white);
        DWebView.setWebContentsDebuggingEnabled(true);
        whiteboardView.getSettings().setAllowUniversalAccessFromFileURLs(true);

        /*
          使用阿里云的 HttpDns，避免 DNS 污染等问题
         */
        useHttpDnsService(false);

        // 使用 LocalFileWebViewClient 对 动态 ppt 拦截进行替换，先查看本地是否有，如果没有再发出网络请求
        LocalFileWebViewClient client = new LocalFileWebViewClient();
        client.setPptDirectory(getCacheDir().getAbsolutePath());
        whiteboardView.setWebViewClient(client);
        initView();
        Intent intent = getIntent();
        String uuid = intent.getStringExtra(JoinRoomActivity.EXTRA_MESSAGE);
        String name = intent.getStringExtra(JoinRoomActivity.EXTRA_MESSAGE_NAME);
        String joinToken = intent.getStringExtra(JoinRoomActivity.EXTRA_MESSAGE_ROOMTOKEN);

        if (!TextUtils.isEmpty(name)) {
            SettingManager.get().setName(name);
        }

        joinRoom(uuid, joinToken);
    }

    private void initView() {
        setListener(R.id.topbar_revoke);
        setListener(R.id.topbar_cancelrevoke);
        setListener(R.id.topbar_location);
        mTopbar_ColorSelect = (CirclePointView) setListener(R.id.topbar_color);
        mTopbar_scaling = findViewById(R.id.topbar_scaling);
        setListener(R.id.topbar_preview);
        setListener(R.id.topbar_first);
        setListener(R.id.topbar_previous);
        mTopbar_pages = findViewById(R.id.topbar_pages);
        setListener(R.id.topbar_next);
        setListener(R.id.topbar_last);
        setListener(R.id.topbar_follow);
        setListener(R.id.topbar_folder);
        setListener(R.id.topbar_share);
        setListener(R.id.topbar_exit);

        setListener(R.id.bottombar_1);
        setListener(R.id.bottombar_2);
        setListener(R.id.bottombar_3);
        setListener(R.id.bottombar_4);
        mBottomBar_shapeSelect = setListener(R.id.bottombar_5);
        setListener(R.id.bottombar_6);
        setListener(R.id.bottombar_7);
        setListener(R.id.bottombar_8);
        setListener(R.id.bottombar_9);
        setListener(R.id.bottombar_10);

        setListener(R.id.bottombar_icon);
        mBottomBar = findViewById(R.id.bottom_bar);
        mBottomIcon = findViewById(R.id.bottombar_icon);
        mShowViewRe = findViewById(R.id.showview_group);

        mTopbar_scaling.setText("100%");
        mTopbar_pages.setText("1/1");
        mColorArr = getResources().getIntArray(R.array.color_arr);
        mLastColor = mColorArr[0];
        mLastTextSize = 10;
        mLastStrokeWidth = 4;

    }

    private View setListener(int id) {
        View view = findViewById(id);
        if (view != null) {
            SelectUtil.setSelect(view);
            view.setOnClickListener(this);
        }
        return view;
    }

    //region room
    private void createRoom() {
        demoAPI.getNewRoom(new DemoAPI.Result() {
            @Override
            public void success(String uuid, String roomToken) {
                joinRoom(uuid, roomToken);
            }

            @Override
            public void fail(String message) {
                alert("创建房间失败", message);
            }
        });
    }

    private void getRoomToken(final String uuid) {
        demoAPI.getRoomToken(uuid, new DemoAPI.Result() {
            @Override
            public void success(String uuid, String roomToken) {
                joinRoom(uuid, roomToken);
            }

            @Override
            public void fail(String message) {
                alert("获取房间 token 失败", message);
            }
        });
    }

    private void joinRoom(String uuid, String roomToken) {
        logRoomInfo("room uuid: " + uuid + "\nroomToken: " + roomToken);

        //存档一下，方便重连
        this.uuid = uuid;
        this.roomToken = roomToken;

        WhiteSdkConfiguration sdkConfiguration = new WhiteSdkConfiguration(demoAPI.getAppIdentifier(), true);

        /*显示用户头像*/
        sdkConfiguration.setUserCursor(true);

        //动态 ppt 需要的自定义字体，如果没有使用，无需调用
        HashMap<String, String> map = new HashMap<>();
        map.put("宋体","https://your-cdn.com/Songti.ttf");
        sdkConfiguration.setFonts(map);

        //图片替换 API，需要在 whiteSDKConfig 中先行调用 setHasUrlInterrupterAPI，进行设置，否则不会被回调。
        WhiteSdk whiteSdk = new WhiteSdk(whiteboardView, WhiteRoomActivity.this, sdkConfiguration,
                new UrlInterrupter() {
                    @Override
                    public String urlInterrupter(String sourceUrl) {
                        return sourceUrl;
                    }
                });

        /** 设置自定义全局状态，在后续回调中 GlobalState 直接进行类型转换即可 */
        WhiteDisplayerState.setCustomGlobalStateClass(MyGlobalState.class);

        whiteSdk.setCommonCallbacks(new CommonCallbacks() {
            @Override
            public String urlInterrupter(String sourceUrl) {
                return sourceUrl;
            }

            @Override
            public void sdkSetupFail(SDKError error) {
                Log.e("ROOM_ERROR", error.toString());
            }

            @Override
            public void throwError(Object args) {

            }

            @Override
            public void onPPTMediaPlay() {
                logAction();
            }

            @Override
            public void onPPTMediaPause() {
                logAction();
            }
        });

        //如需支持用户头像，请在设置 WhiteSdkConfiguration 后，再调用 setUserPayload 方法，传入符合用户信息
        RoomParams roomParams = new RoomParams(uuid, roomToken);

        final Date joinDate = new Date();
        logRoomInfo("native join " + joinDate);
        whiteSdk.joinRoom(roomParams, new AbstractRoomCallbacks() {
            @Override
            public void onPhaseChanged(RoomPhase phase) {
                //在此处可以处理断连后的重连逻辑
                showToast(phase.name());
            }

            @Override
            public void onRoomStateChanged(RoomState modifyState) {
                Log.d(TAG, "onRoomStateChanged:" + modifyState);
                if (modifyState != null) {
                    Log.d(TAG, "" + modifyState.getZoomScale() + "," + modifyState.getBroadcastState() + "," + modifyState.getMemberState()
                            + "," + modifyState.getGlobalState()
                            + "," + modifyState.getRoomMembers()
                            + "," + modifyState.getSceneState()
                    );
                    if (modifyState.getZoomScale() != null){
                        mTopbar_scaling.setText( (int)(modifyState.getZoomScale().doubleValue() * 100) + "%" );
                    }
                }
                logRoomInfo(gson.toJson(modifyState));
            }
        }, new Promise<Room>() {
            @Override
            public void then(Room wRoom) {
                //记录加入房间消耗的时长
                logRoomInfo("native join in room duration: " + (new Date().getTime() - joinDate.getTime()) / 1000f + "s");
                room = wRoom;
                room.disableSerialization(false);
                addCustomEventListener();
            }

            @Override
            public void catchEx(SDKError t) {
                showToast(t.getMessage());
            }
        });
    }
    //endregion

    //region private
    private void alert(final String title, final String detail) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog alertDialog = new AlertDialog.Builder(WhiteRoomActivity.this).create();
                alertDialog.setTitle(title);
                alertDialog.setMessage(detail);
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                finish();
                            }
                        });
                alertDialog.show();
            }
        });
    }

    private void useHttpDnsService(boolean use) {
        if (use) {
            /** 直接使用此 id 即可，sdk 已经在阿里云 HttpDns 后台做过配置 */
            HttpDnsService httpDnsService = HttpDns.getService(getApplicationContext(), "188301");
            httpDnsService.setPreResolveHosts(new ArrayList<>(Arrays.asList("expresscloudharestoragev2.herewhite.com", "cloudharev2.herewhite.com", "scdncloudharestoragev3.herewhite.com", "cloudcapiv4.herewhite.com")));
            whiteboardView.setWebViewClient(new WhiteWebViewClient(httpDnsService));
        }
    }

    private void addCustomEventListener() {
        room.addMagixEventListener(EVENT_NAME, new EventListener() {
            @Override
            public void onEvent(EventEntry eventEntry) {
                logRoomInfo("customEvent payload: " + eventEntry.getPayload().toString());
                showToast(gson.toJson(eventEntry.getPayload()));
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        logRoomInfo( "width:" + whiteboardView.getWidth() / getResources().getDisplayMetrics().density + " height: " + whiteboardView.getHeight() / getResources().getDisplayMetrics().density);
        // onConfigurationChanged 调用时，横竖屏切换并没有完成，需要延迟调用
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                room.refreshViewSize();
                logRoomInfo( "width:" + whiteboardView.getWidth() / getResources().getDisplayMetrics().density + " height: " + whiteboardView.getHeight() / getResources().getDisplayMetrics().density);
            }
        }, 1000);
    }

    //endregion

    //region menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.room_command, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return true;
    }

    private CameraBound customBound(double maxScale) {
        CameraBound bound = new CameraBound();
        bound.setCenterX(0d);
        bound.setCenterY(0d);
        bound.setHeight((double) (whiteboardView.getHeight() / this.getResources().getDisplayMetrics().density));
        bound.setWidth((double) (whiteboardView.getWidth() / this.getResources().getDisplayMetrics().density));
        ContentModeConfig contentModeConfig = new ContentModeConfig();
        contentModeConfig.setScale(maxScale);
        contentModeConfig.setMode(ContentModeConfig.ScaleMode.CENTER_INSIDE_SCALE);
        bound.setMaxContentMode(contentModeConfig);
        return bound;
    }

    public void scalePptToFit(MenuItem item) {
        room.scalePptToFit(AnimationMode.Continuous);
    }

    public void reconnect(MenuItem item) {
        final WhiteRoomActivity that = this;
        room.disconnect(new Promise<Object>() {
            @Override
            public void then(Object b) {
                joinRoom(that.uuid, that.roomToken);
            }

            @Override
            public void catchEx(SDKError t) {

            }
        });
    }

    public void setWritableFalse(MenuItem item) {
        room.setWritable(false, new Promise<Boolean>() {
            @Override
            public void then(Boolean aBoolean) {
                logRoomInfo("room writable: " + aBoolean);
            }

            @Override
            public void catchEx(SDKError t) {

            }
        });
    }

    public void setWritableTrue(MenuItem item) {
        room.setWritable(true, new Promise<Boolean>() {
            @Override
            public void then(Boolean aBoolean) {
                logRoomInfo("room writable: " + aBoolean);
            }

            @Override
            public void catchEx(SDKError t) {

            }
        });
    }

    @SuppressLint("SourceLockedOrientationActivity")
    public void orientation(MenuItem item) {
        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            WhiteRoomActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            WhiteRoomActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    public void setBound(MenuItem item) {
        CameraBound bound = customBound(3);
        room.setCameraBound(bound);
    }

    public void nextScene(MenuItem item) {
        int nextIndex = room.getSceneState().getIndex() + 1;
        room.setSceneIndex(nextIndex, new Promise<Boolean>() {
            @Override
            public void then(Boolean result) {

            }

            @Override
            public void catchEx(SDKError t) {

            }
        });
    }

    public void undoRedoOperation(MenuItem item) {
        // 需要开启本地序列化，才能操作 redo undo
        room.disableSerialization(false);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                room.undo();
            }
        }, 10000);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                room.redo();
            }
        }, 15000);
    }

    public void duplicate(MenuItem item) {
        room.duplicate();
    }

    public void copyPaste(MenuItem item) {
        room.copy();
        room.paste();
    }

    public void deleteOperation(MenuItem item) {
        room.deleteOperation();
    }

    public void getPreviewImage(MenuItem item) {
        room.getScenePreviewImage("/init", new Promise<Bitmap>() {
            @Override
            public void then(Bitmap bitmap) {
                logAction("get bitmap");
            }

            @Override
            public void catchEx(SDKError t) {
                logAction("get bitmap error");
            }
        });
    }

    public void getSceneImage(MenuItem item) {
        room.getSceneSnapshotImage("/init", new Promise<Bitmap>() {
            @Override
            public void then(Bitmap bitmap) {
                logAction("get bitmap");
            }

            @Override
            public void catchEx(SDKError t) {
                logAction("get bitmap error");
            }
        });
    }

    public void staticConvert(MenuItem item) {
        Converter c = new Converter(this.roomToken);
        c.startConvertTask("https://white-cn-edge-doc-convert.oss-cn-hangzhou.aliyuncs.com/LightWaves.pdf", Converter.ConvertType.Static, new ConverterCallbacks(){
            @Override
            public void onFailure(ConvertException e) {
                logAction(e.getMessage());
            }

            @Override
            public void onFinish(ConvertedFiles ppt, ConversionInfo convertInfo) {
                room.putScenes("/static", ppt.getScenes(), 0);
                room.setScenePath("/static/1");
                logAction(convertInfo.toString());
            }

            @Override
            public void onProgress(Double progress, ConversionInfo convertInfo) {
                logAction(String.valueOf(progress));
            }
        });
    }

    public void dynamicConvert(MenuItem item) {
        Converter c = new Converter(this.roomToken);
        c.startConvertTask("https://white-cn-edge-doc-convert.oss-cn-hangzhou.aliyuncs.com/-1/1.pptx", Converter.ConvertType.Dynamic, new ConverterCallbacks(){
            @Override
            public void onFailure(ConvertException e) {
                logAction(e.getMessage());
            }

            @Override
            public void onFinish(ConvertedFiles ppt, ConversionInfo convertInfo) {
                room.putScenes("/dynamic", ppt.getScenes(), 0);
                room.setScenePath("/dynamic/1");
                logAction(convertInfo.toString());
            }

            @Override
            public void onProgress(Double progress, ConversionInfo convertInfo) {
                logAction(String.valueOf(progress));
            }
        });
    }

    public void broadcast(MenuItem item) {
        logAction();
        room.setViewMode(ViewMode.Broadcaster);
    }

    public void getBroadcastState(MenuItem item) {
        logAction();
        BroadcastState broadcastState = room.getBroadcastState();
        showToast(broadcastState.getMode());
        logRoomInfo(gson.toJson(broadcastState));
    }

    public void moveCamera(MenuItem item) {
        logAction();
        CameraConfig config = new CameraConfig();
        config.setCenterX(100d);
        room.moveCamera(config);
    }

    public void moveRectangle(MenuItem item) {
        logAction();
        RectangleConfig config = new RectangleConfig(200d, 400d);
        room.moveCameraToContainer(config);
    }

    public void dispatchCustomEvent(MenuItem item) {
        logAction();
        HashMap<String, String> payload = new HashMap<>();
        payload.put("device", "android");

        room.dispatchMagixEvent(new AkkoEvent(EVENT_NAME, payload));
    }

    public void cleanScene(MenuItem item) {
        logAction();
        room.cleanScene(true);
    }

    public void insertNewScene(MenuItem item) {
        logAction();
        room.putScenes(SCENE_DIR, new Scene[]{
                new Scene("page1")}, 0);
        room.setScenePath(SCENE_DIR + "/page1");
    }

    public void insertPPT(MenuItem item) {
        logAction();
        room.putScenes(SCENE_DIR, new Scene[]{
                new Scene("page2", new PptPage("https://white-pan.oss-cn-shanghai.aliyuncs.com/101/image/alin-rusu-1239275-unsplash_opt.jpg", 600d, 600d))
        }, 0);
        room.setScenePath(SCENE_DIR + "/page2");
    }

    public void insertImage(MenuItem item) {
        room.insertImage(new ImageInformationWithUrl(0d, 0d, 100d, 200d, "https://white-pan.oss-cn-shanghai.aliyuncs.com/40/image/mask.jpg"));
    }

    public void getScene(MenuItem item) {
        logAction();
        logAction(gson.toJson(room.getScenes()));
    }

    public void getRoomPhase(MenuItem item) {
        logAction();
        logRoomInfo("RoomPhase: " + gson.toJson(room.getRoomPhase()));
    }

    public void getRoomState(MenuItem item) {
        logAction();
        //获取房间状态，包含很多信息
        logRoomInfo("roomState: " + gson.toJson(room.getRoomState()));
    }

    public void disconnect(MenuItem item) {

        //如果需要房间断开连接后回调
        room.disconnect(new Promise<Object>() {
            @Override
            public void then(Object o) {
                logAction("disconnect success");
            }

            @Override
            public void catchEx(SDKError t) {

            }
        });

        //如果不需要回调，则直接断开连接即可
        //room.disconnect();
    }

    public void disableOperation(MenuItem item) {
        logAction();
        room.disableOperations(true);
    }

    public void cancelDisableOperation(MenuItem item) {
        logAction();
        room.disableOperations(false);
    }

    public void textArea(MenuItem item) {
        logAction();
        MemberState memberState = new MemberState();
        memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
        memberState.setCurrentApplianceName(Appliance.TEXT);
        memberState.setStrokeWidth(mLastStrokeWidth);
        memberState.setTextSize(mLastTextSize);
        room.setMemberState(memberState);
    }

    public void selector(MenuItem item) {
        logAction();
        MemberState memberState = new MemberState();
        memberState.setCurrentApplianceName(Appliance.SELECTOR);
        room.setMemberState(memberState);
    }

    public void pencil(MenuItem item) {
        logAction();
        MemberState memberState = new MemberState();
        memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
        memberState.setCurrentApplianceName(Appliance.PENCIL);
        memberState.setStrokeWidth(mLastStrokeWidth);
        memberState.setTextSize(mLastTextSize);
        room.setMemberState(memberState);
    }

    public void rectangle(MenuItem item) {
        logAction();
        MemberState memberState = new MemberState();
        memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
        memberState.setCurrentApplianceName(Appliance.RECTANGLE);
        memberState.setStrokeWidth(mLastStrokeWidth);
        memberState.setTextSize(mLastTextSize);
        room.setMemberState(memberState);
    }

    public void color(MenuItem item) {
        logAction();
        MemberState memberState = new MemberState();
        memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
        memberState.setCurrentApplianceName(Appliance.PENCIL);
        memberState.setStrokeWidth(mLastStrokeWidth);
        memberState.setTextSize(mLastTextSize);
        room.setMemberState(memberState);
    }

    public void convertPoint(MenuItem item) {
        //获取特定点，在白板内部的坐标点
        room.convertToPointInWorld(0, 0, new Promise<Point>() {
            @Override
            public void then(Point point) {
                logRoomInfo(gson.toJson(point));
            }

            @Override
            public void catchEx(SDKError t) {
                Logger.error("convertToPointInWorld  error", t);
            }
        });
    }

    public void externalEvent(MenuItem item) {
        logAction();
    }

    public void zoomChange(MenuItem item) {
        CameraConfig cameraConfig = new CameraConfig();
        if (room.getZoomScale() != 1) {
            cameraConfig.setScale(1d);
        } else {
            cameraConfig.setScale(5d);
        }
        room.moveCamera(cameraConfig);
    }

    //endregion

    //region log
    void logRoomInfo(String str) {
        Log.i(ROOM_INFO, Thread.currentThread().getStackTrace()[3].getMethodName() + " " + str);
    }

    void logAction(String str) {
        Log.i(ROOM_ACTION, Thread.currentThread().getStackTrace()[3].getMethodName() + " " + str);
    }

    void logAction() {
        Log.i(ROOM_ACTION, Thread.currentThread().getStackTrace()[3].getMethodName());
    }

    void showToast(Object o) {
        Log.i("showToast", o.toString());
        Toast.makeText(this, o.toString(), Toast.LENGTH_SHORT).show();
    }

    //endregion

    @Override
    public void onClick(View v) {
        if (room == null) {
            return;
        }

        MemberState memberState;
        switch (v.getId()) {
            case R.id.topbar_revoke:
                // 需要开启本地序列化，才能操作 redo undo
//                room.disableSerialization(false);
                room.undo();
                break;
            case R.id.topbar_cancelrevoke:
                // 需要开启本地序列化，才能操作 redo undo
//                room.disableSerialization(false);
                room.redo();
                break;
            case  R.id.topbar_location:
                break;
            case R.id.topbar_color:
                logAction();
                showColorSelect();
                break;
            case R.id.topbar_preview:
                showViewPreview();
                break;
            case R.id.topbar_first:
//                int nextIndex = room.getSceneState().getIndex() + 1;
                mTopbar_pages.setText("1/" + room.getScenes().length);
                room.setSceneIndex(0, new Promise<Boolean>() {
                    @Override
                    public void then(Boolean result) {

                    }

                    @Override
                    public void catchEx(SDKError t) {

                    }
                });
                break;
            case R.id.topbar_previous:
                int previous = room.getSceneState().getIndex() - 1;
                if (previous < 0) {
                    return;
                }
                mTopbar_pages.setText("" + (previous + 1) + "/" + room.getScenes().length);
                room.setSceneIndex(previous, new Promise<Boolean>() {
                    @Override
                    public void then(Boolean result) {

                    }

                    @Override
                    public void catchEx(SDKError t) {

                    }
                });
                break;
            case R.id.topbar_next:
                int maxIndex = room.getScenes().length - 1;
                int nextIndex = room.getSceneState().getIndex() + 1;
                if (nextIndex > maxIndex ) {
                    return;
                }
                mTopbar_pages.setText("" + (nextIndex + 1) + "/" + room.getScenes().length);
                room.setSceneIndex(nextIndex, new Promise<Boolean>() {
                    @Override
                    public void then(Boolean result) {

                    }

                    @Override
                    public void catchEx(SDKError t) {

                    }
                });
                break;
            case R.id.topbar_last:
                mTopbar_pages.setText("" + room.getScenes().length + "/" + room.getScenes().length);
                room.setSceneIndex(room.getScenes().length - 1, new Promise<Boolean>() {
                    @Override
                    public void then(Boolean result) {

                    }

                    @Override
                    public void catchEx(SDKError t) {

                    }
                });
                break;
            case R.id.topbar_follow:
                logAction();
                if (!mIsFollow) {
                    mIsFollow = true;
                    room.setViewMode(ViewMode.Follower);
                } else {
                    mIsFollow = false;
                    room.setViewMode(ViewMode.Freedom);
                }
                break;
            case R.id.topbar_folder:break;
            case R.id.topbar_share:break;
            case R.id.topbar_exit:
                finish();
                break;

            case R.id.bottombar_1:
                logAction();
                memberState = new MemberState();
                memberState.setCurrentApplianceName(Appliance.SELECTOR);
                room.setMemberState(memberState);
                break;
            case R.id.bottombar_2:
                logAction();
                memberState = new MemberState();
                memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
                memberState.setCurrentApplianceName(Appliance.PENCIL);
                memberState.setStrokeWidth(mLastStrokeWidth);
                memberState.setTextSize(mLastTextSize);
                room.setMemberState(memberState);
                break;
            case R.id.bottombar_3:
                logAction();
                memberState = new MemberState();
                memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
                memberState.setCurrentApplianceName(Appliance.TEXT);
                memberState.setStrokeWidth(mLastStrokeWidth);
                memberState.setTextSize(mLastTextSize);
                room.setMemberState(memberState);
                break;
            case R.id.bottombar_4:
                memberState = new MemberState();
                memberState.setCurrentApplianceName(Appliance.ERASER);
                room.setMemberState(memberState);
                break;
            case R.id.bottombar_5:
                //矩形或者圆形
                showRectangleSelect();
                break;
            case R.id.bottombar_6:
                logAction();
                memberState = new MemberState();
                memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
                memberState.setCurrentApplianceName(Appliance.ARROW);
                memberState.setStrokeWidth(mLastStrokeWidth);
                memberState.setTextSize(mLastTextSize);
                room.setMemberState(memberState);
                break;
            case R.id.bottombar_7:
                logAction();
                memberState = new MemberState();
                memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
                memberState.setCurrentApplianceName(Appliance.STRAIGHT);
                memberState.setStrokeWidth(mLastStrokeWidth);
                memberState.setTextSize(mLastTextSize);
                room.setMemberState(memberState);
                break;
            case R.id.bottombar_8:
                logAction();
                memberState = new MemberState();
                memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
                memberState.setCurrentApplianceName(Appliance.LASER_POINTER);
                memberState.setStrokeWidth(mLastStrokeWidth);
                memberState.setTextSize(mLastTextSize);
                room.setMemberState(memberState);
                break;
            case R.id.bottombar_9:
                //上传弹窗
                break;
            case R.id.bottombar_10:
                //缩小bottombar
                mBottomBar.setVisibility(View.GONE);
                mBottomIcon.setVisibility(View.VISIBLE);
                break;
            case R.id.bottombar_icon:
                //显示bottombar
                mBottomBar.setVisibility(View.VISIBLE);
                mBottomIcon.setVisibility(View.GONE);
                break;
        }
    }

    private void showColorSelect() {
        if (mColorPopuWindow == null) {
            View popu = LayoutInflater.from(this).inflate(R.layout.layout_room_popu_color_select, null);
            //参数为1.View 2.宽度 3.高度
            mColorPopuWindow = new PopupWindow(popu, CommonUtil.dp2px(this, 240), CommonUtil.dp2px(this, 180));
            //设置点击外部区域可以取消popupWindow
            mColorPopuWindow.setOutsideTouchable(true);


            mColorGridView = popu.findViewById(R.id.gridview_color_select);
            mColorSelectAdapter = new ColorSelectAdapter(this);
            mColorSelectAdapter.setList(mColorArr);
            mColorGridView.setAdapter(mColorSelectAdapter);

            mColorGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    mLastColor = mColorArr[position];
                    MemberState memberState = new MemberState();
                    memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
                    memberState.setCurrentApplianceName(Appliance.PENCIL);
                    memberState.setStrokeWidth(mLastStrokeWidth);
                    memberState.setTextSize(mLastTextSize);
                    room.setMemberState(memberState);
                    if (position == mColorArr.length -1) {
                        mTopbar_ColorSelect.setCircleColor(R.color.white_for_colorselect);
                    } else {
                        mTopbar_ColorSelect.setCircleColor(mLastColor);
                    }
                    mColorPopuWindow.dismiss();
                }
            });
        }
        mColorSelectAdapter.notifyDataSetChanged();
        //设置popupWindow显示,并且告诉它显示在那个View下面
        mColorPopuWindow.showAsDropDown(mTopbar_ColorSelect);

    }

    private void showViewPreview() {
        mShowViewRe.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.layout_showview_preview, null);

        View addPage = view.findViewById(R.id.showview_add_page);
        SelectUtil.setSelect(addPage);
        addPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logAction();
                int size = room.getSceneState().getScenes().length;
                room.putScenes(SCENE_DIR, new Scene[]{
                        new Scene("page1")}, 0);
                room.setScenePath(SCENE_DIR + "/page1");
                room.getScenes(new Promise<Scene[]>() {
                    @Override
                    public void then(Scene[] scenes) {
                        Log.d(TAG, "add and syn getScens:" + scenes.length);
                    }

                    @Override
                    public void catchEx(SDKError t) {

                    }
                });
            }
        });
        View viewBg = view.findViewById(R.id.showview_bg);
        viewBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShowViewRe.removeAllViews();
            }
        });

        ListView pageList = view.findViewById(R.id.showview_preview_list);
        PageSelectAdapter pageSelectAdapter = new PageSelectAdapter(this);
        pageSelectAdapter.setListener(new PageSelectAdapter.OnPreviewItemClick() {
            @Override
            public void onPreviewImgClick(int position) {
                int size = room.getSceneState().getScenes().length - 1;
                if (position > size) {
                    return;
                }
                mTopbar_pages.setText("" + (position + 1) + "/" + room.getScenes().length);
                room.setSceneIndex(position, new Promise<Boolean>() {
                    @Override
                    public void then(Boolean result) {
                        if (result != null && result == true) {
                            pageSelectAdapter.setIndex(room.getSceneState().getIndex());
                            pageSelectAdapter.setList(room.getScenes());
                            pageSelectAdapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void catchEx(SDKError t) {

                    }
                });
            }

            @Override
            public void onPreviewDelClick(int position) {
                int size = room.getSceneState().getScenes().length - 1;
                if (position > size) {
                    return;
                }
                mTopbar_pages.setText("" + (position + 1) + "/" + room.getScenes().length);
                room.removeScenes(SCENE_DIR + "/page" + position);
                pageSelectAdapter.setIndex(room.getSceneState().getIndex());
                pageSelectAdapter.setList(room.getScenes());
                pageSelectAdapter.notifyDataSetChanged();

            }

            @Override
            public void getBitmap(int position, Promise<Bitmap> promise) {
                String path = SCENE_DIR + "/page" + position;
                if (position == 0) {
                    path = "/init";
                }
                room.getScenePreviewImage(path, promise);
            }
        });
        pageSelectAdapter.setList(room.getScenes());
        pageList.setAdapter(pageSelectAdapter);
        mShowViewRe.addView(view);
    }

    private void showRectangleSelect() {

        if (mRectanglePopu == null) {
            View popu = LayoutInflater.from(this).inflate(R.layout.layout_room_shape_select, null);
            //参数为1.View 2.宽度 3.高度
            mRectanglePopu = new PopupWindow(popu, CommonUtil.dp2px(this, 50), CommonUtil.dp2px(this, 100));
            //设置点击外部区域可以取消popupWindow
            mRectanglePopu.setOutsideTouchable(true);

            View view = popu.findViewById(R.id.square);
            SelectUtil.setSelect(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    logAction();
                    MemberState memberState = new MemberState();
                    memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
                    memberState.setCurrentApplianceName(Appliance.RECTANGLE);
                    memberState.setStrokeWidth(mLastStrokeWidth);
                    memberState.setTextSize(mLastTextSize);
                    room.setMemberState(memberState);
                    mRectanglePopu.dismiss();
                }
            });

            view = popu.findViewById(R.id.circular);
            SelectUtil.setSelect(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    logAction();
                    MemberState memberState = new MemberState();
                    memberState.setStrokeColor(ColorUtil.changeColor2Arr(mLastColor));
                    memberState.setCurrentApplianceName(Appliance.ELLIPSE);
                    memberState.setStrokeWidth(mLastStrokeWidth);
                    memberState.setTextSize(mLastTextSize);
                    room.setMemberState(memberState);
                    mRectanglePopu.dismiss();
                }
            });
        }
        //设置popupWindow显示,并且告诉它显示在那个View下面
        mRectanglePopu.showAsDropDown(mBottomBar_shapeSelect);
    }
}
