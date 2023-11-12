package org.remitot.m3u8.download;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Function;

public class Downloader {

  protected final File outFolder;
  protected final String filenamePrefix;
  protected final Function<URL, URLConnection> connector;
  
  public Downloader(File outFolder, String filenamePrefix, Function<URL, URLConnection> connector) {
    this.outFolder = outFolder;
    this.filenamePrefix = filenamePrefix;
    this.connector = connector;
  }
  
  public static class DefaultConnector implements Function<URL, URLConnection> {
    @Override
    public URLConnection apply(URL url) {
      try {
        return url.openConnection();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  } 
  
  public static final Function<URL, URLConnection> DEFAULT_CONNECTOR = new DefaultConnector(); 
  
  public Downloader(File outFolder, String filenamePrefix) {
    this(outFolder, filenamePrefix, DEFAULT_CONNECTOR);
  }
  
  public void downloadNextTs(URL ts, int i) {
    String filename = filenamePrefix + String.format("%05d", i) + ".ts";
    File file = new File(outFolder, filename);

    try (BufferedInputStream in = new BufferedInputStream(connector.apply(ts).getInputStream());
         OutputStream os = new FileOutputStream(file)) {
      byte dataBuffer[] = new byte[1024];
      int bytesRead;
      while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
        os.write(dataBuffer, 0, bytesRead);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

