package com.vincent.m3u8Downloader.downloader;

import android.text.TextUtils;

import com.vincent.m3u8Downloader.bean.M3U8;
import com.vincent.m3u8Downloader.listener.OnM3U8DownloadListener;
import com.vincent.m3u8Downloader.listener.OnTaskDownloadListener;
import com.vincent.m3u8Downloader.utils.M3U8Log;
import com.vincent.m3u8Downloader.utils.M3U8Util;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;


/**
 * @Author: Vincent
 * @CreateAt: 2021/08/25 22:17
 * @Desc: M3U8下载器
 */
public class M3U8Downloader {
    private static M3U8Downloader instance;

    public M3U8DownloadTask m3U8DownLoadTask;
    private OnM3U8DownloadListener onM3U8DownloadListener;
    private long currentTime;

    private M3U8Downloader() {

    }
    public static M3U8Downloader getInstance(){
        if (null == instance) {
            instance = new M3U8Downloader();
        }
        return instance;
    }

    public void setOnM3U8DownloadListener(OnM3U8DownloadListener onM3U8DownloadListener) {
        this.onM3U8DownloadListener = onM3U8DownloadListener;
    }

    /**
     * 防止快速点击引起ThreadPoolExecutor频繁创建销毁引起crash
     * @return 是否快速点击
     */
    private boolean isQuicklyClick(){
        boolean result = false;
        if (System.currentTimeMillis() - currentTime <= 100){
            result = true;
            M3U8Log.d("is too quickly click!");
        }
        currentTime = System.currentTimeMillis();
        return result;
    }

    public boolean isFinished(String url){
        String dirPath = M3U8Util.getSaveFileDir(url);
        File file = new File(dirPath);
        File mp4File = new File(dirPath + "/loacl.mp4");
        if(file.exists() && file.isDirectory()){
            if (!mp4File.exists()){

            }
            return  true;
        }
        return  false;
    }

    public HashMap<String, String> searchTaskInfo(String url)  {

        HashMap<String, String> infoMap = new HashMap<String, String>();
        String currentTaskUrl = null;
        if(m3U8DownLoadTask != null
        && m3U8DownLoadTask.isRunning()){
            currentTaskUrl =  m3U8DownLoadTask.m3u8Url;
        }

        if(currentTaskUrl != null && currentTaskUrl.equals(url)){
            infoMap.put("isLoaderRunning", "1");
        }else {
            infoMap.put("isLoaderRunning", "0");
            M3U8Log.d("searchTaskInfo url===" + currentTaskUrl);
            M3U8Log.d("searchTaskInfo url===" + url);
        }

        File file = new File(M3U8Util.getSaveFileDir(url));

        if(currentTaskUrl != null && currentTaskUrl.equals(url)){
            double progress = m3U8DownLoadTask.progress();
            infoMap.put("progress", String.format("%.2f", progress));
        }else if(file.exists() && file.isDirectory()){
            infoMap.put("progress", "100.00");
            infoMap.put("localPath", M3U8Util.getSaveFileDir(url) + File.separator + M3U8DownloadTask.LOCAL_FILE_NAME);
        }else {
            File tmpDir = new File(M3U8Util.getSaveFileDirTmp(url));
            if(tmpDir.exists() && tmpDir.isDirectory()){
                String m3u8InfoFilePath = M3U8Util.getSaveFileDirTmp(url) + File.separator + M3U8DownloadTask.REMOTE_FILE_NAME;
                File m3u8InfoFile = new File(m3u8InfoFilePath);
                if (m3u8InfoFile.exists()){
                    try {
                        M3U8 m3u8 = M3U8Util.parseIndex(url, m3u8InfoFilePath);
                        File[] files = tmpDir.listFiles();
                        int allTaskCount = m3u8.getTsList().size() + 3;
                        int finishCount = files.length;
                        if(allTaskCount > 3){
                            double progress =  finishCount*100.0/allTaskCount;
                            infoMap.put("progress", String.format("%.2f", progress));
                        }else {
                            infoMap.put("progress", "0.00");
                            infoMap.put("error", "m3u8 文件解析错误");
                        }
                    }catch (Error | IOException e){
                        infoMap.put("progress", "0.00");
                        infoMap.put("error", "m3u8 文件异常");
                    }
                }else {
                    infoMap.put("progress", "0.00");
                }
            }else {
                infoMap.put("progress", "0.00");
            }
        }
        return  infoMap;
    }

