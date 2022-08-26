import 'dart:io';
import 'dart:isolate';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:m3u8_downloader/m3u8_downloader.dart';
import 'package:m3u8_downloader_example/video_player_page.dart';
import 'package:open_file/open_file.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  ReceivePort _port = ReceivePort();
  String? _downloadingUrl;

  //
  //http://m3u8.afhklsdd.com/d1af20263f1b11cc685ba52e842f6659.m3u8
  // 未加密的url地址（喜羊羊与灰太狼之决战次时代）
  //String url1 = "http://m3u8.afhklsdd.com/2eb056a7a1fc2774aa536421939d73a49652eac7.m3u8"; //"https://cdn.605-zy.com/20210713/MiJecHrZ/index.m3u8";
  // 加密的url地址（火影忍者疾风传）
  //String url2 = "https://v3.dious.cc/20201116/SVGYv7Lo/index.m3u8";
  String url2 =
      "http://m3u8.afhklsdd.com/7266e82a230a18ef457fe82b6c853fad.m3u8";

  //"http://m3u8.afhklsdd.com/7266e82a230a18ef457fe82b6c853fad.m3u8";
  //"https://m3u8.afhklsdd.com/fd1ff0da268f722901030204c0c1edbf.m3u8";//"http://m3u8.afhklsdd.com/d1af20263f1b11cc685ba52e842f6659.m3u8";
  dynamic taskInfo;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance?.addPostFrameCallback((timeStamp) {
      initAsync();
    });
  }

  dynamic progress;

  void initAsync() async {
    String saveDir = await _findSavePath();
    print(saveDir);
    bool result = await M3u8Downloader.initialize(onSelect: () async {
      print('下载成功点击');
      return null;
    });
    print("initialize====$result");
    await M3u8Downloader.config(
      saveDir: saveDir,
      threadCount: 5,
      convertMp4: true,
      debugMode: true,
      progressCallback: progressCallback,
      successCallback: successCallback,
      errorCallback: errorCallback,
    );
    // 注册监听器
    IsolateNameServer.registerPortWithName(
        _port.sendPort, 'downloader_send_port');
    _port.listen((dynamic data) {
      // 监听数据请求
      print(data);
      progress = data;
      setState(() {});
    });
  }

  Future<bool> _checkPermission() async {
    var status = await Permission.storage.status;
    if (!status.isGranted) {
      status = await Permission.storage.request();
    }
    return status.isGranted;
  }

  Future<String> _findSavePath() async {
    var directory =
        await getExternalStorageDirectory(); //getApplicationSupportDirectory();
    String saveDir = directory!.path + '/vPlayDownload';
    Directory root = Directory(saveDir);
    if (!root.existsSync()) {
      await root.create();
    }
    print(saveDir);
    return saveDir;
  }

  static progressCallback(dynamic args) {
    //print("progressCallback====$args");
    final SendPort? send =
        IsolateNameServer.lookupPortByName('downloader_send_port');
    if (send != null) {
      args["status"] = 1;
      send.send(args);
    }
  }

  static successCallback(dynamic args) {
    print("successCallback====$args");
    final SendPort? send =
        IsolateNameServer.lookupPortByName('downloader_send_port');
    if (send != null) {
      send.send({
        "status": 2,
        "url": args["url"],
        "filePath": args["filePath"],
        "dir": args["dir"]
      });
    }
  }

  static errorCallback(dynamic args) {
    print("errorCallback====$args");
    final SendPort? send =
        IsolateNameServer.lookupPortByName('downloader_send_port');
    if (send != null) {
      send.send({"status": 3, "url": args["url"]});
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(
          children: <Widget>[
            ElevatedButton(
              child: Text("${_downloadingUrl == url2 ? '暂停' : '下载'}已加密m3u8"),
              onPressed: () {
                if (_downloadingUrl == url2) {
                  // 暂停
                  setState(() {
                    _downloadingUrl = null;
                  });
                  M3u8Downloader.pause(url2);
                  return;
                }
                // 下载
                _checkPermission().then((hasGranted) async {
                  if (hasGranted) {
                    await M3u8Downloader.config(
                      convertMp4: true,
                    );
                    setState(() {
                      _downloadingUrl = url2;
                    });
                    final result = await M3u8Downloader.download(
                      url: url2,
                      name: "下载已加密m3u8",
                    );
                    print("=========download:$result");
                    if (result != null) {
                      setState(() {
                        _downloadingUrl = null;
                      });
                    }
                  }
                });
              },
            ),
            ElevatedButton(
              child: Text("打开已下载的已加密的文件"),
              onPressed: () async {
                final result = await M3u8Downloader.download(
                    url: url2,
                    name: "下载已加密m3u8",
                    progressCallback: progressCallback,
                    successCallback: successCallback,
                    errorCallback: errorCallback);
                print("=========download:$result");
                if (result != null) {
                  setState(() {
                    _downloadingUrl = null;
                  });
                }
              },
            ),
            ElevatedButton(
              child: Text("清空下载"),
              onPressed: () async {
                await M3u8Downloader.delete(url2);
                print("清理完成");
              },
            ),
            ElevatedButton(
              child: Text(taskInfo?.toString() ?? "查询"),
              onPressed: () async {
                taskInfo = await M3u8Downloader.searchInfo(url2);
                print("=======taskInfo:");
                print(taskInfo);
                print("=======taskInfo:");
                setState(() {});
              },
            ),
            Container(
              child: Text(progress?.toString() ?? "progress"),
            ),
            ElevatedButton(
              child: Text("movie"),
              onPressed: () {
                print("Navigator push:${context}");
                Navigator.of(context).push(MaterialPageRoute(
                  builder: (context) => VideoM38UPlayerPage(),
                ));
                // Navigator.of(context).push(MaterialPageRoute(builder: (context){
                //   print("Navigator push VideoPlayerPage");
                //   return VideoPlayerPage();
                // }));
              },
            ),
            Container(
              height: 300,
              width: 400,
              child: VideoM38UPlayerPage(),
            ),
          ],
        ),
      ),
    );
  }
}
