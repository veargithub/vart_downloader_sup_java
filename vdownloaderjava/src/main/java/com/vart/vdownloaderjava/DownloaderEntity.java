package com.vart.vdownloaderjava;

public class DownloaderEntity {

    public ThreadInfo threadInfo;
    public FileInfo fileInfo;
    public Status status;

    public static class ThreadInfo {//下载状态
        long start = 0; //从文件的什么地方开始下载
        long end = 0; //下载到文件的什么位置
        long offset = 0; //已经下载了多少

        public ThreadInfo(long start, long end, long offset) {
            this.start = start;
            this.end = end;
            this.offset = offset;
        }
    }

    public static class FileInfo {//文件信息
        String url = ""; //下载的url
        public String fileName =""; //下载完成后的文件名
        public String dictionary =""; //文件下载到sd卡的什么位置，默认是/data/data/Android/包名/{dictionary}/{fileName}

        public FileInfo(String url, String fileName, String dictionary) {
            this.url = url;
            this.fileName = fileName;
            this.dictionary = dictionary;
        }
    }

    enum Status {

        pending, //未下载、暂停
        downloading, //下载中
        complete //下载已完成

    }
}