    /**
     * 下载m3u8
     * @param url m3u8下载地址
     */
    public void download(String url) {
        if (TextUtils.isEmpty(url) || isQuicklyClick()) return;

        // 暂停之前的
        if(m3U8DownLoadTask != null){
            m3U8DownLoadTask.stop();
            m3U8DownLoadTask = null;
        }

        // 开启新的下载
        m3U8DownLoadTask = new M3U8DownloadTask();

        try {
            M3U8Log.d("start downloading: " + url);
            m3U8DownLoadTask.download(url, onM3U8DownloadListener);
        } catch (Exception e){
            e.printStackTrace();
            M3U8Log.e("startDownloadTask Error:"+e.getMessage());
        }
    }


    /**
     * 暂停任务（非当前任务）
     */
    public void pause(String url){
        M3U8Log.d("pause download: " + url);
        if(m3U8DownLoadTask != null && m3U8DownLoadTask.m3u8Url.equals(url)){
            m3U8DownLoadTask.stop();
        }
    }

    public void pauseAll(){
        if(m3U8DownLoadTask != null){
            m3U8DownLoadTask.stop();
            m3U8DownLoadTask = null;
        }
    }
    /**
     * 删除下载文件。非线程安全
     * @param url 下载地址
     * @return 删除状态
     */
    public boolean delete(final String url){
        if (m3U8DownLoadTask != null && m3U8DownLoadTask.m3u8Url.equals(url)) {
            m3U8DownLoadTask.stop();
            m3U8DownLoadTask = null;
        }
        String saveDir = M3U8Util.getSaveFileDir(url);
        String saveDirTmp = M3U8Util.getSaveFileDirTmp(url);
        // 删除文件夹
        boolean isDelete = M3U8Util.clearDir(new File(saveDir));
        boolean isDeleteTmp = M3U8Util.clearDir(new File(saveDirTmp));
        return isDelete && isDeleteTmp;
    }
    public  boolean deleteAll() {
        if (m3U8DownLoadTask != null) {
            m3U8DownLoadTask.stop();
        }
        String saveDir = M3U8Util.getSaveDir();
        boolean isDelete = M3U8Util.clearDir(new File(saveDir));
        return  isDelete;
    }
    /**
     * 是否正在下载
     * @return 运行状态
     */
    public boolean isRunning(){
        return m3U8DownLoadTask.isRunning();
    }

    /**
     * 下载任务监听器
     */
    private final OnTaskDownloadListener onTaskDownloadListener = new OnTaskDownloadListener() {
        private long lastLength;
        private float downloadProgress;

        @Override
        public void onStart() {
//            currentM3U8Task.setState(M3U8TaskState.PREPARE);
//            if (onM3U8DownloadListener != null){
//                onM3U8DownloadListener.onDownloadPrepare(currentM3U8Task);
//            }
//            M3U8Log.d("onDownloadPrepare: "+ currentM3U8Task.getUrl());
        }

        @Override
        public void onStartDownload(int totalTs, int curTs) {
            M3U8Log.d("onStartDownload: "+totalTs+"|"+curTs);
//
//            currentM3U8Task.setState(M3U8TaskState.DOWNLOADING);
//            if (totalTs > 0) {
//                downloadProgress = 1.0f * curTs / totalTs;
//            }
        }

        @Override
        public void onDownloadItem(long itemFileSize, int totalTs, int curTs) {

        }

        @Override
        public void onProgress(M3U8DownloadTask tas) {
//            if (onM3U8DownloadListener != null && m3U8DownLoadTask != null){
//                currentM3U8Task.setProgress(tas.progress());
//                onM3U8DownloadListener.onDownloadProgress(currentM3U8Task);
//            }
        }

        @Override
        public void onConvert() {
            M3U8Log.d("onConvert!");
            if (onM3U8DownloadListener != null){
                onM3U8DownloadListener.onConvert();
            }
        }


        @Override
        public void onSuccess(M3U8 m3U8) {
            M3U8Log.d("m3u8 Downloader onSuccess: "+ m3U8);
            m3U8DownLoadTask.stop();
//            currentM3U8Task.setM3U8(m3U8);
//            currentM3U8Task.setState(M3U8TaskState.SUCCESS);
//            if (onM3U8DownloadListener != null) {
//                onM3U8DownloadListener.onDownloadSuccess(currentM3U8Task);
//            }
        }

        @Override
        public void onError(Throwable error) {
//            if (error.getMessage() != null && error.getMessage().contains("ENOSPC")){
//                currentM3U8Task.setState(M3U8TaskState.ENOSPC);
//            }else {
//                currentM3U8Task.setState(M3U8TaskState.ERROR);
//            }
//            if (onM3U8DownloadListener != null) {
//                onM3U8DownloadListener.onDownloadError(currentM3U8Task.getUrl(), error);
//            }
//            M3U8Log.e("onError: " + error.getMessage());
        }
    };
}
