package org.remitot.m3u8.download;

import java.net.URL;

public interface Downloader {
  void downloadNextTs(URL ts, int i);
}
