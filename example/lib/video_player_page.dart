import 'dart:io';

import 'package:m3u8_downloader/m3u8_downloader.dart';
import 'package:video_player/video_player.dart';
import 'package:flutter/material.dart';


//视频播放器封装需要使用动态类
class VideoM38UPlayerPage extends StatefulWidget {
  @override
  _VideoPlayerPageState createState() => _VideoPlayerPageState();
}

//继承VideoApp类
class _VideoPlayerPageState extends State<VideoM38UPlayerPage> {

  //定义一个VideoPlayerController
  VideoPlayerController? _controller;

  //重写类方法initState()，初始化界面
  @override
  void initState()  {
    super.initState();
    print("_VideoPlayerPageState init");
    //设置视频参数 (..)是级联的意思
  }


  void initController() {
    String url = "https://m3u8.afhklsdd.com/5678954c76ece8b4345ae437e7344367e22e2G1o.m3u8";
    M3u8Downloader.searchInfo(url).then((result) {
      if (result is Map) {
        Map fileInfoMap = result;
        String? filePath = fileInfoMap["m3u8Path"];
       // filePath = filePath?.replaceAll("local.m3u8", "remote.m3u8");
        print(filePath);
        if (filePath?.isNotEmpty == true) {

          _controller = VideoPlayerController.file(File(filePath!))
            ..initialize().then((value) {
              setState(() {
                if (_controller == null) {
                  _controller?.addListener(() {setState(() {

                  });});
                  print("_controller success");
                  _controller?.play();
                }
              });
            });
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: Text("back"),
      ),
      body: Center(
        child: (_controller?.value.isInitialized ?? false)
            ? AspectRatio(
          aspectRatio: _controller!.value.aspectRatio,
          child: VideoPlayer(_controller!),
        )
            : Container(
          child: Text("没有要播放的视频"),
        ),
      ),

      //右下角图标按钮onPressed中需要调用setState方法，用于刷新界面
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          setState(() {
            if (_controller != null) {
              _controller!.value.isPlaying
                  ? _controller!.pause() : _controller!.play();
            }else{
              initController();
            }
          });
        },
        child: Icon(
          (_controller?.value.isPlaying ?? false)? Icons.pause : Icons.play_arrow,
        ),
      ),
    );
  }

  //dispose():程序中是用来关闭一个GUI页面的
  //视频播放完需要把页面关闭
  @override
  void dispose() {
    super.dispose();
    _controller?.dispose();
  }
}