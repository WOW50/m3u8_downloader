package com.vincent.m3u8Downloader.downloader;

import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.vincent.m3u8Downloader.bean.M3U8;
import com.vincent.m3u8Downloader.bean.M3U8Ts;
import com.vincent.m3u8Downloader.listener.OnInfoCallback;
import com.vincent.m3u8Downloader.listener.OnM3U8DownloadListener;
import com.vincent.m3u8Downloader.utils.M3U8Log;
import com.vincent.m3u8Downloader.utils.M3U8Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: Vincent
 * @CreateAt: 2021/08/25 22:26
 * @Desc: M3U8下载任务
 */


public class M3U8DownloadTask {

    public static final String LOCAL_FILE_NAME = "local.m3u8";
    public static final String REMOTE_FILE_NAME = "remote.m3u8";
    public static final String M3U8_KEY_NAME = "key.key";

    private static final int WHAT_ON_ERROR = 1001;
    private static final int WHAT_ON_PROGRESS = 1002;
    private static final int WHAT_ON_SUCCESS = 1003;
    private static final int WHAT_ON_START_DOWNLOAD = 1004;
    private static final int WHAT_ON_CONVERT = 1005;


    // 文件保存地址
    public String m3u8Url;
    // 文件保存地址
    private String saveDir;
    // 当前M3U8
    public M3U8 currentM3U8;
    // 线程池
    private ExecutorService executor;

    // 任务是否正在运行
    private boolean isRunning = false;
    // 当前已经在下完成的大小
    private long curLength = 0;
    // 当前下载完成的文件个数
    private final AtomicInteger curTs = new AtomicInteger(0);
    // 总文件个数
    private volatile int totalTs = 0;
    // 单个文件的大小
    private volatile long itemFileSize = 0;
    // 下载任务监听器
    int connTimeout;
    int readTimeout;
    int threadCount;


    public float progress(){
        if (totalTs <= 0){
            return  0;
        }else {
            return (float) (curTs.get() * 100.0/totalTs);
        }
    }

