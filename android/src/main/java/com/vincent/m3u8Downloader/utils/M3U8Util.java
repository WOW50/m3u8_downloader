package com.vincent.m3u8Downloader.utils;

import android.annotation.SuppressLint;
import com.vincent.m3u8Downloader.downloader.M3U8DownloadConfig;
import com.vincent.m3u8Downloader.bean.M3U8;
import com.vincent.m3u8Downloader.bean.M3U8Ts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * @Author: Vincent
 * @CreateAt: 2021/08/25 21:43
 * @Desc: M3U8工具类
 */
public class M3U8Util {

    /**
     * 将Url转换为M3U8对象
     * @param url url地址
     * @return M3U8对象
     * @throws IOException IO异常
     */
    public static M3U8 parseIndex(String url, String filePath) throws IOException {
        M3U8 ret = new M3U8();
        URL baseUrl = new URL(url);
        File remoteFile = new File(filePath);
        BufferedReader reader;
        Boolean fromLocalFile = false;
        if (remoteFile.exists() && remoteFile.length() > 100) {
            File dir = new File(remoteFile.getParent());
            if (dir.isDirectory()) {
                int count = dir.listFiles().length - 1;
                if (count > 0){
                    ret.localTSFileCount = count;
                }
            }
            fromLocalFile = true;
            reader = new BufferedReader(new FileReader(filePath));
        }else {
            reader = new BufferedReader(new InputStreamReader(baseUrl.openStream()));
        }

        ret.setBaseUrl(url);
        StringBuilder strBuilder = new StringBuilder();
        String line;
        float seconds = 0;
        while ((line = reader.readLine()) != null) {
            if (!fromLocalFile) {
                strBuilder.append(line + "\n");
            }
            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF:")) {
                    line = line.substring(8);
                    if (line.endsWith(",")) {
                        line = line.substring(0, line.length() - 1);
                    }
                    seconds = Float.parseFloat(line);
                } else if (line.startsWith("#EXT-X-KEY:")) {
                    String[] lineInfoArr = line.split("#EXT-X-KEY:");
                    line = lineInfoArr[1];
                    String[] arr = line.split(",");
                    for (String s : arr) {
                        if (s.contains("=")) {
                            String k = s.split("=")[0];
                            String v = s.split("=")[1];
                            if (k.equals("URI")) {
                                // 获取key
                                v = v.replaceAll("\"", "");
                                v = v.replaceAll("'", "");
                                ret.methodKeyURL = v;
//                                if(Thread.currentThread() != Looper.getMainLooper().getThread()){
//                                    // 只有查询信息才走主线程, key 本地化
//                                    BufferedReader keyReader = new BufferedReader(new InputStreamReader(new URL(baseUrl, v).openStream()));
//                                    ret.setKey(keyReader.readLine());
//                                    M3U8Log.d("m3u8 key: " + ret.getKey());
//                                }
                            } else if (k.equals("IV")) {
                                // 获取IV
                                ret.setIv(v);
                                M3U8Log.d("m3u8 IV: " + v);
                            }else if(k.equals("METHOD")){
                                ret.methodCode = v;
                            }
                        }
                    }
                }
                continue;
            }
            if (line.endsWith("m3u8")) {
                return parseIndex(new URL(baseUrl, line).toString(), filePath + "sub.m3u8");
            }
            ret.addTs(new M3U8Ts(line, seconds));
            seconds = 0;
        }
        reader.close();
        if(!fromLocalFile) {
            BufferedWriter bfw = new BufferedWriter(new FileWriter(filePath, false));
            bfw.write(strBuilder.toString());
            bfw.close();
        }
        return ret;
    }

    /**
     * 生成AES-128加密本地m3u8索引文件，ts切片和m3u8文件放在相同目录下即可
     * @param m3U8 m3u8文件
     * @param keyPath 加密key
     */
    public static void createLocalM3U8(String fileName, M3U8 m3U8, String keyPath, String iv) throws IOException{
        M3U8Log.d("createLocalM3U8: " + fileName);
        String basePath = M3U8Util.getSaveFileDir(m3U8.getBaseUrl());
        BufferedWriter bfw = new BufferedWriter(new FileWriter(fileName, false));
        bfw.write("#EXTM3U\n");
        bfw.write("#EXT-X-VERSION:3\n");
        bfw.write("#EXT-X-MEDIA-SEQUENCE:0\n");
        bfw.write("#EXT-X-TARGETDURATION:13\n");
        if (keyPath != null) {
            String keyContent = "#EXT-X-KEY:METHOD=" + m3U8.methodCode + ",URI=" ;
           // keyContent = keyContent  + basePath + "/" + keyPath + "\"";
            keyContent = keyContent + "\"" + m3U8.methodKeyURL + "\"";
            if(iv != null){
                keyContent = keyContent + "," + "IV=" + iv;
            }
            keyContent = keyContent + "\n";
            bfw.write(keyContent);
        }
        for (M3U8Ts m3U8Ts : m3U8.getTsList()) {
            bfw.write("#EXTINF:" + m3U8Ts.getSeconds()+",\n");
            bfw.write( "file://" + basePath + "/" + m3U8Ts.obtainEncodeTsFileName());
           // M3U8Log.d("file://" + basePath + "/" + m3U8Ts.obtainEncodeTsFileName());
            bfw.newLine();
        }
        bfw.write("#EXT-X-ENDLIST");
        bfw.flush();
        bfw.close();
    }

    /**
     * 清空文件夹
     * @param dir 文件夹/文件地址
     * @return  删除状态
     */
    public static boolean clearDir(File dir) {
        if (dir.exists()) {
            if (dir.isFile()) {
                return dir.delete();
            } else if (dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null && files.length > 0) {
                    for (File file : files) {
                        clearDir(file);
                    }
                }
                return dir.delete();
            }
        }
        return true;
    }


    private static final float KB = 1024;
    private static final float MB = 1024 * KB;
    private static final float GB = 1024 * MB;

    /**
     * 格式化文件大小
     * @param size 文件大小
     * @return 格式化字符串
     */
    @SuppressLint("DefaultLocale")
    public static String formatFileSize(long size){
        if (size >= GB) {
            return String.format("%.1f GB", size / GB);
        } else if (size >= MB) {
            float value = size / MB;
            return String.format(value > 100 ? "%.0f MB" : "%.1f MB", value);
        } else if (size >= KB) {
            float value =  size / KB;
            return String.format(value > 100 ? "%.0f KB" : "%.1f KB", value);
        } else {
            return String.format("%d B", size);
        }
    }

    /**
     * 生成本地m3u8索引文件，ts切片和m3u8文件放在相同目录下即可
     * @param m3U8 m3u8文件
     */
    public static void createLocalM3U8(String fileName, M3U8 m3U8) throws IOException{
        createLocalM3U8(fileName, m3U8, null, null);
    }


    /**
     * 保存文件
     * @param text 文件内容
     * @param fileName 文件名
     * @throws IOException IO异常
     */
    public static void saveFile(String text, String fileName) throws IOException{
        File file = new File(fileName);
        BufferedWriter out = new BufferedWriter(new FileWriter(file));
        out.write(text);
        out.flush();
        out.close();
    }

    /**
     * 获取url保存的地址
     * @param url 请求地址
     * @return 地址
     */
    public static String getSaveFileDir(String url){
        return M3U8DownloadConfig.getSaveDir() + File.separator + EncryptUtil.md5Encode(url);
    }
    public static String getSaveFileDirTmp(String url){
        return M3U8DownloadConfig.getSaveDir() + File.separator + EncryptUtil.md5Encode(url) + "_tmp";
    }

    public static String getSaveDir(){
        return M3U8DownloadConfig.getSaveDir();
    }

}
