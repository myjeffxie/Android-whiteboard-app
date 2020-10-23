package com.herewhite.demo;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.herewhite.demo.manager.SettingManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by buhe on 2018/8/16.
 */

public class DemoAPIv5 {

    static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    /**
     * 请在 https://console.herewhite.com 中注册";
     */
    private static String sdkToken = "请在 https://console.herewhite.com 中注册";

    private static final String host = "https://shunt-api.netless.link/v5";

    public String getAppIdentifier() {
        return AppIdentifier;
    }
    private String TAG = "demo api";
    private String AppIdentifier = "792/uaYcRG0I7ctP9A";
    private String demoUUID = "daef60b584ea4892a381c410ae15fe28";
    private String demoRoomToken = "WHITEcGFydG5lcl9pZD1ZSEpVMmoxVXAyUzdoQTluV3dvaVlSRVZ3MlI5M21ibmV6OXcmc2lnPWJkODdlOGFkZDcwZmEzN2YzNWQ3OTAyYmViMWFlMDk2YjQ1ZWI0MmM6YWRtaW5JZD02Njcmcm9vbUlkPWRhZWY2MGI1ODRlYTQ4OTJhMzgxYzQxMGFlMTVmZTI4JnRlYW1JZD03OTImcm9sZT1yb29tJmV4cGlyZV90aW1lPTE2MTIwMzU1MTgmYWs9WUhKVTJqMVVwMlM3aEE5bld3b2lZUkVWdzJSOTNtYm5lejl3JmNyZWF0ZV90aW1lPTE1ODA0Nzg1NjYmbm9uY2U9MTU4MDQ3ODU2NTczODAw";
    private static DemoAPIv5 mApi;

    private DemoAPIv5() {
        if (!TextUtils.isEmpty(BuildConfig.sdkTokenByEnv)) {
            sdkToken = BuildConfig.sdkTokenByEnv;
        }
        if (!TextUtils.isEmpty(BuildConfig.APPIDENTIFIER)) {
            AppIdentifier = BuildConfig.APPIDENTIFIER;
        }
    }

    public static DemoAPIv5 get() {
        if (mApi == null) {
            synchronized (DemoAPIv5.class) {
                if (mApi == null) {
                    mApi = new DemoAPIv5();
                }
            }
        }
        return mApi;
    }

    String getDemoUUID() {
        return demoUUID;
    }

    private OkHttpClient client = new OkHttpClient();
    private Gson gson = new Gson();

    public boolean hasDemoInfo() {
        if (TextUtils.isEmpty(BuildConfig.sdkTokenByEnv)) {
            return demoUUID.length() > 0 && demoRoomToken.length() > 0;
        }
        return false;
    }

    public boolean validateToken() {
        return hasDemoInfo() || sdkToken.length() > 50;
    }

    public interface Result {
        void success(String uuid, String roomToken);
        void fail(String message);
    }

    public void getNewRoom(String name, final Result result) {

        createRoom(name, 100, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                result.fail("网络请求错误：" + e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    int code = response.code();
                    if (code == 200 || code == 201) {
                        JsonObject room = gson.fromJson(response.body().string(), JsonObject.class);
                        String uuid = room.get("uuid").getAsString();
                        getRoomToken(uuid, new Callback() {

                            @Override
                            public void onFailure(Call call, IOException e) {
                                result.fail("网络请求错误：" + e.toString());
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                int code = response.code();
                                if (code == 200 || code == 201) {
                                    String token = response.body().string();
                                    token = token.replaceAll("\"", "");
                                    result.success(uuid, token);
                                } else {
                                    assert response.body() != null;
                                    result.fail("创建房间失败：" + response.body().string());
                                }
                            }
                        });

                    } else {
                        assert response.body() != null;
                        result.fail("创建房间失败：" + response.body().string());
                    }
                } catch (Throwable e) {
                    result.fail("网络请求错误：" + e.toString());
                }
            }
        });
    }

    private void createRoom(String name, int limit, Callback callback) {

        Map<String, Object> roomSpec = new HashMap<>();
        roomSpec.put("name", name);
        roomSpec.put("limit", limit);
        roomSpec.put("isRecord", true);

        RequestBody body = RequestBody.create(JSON, gson.toJson(roomSpec));

        Request request = new Request.Builder()
                .url(host + "/rooms")
                .addHeader("token", sdkToken)
                .post(body)
                .build();

        Call call = client.newCall(request);
        call.enqueue(callback);
    }

    public void getRoomToken(final String uuid, final Callback callback) {

        Map<String, Object> roomSpec = new HashMap<>();
        roomSpec.put("lifespan", 0);
        roomSpec.put("role", "admin");

        RequestBody body = RequestBody.create(JSON, gson.toJson(roomSpec));
        Request request = new Request.Builder()
                .url(host + "/tokens/rooms/" + uuid)
                .addHeader("token", sdkToken)
                .post(body)
                .build();
        Call call = client.newCall(request);
        call.enqueue(callback);
    }

    public void getRoomToken(final String uuid, final Result result) {

        Map<String, Object> roomSpec = new HashMap<>();
        roomSpec.put("lifespan", 0);
        roomSpec.put("role", "admin");

        RequestBody body = RequestBody.create(JSON, gson.toJson(roomSpec));
        Request request = new Request.Builder()
                .url(host + "/tokens/rooms/" + uuid)
                .addHeader("token", sdkToken)
                .post(body)
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                result.fail("网络请求错误：" + e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    assert response.body() != null;
                    int code = response.code();
                    if (code == 200 || code == 201) {
                        String token = response.body().string();
                        token = token.replaceAll("\"", "");
                        Log.d(TAG, "get token:" + token);
                        result.success(uuid, token);
                    } else {
                        result.fail("获取房间 token 失败：" + response.body().string());
                    }
                } catch (Throwable e) {
                    result.fail("网络请求错误：" + e.toString());
                }
            }
        });
    }

    public void downloadZip(String zipUrl, String des) {
        Request request = new Request.Builder().url(zipUrl).build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "download error: " + e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException(("下载失败: " + response));
                }
                String path = des + "/convertcdn.netless.link/dynamicConvert";
                File file = new File(path);
                if (!file.exists()) {
                    boolean success = file.mkdirs();
                    Log.i("LocalFile", "success: " + success + " path: " + path);
                } else {
                    Log.i("LocalFile", path + " is exist");
                }

                FileOutputStream fos = new FileOutputStream(path + "/1.zip", false);
                fos.write(response.body().bytes());
                fos.close();
                unzip(new File(path + "/1.zip"), new File(path));
                Log.i("LocalFile", "unzip");
                //标记已下载解压处理完成,下次不要重复.
                SettingManager.get().setDownLoadZip(true);
            }
        });
    }

    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
    }

}