    private final WeakHandler mHandler = new WeakHandler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            return true;
        }
    });

    public M3U8DownloadTask() {
        connTimeout = M3U8DownloadConfig.getConnTimeout();
        readTimeout = M3U8DownloadConfig.getReadTimeout();
        threadCount = M3U8DownloadConfig.getThreadCount();
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 开始下载
     * @param url m3u8下载地址
     * @param onTaskDownloadListener 任务下载监听器
     */
    public void download(final String url,  final OnM3U8DownloadListener onTaskDownloadListener) {
        m3u8Url = url;
        saveDir = M3U8Util.getSaveFileDirTmp(url);
        File dirTmp = new File(saveDir);
        if(!dirTmp.exists()){
            if(!dirTmp.mkdir()){
                M3U8Log.e( "fail:" +  dirTmp.getPath());
            }
        }
        isRunning = true;
        getM3U8Info(url, new OnInfoCallback() {// 获取m3u8
            @Override
            public void success(final M3U8 m3u8) {
                currentM3U8 = m3u8;
                start(m3u8, onTaskDownloadListener);
            }

            @Override
            public void error(Exception e) {
                isRunning = false;
                currentM3U8 = null;
                onTaskDownloadListener.onDownloadError(url, e.getCause());
            }
        });
    }

    /**
     * 获取m3u8信息
     * @param url m3u8地址
     * @param callback 回调函数
     */
    private synchronized void getM3U8Info(final String url, final OnInfoCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    M3U8 m3u8 = M3U8Util.parseIndex(url, saveDir + File.separator + REMOTE_FILE_NAME);
                    curTs.set(m3u8.localTSFileCount);
                    totalTs = m3u8.getTsList().size();
                    callback.success(m3u8);
                } catch (Exception e) {
                    callback.error(e);
                }
            }
        }).start();
    }

    /**
     * 开始下载
     */
    private void start(final M3U8 m3u8Model,  final OnM3U8DownloadListener onTaskDownloadListener) {
        new Thread() {
            @Override
            public void run() {
                try {
                    batchDownloadTs(m3u8Model, onTaskDownloadListener);// 开始下载
                    if (isRunning) {
                        String m3u8Path = saveDir + File.separator + LOCAL_FILE_NAME;
                        if (TextUtils.isEmpty(currentM3U8.methodKeyURL)) {
                            M3U8Util.createLocalM3U8(m3u8Path, currentM3U8);
                        } else {
                            M3U8Util.createLocalM3U8(m3u8Path, currentM3U8, M3U8_KEY_NAME, currentM3U8.getIv());
                        }

                        File file = new File(saveDir);
                        if (file.isDirectory()){
                            String finishPath = M3U8Util.getSaveFileDir(m3u8Url);
                            if(file.renameTo(new File(finishPath))){
                                currentM3U8.setLocalPath(finishPath + File.separator + LOCAL_FILE_NAME);
                                currentM3U8.setDirPath(finishPath);
                            }else{
                                M3U8Log.e("movie cache==文件替换失败" + file.getPath());
                            }
                        }
                        isRunning = false;
                        onTaskDownloadListener.onDownloadSuccess(M3U8DownloadTask.this);
                    }
                } catch (InterruptedIOException e) {
                    // 被中断了，使用stop时会抛出这个，不需要处理
                    isRunning = false;
                    onTaskDownloadListener.onDownloadError(m3u8Url, e.getCause());
                } catch (IOException e) {
                    isRunning = false;
                    onTaskDownloadListener.onDownloadError(m3u8Url, e.getCause());
                    handlerError(e);
                } catch (Exception e) {
                    isRunning = false;
                    onTaskDownloadListener.onDownloadError(m3u8Url, e.getCause());
                    handlerError(e);
                }
            }
        }.start();
    }

    /**
     * 批量下载ts切片
     * @param m3u8Info M3U8对象
     */
    private void batchDownloadTs(final M3U8 m3u8Info, final OnM3U8DownloadListener onTaskDownloadListener) {
        final File dir = new File(saveDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        if (!TextUtils.isEmpty(m3u8Info.getKey())) {
            try {// 保存key文件
                M3U8Util.saveFile(m3u8Info.getKey(), saveDir + File.separator + "key.key");
            } catch (IOException e) {
                M3U8Log.e("saveFile fail!" + e.getMessage());
                handlerError(e);
            }
        }
        M3U8Log.d("Downloading !");
        final String basePath = m3u8Info.getBaseUrl();
        for (final M3U8Ts m3u8Ts : m3u8Info.getTsList()) {
            if (!isRunning){
                return;
            }
            netTaskLoad(m3u8Ts, dir, basePath, onTaskDownloadListener);
        }
    }


    private void netTaskLoad(M3U8Ts m3u8Ts, File dir, String basePath, final OnM3U8DownloadListener onTaskDownloadListener){
        File file;
        String fileName = dir + File.separator + m3u8Ts.obtainEncodeTsFileName();
        try {
            file = new File(fileName);
        } catch (Exception e) {
            file = new File(dir + File.separator + m3u8Ts.getUrl());
        }

        if (!file.exists()) {
            M3U8Log.d("ts load url=========" + m3u8Ts.getUrl());
            FileOutputStream fos = null;
            InputStream inputStream = null;
            boolean readFinished = false;
            try {
                URL url = m3u8Ts.obtainFullUrl(basePath);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(connTimeout);
                conn.setReadTimeout(readTimeout);
                if (conn.getResponseCode() == 200) {
                    inputStream = conn.getInputStream();
                    File tmpFile = new File(fileName + "_tmp");
                    if(tmpFile.exists()){
                        tmpFile.delete();
                    }
                    fos = new FileOutputStream(tmpFile);//会自动创建文件
                    int len;
                    byte[] buf = new byte[1024];
                    while ((len = inputStream.read(buf)) != -1) {
                        curLength += len;
                        fos.write(buf, 0, len);//写入流中
                    }
                    fos.close();
                    if(!tmpFile.renameTo(file)){
                        M3U8Log.e("rename file fail:" + tmpFile.getPath());
                    }
                } else {
                    handlerError(new Throwable("ts:" + url + "netCode:" + conn.getResponseCode()));
                }
                readFinished = true;
            } catch (MalformedURLException e) {
                M3U8Log.e("MalformedURLException" + e.getMessage());
                handlerError(e);
            } catch (IOException e) {
                M3U8Log.e("IOException" + e.getMessage());
                handlerError(e);
            } catch (Error e) {
                M3U8Log.e("Error=" + e.getMessage());
            } finally {
                // 如果没有读取完，则删除
                if (!readFinished && file.exists()) {
                    file.delete();
                }
                // 关流
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                itemFileSize = file.length();
                m3u8Ts.setFileSize(itemFileSize);
                curTs.incrementAndGet();
                onTaskDownloadListener.onDownloadProgress(this);
            }

        } else {
            itemFileSize = file.length();
            m3u8Ts.setFileSize(itemFileSize);
        }
    }

    /**
     * 停止任务
     */
    public void stop() {
        M3U8Log.d("=========task stop");
        isRunning = false;
    }

    /**
     * 处理异常
     * @param e 异常信息
     */
    private void handlerError(Throwable e) {
        if (!"Task running".equals(e.getMessage())) {
           // stop();
        }
        // 不提示被中断的情况
        if ("thread interrupted".equals(e.getMessage())) {
            return;
        }
        e.printStackTrace();
//        Message msg = Message.obtain();
//        msg.obj = e;
//        msg.what = WHAT_ON_ERROR;
//        mHandler.sendMessage(msg);
    }

    /**
     * 获取m3u8本地路径
     * @param url m3u8地址
     * @return 文件路径
     */
    public static String getM3U8Path(String url) {
        return M3U8Util.getSaveFileDir(url) + File.separator + LOCAL_FILE_NAME;
    }

}
